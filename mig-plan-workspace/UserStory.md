# User Stories

CoolStore: Java EE 7 → Quarkus 3.8.1 on Red Hat OpenShift — completion of in-flight migration.
Task references point to task groups in `tasks.md`. Specification: `../mig-prompt-workspace/migration-prompt.md`.

---

## User Story 1: Green Build and Test Parity

**As a:** CoolStore maintainer
**I want:** the Quarkus build green on Java 17 with the full test suite converted to JUnit 5 and passing, and all legacy Java EE artifacts (web.xml, beans.xml, WebLogic shim) removed
**So that:** the repository has a trustworthy foundation — every later change is verified by tests, and no dead Java EE configuration can drift back in

---

### Tasks
- ## 1. Build Foundation & Dependency Hygiene (pom.xml)
- ## 2. Test Suite Migration to JUnit 5 / @QuarkusTest (7 files)
- ## 3. Legacy Artifact Cleanup (web.xml, beans.xml, WebLogic shim)

### Acceptance Criteria
- [ ] `mvn clean verify` succeeds on Java 17 with the Quarkus 3.8.1 toolchain
- [ ] All 21 test files run on JUnit 5 / `@QuarkusTest`; zero `org.junit.Test`/`org.junit.Before` references and zero `javax.*` imports under `src/test/java`
- [ ] `mockito-junit-jupiter` present in POM; `src/test/resources/META-INF/persistence.xml` deleted; test config sourced solely from the `%test` profile
- [ ] `src/main/webapp/WEB-INF/web.xml`, `beans.xml`, and `src/main/java/weblogic/application/*` deleted with no dangling references (blast-radius check clean)
- [ ] REST base path pinned to `/services/rest/*` and covered by characterization tests
- [ ] ≥80% line coverage on newly modified code

### Details
- **Priority:** High (unblocks all other stories)
- **Estimate:** 13 story points (3 groups; test conversion of 7 files is the bulk)

### Preconditions
- Repository state as analyzed by rgctl (28 main / 21 test files; 23 port markers)
- Maven 3.8+, Java 17 JDK available

### Steps to Reproduce / Implementation Notes
1. Update POM (dependency, Java 17, TODO resolution) — Task Group 1
2. Convert the 7 JUnit 4 files; fix `javax` imports in `ShoppingCartOrderProcessorTest`; delete legacy test persistence unit — Task Group 2
3. Delete legacy web descriptor and WebLogic shim after reference check — Task Group 3
4. Validate: `mvn clean verify` green; grep confirms no legacy references

### Dependencies
- None (first story)

### Test Cases
- Test 1: `mvn test` — full suite green (mig-test-gen output: characterization tests for `/services/rest/*` contracts)
- Test 2: Startup lifecycle `@QuarkusTest` — Flyway migration executes, startup beans initialize
- Test 3: `ShoppingCartOrderProcessorTest` — mocked `Emitter<String>` receives JSON cart payload on checkout

### Notes / Comments
- The 7 files to convert: `ProductTest`, `PromotionTest`, `ProductEndpointTest`, `ProductServiceTest`, `PromoServiceTest`, `ShoppingCartOrderProcessorTest`, `ShoppingCartServiceTest`
- Keep `quarkus.devservices.enabled=false` — tests must not require a container daemon

---

## User Story 2: Secure Checkout Authentication (Keycloak OIDC)

**As a:** CoolStore user
**I want:** to sign in through Keycloak and have my cart and checkout protected by modern OIDC authentication
**So that:** only authenticated customers can place orders, and the app meets enterprise security standards on OpenShift

---

### Tasks
- ## 4. Keycloak OIDC Security Activation

### Acceptance Criteria
- [ ] Keycloak realm `eap` (client `coolstore`) provisioned from `realm-export.json` with rotated secrets
- [ ] `quarkus.oidc.*` active in `application.properties` with the client secret injected via env placeholder — no secrets in Git (all 7 rgctl-flagged findings remediated)
- [ ] Browse/catalog endpoints accessible anonymously (`@PermitAll`); cart/checkout/order endpoints require authentication (`@RolesAllowed`) — matching the original Java EE behavior
- [ ] Security tests: 401 without token, 200/403 with valid roles; endpoint tests still pass via `%test` auth configuration
- [ ] Frontend sign-in flow works through the Keycloak JS adapter against quarkus-oidc

