# Containerization Report: CoolStore (coolstore-quarkus)

**Stage**: MigIQ containerize (Task Group 6 of `mig-plan-workspace/tasks.md`)
**Date**: 2026-10-28 | **Executor**: MigIQ Containerizer
**Prior stages**: mig-prompt-builder → mig-plan → mig-execute (Groups 1–5 complete)

## Executive Summary

- **Application**: CoolStore e-commerce monolith — product catalog, cart with promotion/shipping calculation, checkout; AngularJS 1.x SPA served by the backend
- **Language/Framework**: Java 21, Quarkus 3.8.1 (quarkus-bom), Maven build
- **Base Image**: `registry.access.redhat.com/ubi9/openjdk-21` (builder) → `ubi9/openjdk-21-runtime` (runtime, JRE-only)
- **Deployment Target**: Red Hat OpenShift 4.x (internal image registry)
- **Build Strategy**: Multi-stage — Maven builder on JDK 21, fast-jar runtime on JRE image, non-root UID 1001
- **Build Time / Image Size**: Not measurable in this sandbox (no Podman) — deferred to CI, commands below
- **Security Scan**: Deferred to CI (`grype` command documented); static security posture hardened by design

## Graph Analysis Findings

rgctl index rebuilt for this stage (`rgctl discover . --with-cfg --with-security --with-taint --with-harmonic`) after the graph from prior stages was found missing.

### Application Structure
- **Nodes / Edges**: 18,788 / 72,861 across 538 files (16,499 functions)
- **Communities**: 11,694 detected (modularity 0.45) — count is dominated by vendored frontend JS under `src/main/resources/META-INF/resources/bower_components/`; application-relevant communities include `coolstore.model::CatalogItemEntity` (81 members), `coolstore.model::getShoppingCartItemList` (49), `ShoppingCart` (48)
- **Hotspots (PageRank)**: top nodes (`factory`, `apply`, …) are all vendored bower JS — not application code; the Java layer is cleanly modular (model → service → rest)
- **Primary Language**: Java 21 (Maven, `maven.compiler.release=21`); frontend: static AngularJS assets
- **Framework**: Quarkus 3.8.1 — arc, resteasy-reactive-jackson, hibernate-orm, jdbc-postgresql, jaxb, smallrye-reactive-messaging-kafka, narayana-jta, flyway, logging-json, oidc, **smallrye-health (added this stage)**

### External Dependencies (from graph + application.properties)

| Dependency | Usage | Container strategy |
|---|---|---|
| PostgreSQL | Production datasource (`%prod`, Flyway-owned schema) | `database-statefulset.yaml` (dev/test); managed DB recommended for prod |
| Apache Kafka | Orders topic; consumer groups `coolstore-orders` + `coolstore-inventory`; DLQs | Platform service (AMQ Streams) — bootstrap via ConfigMap; deploy stage wires it |
| Keycloak (OIDC) | Realm `eap`, confidential client `coolstore`; SPA uses public client `eap-app` | Platform service — auth URL + client secret via Secret |
| audit-logging-library 2.0.0 | `StreamableAuditLogger` over TCP (`logEventAsync`) | External TCP sink — endpoint via ConfigMap; jar resolves from project-local `lib/` repo at build time |
| H2 | dev/test profiles only | Not used in `%prod`; nothing to deploy |

### Platform-Specific Code Identified (blocker analysis)

- **Hardcoded filesystem paths**: none in Java source (rgctl semantic/grep) — no local file persistence, no uploads directory → **no PVC required**
- **`localhost` references**: only as MicroProfile Config env-var *defaults* (DB URL, Kafka bootstrap, OIDC URL, audit host) — all overridable via environment; no code change needed
- **Windows/OS-specific code**: none
- **Logging**: console/JSON only (`quarkus-logging-json`) — no log files written to disk; root filesystem can be read-only
- **Taint finding** (`bower_components/matchHeight/gulpfile.js`): already removed by the execute stage; verified absent
- **Session affinity**: none — cart state is cartId-keyed REST state → `sessionAffinity: None` on the Service; scales horizontally
- **Secrets (rgctl security findings, 4)**: placeholder credentials in README runbook and `realm-export.json` values — documented for rotation (see Security Considerations); no runtime secrets are baked into the image

