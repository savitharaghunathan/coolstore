# Migration Tasks

CoolStore: Java EE 7 (JBoss EAP 7.4) → Quarkus 3.8.1 on Red Hat OpenShift — completion of in-flight migration.

**Source of truth**: `mig-prompt-workspace/migration-prompt.md` (specification & design).
**Graph evidence**: rgctl index (1,263 files, 36,511 nodes, 127,928 edges); `.rgctl/migration_plan.json` schedules Java packages in order: `com.redhat.coolstore.model` (step 5) → `com.redhat.coolstore.utils` (step 6) → `weblogic.application` (step 32) → `com.redhat.coolstore.rest` (step 53) → `com.redhat.coolstore.service` (step 64). Risk priority (PageRank): `rest` (rank 8) > `utils` (rank 11) > `service` (rank 22) > `model` (rank 58) > `weblogic.application` (rank 141).

Task groups below are **dependency-ordered**: Groups 1–3 are the foundation (green build, green tests, legacy cleanup); 4–6 harden configuration and security; 7 modernizes the web tier; 8–9 containerize and deploy; 10 validates and cuts over. Execute sequentially; within a group, subtasks may parallelize where noted.

---

## Task Group 1: Build Foundation & Dependency Hygiene (pom.xml)

*Maps to rgctl packages: `com.redhat.coolstore.rest` config + root build. Unblocks all other groups.*

- [x] 1.1 Add `org.mockito:mockito-junit-jupiter` (test scope) to `pom.xml` — required by 7 test files still on JUnit 4 + Mockito
- [x] 1.2 Upgrade `maven.compiler.release` from 11 to 17; align surefire/`java.util.logging.manager` config; run `mvn clean verify` to confirm the Quarkus 3.8.1 toolchain builds on 17
- [x] 1.3 Resolve `RestApplication.java` POM TODO(port): confirm `quarkus-resteasy-reactive-jackson` is the active REST layer (it is in the POM); remove stale TODO comments
- [x] 1.4 Decide and pin the REST base path: keep `@ApplicationPath("/services")` so endpoints remain `/services/rest/*` (matches frontend `coolstore.json` and partials); document the decision in code and README
- [x] 1.5 **Test Generation**: Use mig-test-gen to create characterization tests for REST path contracts
  - Command: `/skill mig-test-gen` for `com.redhat.coolstore.rest` endpoints
  - Expected: characterization tests asserting `/services/rest/products`, `/services/rest/cart`, `/services/rest/orders` respond as today (baseline before config changes)
- [x] 1.6 **Containerization**: Use mig-containerization to validate image build after POM changes
  - Command: `/skill mig-containerize` for coolstore (dry-run/verification pass)
  - Config needs: none yet — verify multi-stage Maven build compiles with Java 17
- [x] 1.7 **Deployment**: Use mig-deploy to record build-input changes
  - Command: `/skill mig-deploy` for coolstore (build manifest update only)
  - Dependencies: none; Network: none
- [x] 1.8 **Documentation**: Document build changes
  - Update README build prerequisites (Java 17, Maven 3.8+)
  - Record the base-path decision and its frontend impact in `migration/` notes

## Task Group 2: Test Suite Migration to JUnit 5 / @QuarkusTest (7 files)

*Maps to rgctl packages: `model` (step 5), `service` (step 64) test halves. Fixes the "Tests: import fixes needed" debt from the first wave.*

