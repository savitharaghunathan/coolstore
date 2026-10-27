# Containerization Report: CoolStore

## Executive Summary

- **Application**: CoolStore — retail storefront monolith, migrated from Java EE 7 / JBoss EAP 7.4 to Quarkus 3.8.1 (Java 17)
- **Language/Framework**: Java 17, Quarkus 3.8.1 (RESTEasy Reactive, Hibernate ORM, Flyway, SmallRye Reactive Messaging Kafka, quarkus-oidc, smallrye-health); vendored AngularJS/Polymer SPA served from `META-INF/resources/`
- **Base Image**: `registry.access.redhat.com/ubi9/openjdk-17-runtime:1.22-1` (runtime), `ubi9/openjdk-17:1.21-2` (builder) — tags verified live against the registry API
- **Deployment Target**: Red Hat OpenShift
- **Build Time / Image Size / Security Scan**: deferred to the CI gate — this stage's sandbox has no Podman/JDK (see Known Issues)
- **Migration Complexity**: **Low-to-Medium** — no containerization blockers found by graph analysis; complexity is operational (external PostgreSQL + Kafka + Keycloak, sticky sessions)

## Graph Analysis Findings

Analysis performed with rgctl (index rebuilt this stage: `rgctl discover . --with-cfg --with-security --with-taint --with-harmonic` + `semantic index`, because no index existed).

### Application Structure
- **Nodes**: 37,240 | **Edges**: 108,176 (status) | **Files indexed**: 1,318
- **Communities**: 24,830 (modularity 0.47); largest app subsystem: **ShoppingCart** (86 members)
- **Functions analyzed**: 26,966 (avg complexity 1.0 — no high-complexity hotspots)
- **Top PageRank nodes** are vendored frontend libraries (`bower_components`), not application code — the app-side graph is small and clean; `Date`/external JDK classes rank highest
- **Import-prefix census** (external dependency evidence): `jakarta.persistence` (45), `io.quarkus` (24), `jakarta.ws.rs` (23), `jakarta.inject` (19), `org.slf4j` (18), `jakarta.enterprise` (17), `jakarta.transaction` (7), `org.flywaydb` (2) — matches pom.xml exactly

### External Dependencies
| Dependency | Graph/config evidence | Containerization decision |
|---|---|---|
| PostgreSQL 13+ | `%prod.quarkus.datasource.*` (env-injected) | StatefulSet + PVC (`postgresql-statefulset.yaml`) |
| Kafka 3.x | SmallRye channels `orders`/`inventory`, distinct groups `coolstore-orders`/`coolstore-inventory`, DLQs | Strimzi Kafka + 3 KafkaTopics (`kafka.yaml`) |
| Keycloak 20 (realm `eap`, client `coolstore`) | `quarkus.oidc.*` (env-injected secret + URL) | Deployment + realm ConfigMap import (`keycloak.yaml`) |

### Platform-Specific Code Identified (blockers)
- **Hardcoded file paths**: none — semantic query (`file path upload storage localhost hardcoded configuration`) returned only vendored JS hits; source grep found `localhost` in a Java comment only. No file-storage code, no PVC needed beyond the database.
- **Configuration**: `application.properties` is the sole config source; all production values are `${ENV:default}` placeholders → externalized to ConfigMap/Secret.
- **Secrets**: discover security scan flagged 2 potential secrets — both in `realm-export.json` and are realm *policy* keys (`resetPasswordAllowed`, `"password"` in a policy list), not secret values (prior stage scrub-verified 0 secret values; runtime credentials are env-injected only).
- **Taint**: 1 taint flow flagged at discover (PDG-backed) — recorded for CI security review; no filesystem sinks involved.

### Migration Complexity: Low-to-Medium
No god nodes in application code; coupling is to the three external services; the `ShoppingCart` community (86 members, `@SessionScoped`) drives the only scaling constraint (sticky sessions, COOLSTORE-201).

## Containerization Strategy

### Base Image Selection
- **Runtime**: `registry.access.redhat.com/ubi9/openjdk-17-runtime:1.22-1` — language-specific UBI9 JRE image; default non-root user (UID 185); no pull secret required (access registry)
- **Builder**: `registry.access.redhat.com/ubi9/openjdk-17:1.21-2` — full JDK + Maven for the multi-stage build
- **Alternatives considered**: UBI8 openjdk-17 (prior migration-stage `Containerfile`) — works, but UBI9 is the current long-lived UBI major and aligns with skill guidance; `ubi9/ubi-minimal` + custom Java install — rejected (no maintained JRE)