### Migration Complexity: **Low–Medium**

**Reasoning**: the Java layer is cleanly layered with all external endpoints already externalized as env vars by the execute stage; the only build-time wrinkle is the project-local Maven repository (`lib/`) for `audit-logging-library:2.0.0`, handled by copying `lib/` into the builder stage only. Vendored frontend JS inflates the graph but is static content already pruned to runtime assets.

## Containerization Strategy

### Base Image Selection
- **Builder**: `registry.access.redhat.com/ubi9/openjdk-21:latest` — Red Hat UBI9 with JDK 21 + Maven; matches `maven.compiler.release=21` (audit-logging-library 2.x requirement)
- **Runtime**: `registry.access.redhat.com/ubi9/openjdk-21-runtime:latest` — JRE-only UBI9, smaller attack surface, no build tools
- **Alternatives considered**: `ubi9/ubi-minimal` + custom JRE (rejected — loses Red Hat Java support/packaging); native compilation (rejected — JVM mode per migration prompt deliverable 1)
- **Production**: pin by digest (task 6.4) — `skopeo inspect <image> | jq -r .Digest`, then replace tags with `@sha256:<digest>`

### Build Strategy
- **Type**: Multi-stage (2 stages)
  1. **builder** — copies `pom.xml` + `lib/` (dependency layer, cached), then `src/`; `mvn clean package -DskipTests` (tests run in CI; also no JDK/Maven locally in this sandbox, consistent with execute-stage tasks 1.1/1.2)
  2. **runtime** — copies only `target/quarkus-app/` (fast-jar: runner + libs + static SPA); `ENTRYPOINT java $JAVA_OPTS -jar quarkus-run.jar`
- **Excluded from the image** (via multi-stage + `.dockerignore`): source, `lib/` jars, `.git`, `.rgctl/`, MigIQ workspaces/`migration/` docs, `target/`, tests, `realm-export.json`
- **JVM sizing**: `-XX:MaxRAMPercentage=75.0` — heap follows the pod memory limit

### Security Hardening Applied
- ✅ Multi-stage build — no build tools in the runtime image
- ✅ Non-root execution, fixed UID 1001 (OpenShift arbitrary-UID safe)
- ✅ `readOnlyRootFilesystem: true` + `emptyDir` for `/tmp` (only writable path the JVM needs)
- ✅ All capabilities dropped (`capabilities.drop: [ALL]`), `allowPrivilegeEscalation: false`, `runAsNonRoot: true`, `seccompProfile: RuntimeDefault` — compatible with SCC `restricted-v2`
- ✅ All secrets/config externalized (env vars ← Secret/ConfigMap); no credentials in image layers
- ✅ Probes on Quarkus health endpoints; TCP-level `HEALTHCHECK` for bare podman/docker runs
- ✅ Rolling update (`maxUnavailable: 0`) and resource requests/limits on the workload
- ⏳ Vulnerability scan + base-image digest pinning: commands documented, execution deferred to CI (no Podman/registry access in this sandbox)

## Generated Artifacts (repo root)

