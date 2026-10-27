# Deployment Report: CoolStore

**Stage**: MigIQ Deploy (follows containerize stage `ed3cfee`)
**Skill**: mig-deploy (OpenShift Deployment with Dependency Management)
**Outcome**: ⚠️ **Live deployment blocked by sandbox environment** — deployment runbook,
graph-derived endpoint test plan, and one-command deploy script delivered instead
(see [Blocked](#1-deployment-blocked-by-sandbox-evidence) and [Runbook](#3-deployment-runbook)).

---

## 0. Executive Summary

The deploy stage was executed per the mig-deploy skill workflow: prerequisites checked,
all containerization artifacts reviewed, external dependencies mapped from
`CONTAINERIZATION_REPORT.md`, the rgctl knowledge graph re-indexed (it was missing),
and the controller/API endpoint surface extracted from the graph for post-deploy
testing.

The **actual push to an OpenShift cluster could not be performed** from this stage's
sandbox: there is no `oc` CLI, no container runtime, and the only reachable cluster
(a Kubernetes v1.37 API reachable via the in-cluster service account) is **not an
OpenShift cluster** (no `route.openshift.io` API) and **denies every write** to this
pod's service account. This matches the limitation already recorded by the
containerize stage ("no Podman/JDK/`oc` … the CI gate") — the deploy execution is the
next CI-gate item.

Everything that *can* be prepared without cluster credentials has been done and
committed:

| Deliverable | Where |
|---|---|
| Deployment runbook (exact commands, dependency order) | §3 below |
| One-command deploy script implementing the skill workflow | `scripts/deploy-openshift.sh` |
| Graph-derived post-deploy endpoint test plan (rgctl) | §4 below |
| Pre-flight manifest validation findings | §5 below |
| rgctl knowledge-graph index | rebuilt at `.rgctl/` (gitignored; regenerate via `rgctl discover`) |

---

## 1. Deployment Blocked by Sandbox (Evidence)

Per the mig-deploy skill ("Verify Before Deploy … fail fast with clear error
messages"), all prerequisites were probed before any deploy attempt:

| Check | Result | Evidence |
|---|---|---|
| `oc` CLI present | ❌ | `oc: command not found`; no binary found anywhere on the filesystem |
| OpenShift login (`oc whoami`) | ❌ impossible | no `oc`, and no cluster URL/credentials exist in the sandbox or repo |
| Container runtime (podman/docker) | ❌ | neither installed (also noted in `CONTAINERIZATION_REPORT.md` Known Issues #1) |
| Reachable cluster is OpenShift? | ❌ | in-cluster API answers `v1.37.0`; `/apis/route.openshift.io/v1` → **404**; `/apis/kafka.strimzi.io` → **404** |
| Service-account write perms | ❌ | `system:serviceaccount:default:default`; `SelfSubjectRulesReview` shows only `create selfsubjectreviews` + `get clustertrustbundles` |
| Namespace create | ❌ 403 | `namespaces … cannot create resource "namespaces" at the cluster scope` |
| Deployment create (even in own ns) | ❌ 403 | `deployments.apps … cannot create … in the namespace "default"` |
| Any other credentials | ❌ | no kubeconfig, token, or external cluster URL anywhere (filesystem + repo searched) |

The only cluster reachable from this sandbox is the migration harness's own
Kubernetes API (the stage pod itself: `migiq-migration-multistage-1793139675-deploy`,
API groups include the harness's `konveyor.io` skill-card API). It is plain
Kubernetes, not OpenShift, and the stage's service account is intentionally
read-only. **There is no target OpenShift cluster reachable from this stage.**

➡️ To run the deployment, execute `scripts/deploy-openshift.sh` (§3) from an
environment with `oc` logged into the target OpenShift cluster — the CI gate
environment defined in `mig-execute-workspace/EXECUTION_REPORT.md` is the intended
place.

---

## 2. What Was Verified (Pre-Flight)

### Containerization artifacts present (mig-deploy prerequisite #1) ✅
`Dockerfile`, `.dockerignore`, `deployment.yaml`, `service.yaml`, `route.yaml`,
`configmap.yaml`, `secret.yaml` (template), `postgresql-statefulset.yaml`,
`kafka.yaml`, `keycloak.yaml`, `realm-export.json`, `podman-compose.yml` — all at
repo root, as produced by the containerize stage.

### External dependencies (mig-deploy steps 3–5) — detected and mapped
| Dependency | Manifest | Cluster requirement | Notes |
|---|---|---|---|
| PostgreSQL 15 | `postgresql-statefulset.yaml` | PVC (default storage class), `registry.redhat.io` pull secret | DB credentials from `coolstore-secrets` (`db-username`, `db-password`) |
| Kafka (Strimzi) | `kafka.yaml` | **Strimzi / AMQ Streams operator must be installed** (`Kafka` + 3 `KafkaTopic` CRs: `orders`, `orders-dlq`, `inventory-dlq`) | Bootstrap `my-cluster-kafka-bootstrap:9092` |
| Keycloak 20 | `keycloak.yaml` + `realm-export.json` | `keycloak-admin` secret + `keycloak-realm` ConfigMap (out-of-band) | Realm `eap`, client `coolstore`; runs `start-dev` (dev-profile acceptable for dev/test) |

### rgctl knowledge graph — rebuilt this stage
The index was missing (`.rgctl/` absent, `rgctl metrics` → `graph_not_found`), so per
the stage instructions it was regenerated with the mig-rgctl deploy-readiness
profile:

```
rgctl discover . --with-cfg --with-security --with-taint --with-harmonic
# → Indexed 1328 files → 37,410 nodes / 129,830 edges
# → 24,978 communities (modularity 0.47); 26,966 functions (avg complexity 1.0)
# → security: 5 potential secrets (realm policy keys — verified non-blocking by prior stages)
# → taint: 1 flow (documented for CI security review)
# → PageRank top: external `Date` + vendored frontend JS (no app-side god nodes)
```

The graph re-confirms the containerize stage's findings: coupling is to the three
external services, not internal code; no deploy-blocking hotspots.

---

## 3. Deployment Runbook (target: any OpenShift 4.x with `oc`)

Prerequisites: `oc` logged in (`oc login <cluster-url>`); Strimzi (AMQ Streams)
operator installed; `registry.redhat.io` pull secret in the namespace; podman or
docker for the image build. The whole sequence is automated by
`scripts/deploy-openshift.sh` (same steps, with dependency existence checks).

```bash
# 0. Target namespace
oc new-project coolstore

# 1. Secrets FIRST (deployment order per skill: secrets → config → deps → app → route)
#    Replace placeholders — never commit real values (secret.yaml is a template):
oc create secret generic coolstore-secrets \
  --from-literal=db-username=coolstore \
  --from-literal=db-password='<generated>' \
  --from-literal=oidc-client-secret='<rotated-from-realm-export>'
oc create secret generic keycloak-admin \
  --from-literal=KEYCLOAK_ADMIN=admin --from-literal=KEYCLOAK_ADMIN_PASSWORD='<generated>'

# 2. Non-sensitive config
oc apply -f configmap.yaml

# 3. Keycloak realm import (59KB realm-export.json, not embedded in keycloak.yaml)
oc create configmap keycloak-realm --from-file=realm-export.json=realm-export.json

# 4. Build + push image (multi-stage Dockerfile: UBI9 JDK builder → UBI9 JRE runtime)
REGISTRY=$(oc get route default-route -n openshift-image-registry \
  -o jsonpath='{.spec.host}')           # or your quay.io namespace
podman build -t $REGISTRY/coolstore/coolstore:1.0.0 -f Dockerfile .
podman login -u $(oc whoami) -p $(oc whoami -t) $REGISTRY
podman push $REGISTRY/coolstore/coolstore:1.0.0
# then set `image:` in deployment.yaml to $REGISTRY/coolstore/coolstore:1.0.0
# (deploy-openshift.sh does this automatically via oc set-image / sed)

# 5. Dependencies (check existence first — skill step 4; script does this too)
oc apply -f kafka.yaml                    # requires Strimzi operator
oc apply -f postgresql-statefulset.yaml   # requires registry.redhat.io pull secret
oc apply -f keycloak.yaml
oc wait --for=condition=ready pod -l name=postgresql -n coolstore --timeout=300s
oc wait --for=condition=ready pod -l name=keycloak   -n coolstore --timeout=300s

# 6. Application
oc apply -f deployment.yaml
oc apply -f service.yaml
oc apply -f route.yaml
oc rollout status deployment/coolstore -n coolstore --timeout=300s

# 7. Verify (route + health + graph-derived endpoints §4)
oc get pods,svc,route -n coolstore
ROUTE_URL="https://$(oc get route coolstore -n coolstore -o jsonpath='{.spec.host}')"
curl -fsS "$ROUTE_URL/q/health" | grep '"UP"'
./scripts/openshift-smoke-test.sh "$ROUTE_URL"   # health + SPA + catalog + authz
```

Rollback: `oc rollout undo deployment/coolstore -n coolstore`; manifest-level:
`git revert <deploy-stage-commit>` (JBoss EAP path remains in history until cutover,
per TG10.5).

---

## 4. Post-Deploy Endpoint Test Plan (rgctl-derived)

Controller/API surface identified from the knowledge graph — per the rgctl skill's
structured-query verbs (preferred over the mig-deploy skill's experimental-GQL
example; `find --annotation` is the supported syntax):

```
rgctl -f json find --annotation @Path --type class
# → OrderEndpoint, CartEndpoint, ProductEndpoint  (com.redhat.coolstore.rest)
```

Cross-referenced with source annotations and the `@QuarkusTest` characterization
tests (which pin the URL base): `RestApplication @ApplicationPath("/services")` →
all resources resolve under **`/services/rest/*`** (see `RestApplication` javadoc,
decision TG1.4/TG3.3; asserted by `ProductEndpointTest`).

| # | Endpoint | Method | Auth | Expected (unauthenticated) |
|---|---|---|---|---|
| 1 | `/q/health` | GET | public | 200 `{"status":"UP"}` (liveness; DB+Kafka checks via smallrye-health) |
| 2 | `/q/health/ready` | GET | public | 200 `"UP"` (readiness gate: Flyway schema + Kafka) |
| 3 | `/` | GET | public | 200 `<!DOCTYPE html` (vendored SPA) |
| 4 | `/services/rest/products` | GET | `@PermitAll` | 200 JSON list, contains `"itemId"` |
| 5 | `/services/rest/products/{itemId}` | GET | `@PermitAll` | 200 product / 404 |
| 6 | `/services/rest/orders` | GET | `@RolesAllowed("user")` | **401** |
| 7 | `/services/rest/orders/{orderId}` | GET | `@RolesAllowed("user")` | **401** |
| 8 | `/services/rest/cart/{cartId}` | GET | `@RolesAllowed("user")` | **401** |
| 9 | `/services/rest/cart/checkout/{cartId}` | POST | `@RolesAllowed("user")` | **401** |
| 10 | `/services/rest/cart/{cartId}/{itemId}/{quantity}` | POST / DELETE | `@RolesAllowed("user")` | **401** |

Minimum smoke set (already encoded in `scripts/openshift-smoke-test.sh`):
#1, #3, #4, #8 (expect 401 — proves OIDC enforcement, not just availability).
Endpoints #6–#10 become 200 with a Keycloak `user`-role token from realm `eap`
(client `coolstore`) — recommended CI-gate addition beyond the current script.

PageRank guidance (skill: "prioritize smoke tests on hotspots"): app-side graph is
flat — top-ranked nodes are vendored frontend libraries and JDK classes, so the
endpoint list above (the entire JAX-RS surface) is complete coverage; there are no
additional hotspot controllers to prioritize.

---

## 5. Pre-Flight Manifest Findings

Validated against the skill's checklist (safe order, security contexts, probes,
resources) — all pass, with these provisioning requirements carried into the
runbook/script:

1. **`secret.yaml` is a template** — `CHANGE_ME` placeholders must be replaced
   out-of-band (never committed); the deploy script *refuses* to apply it as-is.
2. **Image placeholder** — `deployment.yaml` pins `quay.io/coolstore/coolstore:1.0.0`
   (upstream of any real registry); must be retagged to the target registry (§3 step 4).
3. **`keycloak-admin` secret + `keycloak-realm` ConfigMap** are not in any manifest
   (by design) — created out-of-band in §3 steps 1 and 3.
4. **Strimzi operator** is a hard prerequisite for `kafka.yaml` (`Kafka`/`KafkaTopic`
   CRDs are not built into OpenShift).
5. **`registry.redhat.io/rhel9/postgresql-15:latest`** requires terms-accepted pull
   secret; pin a digest for production (carried from containerize report issue #3).
6. Ephemeral Kafka storage + single replica + Keycloak `start-dev` are dev/test
   settings — production hardening list is in `CONTAINERIZATION_REPORT.md` §Next Steps.

---

## 6. Deployment Report (cluster snapshot)

*Not applicable — no workload was pushed.* Cluster state at close of stage: the
sandbox's reachable cluster (harness Kubernetes v1.37.0) reports zero CoolStore
workloads (none could be created; see §1). When the runbook/script is executed
against the target OpenShift cluster, the expected close-out state is:

```
namespace/coolstore
deployment/coolstore        2/2 ready   image: <registry>/coolstore/coolstore:1.0.0
statefulset/postgresql      1/1 ready   1Gi PVC
kafka/my-cluster + 3 topics ready (Strimzi)
deployment/keycloak         1/1 ready   realm eap imported
route/coolstore             https://coolstore-<apps-domain>  (edge TLS)
pods: all Running/Ready; smoke tests §4 green
```

## 7. Known Issues and Limitations

1. **No live deployment executed** from this sandbox (§1) — first CI-gate item:
   run `scripts/deploy-openshift.sh` from an `oc`-authenticated environment.
2. rgctl `--with-security` re-flagged the same 5 realm *policy* keys (non-secrets;
   prior stages scrub-verified) and 1 taint flow — unchanged, non-blocking, tracked
   for CI security review.
3. Image build/push is part of the script but untested here (no container runtime
   in sandbox) — the Dockerfile itself was validated (pinned UBI9 tags) by the
   containerize stage.
4. `openshift-smoke-test.sh` covers #1/#3/#4/#8 of the endpoint plan; authenticated
   tests (#6–#10 with a realm token) are a recommended CI-gate addition.

---

**Artifacts this stage adds**: `DEPLOYMENT_REPORT.md` (this file),
`scripts/deploy-openshift.sh` (runbook automation). The rgctl index is regenerated
on demand (`.gitignore`d by design).