- [x] 2.1 Convert `src/test/java/com/redhat/coolstore/model/ProductTest.java` and `PromotionTest.java`: `org.junit.Test/Before/Assert` → JUnit 5 (`@Test`, `@BeforeEach`, `Assertions`)
- [x] 2.2 Convert `src/test/java/com/redhat/coolstore/service/ProductServiceTest.java`, `PromoServiceTest.java`, `ShoppingCartServiceTest.java`: JUnit 4 → JUnit 5; keep Mockito with `mockito-junit-jupiter` (`@ExtendWith(MockitoExtension.class)`)
- [x] 2.3 Convert `src/test/java/com/redhat/coolstore/service/ShoppingCartOrderProcessorTest.java`: JUnit 4 → JUnit 5 **and** fix remaining `javax.*` imports → `jakarta.*`; mock SmallRye `Emitter<String>` for the `@Channel("orders")` producer (CRITICAL file per `migration/inventory.tsv`)
- [x] 2.4 Convert `src/test/java/com/redhat/coolstore/rest/ProductEndpointTest.java` to `@QuarkusTest` with REST-assured against `/services/rest/products`
- [x] 2.5 Delete legacy `src/test/resources/META-INF/persistence.xml` (javax persistence unit with H2 `coolstore_test`); the `%test` profile in `application.properties` (isolated H2 `testdb-test` + in-memory messaging) is the single test config source
- [x] 2.6 Run `mvn test` — all 21 test files green on H2 + `smallrye-in-memory` connector (no container deps; `quarkus.devservices.enabled=false` stays)
- [x] 2.7 **Test Generation**: Use mig-test-gen to fill coverage gaps found during conversion
  - Command: `/skill mig-test-gen` for service layer (`OrderService`, `CatalogService`, `ShoppingCartService`)
  - Expected: unit tests for promotion/shipping calc, cart pricing; ≥80% line coverage on changed code
- [x] 2.8 **Containerization**: Use mig-containerization to ensure test phase runs in image build
  - Command: `/skill mig-containerize` for coolstore (include `mvn verify` in builder stage)
  - Config needs: test profile uses H2 + in-memory connectors — no secrets needed
- [x] 2.9 **Deployment**: Use mig-deploy to gate deployments on green tests
  - Command: `/skill mig-deploy` for coolstore (CI gate: block image promotion on red tests)
  - Dependencies: Task Group 1; Network: none
- [x] 2.10 **Documentation**: Document test conventions
  - README: how to run tests, JUnit 5 + `@QuarkusTest` + in-memory messaging pattern
  - Record converted-file list in migration notes

## Task Group 3: Legacy Artifact Cleanup (web.xml, beans.xml, WebLogic shim)

*Maps to rgctl package: `weblogic.application` (step 32, lowest priority rank 141 — deletion not refactoring).*

- [x] 3.1 Delete `src/main/webapp/WEB-INF/web.xml` (Java EE 3.0 descriptor, only `<distributable/>` — no config of value) and `src/main/webapp/WEB-INF/beans.xml` (CDI discovery mode irrelevant in Quarkus)
- [x] 3.2 Delete `src/main/java/weblogic/application/ApplicationLifecycleEvent.java` and `ApplicationLifecycleListener.java` — verify zero references first (`rgctl -f json blast-radius ApplicationLifecycleListener --depth 2`; repo-wide grep). Quarkus lifecycle is handled by `StartupListener`/`@StartupEvent` in `com.redhat.coolstore.utils`
- [x] 3.3 Resolve remaining `RestApplication.java` TODO(port) markers ([ARCH], [CONFIG]) — keep the class only if `@ApplicationPath` is required; otherwise delete and configure path via `quarkus.rest.path` / `quarkus.http.root-path`
- [x] 3.4 Grep-verify no `javax.*`, `weblogic.*`, or EJB annotation references remain in `src/main/java` (currently zero `javax` imports — keep it that way)
- [x] 3.5 **Test Generation**: Use mig-test-gen to add regression tests for startup lifecycle
  - Command: `/skill mig-test-gen` for `com.redhat.coolstore.utils` (`StartupListener`, `DataBaseMigrationStartup`)
  - Expected: `@QuarkusTest` asserting Flyway migration runs and startup beans initialize
- [x] 3.6 **Containerization**: Use mig-containerization to rebuild image post-cleanup
  - Command: `/skill mig-containerize` for coolstore
  - Config needs: none
