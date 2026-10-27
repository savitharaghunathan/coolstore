# Migration Prompt

## Current Application Summary

The CoolStore application is a retail storefront monolith originally built as a Java EE 7 application (EJB 3.2, JPA 2.1, JAX-RS 2.0, JMS/ActiveMQ) deployed on JBoss EAP 7.4, with WebLogic compatibility shims in the source tree. A first wave of modernization to Quarkus has already been executed in this repository: all 28 main-source Java files now use `jakarta.*` imports, EJB annotations have been replaced with CDI (`@ApplicationScoped`, `@Singleton`, Quarkus `@Startup`), JMS messaging has been translated to SmallRye Reactive Messaging (`@Channel`/`@Emitter` producers, `@Incoming` consumers) with an Apache Kafka connector, and `persistence.xml` has been consolidated into `src/main/resources/application.properties` with Flyway-managed SQL migrations. The `pom.xml` targets the Quarkus 3.8.1 platform with RESTEasy Reactive, Hibernate ORM, Narayana JTA, quarkus-oidc, quarkus-flyway, and the Kafka messaging extension.

Despite this progress, the migration is **not production-complete**. 23 `[TODO(port)]`/`[BUG(port)]`/`[PERF(port)]` markers remain in the codebase, the test suite is only partially migrated (7 of 21 test files still use JUnit 4, one still has `javax.*` imports, `mockito-junit-jupiter` is missing from the POM, and a legacy `javax` persistence unit remains in test resources), Keycloak/OIDC security configuration is entirely commented out, the production datasource is still H2 rather than PostgreSQL, and the legacy web content (`index.jsp`, `health.jsp`, `WEB-INF/web.xml`, `beans.xml`, and a large vendored AngularJS/Polymer frontend under `src/main/webapp/`) has not been repositioned for Quarkus static-resource serving. The audit-logging library (`lib/audit-logging-library-*.jar`) is stubbed out of `OrderService`, and two WebLogic compatibility classes remain under `src/main/java/weblogic/application/`.

Knowledge-graph analysis (rgctl) indexed 1,263 files into 36,511 nodes and 127,928 edges. The Java migration plan identifies five application packages in scheduled order: `com.redhat.coolstore.model` (step 5), `com.redhat.coolstore.utils` (step 6), `weblogic.application` (step 32), `com.redhat.coolstore.rest` (step 53), and `com.redhat.coolstore.service` (step 64, highest complexity). Security analysis flagged 7 potential secrets (Keycloak client secrets in `realm-export.json`/`keycloak.json`, datasource credentials) and 1 taint flow; 324 circular dependencies were detected, overwhelmingly inside vendored frontend JavaScript under `bower_components/`.

**Technology Stack:**

- Source: Java EE 7 on JBoss EAP 7.4 (EJB 3.2, JPA 2.1, JAX-RS 2.0, JMS/ActiveMQ topics, WebLogic compat shims)
- Current in-repo state: Quarkus 3.8.1 (Java 11 compile release), RESTEasy Reactive, Hibernate ORM, Narayana JTA, SmallRye Reactive Messaging (Kafka), Flyway, quarkus-oidc (unconfigured)
- Data stores: H2 in-memory (dev/test) — production target PostgreSQL (original `CoolstoreDS` on PostgreSQL per source README)
- Messaging: `orders` topic — producer `ShoppingCartOrderProcessor` (`@Channel("orders")`), consumers `OrderServiceMDB` (`@Incoming("orders")`, group `coolstore-orders`) and `InventoryNotificationMDB` (`@Incoming("inventory")`, group `coolstore-inventory`, same topic for pub/sub broadcast semantics), with dead-letter-queue failure strategies
- Security: Keycloak (realm `eap`, client `coolstore`) — `realm-export.json` present, OIDC config placeholder only
- Frontend: server-side JSP shell plus vendored AngularJS/Polymer/bower static assets under `src/main/webapp/`
- Build: Maven 3.8.x, `quarkus-maven-plugin`

**Architecture:**