### Details
- **Priority:** High
- **Estimate:** 8 story points

### Preconditions
- Story 1 complete (green build/tests)
- Keycloak 20.x available for realm import

### Steps to Reproduce / Implementation Notes
1. Import realm, rotate client secrets — Task Group 4.1, 4.5
2. Activate OIDC config and method security — 4.2, 4.3
3. Repoint frontend adapter (`keycloak.json`) — 4.4
4. Validate: sign-in → checkout in dev mode; security test suite green

### Dependencies
- User Story 1
- External: Keycloak instance (in-cluster deployment handled in Story 6)

### Test Cases
- Test 1: Unauthenticated GET on a protected endpoint returns 401
- Test 2: Authenticated cart checkout succeeds with valid bearer token
- Test 3: Role-restricted endpoint returns 403 for insufficient role

### Notes / Comments
- Vendored `keycloak.js` adapter is Keycloak-20-era — verify compatibility; if broken, minimal frontend rework is in scope (flagged risk in migration prompt)
- Transaction semantics for `@Incoming` consumers are unchanged (RULEBOOK Amendment 1 stays)

---

## User Story 3: Production-Ready Data, Messaging, and Audit Trail

**As a:** CoolStore operator
**I want:** the app configured for production PostgreSQL and Kafka (with audit logging restored) instead of dev-only H2 and stubbed audit calls
**So that:** orders persist durably, inventory messages flow reliably with dead-letter handling, and every order leaves a compliance-grade audit event

---

### Tasks
- ## 5. Production Data & Messaging Configuration
- ## 6. Audit Logging Restoration

### Acceptance Criteria
- [ ] `%prod` profile targets PostgreSQL via env-injected URL/credentials; Flyway migrates at startup and Hibernate `validate` passes against it
- [ ] Kafka bootstrap servers env-injected; consumer groups `coolstore-orders` and `coolstore-inventory` remain distinct (topic broadcast preserved); DLQ topics `orders-dlq`/`inventory-dlq` configured
- [ ] Every `@Incoming` method performing JPA writes keeps `@Transactional` (regression test guards TransactionRequiredException)
- [ ] Audit events emitted from `OrderService` on order save via the chosen `audit-logging-library` integration (private repo, bundled JAR, or SLF4J replacement — decision recorded)
- [ ] `ShoppingCartService` session-scaling decision documented (sticky sessions or client-side cart) before multi-replica rollout

### Details
- **Priority:** High
- **Estimate:** 8 story points

### Preconditions
- Story 1 complete
- PostgreSQL 13+ and Kafka 3.x reachable for validation (Testcontainers optional for CI)

### Steps to Reproduce / Implementation Notes
1. Activate `%prod` datasource + Flyway/Hibernate alignment — Task Group 5.1, 5.2
2. Externalize Kafka config; verify groups/DLQ — 5.3, 5.4
3. Session-scope assessment — 5.5
4. Restore audit logging in `OrderService` — Task Group 6
5. Validate: integration tests green on test profile; optional Testcontainers PostgreSQL run

### Dependencies
- User Story 1
- User Story 2 (OIDC secret pattern reused for datasource secrets)

### Test Cases
- Test 1: Order persist + inventory decrement through in-memory Kafka connector (H2 test profile)
- Test 2: Flyway `validate` passes against PostgreSQL schema produced by `V1_1`/`V1_2`
- Test 3: Audit event captured (mocked appender) on order save

### Notes / Comments
- The two consumer groups MUST NOT be merged — collapsing them would load-balance inventory updates away (original JMS topic broadcast semantics)
- `lib/audit-logging-library-1.0.0.jar` and `2.0.0.jar` both exist — pick one and document why

---

## User Story 4: Modern Static Web Tier

**As a:** CoolStore shopper
**I want:** the storefront served as static Quarkus resources with proper health endpoints
**So that:** the UI loads fast from the container image and OpenShift can monitor liveness/readiness without legacy JSPs