| File | Purpose |
|---|---|
| `Dockerfile` | Multi-stage image build (builder + runtime) |
| `.dockerignore` | Minimal build context (excludes `.rgctl/`, workspaces, docs, `target/`) |
| `configmap.yaml` | Non-sensitive config: Kafka bootstrap, audit TCP endpoint, CORS origins |
| `secret.yaml` | Sensitive config (PLACEHOLDER values): DB URL/user/password, OIDC auth URL/client-id/secret |
| `deployment.yaml` | App workload: 3 replicas, security contexts, probes, resources, rolling strategy |
| `service.yaml` | ClusterIP Service on 8080 (no session affinity) |
| `route.yaml` | OpenShift Route, edge TLS + HTTP→HTTPS redirect |
| `database-statefulset.yaml` | PostgreSQL 15 StatefulSet + headless Service + 10Gi PVC (dev/test) |
| `podman-compose.yml` | Local dev stack: app + PostgreSQL + Kafka (Strimzi) + Keycloak (realm auto-import) |
| `CONTAINERIZATION_REPORT.md` | This report |
| `pom.xml` (edited) | Added `quarkus-smallrye-health` (task 6.2) |

## Deployment Instructions

### Prerequisites
1. OpenShift cluster access (`oc` CLI logged in), `podman` on the build machine
2. Platform services: Kafka (AMQ Streams), Keycloak with realm `eap` imported from `realm-export.json` (client secret rotated)
3. Optional: managed PostgreSQL instead of the in-cluster StatefulSet

### Build, Scan, Push (CI or workstation with Podman)
```bash
podman build -t coolstore:1.0.0 -f Dockerfile .

# Security scan (task 6.4)
grype coolstore:1.0.0 --fail-on medium

# Pin base images for production (record digests, then update Dockerfile FROM lines)
skopeo inspect registry.access.redhat.com/ubi9/openjdk-21:latest | jq -r .Digest
skopeo inspect registry.access.redhat.com/ubi9/openjdk-21-runtime:latest | jq -r .Digest

# Push to the OpenShift internal registry
oc new-project coolstore   # skip if the namespace exists
podman login -u $(oc whoami) -p $(oc whoami -t) image-registry.openshift-image-registry.svc:5000
podman tag coolstore:1.0.0 image-registry.openshift-image-registry.svc:5000/coolstore/coolstore:1.0.0
podman push image-registry.openshift-image-registry.svc:5000/coolstore/coolstore:1.0.0
```

### Local smoke test (task 6.3 — run in CI/workstation)
```bash
podman-compose up -d                       # app + db + kafka + keycloak
curl -f http://localhost:8080/q/health     # overall health
curl -f http://localhost:8080/q/health/ready
curl -f http://localhost:8080/services/products | head   # catalog (public, seeded by Flyway)
podman-compose down
```

### Deploy to OpenShift (apply order)
```bash
oc apply -f secret.yaml                  # replace placeholders first (or oc create secret …)
oc apply -f configmap.yaml               # edit Kafka/audit/CORS endpoints per environment
oc apply -f database-statefulset.yaml    # only if no managed PostgreSQL
oc apply -f deployment.yaml
oc apply -f service.yaml
oc apply -f route.yaml
```

### Verify
```bash
oc get pods -l app=coolstore
oc get route coolstore-route
oc logs deployment/coolstore | head
curl -fs https://$(oc get route coolstore-route -o jsonpath='{.spec.host}')/q/health/ready
curl -fs https://$(oc get route coolstore-route -o jsonpath='{.spec.host}')/services/products | head
```

## Configuration Reference

| Variable | Description | Source | Required |
|---|---|---|---|
| `QUARKUS_DATASOURCE_JDBC_URL` | PostgreSQL JDBC URL | Secret | Yes (prod) |
| `QUARKUS_DATASOURCE_USERNAME` / `_PASSWORD` | DB credentials | Secret | Yes (prod) |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | ConfigMap | Yes (prod) |
| `OIDC_AUTH_SERVER_URL` | Keycloak realm URL (`…/realms/eap`) | Secret | Yes (prod) |
| `OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET` | Confidential client credentials | Secret | Yes (prod) |
| `AUDIT_LOGGING_HOST` / `_PORT` | Audit TCP sink | ConfigMap | Yes (prod) |
| `CORS_ORIGINS` | Allowed SPA origins (Route URL) | ConfigMap | Recommended |