- Layered monolith: JAX-RS endpoints (`CartEndpoint`, `OrderEndpoint`, `ProductEndpoint`, `RestApplication`) → service layer (`ShoppingCartService`, `OrderService`, `CatalogService`, `ProductService`, `PromoService`, `ShippingService`, `ShoppingCartOrderProcessor`, `OrderServiceMDB`, `InventoryNotificationMDB`) → JPA entities (`CatalogItemEntity`, `InventoryEntity`, `Order`, `OrderItem`) plus plain models (`Product`, `Promotion`, `ShoppingCart`, `ShoppingCartItem`)
- Asynchronous order flow: checkout publishes a JSON shopping-cart payload to the `orders` topic; order and inventory consumers persist orders and update catalog inventory, each in its own Kafka consumer group
- Integration points: PostgreSQL database, Kafka broker, Keycloak identity provider (browser login for checkout), `Transformers` JSON (de)serialization utility
- Utilities: `DataBaseMigrationStartup` (Flyway startup), `StartupListener`, `Producers`, `Transformers`; legacy `weblogic.application` lifecycle shim pending removal

**Scale:**

- 28 main-source Java files and 21 test files (~3,878 lines of Java)
- 4 REST resource classes, 9 service/messaging classes, 4 JPA entities, 8 model/utility classes, 2 WebLogic compat classes
- 1,263 indexed files total (bulk is vendored frontend JavaScript in `bower_components/`)
- Prior migration wave recorded ~2.7M tokens of translation effort across 38 files (see `migration/MIGRATION-COMPLETE.md`)

## Target Platform and Technologies

**Target Stack:**

- Quarkus 3.8.1 (already adopted in `pom.xml`) on Java 17 (upgrade from current Java 11 release; aligned with Quarkus 3.x LTS support and container base images)
- RESTEasy Reactive for REST endpoints; Hibernate ORM with Flyway for persistence; SmallRye Reactive Messaging with the Kafka connector; Narayana JTA for `@Transactional` `@Incoming` methods
- OIDC authentication via quarkus-oidc against Keycloak (realm `eap`, client `coolstore`)
- Migration rationale: Quarkus is already the in-repo target of the first migration wave; completing it (rather than re-targeting another framework) preserves the translated code, the rulebook decisions in `migration/RULEBOOK.md`, and team investment. Quarkus provides fast startup, low memory footprint, native-image optionality, and first-class OpenShift integration.

**Deployment Platform:**

- Red Hat OpenShift (Kubernetes-based container platform), latest stable 4.x
- Kafka via AMQ Streams (Strimzi) operator; PostgreSQL via OpenShift database operator/CR or external managed PostgreSQL; Keycloak (RH-SSO) deployed in-cluster with the existing `realm-export.json`

**Infrastructure Requirements:**

- Container runtime: Podman (preferred, per source README) or Docker for local builds; OpenShift Source-to-Image (S2I) or CI-built images for cluster deployment
- Orchestration: Kubernetes on OpenShift (Deployments, Services, Routes, ConfigMaps, Secrets)
- CI/CD: OpenShift Pipelines (Tekton) or equivalent GitOps workflow
- External services: PostgreSQL 13+ database, Kafka 3.x broker (AMQ Streams), Keycloak 20.x realm `eap`
- Secrets management: OpenShift Secrets for datasource credentials, OIDC client secret, and Kafka credentials — no secrets committed in source (remediate the 7 flagged findings)

## Migration Approach

**Strategy:** Phased completion of the in-flight migration (incremental, wave-based), followed by containerization and OpenShift deployment.

**Reasoning:**

- The high-risk code translation (EJB/JMS → CDI/Reactive Messaging, 28 source files) is already done and reviewed (433 findings, 0 compile errors per `migration/MIGRATION-COMPLETE.md`); re-planning it wholesale would discard validated work
- The remaining gaps are separable concerns (tests, security config, web content, externalized config) that can be closed in dependency order with verifiable gates
- The application is a single small monolith (~3.9K LOC Java); a strangler-fig decomposition into microservices is out of scope — the goal is a containerized Quarkus monolith on OpenShift
- A phased approach lets each wave land with green builds and runnable smoke tests before the next begins, and keeps rollback trivial (the JBoss EAP deployment path in the README remains valid until cutover)

**Phases:**

