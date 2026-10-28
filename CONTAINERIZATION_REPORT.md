# Containerization Report: CoolStore (Quarkus)

**Stage**: MigIQ Containerize (Task Group 7 + TG 3.8/4.6/5.7 hand-offs)
**Date**: 2026-10-28
**Previous stages**: mig-prompt-builder → mig-plan → mig-execute (Quarkus 3.8.1, Java 17 migration complete with documented follow-ups)

## Executive Summary

- **Application**: CoolStore monolith — AngularJS static front end + JAX-RS REST API + JPA/Hibernate persistence + Kafka order pipeline + Keycloak OIDC
- **Language/Framework**: Java 17, Quarkus 3.8.1 (fast-jar), Maven build
- **Base Images**: `registry.access.redhat.com/ubi9/openjdk-17` (builder) → `registry.access.redhat.com/ubi9/openjdk-17-runtime` (runtime, UID 1001)
- **Deployment Target**: Red Hat OpenShift (restricted-v2 SCC compatible)
- **Build Time / Image Size / Security Scan**: **Not executed in this environment** (no podman/maven/JVM toolchain available to the containerizer session). Build, scan and push commands are provided below and must be run by CI or the deploy stage (TG 7.3).

## rgctl Graph Analysis Findings

Index rebuilt at stage start (`rgctl discover . --with-cfg --with-security --with-taint --with-harmonic`; the prior index was missing — `graph_not_found`).

### Application Structure
- **Nodes / Edges**: 36,606 / 128,218 (1,263 files; 26,953 functions analyzed)
- **Communities**: 24,269 detected (modularity 0.46) — dominated by vendored bower front-end assets; application communities map to `coolstore.model`, service/REST layers, and an "Infrastructure / Common Library" cluster
- **Hotspots (PageRank)**: top-ranked nodes are `java.util.Date` and vendored bower JS — **no application-code hotspots**, i.e. no single service class needs special probe/replica treatment beyond the standard manifests
- **Migration complexity**: **Low for containerization** — all external integrations (PostgreSQL, Kafka, Keycloak) are configuration-driven, not code-driven

### External Dependencies (from graph + config analysis)
| Dependency | Detected in | Disposition |
|---|---|---|
| PostgreSQL | `%prod.quarkus.datasource.*` (env-externalized) | StatefulSet `postgres.yaml` (dev) / managed service (prod) |
| Kafka | SmallRye Reactive Messaging channels (`orders` producer; `coolstore-orders` + `coolstore-inventory` consumer groups; `orders-dlq`, `inventory-dlq` DLQs) | AMQ Streams/Strimzi `kafka.yaml` (broker + 3 topics) |
| Keycloak (OIDC) | `quarkus.oidc.*` (env-externalized) | `keycloak.yaml` (v20.0.5, realm `eap` imported) |
| `com.enterprise:audit-logging-library:2.0.0` | pom.xml + `lib/` jar (not in any Maven repo) | Bootstrapped inside Dockerfile builder stage via `install:install-file` |

### Platform-Specific Code / Blockers
- **Hardcoded paths / localhost / file writes in Java source**: none (grep + semantic search; semantic hits were exclusively vendored bower assets)
- **`localhost` defaults in configuration**: DB/Kafka/OIDC defaults are per-environment overrides; all now externalizable via env vars (see Configuration Reference). One gap fixed this stage: Kafka bootstrap was hardcoded → `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`
- **Missing health extension**: pom.xml had no `quarkus-smallrye-health`, so `/q/health/*` probes would 404 → dependency added this stage
- **Secrets scan** (`--with-security`): 4 potential-secret findings — client secrets inside `realm-export.json` (realm fixture), README example credentials (docs), and the `QUARKUS_OIDC_CREDENTIALS_SECRET` env-var default fallback (not a real secret). No secret values are baked into the image; runtime credentials come only from OpenShift Secrets
- **Taint flow**: 1 vulnerable path in vendored bower `gulpfile.js` — not application code, out of scope (matches prior-stage triage)

## Containerization Strategy

### Base Image Selection
- **Selected**: `ubi9/openjdk-17` (builder) → `ubi9/openjdk-17-runtime` (runtime). Matches the migrated stack (Java 17, Quarkus 3.8.1 JVM mode); Red Hat supported, patched, OpenShift-native
- **Native image** (plan task 7.2): **deferred — JVM image chosen**. Rationale: custom `audit-logging-library` jar and JAXB usage add native-image risk; GraalVM build time/cost is not justified before behavior parity is proven (6 known test failures from the execute stage). The Dockerfile is native-ready in structure (multi-stage, artifact copy) if `%prod.native` is revisited