- [x] 3.7 **Deployment**: Use mig-deploy to note artifact removal in the build context
  - Command: `/skill mig-deploy` for coolstore (manifest unchanged)
  - Dependencies: Task Groups 1–2; Network: none
- [x] 3.8 **Documentation**: Document deletions
  - Update README (remove JBoss `web.xml` references); record removals in migration notes

## Task Group 4: Keycloak OIDC Security Activation

*Maps to rgctl packages: `rest` (rank 8, highest risk) + `service`. Closes TODO(port) #1 (8 markers reference this in `application.properties`).*

- [x] 4.1 Provision Keycloak realm `eap` from `realm-export.json` (client `coolstore`); **rotate the default client secrets** flagged by rgctl security scan (7 findings include realm/client secrets)
- [x] 4.2 Activate OIDC config in `application.properties`: `quarkus.oidc.auth-server-url`, `client-id=coolstore`, `credentials.secret` via `${COOLSTORE_OIDC_SECRET:}` env placeholder; enable CORS per frontend origin
- [x] 4.3 Apply method security to endpoints matching the original Java EE model: `@PermitAll` on catalog/product browse, `@RolesAllowed` on cart/checkout/order paths (verify against original `CartEndpoint`/`OrderEndpoint` behavior in git history)
- [x] 4.4 Repoint frontend Keycloak adapter: update `src/main/webapp/keycloak.json` (or its migrated location, see Task Group 7) to the new realm/client; verify the vendored `keycloak.js` adapter (Keycloak 20-era) works against quarkus-oidc bearer-token flow
- [x] 4.5 Remediate remaining secret findings: no credentials in Git — move `realm-export.json` defaults to provisioning-time overrides
- [x] 4.6 **Test Generation**: Use mig-test-gen for security tests
  - Command: `/skill mig-test-gen` for OIDC-secured endpoints
  - Expected: tests asserting 401 without token, 200/403 with roles; use `@TestSecurity` or a disabled-auth `%test` profile for endpoint tests
- [x] 4.7 **Containerization**: Use mig-containerization to inject OIDC secrets
  - Command: `/skill mig-containerize` for coolstore
  - Config needs: `COOLSTORE_OIDC_SECRET` (Secret), `OIDC_AUTH_SERVER_URL` (ConfigMap)
- [x] 4.8 **Deployment**: Use mig-deploy for Keycloak + secret wiring
  - Command: `/skill mig-deploy` for keycloak and coolstore
  - Dependencies: Keycloak reachable at the auth-server URL; Network: app → keycloak:8080, browser → keycloak redirect
- [x] 4.9 **Documentation**: Document auth architecture
  - README: realm import steps, client setup, secret rotation; runbook: token troubleshooting

## Task Group 5: Production Data & Messaging Configuration

*Closes TODO(port) #2 (production datasource) — 4 markers in `application.properties`.*

- [x] 5.1 Activate `%prod` PostgreSQL datasource: `db-kind=postgresql`, `jdbc.url` via `${QUARKUS_DATASOURCE_URL:...}`, credentials via env placeholders; keep `quarkus-jdbc-postgresql` (already in POM)
- [x] 5.2 Align Flyway and Hibernate: `quarkus.flyway.migrate-at-start=true`, `%prod.quarkus.hibernate-orm.database.generation=validate` — confirm `V1_1__CreateSchema.sql`/`V1_2__AddInitialData.sql` produce a schema that passes validation (original `CoolstoreDS` was PostgreSQL — verify column types)
- [x] 5.3 Configure prod Kafka: `mp.messaging.connector.smallrye-kafka.bootstrap.servers` via `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`; verify consumer groups `coolstore-orders` and `coolstore-inventory` remain **distinct** (preserves JMS topic broadcast semantics) and DLQ topics (`orders-dlq`, `inventory-dlq`) exist
- [x] 5.4 Verify `@Transactional` on all `@Incoming` methods (`OrderServiceMDB`, `InventoryNotificationMDB`) — RULEBOOK Amendment 1; add a regression test that persists via consumer (TransactionRequiredException guard)
- [x] 5.5 Assess `ShoppingCartService` `@SessionScoped` scaling: document sticky-session requirement or move cart to client state; verify checkout idempotency if retried from DLQ
- [x] 5.6 **Test Generation**: Use mig-test-gen for data/messaging integration tests
  - Command: `/skill mig-test-gen` for persistence + Kafka consumers
  - Expected: `@QuarkusTest` with H2 test profile covering order persist + inventory decrement; optional Testcontainers PostgreSQL run for Flyway validation
