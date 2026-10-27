# MigIQ Containerize Stage — Handoff

**Stage**: mig-containerize | **Date**: 2026-10-27 22:18 UTC | **Branch**: `konveyor/migration-1793137290`
**Status**: ✅ Stage complete (code/config artifacts) — runtime build/test/scan delegated to CI

## What Was Completed

### 1. rgctl index (was missing — rebuilt)
- No prior graph index existed (`GRAPH_NOT_FOUND`), so per mig-rgctl skill the index was
  rebuilt from the app repo root with containerization flags:
  `rgctl discover . --with-cfg --with-security --with-taint --with-harmonic`
- Result: 37,177 nodes / 129,381 edges / 24,779 communities (modularity 0.47);
  semantic index built (`rgctl semantic index`). Artifacts in `.rgctl/` (gitignored).
- Communities validated non-empty (workflow requirement); Java app clusters cohesively
  (`coolstore.model`, `.service`, `.rest`, `.utils`) — single deployable unit.

### 2. Containerization-readiness analysis (graph + source)
- **External deps (prod profile)**: PostgreSQL (Flyway V1_1/V1_2), Kafka via SmallRye
  Reactive Messaging (topic `orders`, groups `coolstore-orders`/`coolstore-inventory`,
  DLQs `orders-dlq`/`inventory-dlq`), Keycloak OIDC (realm `eap`, public client `eap-app`).
- **No filesystem I/O** in Java source → no app PVC required (only `emptyDir /tmp`).
- **Config fully env-driven** (localhost only as `${ENV:default}` fallbacks); no
  hardcoded credentials; `keycloak.json` is public-client (no secret embedded).
- **Security findings**: 6 "potential secrets" traced to vendored bower assets (e.g.
  `angular-patternfly/deploy_key.enc`) + realm metadata — not app credentials; CI
  secret-scan gates promotion regardless. 1 taint flow (external Kafka message →
  `Log.debugf` in `OrderServiceMDB.onMessage`) — log-injection risk only, tracked as
  security follow-up, NOT a containerization blocker.

### 3. Deliverables written to repo root (commit 654d229, 14 files, 723 insertions)
- `Dockerfile` — multi-stage UBI9 (`ubi9/openjdk-17` builder w/ Maven →
  `ubi9/openjdk-17-runtime`), non-root UID 1001, `-XX:MaxRAMPercentage=75.0`,
  layer-cached `dependency:go-offline`, `-DskipTests` (tests run in Tekton).
  Dockerfile `HEALTHCHECK` intentionally omitted (OpenShift ignores it; JRE image has
  no curl — probes in deployment.yaml are authoritative). See report for rationale.
- `.dockerignore` — minimal build context (src + pom.xml only).
- Manifests (namespace `coolstore-dev`): `serviceaccount.yaml`, `configmap.yaml`
  (coolstore-config: JDBC URL, Kafka bootstrap, CORS origin), `secret.yaml`
  (coolstore-db + coolstore-oidc TEMPLATES with REPLACE_ME placeholders only),
  `deployment.yaml` (restricted-v2 hardening: non-root 1001, read-only rootfs,
  caps dropped, seccomp RuntimeDefault; startup/liveness/readiness probes on
  `/q/health/live|ready`; envFrom TG6.5 contract; 250m/256Mi → 1/512Mi),
  `service.yaml` (ClusterIP :8080), `route.yaml` (edge TLS + HTTPS redirect),
  `kafka-topics.yaml` (Strimzi orders/orders-dlq/inventory-dlq, cluster
  `my-cluster`), `hpa.yaml` (2→6 @75% CPU), `pdb.yaml` (minAvailable 1),
  `database-statefulset.yaml` (dev/test PostgreSQL 15, headless svc, 10Gi PVC,
  creds from coolstore-db).
- `podman-compose.yml` — local dev parity (app + PostgreSQL + single-node Strimzi
  Kafka + Keycloak 20.0.5 with realm import from `realm-export.json`).
- `CONTAINERIZATION_REPORT.md` — full report: graph findings, strategy, security
  hardening, deployment steps, config reference, rollback, known issues, next steps.
- All YAML validated (no tabs/trailing-ws; structural check — PyYAML unavailable).
- Prior-stage artifacts untouched: `Containerfile`, `k8s/openshift.yaml`,
  `k8s/tekton-pipeline.yaml` remain as execute-stage fallback/CI definitions.

## What Remains (for CI / mig-deploy stage)

1. **CI build & scan (blocking gate, TG6.4)**: run `mvn clean verify`, build the image
   from the repo-root `Dockerfile` (or existing `Containerfile`), push to the OpenShift
   internal registry, and run vulnerability scanning (Trivy / OpenShift integrated
   scan). This environment has NO podman/docker/mvn/java/grype — same constraint
   recorded in `mig-execute-workspace/EXECUTION_REPORT.md`. Static verification only.
2. **mig-deploy stage**: apply repo-root manifests (order documented in
   CONTAINERIZATION_REPORT.md "Deployment Instructions": namespace → real secrets
   out-of-band → serviceaccount/configmap → database (dev/test) → kafka-topics →
   deployment/service/route → hpa/pdb); provision Keycloak (realm `eap` from
   `realm-export.json`), PostgreSQL, and AMQ Streams cluster `my-cluster`.
3. **Frontend `keycloak.json`** still defaults to `http://localhost:8081/` — deploy
   stage must override via ConfigMap (carried over from execute-stage TG4.3/TG7.4).
4. **Secrets**: replace `REPLACE_ME` placeholders via `oc create secret` out-of-band —
   never commit real credentials.
5. **E2E validation** (TG8.1 parity, TG8.2 DLQ poison-message demo, TG8.3 performance
   baseline) — pending cluster access.
6. **Security follow-ups**: remediate the `OrderServiceMDB` log-sink taint flow; keep
   Tekton secret-scan gating bower asset findings; pin image tags/digests instead of
   `latest` for production; evaluate native image build (TG6.2 follow-up).
7. **Deferred app work** (non-blocking, from prior stages): audit-logging library stub
   (TODO(port)), 18 TODO/BUG/PERF markers burn-down.

## Key References
- Full report: `CONTAINERIZATION_REPORT.md` (repo root)
- Env contract (TG6.5): ConfigMap `coolstore-config`, Secrets `coolstore-db`/`coolstore-oidc`
- Prior stage: `mig-execute-workspace/EXECUTION_REPORT.md`, `migration/MIGRATION-COMPLETE.md`
- CI: `k8s/tekton-pipeline.yaml` (test → secret-scan → buildah)