---

### Tasks
- ## 7. Web Content Migration (JSP → Quarkus static resources)

### Acceptance Criteria
- [ ] Static assets (`app/`, `partials/`, `bower_components/`, `coolstore.json`, `keycloak.json`) served from `META-INF/resources`; no `src/main/webapp/` remnants referenced
- [ ] `index.jsp` replaced by static `index.html` rendering the same SPA shell
- [ ] `health.jsp` replaced by `/q/health` + `/q/health/ready` (quarkus-smallrye-health with DB + Kafka checks)
- [ ] Asset-path smoke test: SPA loads, no 404s on partials/scripts in dev mode
- [ ] Sign-in (Story 2) and checkout flows work end-to-end from the static UI

### Details
- **Priority:** Medium
- **Estimate:** 5 story points

### Preconditions
- Stories 1–3 complete (auth + config needed for full UI validation)

### Steps to Reproduce / Implementation Notes
1. Relocate static assets to `META-INF/resources` — Task Group 7.1
2. Convert JSPs (static shell + health extension) — 7.2, 7.3
3. Fix asset references and Keycloak adapter init — 7.4
4. Validate: browse, sign-in, checkout in dev mode

### Dependencies
- User Story 2 (OIDC), User Story 3 (config)

### Test Cases
- Test 1: `GET /` returns 200 with the SPA shell
- Test 2: `GET /q/health` returns 200 with DB/Kafka checks UP
- Test 3: E2E smoke — catalog loads, cart works from the static frontend

### Notes / Comments
- Vendored `bower_components` (AngularJS/Polymer era) stays as-is — pinned and scanned, not rewritten (324 circular deps live there; out of scope)

---

## User Story 5: Containerized CoolStore

**As a:** Platform Engineer
**I want:** a hardened multi-stage container image of the CoolStore Quarkus app with all configuration externalized
**So that:** the app can be deployed consistently across environments with secrets managed by the platform, not baked into images

---

### Tasks
- ## 8. Containerization

### Acceptance Criteria
- [ ] Multi-stage `Containerfile` (UBI8 OpenJDK 17 builder → minimal runtime), non-root user, `-XX:MaxRAMPercentage` JVM tuning
- [ ] Image passes scanning with zero critical CVEs; promoted to the OpenShift internal registry with `:dev`/`:stage`/`:prod` tags
- [ ] All runtime config (datasource, OIDC secret, Kafka) injected via environment — verified by running the container with env-only overrides
- [ ] Container smoke test passes: `/q/health` 200 and one REST round-trip in-container
- [ ] Optional `-Pnative` profile scaffolded in POM for later evaluation

### Details
- **Priority:** Medium
- **Estimate:** 5 story points

### Preconditions
- Stories 1–4 complete (image content is final)

### Steps to Reproduce / Implementation Notes
1. Write `Containerfile`; build with Podman — Task Group 8.1
2. Externalize config; verify env-only run — 8.2
3. Scan and promote — 8.4
4. Validate: smoke script inside container

### Dependencies
- User Stories 1–4

### Test Cases
- Test 1: Container starts and `/q/health` returns 200 within 60s
- Test 2: REST round-trip (`/services/rest/products`) inside container with test env vars
- Test 3: Scanner report shows zero critical CVEs

### Notes / Comments
- JVM mode first; native image is an optional follow-up (POM TODO(port) addressed here as scaffold)

---

## User Story 6: OpenShift Deployment of the Full Stack

**As a:** Platform Engineer
**I want:** CoolStore running on OpenShift with PostgreSQL, Kafka (AMQ Streams), and Keycloak provisioned from committed manifests
**So that:** the application is production-hosted on an enterprise Kubernetes platform with health-gated rolling updates and managed secrets

---

### Tasks
- ## 9. OpenShift Deployment