### Build Strategy
Multi-stage: (1) UBI9 OpenJDK 17 builder — installs the audit-library jar into the stage-local Maven repo (the execute stage's `install:install-file` bootstrap does not survive into a clean container build — this was a real blocker found during analysis), resolves dependencies as a cached layer, builds the fast-jar with tests skipped; (2) UBI9 OpenJDK 17 JRE runtime — copies `target/quarkus-app/` to `/deployments`, runs as UID 1001. The AngularJS front end is already embedded in the jar (maven-resources-plugin → `META-INF/resources`, TG 4.2), so the image serves it at `/`.

### Security Hardening Applied (application)
- ✅ Non-root (UID 1001, UBI OpenJDK default — verified against published image metadata)
- ✅ `readOnlyRootFilesystem: true` + writable `emptyDir` at `/tmp`
- ✅ All capabilities dropped; `allowPrivilegeEscalation: false`; `seccompProfile: RuntimeDefault`; `runAsNonRoot: true`
- ✅ Secrets only via Secret refs (env) — never baked into image or git (secret.yaml is a placeholder template)
- ✅ `.dockerignore` excludes migration artifacts, `.git`, `.rgctl`, workspaces
- ⚠️ CVE scan: **pending** — run `grype coolstore-quarkus:1.0.0 --fail-on high` before registry push (TG 7.3 gate)

### Third-Party Image Non-Root Verification (stage requirement)
Before applying the application's security context to dependency manifests, each image's non-root support was checked against published image metadata (no registry access from this session — runtime verification commands below):

| Image | Declared USER | Security context applied | Why not full hardening |
|---|---|---|---|
| `ubi9/openjdk-17-runtime` | 1001 | Full (runAsUser 1001, RO rootfs, drop ALL) | Supports non-root + RO rootfs by design |
| `rhel9/postgresql-15` | 26 | `runAsNonRoot: true` + seccomp only | Entry point runs `initdb` as UID 26 and writes outside the data PVC — forcing `runAsUser: 1001` or RO rootfs would crash the pod |
| `quay.io/keycloak/keycloak:20.0.5` | 1000 | `runAsNonRoot: true` + seccomp only | Writes H2 state to `/opt/keycloak/data` as UID 1000 — forced UID/RO rootfs would break startup |
| Strimzi Kafka pods | operator-managed | none authored | Operator applies its own restricted-v2-compatible contexts |

Runtime verification once cluster/registry access exists:
```bash
skopeo inspect docker://registry.redhat.io/rhel9/postgresql-15:latest | jq '.Env, .Labels'
oc get pod <pod> -o jsonpath='{.spec.securityContext}{"\n"}{.status.containerStatuses[0].imageID}'
```

## Generated Artifacts (repo root)

| File | Purpose |
|---|---|
| `Dockerfile` | Multi-stage UBI9 build, audit-lib bootstrap, non-root runtime |
| `.dockerignore` | Minimal build context |
| `configmap.yaml` | DB URL, Kafka bootstrap, OIDC URL/client-id |
| `secret.yaml` | Credential **template** (placeholders only; prefer `oc create secret`) |
| `deployment.yaml` | App workload: probes, resources, full security context, rolling update |
| `service.yaml` | ClusterIP :8080, ClientIP affinity (see Known Issues #3) |
| `route.yaml` | Edge-TLS external route |
| `hpa.yaml` | CPU autoscaling 1–3 replicas |
| `postgres.yaml` | StatefulSet + headless Service + 10Gi PVC |
| `kafka.yaml` | AMQ Streams Kafka CR + `orders`, `orders-dlq`, `inventory-dlq` topics |
| `keycloak.yaml` | Keycloak 20.0.5 + realm import + Service + Route |
| `podman-compose.yml` | Local dev stack mirroring the OpenShift deployment |

Source changes (containerization-enabling): `pom.xml` (+`quarkus-smallrye-health`), `src/main/resources/application.properties` (Kafka bootstrap env placeholder).

## Deployment Instructions

Prereqs: `oc` logged in, project created, AMQ Streams operator installed (for `kafka.yaml`).

```bash
oc new-project coolstore

# 1. Secrets (out-of-band — see secret.yaml header; then)
oc apply -f secret.yaml                       # or the oc create secret variant

# 2. Supporting services (order matters: Kafka CR must exist before topics reconcile)
oc create configmap keycloak-realm --from-file=realm-export.json=realm-export.json
oc apply -f postgres.yaml
oc apply -f kafka.yaml
oc apply -f keycloak.yaml

# 3. App image (build, scan, push — TG 7.3 gate)
podman build -t coolstore-quarkus:1.0.0 .
grype coolstore-quarkus:1.0.0 --fail-on high
podman tag coolstore-quarkus:1.0.0 \
  image-registry.openshift-image-registry.svc:5000/coolstore/coolstore-quarkus:1.0.0
podman login -u $(oc whoami) -p $(oc whoami -t) \
  image-registry.openshift-image-registry.svc:5000
podman push image-registry.openshift-image-registry.svc:5000/coolstore/coolstore-quarkus:1.0.0

# 4. Create the `coolstore` confidential client in realm `eap` (see Known Issues #1)
#    and put its secret into coolstore-secrets/QUARKUS_OIDC_CREDENTIALS_SECRET.

# 5. Point the app at the real Keycloak route host in configmap.yaml
oc get route keycloak -o jsonpath='{.spec.host}{"\n"}'   # edit configmap.yaml, then
oc apply -f configmap.yaml

# 6. App
oc apply -f deployment.yaml -f service.yaml -f route.yaml -f hpa.yaml

# 7. Verify
oc get pods -w
curl -f https://$(oc get route coolstore -o jsonpath='{.spec.host}')/q/health/ready
oc logs deployment/coolstore-quarkus | grep -i flyway    # V1_1/V1_2 migrations run at startup
```

## Configuration Reference

| Variable | Source | Notes |
|---|---|---|
| `QUARKUS_DATASOURCE_JDBC_URL` | ConfigMap | `jdbc:postgresql://postgres:5432/coolstore` |
| `QUARKUS_DATASOURCE_USERNAME` / `_PASSWORD` | Secret | must match `POSTGRESQL_USER` / `_PASSWORD` |
| `KAFKA_BOOTSTRAP_SERVERS` | ConfigMap | `my-cluster-kafka-bootstrap:9092` |
| `QUARKUS_OIDC_AUTH_SERVER_URL` | ConfigMap | Keycloak route URL, realm `eap` |
| `QUARKUS_OIDC_CLIENT_ID` | ConfigMap | `coolstore` (confidential client — create in realm) |
| `QUARKUS_OIDC_CREDENTIALS_SECRET` | Secret | client secret for the above |
| `KEYCLOAK_ADMIN` / `_PASSWORD` | Secret | Keycloak bootstrap admin |

Resources: app 250m/512Mi → 1/1Gi; probes on `/q/health/ready` (10s delay) and `/q/health/live` (30s delay); Flyway is the schema authority (`DataBaseMigrationStartup`, `flyway.migrate.enabled=true` in prod, `%prod` Hibernate `validate`).

## Rollback

```bash
oc rollout undo deployment/coolstore-quarkus            # or --to-revision=N
```
Full-stack rollback to the pre-migration platform is documented in the plan (TG 8.4): redeploy `ROOT.war` on JBoss EAP; rewind Kafka consumer groups (`orders-dlq` replay) via `kafka-consumer-groups --reset-offsets`.

## Known Issues and Gaps

1. **`coolstore` confidential client missing from realm export** — `realm-export.json` only defines the public `eap-app` front-end client. The backend OIDC client (`coolstore`) must be created in Keycloak post-import (console or `kcadm.sh`) and its secret wired into `coolstore-secrets`, otherwise OIDC tenant init fails at startup. Tracked for the deploy stage.
2. **Front-end `keycloak.json` is baked into the image** with `auth-server-url: http://localhost:8081/` — patch it at build time (CI step replacing the host with the Keycloak route) or mount an overridden file; tracked for deploy stage (plan task 4.3).
3. **In-memory carts limit horizontal scaling** — `ShoppingCartService` keeps cartId-keyed carts in pod memory, so the Service uses `sessionAffinity: ClientIP` and replicas=2. Externalizing cart state (Redis/DB) is the prerequisite for free scale-out; HPA max is capped at 3 with this caveat.
4. **Test suite**: 6 known failures from the execute stage — tests are skipped in the image build (`-DskipTests`) and must gate CI before push (TG 6.7); `grype` scan not yet run (no toolchain here).
5. **Dev-grade supporting services**: single-broker ephemeral Kafka, ephemeral-H2 Keycloak, `:latest` Postgres tag — pin digests, add replicas/persistent storage, and prefer managed services for production.
6. **rgctl artifact hygiene**: `.rgctl/` analysis cache added to `.gitignore` (never commit graph binaries).

## Next Steps

1. ⏳ Run build + `grype` scan + push (CI/deploy stage) — commands above
2. ⏳ mig-deploy stage: Tekton pipeline, GitOps promotion, cutover/parallel-run, observability (Micrometer metrics is a deploy-stage add — `quarkus-micrometer` not yet in pom)
3. ⏳ Close Known Issues #1–#3; rerun `mvn verify` to clear the 6 test failures before promotion