### Build Strategy
- **Type**: Multi-stage (compiled language)
- **Stage 1**: `mvn dependency:go-offline` (cached layer) → `mvn package -DskipTests` (tests run in CI before image build, per plan TG2.9 CI gate)
- **Stage 2**: copies only `target/quarkus-app/` (fast-JAR) into the JRE image; `curl` added via `microdnf` for the image-level HEALTHCHECK
- **Runtime**: `java $JAVA_OPTS -jar quarkus-app/quarkus-run.jar` with `-XX:MaxRAMPercentage=75.0` (container-aware heap)

### Security Hardening Applied
- ✅ Multi-stage build (no build tools in runtime image)
- ✅ Non-root execution (UID 185 — UBI OpenJDK default) + `runAsNonRoot: true` in pod and container security contexts
- ✅ `readOnlyRootFilesystem: true` with `emptyDir` `/tmp`
- ✅ All capabilities dropped (`drop: ALL`), `allowPrivilegeEscalation: false`, `seccompProfile: RuntimeDefault` (restricted-v2 compatible)
- ✅ Pinned image tags (no `latest` in the app image)
- ✅ Secrets externalized (Secret + env injection) — none in Git or image layers
- ✅ HEALTHCHECK in image + readiness/liveness probes in the Deployment
- ✅ `.dockerignore` excludes workspaces, manifests, migration docs, `realm-export.json`, `.rgctl/`
- ⏳ Vulnerability scan (`grype`/OpenShift registry scan) — CI gate (no scanner in sandbox)

## Generated Artifacts (repo root — this stage's deliverables)

### Container Image
- **Name**: `coolstore:1.0.0` (local) → `quay.io/coolstore/coolstore:1.0.0` or OpenShift internal registry
- **Definition**: `Dockerfile`; exclusions: `.dockerignore`; local stack: `podman-compose.yml`
- Note: the migration-stage `Containerfile` (UBI8) is retained in git history; the root `Dockerfile` is the containerize-stage hardened deliverable (UBI9, pinned tags, HEALTHCHECK, curl).

### Kubernetes/OpenShift Manifests
1. `configmap.yaml` — non-sensitive runtime config (DB URL, Kafka bootstrap, OIDC URL, CORS)
2. `secret.yaml` — **template** with `CHANGE_ME` placeholders (provision real values out-of-band)
3. `deployment.yaml` — app workload (2 replicas, security contexts, probes, resources, env wiring)
4. `service.yaml` — ClusterIP 8080 + `sessionAffinity: ClientIP` (sticky sessions until COOLSTORE-201)
5. `route.yaml` — edge TLS termination, HTTP→HTTPS redirect
6. `postgresql-statefulset.yaml` — PostgreSQL 15 StatefulSet + headless Service + 1Gi PVC
7. `kafka.yaml` — Strimzi Kafka + topics `orders`, `orders-dlq`, `inventory-dlq`
8. `keycloak.yaml` — Keycloak 20 + realm import via ConfigMap + admin Secret refs
9. `podman-compose.yml` — local development mirroring the OpenShift topology

## Deployment Instructions

### Prerequisites
OpenShift cluster with `oc` CLI; Strimzi (AMQ Streams) operator installed for `kafka.yaml`; registry credentials for `registry.redhat.io` (PostgreSQL image); image built and pushed.

### Steps
```bash
oc new-project coolstore

# 1. Secrets (replace placeholders / create out-of-band — never commit real values)
oc apply -f secret.yaml
oc create secret generic keycloak-admin \
  --from-literal=KEYCLOAK_ADMIN=<admin> --from-literal=KEYCLOAK_ADMIN_PASSWORD=<pass>

# 2. Non-sensitive config
oc apply -f configmap.yaml

# 3. Keycloak realm import
oc create configmap keycloak-realm --from-file=realm-export.json=realm-export.json

# 4. Build + push image, then update `image:` in deployment.yaml
podman build -t quay.io/coolstore/coolstore:1.0.0 -f Dockerfile .
podman push quay.io/coolstore/coolstore:1.0.0

# 5. Dependencies (Strimzi operator must be installed for kafka.yaml)
oc apply -f kafka.yaml
oc apply -f postgresql-statefulset.yaml
oc apply -f keycloak.yaml

# 6. Application
oc apply -f deployment.yaml
oc apply -f service.yaml
oc apply -f route.yaml

# 7. Verify
oc get pods
oc logs deployment/coolstore
curl -fsS https://$(oc get route coolstore -o jsonpath='{.spec.host}')/q/health
./scripts/openshift-smoke-test.sh https://$(oc get route coolstore -o jsonpath='{.spec.host}')
```

All manifests are declarative and idempotent (`oc apply` is safe to re-run).

## Configuration Reference

