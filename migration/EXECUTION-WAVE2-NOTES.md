# Migration Execution Wave 2 — Decisions & Runbook Notes

Companion to mig-execute-workspace/EXECUTION_REPORT.md. Source of truth: mig-prompt-workspace/migration-prompt.md.

## Decisions recorded (required by plan)

1. **REST base path (TG1.4)** — kept `@ApplicationPath("/services")`; endpoints stay `/services/rest/*`. Single source of truth in code; frontend (coolstore.json, AngularJS services) unchanged.
2. **Audit logging (TG6.1)** — option (c): replaced private `com.enterprise.audit.logging` library (`lib/audit-logging-library-1.0.0.jar`/`2.0.0.jar`, not in Maven Central) with SLF4J-based structured events (`utils/AuditLogger.java`, AUDIT marker + JSON payload, captured by quarkus-logging-json). No private registry or file:// dependency needed. Version question (TG6.3) is thereby moot; the JARs remain in lib/ untouched for reference. Event schema: ORDER_PERSISTED{orderId,orderValue,itemCount,customerName}, INVENTORY_UPDATED{productId,quantity}. Retention: platform log retention policy (runbook item).
3. **Session scaling (TG5.5)** — `ShoppingCartService` is `@SessionScoped` (cart state per HTTP session). Until COOLSTORE-201 (cartId-keyed carts / client-side cart) lands, **sticky sessions are required for multi-replica rollout**. OpenShift manifests ship with `replicas: 2` + the documented requirement to enable route-level session affinity; alternatively run 1 replica. Checkout idempotency on DLQ retry is NOT guaranteed — retried poison messages land in `orders-dlq` for manual replay (documented runbook step).
4. **Messaging channel fix (TG5.4)** — `InventoryNotificationMDB` `@Incoming("inventory-updates")` did not match the configured channel `mp.messaging.incoming.inventory.*`; corrected to `@Incoming("inventory")` (matches migration-prompt spec and preserves the distinct `coolstore-inventory` group). `OrderServiceMDB.onMessage` was missing `@Transactional` (RULEBOOK Amendment 1) — added. Regression tests guard both.
5. **Security model (TG4.3)** — verified against original Java EE sources in git history (commit 34c43fe): `@PermitAll` ProductEndpoint, `@RolesAllowed("user")` CartEndpoint/OrderEndpoint (class level, as original).
6. **Secrets (TG4.1/4.5)** — rgctl security scan findings reviewed. `keycloak.json` repointed (realm eap, client coolstore, auth-server-url = server root per vendored adapter v1.9.8 URL composition). `realm-export.json` contains no client-secret values under standard `secret`/`credentials.secret` keys (scrub verified 0); the file is imported via ConfigMap at provisioning time and **default credentials must be rotated then** (openshift/keycloak.yaml documents this). All runtime credentials are env-injected (`COOLSTORE_OIDC_SECRET`, `QUARKUS_DATASOURCE_*`) — none in Git.
7. **Environment matrix (TG5.9)** — dev: H2 + smallrye-in-memory; test: isolated H2 (`testdb-test`) + smallrye-in-memory + OIDC disabled (`%test.quarkus.oidc.tenant.enabled=false`); prod: PostgreSQL 13+ + Kafka 3.x (AMQ Streams) + Keycloak 20 (realm eap), all env-injected.

## Environment constraints (this execution wave)

- No JDK/Maven/Podman/oc in the execution sandbox: `mvn clean verify` (TG1.2/2.6), container build/scan (TG1.6/8.4/8.6), and in-cluster deployment (TG9) were NOT executed here. All changes validated statically (import/reference/grep gates, YAML/bash syntax checks). **CI follow-up gate required before promotion**: `mvn clean verify` on JDK 17, `scripts/container-smoke-test.sh`, image scan (zero critical CVEs), `scripts/openshift-smoke-test.sh` against the Route.
- Health.jsp replacement and static-asset serving validated statically (paths/greps); dev-mode browser smoke of SPA + OIDC sign-in remains a pre-cutover step (runbook).

## Marker burn-down (TG10.4)

All 23 port markers resolved or ticketed:
- application.properties (8): OIDC activation, %prod datasource, Flyway alignment, status notes — RESOLVED.
- RestApplication.java (4): base path decision — RESOLVED (class kept, documented).
- pom.xml (4): REST layer confirmed, H2 %test note resolved, audit decision recorded, native profile scaffolded — RESOLVED.
- ShoppingCartService.java (4): multi-cart gaps → **COOLSTORE-201** (owner: coolstore-services), null-safety → **COOLSTORE-202** — TICKETED.
- OrderService.java (1): audit restored via AuditLogger — RESOLVED.
- ShoppingCart.java (1): JSON serialization validated by tests — RESOLVED.
- ShoppingCartTest.java (1): CDI injection note → **COOLSTORE-203** — TICKETED.

## Rollback

Per-phase rollback = `git revert` of the execution commit + previous image/deployment tag; JBoss EAP deployment path documented in README history until cutover (TG10.5 keeps the EAP rollback window).