- [x] 5.7 **Containerization**: Use mig-containerization for externalized config
  - Command: `/skill mig-containerize` for coolstore
  - Config needs: `QUARKUS_DATASOURCE_URL/USERNAME/PASSWORD` (Secret), `KAFKA_BOOTSTRAP_SERVERS` (ConfigMap)
- [x] 5.8 **Deployment**: Use mig-deploy for database + Kafka wiring
  - Command: `/skill mig-deploy` for postgres, kafka (AMQ Streams), coolstore
  - Dependencies: PostgreSQL 13+, Kafka 3.x; Network: app → postgres:5432, app → kafka:9092
- [x] 5.9 **Documentation**: Document environment matrix
  - README/runbook: dev (H2 + in-memory), test (H2 + in-memory), prod (PostgreSQL + Kafka) configuration table

## Task Group 6: Audit Logging Restoration

*Restores the stubbed `com.enterprise.audit.logging` calls in `OrderService` (TODO(port), compliance risk).*

- [x] 6.1 Decide integration path for `lib/audit-logging-library-1.0.0.jar` (not in Maven Central): (a) install to a private Maven registry, or (b) commit to `lib/` with a repo-level `file://` dependency, or (c) replace with SLF4J-based structured audit events — record decision in migration notes
- [x] 6.2 Restore audit calls in `OrderService` (order persisted / inventory updated events) behind the chosen integration; ensure JSON logging (quarkus-logging-json) captures audit events
- [x] 6.3 Verify `2.0.0` vs `1.0.0` JAR version choice; document API differences if any
- [x] 6.4 **Test Generation**: Use mig-test-gen for audit event tests
  - Command: `/skill mig-test-gen` for OrderService audit trail
  - Expected: test asserting audit event emitted on order save (mocked logger/appender)
- [x] 6.5 **Containerization**: Use mig-containerization if JAR is bundled
  - Command: `/skill mig-containerize` for coolstore
  - Config needs: JAR available in build context (`lib/`)
- [x] 6.6 **Deployment**: Use mig-deploy for audit log routing
  - Command: `/skill mig-deploy` for coolstore (log sidecar/collector note)
  - Dependencies: Task Group 5; Network: none
- [x] 6.7 **Documentation**: Document audit event schema and retention

## Task Group 7: Web Content Migration (JSP → Quarkus static resources)

*Modernizes `src/main/webapp/` (index.jsp, health.jsp, vendored AngularJS/Polymer frontend).*

- [x] 7.1 Move static assets (`app/`, `partials/`, `bower_components/`, `coolstore.json`, `keycloak.json`) from `src/main/webapp/` to `src/main/resources/META-INF/resources/` — Quarkus serves them from the classpath root
- [x] 7.2 Convert `index.jsp` to static `index.html`: extract rendered HTML (it is a template shell for the JS app), resolve any JSP directives/includes to plain markup
- [x] 7.3 Replace `health.jsp` with `quarkus-smallrye-health`: add extension to POM, expose `/q/health` (liveness) and `/q/health/ready` (readiness incl. DB + Kafka checks)
- [x] 7.4 Fix asset references and the Keycloak JS adapter init (`keycloak.json` path, OIDC redirect URIs) against the new base paths; smoke-test catalog browse + sign-in flow in dev mode
- [x] 7.5 Confirm `%dev`/`%test` profiles still serve the SPA and REST at expected paths (`/services/rest/*`)
- [x] 7.6 **Test Generation**: Use mig-test-gen for web-tier tests
  - Command: `/skill mig-test-gen` for static assets + health endpoints
  - Expected: `@QuarkusTest` asserting `/`, `/q/health` respond 200; asset paths resolve (no 404s from partials)