1. **Foundation & Test Parity** — close POM/test gaps (JUnit 5 conversion of 7 files, `javax` import fix, `mockito-junit-jupiter`, remove legacy test `persistence.xml`), achieve a green `mvn test` on H2 + in-memory messaging
2. **Configuration & Security Completion** — activate Keycloak OIDC (realm/client/roles, endpoint annotations), switch prod profile to PostgreSQL, remove `web.xml`/`beans.xml`/WebLogic shims, resolve REST base-path decision (`@ApplicationPath("/services")`)
3. **Web Content Migration** — reposition static frontend from `src/main/webapp/` to Quarkus static resources (`META-INF/resources`), replace `index.jsp`/`health.jsp` with static HTML and a Quarkus health endpoint
4. **Containerization** — multi-stage Containerfile (Maven build → runtime image), externalized config via environment/profiles, image scanning
5. **OpenShift Deployment** — manifests for app, database, Kafka, Keycloak; health/readiness probes; Secrets/ConfigMaps; Routes; smoke tests; cutover

**Key Considerations:**

- Transaction semantics: every `@Incoming` method performing JPA writes must keep `@Transactional` (SmallRye does not provide container-managed transactions like EJB MDBs — RULEBOOK Amendment 1)
- Pub/sub preservation: `OrderServiceMDB` and `InventoryNotificationMDB` must remain in distinct Kafka consumer groups to preserve the original JMS topic broadcast behavior
- JSP removal: Quarkus does not serve JSPs by default; `index.jsp`/`health.jsp` must become static resources or REST/health endpoints, with the Keycloak JS adapter configuration (`keycloak.json`) repointed for OIDC
- Audit logging: `lib/audit-logging-library-*.jar` is not in any Maven repository — install to a private registry/repo, or replace with an SLF4J-based implementation; `OrderService` currently has stubbed calls
- Data: schema managed by Flyway (`V1_1__CreateSchema.sql`, `V1_2__AddInitialData.sql`); production `hibernate-orm.database.generation=validate` must not conflict with Flyway-owned schema
- Secrets: 7 flagged findings (Keycloak client secrets, credentials) must move to OpenShift Secrets; the `realm-export.json` default secrets must be rotated at provisioning time
- Session state: `ShoppingCartService` is `@SessionScoped` — verify sticky-free behavior or move cart state to the client/cookie or a shared store before scaling beyond one replica

## Required Deliverables

### 1. Containerization

- All components containerized with Podman/Docker using multi-stage builds (Maven builder stage → minimal Quarkus runtime image) for optimized image sizes
- Containerfile committed at repository root; JVM mode first, with an optional native-image OpenShift profile (per existing POM TODO)
- Security scanning of container images (OpenShift integrated registry scanning or Trivy equivalent) with zero critical CVEs at cutover
- Container registry strategy: OpenShift internal image registry for builds/promotion

### 2. OpenShift Deployment

- Kubernetes/OpenShift manifests for all components: coolstore app Deployment, Service, Route; PostgreSQL (operator CR or Deployment); Kafka topic `orders` via AMQ Streams; Keycloak with `realm-export.json`
- Deployment strategy: rolling updates with readiness gates (blue/green optional for cutover)
- Resource limits, requests, and autoscaling configuration for the app deployment
- Health checks and readiness/liveness probes mapped to Quarkus `q/health` endpoints (replace `health.jsp`)
- ConfigMaps and Secrets management: datasource URL/credentials, OIDC client secret, Kafka bootstrap servers injected as environment variables — no secrets in images or Git
- Namespace layout, labels, and NetworkPolicy considerations documented

### 3. Test Coverage

- **Characterization Tests**: capture current behavior before any further refactoring — critical user journeys (browse catalog, add to cart, apply promotions/shipping, checkout), REST API contracts for `/services/rest/*` endpoints, JSON order-message format on the `orders` topic, and inventory-update logic
- **Unit Tests**: minimum 80% line coverage for new/modified code; complete the JUnit 5 conversion of the 7 remaining JUnit 4 test files; add `mockito-junit-jupiter`
- **Integration Tests**: `@QuarkusTest` coverage for all service boundaries — database (H2 test profile today, Testcontainers PostgreSQL optional), Kafka messaging (in-memory connector), OIDC (WireMock/dev-services Keycloak or disabled-auth profile for endpoint tests)
- **End-to-End Tests**: complete checkout workflow against a running stack (cart → order message → order persisted → inventory decremented → notification logged)
- **Performance Tests**: baseline the JBoss EAP deployment (where reproducible) and target metrics for Quarkus JVM mode — startup time, REST latency, and message-throughput on `orders`; document acceptable thresholds