### Environment Variables
| Variable | Description | Source | Required |
|---|---|---|---|
| `QUARKUS_DATASOURCE_URL` | PostgreSQL JDBC URL | ConfigMap | Yes |
| `QUARKUS_DATASOURCE_USERNAME` | DB user | Secret (`db-username`) | Yes |
| `QUARKUS_DATASOURCE_PASSWORD` | DB password | Secret (`db-password`) | Yes |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap (orders/DLQ topics) | ConfigMap | Yes |
| `COOLSTORE_OIDC_AUTH_SERVER_URL` | Keycloak realm `eap` issuer URL | ConfigMap | Yes |
| `COOLSTORE_OIDC_SECRET` | Keycloak client `coolstore` secret | Secret (`oidc-client-secret`) | Yes |
| `COOLSTORE_CORS_ORIGINS` | SPA allowed origin (`*` → tighten to Route URL) | ConfigMap | No |
| `JAVA_OPTS` | JVM opts (MaxRAMPercentage 75, 0.0.0.0 bind) | image default | No |

### Resource Requirements
- **App pod**: requests 250m/512Mi, limits 1 CPU/1Gi (2 replicas)
- **PostgreSQL**: requests 250m/256Mi, limits 1 CPU/1Gi + 1Gi PVC
- **Keycloak**: requests 250m/512Mi, limits 1 CPU/1Gi

## Health and Monitoring
- **Readiness**: `GET /q/health/ready` (DB + Kafka checks via smallrye-health) — initial 10s, period 5s
- **Liveness**: `GET /q/health` — initial 15s, period 10s; image HEALTHCHECK every 30s
- **Logs**: JSON via quarkus-logging-json; audit events (`ORDER_PERSISTED`, `INVENTORY_UPDATED`) as structured JSON — route/retain with the cluster log collector
- **Startup**: Flyway migrates at startup; Hibernate only validates in `%prod` (readiness gate covers schema readiness)

## Security Considerations
- **Image**: UBI9 (Red Hat supported, patched); multi-stage; no secrets in layers; pinned tags
- **Runtime**: non-root UID 185, read-only root FS, no capabilities, restricted-v2 compatible SCC
- **Network**: TLS terminated at the Route (edge, HTTP redirected); internal traffic plain within the namespace
- **Secrets**: OpenShift Secrets only; recommendation to adopt an external secret manager (Vault) and rotate the Keycloak realm defaults at provisioning

## Rollback Procedure
```bash
oc rollout undo deployment/coolstore            # or: --to-revision=N
oc rollout history deployment/coolstore
git revert <containerize-stage-commit>          # manifest/Dockerfile rollback
```
The JBoss EAP deployment path remains available in git history until cutover (per migration TG10.5).

## Known Issues and Limitations
1. **No runtime validation in this stage's sandbox** (no Podman/JDK/`oc`): image build, vulnerability scan, `oc apply`, and in-cluster smoke tests are the CI gate — same gate documented in `mig-execute-workspace/EXECUTION_REPORT.md`.
2. `scripts/container-smoke-test.sh` overrides `QUARKUS_DATASOURCE_URL` with an H2 URL while `%prod` pins `db-kind=postgresql` and the H2 driver is test-scoped — the script should be re-pointed at the compose stack (`podman-compose up` + the openshift smoke checks) at CI time.
3. `registry.redhat.io/rhel9/postgresql-15` requires terms acceptance / pull secret; `:latest` should be pinned for production. Kafka/Keycloak run single-replica with ephemeral storage — scale for production.
4. Sticky sessions (`sessionAffinity: ClientIP`) are an approximation pending COOLSTORE-201 (cart state externalization).
5. Keycloak runs `start-dev`; production should use `start --optimized` + TLS.
6. 1 taint flow + 2 secret candidates from discover were reviewed as non-blocking (policy keys, no values) — keep in CI security review.

## Next Steps
1. ⏳ CI gate: `mvn clean verify`, `podman build -f Dockerfile`, `grype` scan (zero critical CVEs), smoke test
2. ⏳ Deploy to dev/test OpenShift; run `scripts/openshift-smoke-test.sh` against the Route
3. ⏳ Performance validation (startup <10s, p95 within 2× EAP baseline) and E2E checkout flow
4. ⏳ Set up CI/CD pipeline, monitoring/alerting, secret rotation policy
5. ⏳ Production hardening: pin DB image, persistent Kafka storage, Keycloak TLS, CORS tightening

## Support and Resources
- Red Hat UBI images: https://catalog.redhat.com/software/containers/search
- OpenShift docs: https://docs.openshift.com/
- Prior stages: `mig-plan-workspace/tasks.md` (TG8/TG9), `mig-execute-workspace/EXECUTION_REPORT.md`, `migration/EXECUTION-WAVE2-NOTES.md`