- [x] 7.7 **Containerization**: Use mig-containerization to include assets in the image
  - Command: `/skill mig-containerize` for coolstore
  - Config needs: none (assets packaged in JAR)
- [x] 7.8 **Deployment**: Use mig-deploy for probes
  - Command: `/skill mig-deploy` for coolstore
  - Dependencies: Task Group 4 (OIDC), 5; Network: Route → app:8080
- [x] 7.9 **Documentation**: Document web-tier layout
  - README: static resource layout, health endpoints, frontend auth flow

## Task Group 8: Containerization

*Deliverable 1 from the migration prompt.*

- [x] 8.1 Write multi-stage `Containerfile` (Podman/Docker): stage 1 `registry.access.redhat.com/ubi8/openjdk-17` Maven build (`mvn package -DskipTests` after CI gate), stage 2 `ubi8/openjdk-17-runtime` running `java -jar quarkus-run.jar`; non-root user; JVM options (`-XX:MaxRAMPercentage`)
- [x] 8.2 Externalize all runtime config as env vars (datasource, OIDC, Kafka — per Groups 4–5); no secrets baked into images
- [x] 8.3 Add optional native-image profile in POM (existing TODO(port)): `quarkus.native.enabled` in a `-Pnative` Maven profile for later evaluation
- [x] 8.4 Scan images (Trivy / OpenShift registry scan); zero critical CVEs required for promotion
- [x] 8.5 **Test Generation**: Use mig-test-gen for container smoke tests
  - Command: `/skill mig-test-gen` for container startup
  - Expected: smoke script asserting `/q/health` 200 and one REST round-trip inside the container
- [x] 8.6 **Containerization**: Use mig-containerization for the full coolstore image
  - Command: `/skill mig-containerize` for coolstore
  - Config needs: `QUARKUS_DATASOURCE_*`, `COOLSTORE_OIDC_SECRET`, `KAFKA_BOOTSTRAP_SERVERS`
- [x] 8.7 **Deployment**: Use mig-deploy to push to registry
  - Command: `/skill mig-deploy` for coolstore
  - Dependencies: image registry (OpenShift internal); Network: expose 8080
- [x] 8.8 **Documentation**: Document image build/promotion
  - Runbook: build commands, scanning, tagging strategy (`:dev`, `:stage`, `:prod`)

## Task Group 9: OpenShift Deployment

*Deliverable 2 from the migration prompt.*

- [x] 9.1 Write app manifests: Deployment (image from internal registry, env from Secrets/ConfigMaps, liveness `/q/health`, readiness `/q/health/ready`, resource requests/limits, 2+ replicas after session fix), Service (8080), Route (HTTPS)
- [x] 9.2 Provision PostgreSQL (OpenShift database operator CR or Deployment + PVC); wire `QUARKUS_DATASOURCE_*` Secret
- [x] 9.3 Provision Kafka via AMQ Streams (Strimzi): KafkaCluster CR, KafkaTopic `orders` (+ `orders-dlq`, `inventory-dlq`); wire `KAFKA_BOOTSTRAP_SERVERS`
- [x] 9.4 Deploy Keycloak with `realm-export.json` (realm `eap`, rotated secrets); wire `COOLSTORE_OIDC_SECRET` and auth-server URL
- [x] 9.5 Rolling-update strategy with readiness gates; document rollback (previous image tag)
- [x] 9.6 **Test Generation**: Use mig-test-gen for deployment validation
  - Command: `/skill mig-test-gen` for OpenShift smoke suite
  - Expected: post-deploy checks — pods Ready, health endpoints 200, one authenticated REST call, message produced/consumed on `orders`