## Resource Requirements

- **Application pod**: requests 250m CPU / 256Mi memory; limits 500m / 512Mi (heap ≈ 75% of limit via `MaxRAMPercentage`)
- **PostgreSQL pod** (if deployed): requests 250m / 256Mi; limits 1000m / 1Gi; storage 10Gi (RWO PVC)
- **Replicas**: 3 (horizontal scaling replaces JBoss EAP standalone-full-ha clustering)

## Health and Monitoring

- **Endpoints** (provided by `quarkus-smallrye-health`): `/q/health/live`, `/q/health/ready`, `/q/health/started`
  - Readiness includes the **database check** (registered by hibernate-orm/jdbc) and **Kafka check** (registered by reactive-messaging) — task 6.2
- **Probes**: startup (5s×24), readiness (10s delay, 5s period), liveness (30s delay, 10s period)
- **Logs**: JSON to stdout (`quarkus-logging-json`) — scrape via OpenShift logging; no disk writes
- **HPA/metrics**: micrometer + HPA are deploy-stage scope (tasks 7.3)

## Security Considerations

- **Image**: Red Hat UBI (supported, patched via registry); no secrets in layers; minimal runtime (JRE-only)
- **Runtime**: UID 1001 non-root, read-only rootfs, all caps dropped, `restricted-v2`-compatible pod/container security contexts
- **Network**: Route with edge TLS + redirect; internal traffic in-cluster
- **Secrets**: `secret.yaml` ships **placeholders only** — create real values out-of-band (`oc create secret …`); rotate the Keycloak `coolstore` client secret after realm import (the `realm-export.json` default is a placeholder). The 4 rgctl secret findings map to those documented placeholder credentials (README + realm-export) — triaged as non-exploitable placeholders, rotation documented
- **Recommendation**: external secret manager (Vault / ESO) for production

## Rollback Procedure

```bash
oc rollout history deployment/coolstore
oc rollout undo deployment/coolstore                 # previous revision
oc rollout undo deployment/coolstore --to-revision=2
oc get pods -l app=coolstore && oc logs deployment/coolstore | tail
```
Roll back the platform independently: point `QUARKUS_DATASOURCE_JDBC_URL` back at the previous database (Flyway schema is additive) or scale the previous image tag's Deployment.

## Known Issues and Limitations

1. **No Podman/JDK in the containerize sandbox** — image build, in-container smoke test (6.3), and scan/digest pinning (6.4) are **deferred to CI**; all commands are documented above (same precedent as execute-stage tasks 1.1/1.2)
2. **Base images referenced by `:latest` tag** — pin by digest in CI before production (commands above)
3. **Audit TCP sink must exist** at `AUDIT_LOGGING_HOST:9999`, else checkout audit events are dropped asynchronously (non-blocking by design)
4. **Kafka + Keycloak are platform services** — AMQ Streams wiring, realm deployment, HPA, and CI/CD are deploy-stage scope (Group 7)
5. **`database-statefulset.yaml` is dev/test-grade** — use a managed/HA database for production
6. **Quarkus fast-jar JVM mode** — native build intentionally out of scope per migration prompt

## Next Steps

1. ✅ Containerization artifacts complete (Group 6: 6.1, 6.2, 6.5 done; 6.3/6.4 → CI)
2. ⏳ CI: `mvn clean verify` on JDK 21 + `podman build` + smoke tests + `grype` scan + digest pinning
3. ⏳ MigIQ deploy stage (Group 7): AMQ Streams/Keycloak wiring, HPA, CI/CD pipeline, runbook
4. ⏳ Group 8 parity validation & cutover

## Support and Resources

- **Red Hat UBI images**: https://catalog.redhat.com/software/containers/search
- **OpenShift docs**: https://docs.openshift.com/container-platform/latest/
- **Podman**: https://docs.podman.io/
- **Quarkus container guide**: https://quarkus.io/guides/container-image