### Acceptance Criteria
- [ ] App Deployment (2+ replicas after session fix), Service, and HTTPS Route applied from manifests; env bound to Secrets/ConfigMaps
- [ ] PostgreSQL provisioned with PVC; Kafka cluster + topics `orders`, `orders-dlq`, `inventory-dlq` via AMQ Streams
- [ ] Keycloak deployed with realm `eap` imported and rotated secrets
- [ ] Liveness/readiness probes wired to `/q/health`, `/q/health/ready`; rolling updates gated on readiness
- [ ] Post-deploy smoke suite green: pods Ready, health 200, authenticated REST call, message produced and consumed on `orders`

### Details
- **Priority:** High
- **Estimate:** 8 story points

### Preconditions
- Story 5 (image in registry)
- OpenShift 4.x cluster with AMQ Streams operator

### Steps to Reproduce / Implementation Notes
1. Apply app manifests with probes/resources — Task Group 9.1
2. Provision PostgreSQL, Kafka, Keycloak — 9.2–9.4
3. Wire Secrets/ConfigMaps; rolling update — 9.5
4. Validate: smoke suite against the Route

### Dependencies
- User Stories 1–5

### Test Cases
- Test 1: All pods Ready within rollout timeout; Route serves the SPA
- Test 2: Authenticated checkout produces an `orders` message; both consumer groups process it
- Test 3: Rollback drill — previous image tag redeploys cleanly

### Notes / Comments
- `@SessionScoped` cart: enable multiple replicas only after the Story 3 session decision (sticky sessions or client cart)

---

## User Story 7: Production Validation and Cutover

**As a:** CoolStore product owner
**I want:** the OpenShift deployment validated end-to-end against performance targets, with the JBoss EAP path decommissioned
**So that:** customers get a modern platform with equal-or-better behavior, and we stop maintaining two deployment paths

---

### Tasks
- ## 10. E2E Validation, Performance & Cutover

### Acceptance Criteria
- [ ] E2E checkout flow passes on OpenShift: browse → OIDC sign-in → cart → checkout → Kafka order → PostgreSQL persist → inventory decrement → audit event
- [ ] Performance targets met: startup <10s (JVM), REST p95 within 2× of EAP baseline, zero message loss on `orders` (acks=all + DLQ verified)
- [ ] All 23 `[TODO(port)]`/`[BUG(port)]`/`[PERF(port)]` markers resolved or converted to tracked tickets with owners
- [ ] Observability live: metrics endpoint scraped by OpenShift monitoring, JSON logs retained
- [ ] README rewritten for Quarkus + OpenShift; runbook complete; migration completion report supersedes `migration/MIGRATION-COMPLETE.md`
- [ ] Traffic cut over to the OpenShift Route; EAP path kept for a rollback window, then decommissioned

### Details
- **Priority:** High (closing story)
- **Estimate:** 8 story points

### Preconditions
- User Stories 1–6 complete

### Steps to Reproduce / Implementation Notes
1. Run E2E + performance suites against the OpenShift environment — Task Group 10.1, 10.2
2. Stand up observability — 10.3
3. Burn down port markers — 10.4
4. Cutover with rollback window; decommission EAP — 10.5
5. Publish final docs — 10.9

### Dependencies
- User Stories 1–6

### Test Cases
- Test 1: Automated E2E suite (mig-test-gen output) passes against the production Route
- Test 2: Load test — p95 latency within target; no order loss over sustained checkout traffic
- Test 3: DLQ drill — poison message lands in `orders-dlq` without stopping consumers

### Notes / Comments
- Keep the JBoss EAP deployment path (README section) valid until cutover — it is the rollback plan
- Marker burn-down list: 8 in `application.properties`, 4 in `ShoppingCartService`, 4 in `RestApplication`, 4 in `pom.xml`, 1 each in `ShoppingCartTest`, `OrderService`, `ShoppingCart`

---

## Story Dependency Order

```
Story 1 (foundation) → Story 2 (auth) ─┐
                    → Story 3 (data) ──┼→ Story 4 (web) → Story 5 (image) → Story 6 (openshift) → Story 7 (cutover)
```

Stories 2 and 3 may run in parallel after Story 1. Story 4 requires 2 + 3. Total estimate: **55 story points** across 10 task groups — sized for 3–4 sprints with a small team, or a single agent-driven execution wave using this plan's task hooks (mig-test-gen, mig-containerize, mig-deploy).