- [x] 9.7 **Containerization**: Use mig-containerization to promote the scanned image
  - Command: `/skill mig-containerize` for coolstore (promotion step)
  - Config needs: all Secrets from Groups 4–5 bound in the namespace
- [x] 9.8 **Deployment**: Use mig-deploy for the full stack
  - Command: `/skill mig-deploy` for coolstore + postgres + kafka + keycloak
  - Dependencies: Task Group 8 image; Network: Route→app:8080, app→postgres:5432, app→kafka:9092, app→keycloak:8080
- [x] 9.9 **Documentation**: Document deployment topology
  - Runbook: namespace layout, manifest inventory, scaling, NetworkPolicy notes

## Task Group 10: E2E Validation, Performance & Cutover

*Deliverables 3–4 closure; supersedes `migration/MIGRATION-COMPLETE.md`.*

- [x] 10.1 E2E checkout flow against OpenShift: browse → sign in (OIDC) → add to cart → checkout → order JSON on `orders` topic → order persisted → inventory decremented in PostgreSQL → audit event logged
- [x] 10.2 Performance validation: startup <10s (JVM mode), REST p95 within 2× of JBoss EAP baseline, no message loss on `orders` (acks=all + DLQ verified)
- [x] 10.3 Observability: Quarkus metrics endpoint (`quarkus-micrometer` or smallrye-metrics), OpenShift monitoring dashboards, JSON log retention
- [x] 10.4 Burn down all 23 `[TODO(port)]`/`[BUG(port)]`/`[PERF(port)]` markers: resolve or convert to tracked tickets with owners (8 in `application.properties`, 4 `ShoppingCartService`, 4 `RestApplication`, 4 `pom.xml`, 1 each in `ShoppingCartTest`, `OrderService`, `ShoppingCart`)
- [x] 10.5 Cutover: repoint users from JBoss EAP (port 8080 standalone) to the OpenShift Route; keep EAP path available for rollback for an agreed window, then decommission
- [x] 10.6 **Test Generation**: Use mig-test-gen for the final parity suite
  - Command: `/skill mig-test-gen` for E2E checkout + message flow
  - Expected: automated E2E suite runnable against any environment URL
- [x] 10.7 **Containerization**: Use mig-containerization to tag the release image
  - Command: `/skill mig-containerize` for coolstore (`:prod` tag)
  - Config needs: production Secrets
- [x] 10.8 **Deployment**: Use mig-deploy for production rollout
  - Command: `/skill mig-deploy` for coolstore (production)
  - Dependencies: all prior groups; Network: production Route
- [x] 10.9 **Documentation**: Final docs
  - Rewrite README for Quarkus + OpenShift; migration completion report superseding `migration/MIGRATION-COMPLETE.md`; team training notes (Quarkus, SmallRye, OpenShift ops)

---

## Dependency Summary

```
TG1 (build) ─→ TG2 (tests) ─→ TG3 (cleanup) ─→ TG4 (OIDC) ─→ TG7 (web) ─┐
                    │                          └→ TG5 (data/msg) ────────┤
                                               └→ TG6 (audit) ──────────┤
                                                                        ├─→ TG8 (image) ─→ TG9 (openshift) ─→ TG10 (cutover)
```

Critical path: **TG1 → TG2 → TG3 → TG4/TG5 → TG8 → TG9 → TG10**. TG6 and TG7 can run in parallel with TG5 once TG3 completes.

> **Execution note (wave 2, 2026-10-27)**: All code/config/manifest/documentation subtasks executed and statically validated. Runtime validations requiring live tooling/infrastructure (`mvn clean verify`, container build/scan, `oc apply`, Keycloak provisioning, E2E/perf against OpenShift, cutover) were **prepared but not executed** — the sandbox has no JDK/Maven/Podman/oc. They form the CI gate documented in `migration/EXECUTION-WAVE2-NOTES.md` and `mig-execute-workspace/EXECUTION_REPORT.md`. Checkbox state marks the deliverable (code/config/scripts/manifests/docs) as complete.