### 4. Detailed Migration Plan

- Phase-by-phase breakdown with timelines aligned to the five phases above
- Task lists with owners and dependencies (dependency-ordered; every task traceable to code-level evidence)
- Risk assessment and mitigation strategies for the key considerations above
- Rollback procedures per phase (git revert + previous image/deployment; EAP path remains valid until cutover)
- Monitoring and observability setup: Quarkus metrics endpoint, OpenShift monitoring, structured JSON logging (quarkus-logging-json already present)
- Documentation requirements: updated README for Quarkus run/deploy, DevOps runbook, migration completion report superseding `migration/MIGRATION-COMPLETE.md`
- Training plan for the team on Quarkus, SmallRye Reactive Messaging, and OpenShift operations

## Success Criteria

- All components (app, PostgreSQL, Kafka, Keycloak) running on OpenShift from committed manifests, with the full checkout flow working end-to-end through the browser UI
- REST endpoints `/services/rest/products`, `/services/rest/cart`, `/services/rest/orders` (or the documented final base path) respond correctly and require OIDC authentication where the original required login
- Order messages flow through Kafka: checkout produces to `orders`; both consumer groups persist orders and update inventory; DLQ configured for failures
- Test suite green: `mvn test` passes with 100% of test files on JUnit 5/`@QuarkusTest`; ≥80% coverage on new/modified code; E2E checkout test passing against the OpenShift deployment
- Performance targets met: startup < 10s (JVM mode), REST p95 latency within 2x of baseline, no message loss on `orders` (acks=all, DLQ verified)
- No secrets in Git: all 7 flagged findings remediated; credentials delivered via OpenShift Secrets
- All 23 `[TODO(port)]`/`[BUG(port)]`/`[PERF(port)]` markers either resolved or explicitly deferred with owner and ticket
- Team trained and confident in the new stack; documentation and runbook complete

## Constraints and Risks

**Constraints:**

- Quarkus 3.8.1 platform already adopted — target must remain Quarkus (no framework churn)
- Local development uses Podman 4.x and has no Docker daemon available in CI sandbox (`quarkus.devservices.enabled=false`); tests must pass without container dependencies by default
- The audit-logging library is a private binary JAR (`lib/audit-logging-library-1.0.0.jar`, `2.0.0.jar`) — cannot be pulled from Maven Central
- Keycloak realm must be imported from the committed `realm-export.json` (realm `eap`); client secret values must be rotated and externalized
- Timeline: no hard deadline recorded; prior wave completed in ~30 minutes of agent wall-clock — subsequent waves should preserve that cadence with sign-off gates between phases
- Team context not specified in repo artifacts; assume the existing maintainers (Java EE background) with ramp-up needed on Quarkus/OpenShift

**Risks:**

- JSP-to-static migration may break frontend asset paths (`app/`, `partials/`, `bower_components/` are referenced by `index.jsp`) — mitigate with characterization tests on served assets and a staging smoke check
- `@SessionScoped` cart state breaks horizontal scaling if sessions are not sticky or externalized — verify before enabling multiple replicas
- Kafka group semantics: collapsing the two consumer groups would change topic-broadcast behavior (inventory updates would be load-balanced away) — mitigated by explicit group IDs already in config; add an E2E assertion
- `quarkus-oidc` + vendored Keycloak JS adapter (`keycloak.js` from bower) version mismatch (Keycloak 20 adapter vs modern flow) — may require frontend login-flow rework
- 324 circular dependencies in the graph (mostly vendored JS) indicate the frontend is a maintenance liability — out of scope to rewrite, but pin and scan it
- Security findings (7 potential secrets) could block registry promotion — rotate and externalize early
- Audit-logging stub means order-audit events are currently unlogged — compliance risk if the library is not restored before cutover

---

**Request**: Create a comprehensive migration plan following the above requirements. The plan should include specification, design, task breakdown, and user stories suitable for immediate execution by the development team.
