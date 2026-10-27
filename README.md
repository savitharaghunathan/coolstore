# CoolStore — Quarkus 3.8.1 on OpenShift

Retail storefront monolith, migrated from Java EE 7 / JBoss EAP 7.4 to **Quarkus 3.8.1** (Java 17), containerized and deployed on Red Hat OpenShift. Migration history: `migration/` (wave 1 code translation, wave 2 completion — see `migration/EXECUTION-WAVE2-NOTES.md`).

## Prerequisites

- **Java 17 JDK** (build/runtime), **Maven 3.8+**, Podman 4.x (container builds), `oc` CLI (OpenShift deploy)
- External services (prod): PostgreSQL 13+, Kafka 3.x (AMQ Streams), Keycloak 20.x (realm `eap`)

## Build & Run

```bash
mvn clean verify                        # tests: JUnit 5 + @QuarkusTest on H2 + in-memory messaging (no Docker needed)
mvn quarkus:dev                         # dev mode: http://localhost:8080 (H2 + in-memory Kafka)
```

- REST endpoints: `/services/rest/products` (public), `/services/rest/cart/*`, `/services/rest/orders` (OIDC role `user`)
- The base path is pinned by `@ApplicationPath("/services")` in `RestApplication` — do not move it to config without updating the frontend (`META-INF/resources/coolstore.json`).
- Health: `/q/health`, `/q/health/ready` (replaced `health.jsp`). SPA served from `src/main/resources/META-INF/resources/` (moved from `src/main/webapp/`; no JSPs remain).

## Tests

- 21 test files, all JUnit 5; service tests use `@ExtendWith(MockitoExtension.class)` (`mockito-junit-jupiter`), REST tests use `@QuarkusTest` + REST-assured against `/services/rest/*`.
- `%test` profile: isolated H2 (`testdb-test`) + `smallrye-in-memory` connectors + OIDC disabled — single test-config source (legacy test `persistence.xml` removed).
- `quarkus.devservices.enabled=false` stays: tests never require a container daemon.

## Configuration (env matrix)

| | dev | test | prod |
|---|---|---|---|
| DB | H2 mem | H2 mem `testdb-test` | PostgreSQL (`QUARKUS_DATASOURCE_URL/USERNAME/PASSWORD`) |
| Messaging | in-memory | in-memory | Kafka (`KAFKA_BOOTSTRAP_SERVERS`) |
| Auth | OIDC (localhost Keycloak) | disabled | Keycloak realm `eap` (`COOLSTORE_OIDC_AUTH_SERVER_URL`, `COOLSTORE_OIDC_SECRET`) |

Flyway migrates at startup; Hibernate only `validate`s in prod. Kafka consumer groups `coolstore-orders` and `coolstore-inventory` are intentionally **distinct** (JMS topic broadcast parity) — do not merge. `@Incoming` methods are `@Transactional` (RULEBOOK Amendment 1).

## Container & Deployment

```bash
podman build -t coolstore:dev -f Containerfile .
./scripts/container-smoke-test.sh coolstore:dev        # /q/health + REST round-trip in-container
oc apply -f openshift/                                  # see openshift/README.md for order + secrets
./scripts/openshift-smoke-test.sh https://<route>
```

- Multi-stage `Containerfile`: UBI8 OpenJDK 17 builder → runtime, non-root (UID 185), `-XX:MaxRAMPercentage=75.0`. All runtime config env-injected — **no secrets in images or Git**.
- `openshift/`: app Deployment (2 replicas — sticky sessions required until COOLSTORE-201; see migration notes), Service, Route, ConfigMap, PostgreSQL (+PVC), Kafka (Strimzi: `orders`, `orders-dlq`, `inventory-dlq`), Keycloak (realm import via ConfigMap; **rotate defaults at provisioning**).
- Optional native build: `mvn package -Pnative` (scaffold; JVM mode is the production default).
- Rollback: `git revert` + previous image tag (`oc rollout undo`); JBoss EAP path in git history remains the fallback until cutover.

## Auth (Keycloak OIDC)

Backend: `quarkus-oidc` (bearer tokens; `@PermitAll` products, `@RolesAllowed("user")` cart/orders — original Java EE model). Frontend: vendored Keycloak JS adapter configured by `META-INF/resources/keycloak.json` (realm `eap`, client `coolstore`; static file — update the auth-server URL at deploy time for in-cluster Keycloak).

## Ops

- Audit events: structured JSON via `utils/AuditLogger` (ORDER_PERSISTED, INVENTORY_UPDATED) — route/retain with your log collector.
- Known tracked work (was port-marker debt): COOLSTORE-201 multi-cart session state, COOLSTORE-202 cart null-safety, COOLSTORE-203 ShoppingCartTest CDI injection.
- Full runbook and decisions: `migration/EXECUTION-WAVE2-NOTES.md`, deployment topology: `openshift/README.md`.
