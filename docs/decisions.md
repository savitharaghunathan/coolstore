# Quarkus Migration Decisions Log

Tracking all design decisions made during the CoolStore Java EE 7 → Quarkus migration brainstorming.

## Decisions

### 1. Migration goal

**Options:**
- A) Modernization demo/example — reference app for Konveyor/Kai migration tooling
- B) Production-readiness — fully functional Quarkus app with tests, health checks, proper config
- C) Minimal viable migration — compile and run on Quarkus with least change, preserve existing behavior

**Chosen: C) Minimal viable migration**

---

### 2. How to handle JMS messaging (order topic)

*The app uses JMS to publish orders to a topic, and a Message-Driven Bean consumes them.*

**Options:**
- A) Replace with direct method calls — remove JMS entirely, have ShoppingCartOrderProcessor call OrderService directly. Simplest, no broker dependency, but loses async decoupling.
- B) Use SmallRye Reactive Messaging — Quarkus-native approach, in-memory channel preserves async pub/sub with minimal infrastructure.
- C) Keep JMS via Quarkus JMS extension — closest to original code but requires external message broker (Artemis) at runtime.

**Recommendation:** A — eliminates broker dependency, simplest for minimal migration.

**Chosen: B) SmallRye Reactive Messaging**

---

### 3. What to do with WebLogic code

*The project has WebLogic stubs (`weblogic.*` package), a `StartupListener` extending WebLogic's `ApplicationLifecycleListener`, and `InventoryNotificationMDB` using WebLogic JNDI factories.*

**Options:**
- A) Delete all WebLogic code entirely — remove `weblogic/` package, `StartupListener`, and `InventoryNotificationMDB`. Cleanest, but loses inventory notification behavior.
- B) Delete WebLogic stubs, convert InventoryNotificationMDB to a Quarkus CDI observer — preserves inventory-threshold-checking logic by wiring it into reactive messaging. Replace `StartupListener` with `@Observes StartupEvent` if needed.
- C) Delete WebLogic stubs, keep InventoryNotificationMDB as dead code with TODOs — minimal effort but leaves non-functional code.

**Recommendation:** A — the MDB was already non-functional (fake WebLogic JNDI).

**Chosen: B) Delete WebLogic stubs, convert InventoryNotificationMDB to CDI observer**

---

### 4. How to handle @SessionScoped CartEndpoint and @Stateful ShoppingCartService

*The current design stores cart state in the HTTP session via `@SessionScoped` on the REST endpoint and `@Stateful` on the EJB. Quarkus has no `@Stateful` EJBs.*

**Options:**
- A) Keep `@SessionScoped` on the endpoint, convert ShoppingCartService to `@SessionScoped` CDI bean — closest to original behavior, cart state lives in HTTP session, works with existing AngularJS frontend.
- B) Convert to `@ApplicationScoped` with in-memory `Map<String, ShoppingCart>` keyed by cartId — stateless REST style, no session dependency, but cart lost on restart.
- C) Convert to `@ApplicationScoped` and persist cart state to database — most durable but adds schema changes, not minimal.

**Recommendation:** A — smallest behavioral change.

**Chosen: A) Keep @SessionScoped, convert ShoppingCartService to @SessionScoped CDI bean**

---

### 5. How to handle the custom audit-logging-library

*The project has a system-scoped JAR (`lib/audit-logging-library-1.0.0.jar`) used in `OrderService`. A v2.0.0 JAR exists in `lib/` and Konveyor migration rules (`rules/rule.yaml`) describe the v1→v2 upgrade (`FileSystemAuditLogger` → `StreamableAuditLogger`).*

**Options:**
- A) Keep v1.0.0 as-is — update Maven coordinates from `<scope>system</scope>` to install to local repo or use repository-relative path. Minimal change.
- B) Upgrade to v2.0.0 per the Konveyor rules — switch to `StreamableAuditLogger`, add async config. The v2 JAR already exists in `lib/`.
- C) Remove audit logging entirely — simplest but loses functionality.

**Recommendation:** A — minimal change.

**Chosen: B) Upgrade to v2.0.0 per Konveyor rules**

---

### 6. How to handle the frontend (AngularJS + JSPs)

*Quarkus doesn't support JSP. The app has `index.jsp` (creates HTTP session, loads AngularJS SPA) and `health.jsp` (returns "1").*

**Options:**
- A) Convert JSPs to static files, serve from `META-INF/resources/` — turn `index.jsp` into `index.html`, replace `health.jsp` with Quarkus SmallRye Health extension, serve AngularJS as static resources.
- B) Use Quarkus Qute templating to replace JSPs — if server-side rendering logic is needed. More work, and the JSPs don't do much server-side.
- C) Strip the frontend entirely, keep only the REST API — smallest surface area but loses the UI.

**Recommendation:** A — JSPs do almost nothing server-side, Quarkus health checks are better.

**Chosen: B) Use Quarkus Qute templating to replace JSPs**

---

### 7. Target Java version

*Quarkus 3.x requires Java 17 minimum. Current project targets Java 1.8.*

**Options:**
- A) Java 17 — minimum required by Quarkus 3.x, LTS, widely supported, smallest jump from Java 8.
- B) Java 21 — latest LTS, aligns with Konveyor migration rules, better performance, virtual threads available.

**Recommendation:** B — current LTS, Konveyor rules already reference it.

**Chosen: B) Java 21**

---

### 8. Migration strategy

**Options:**
- A) Incremental in-place migration — modify existing project file-by-file: update `pom.xml`, rename `javax.*` → `jakarta.*`, convert EJBs to CDI beans, update config. Lower risk per step, easier to debug, preserves git history.
- B) Scaffold a new Quarkus project, port code into it — generate fresh Quarkus skeleton, copy and adapt source files. Clean structure but harder to track changes.

**Recommendation:** A — keeps git history meaningful, more natural for minimal migration.

**Chosen: A) Incremental in-place migration**

---

### 9. Design — Build System (pom.xml)

**Approved.** Replace POM with Quarkus 3.x BOM-based structure. Java 21. JAR packaging (uber-jar). Extensions: `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-hibernate-orm`, `quarkus-jdbc-postgresql`, `quarkus-flyway`, `quarkus-smallrye-reactive-messaging`, `quarkus-qute-web`, `quarkus-smallrye-health`, `quarkus-undertow`. Install `audit-logging-library-2.0.0.jar` via local Maven repo or `maven-install-plugin`.

---

### 10. Design — Namespace & EJB Conversion

**Approved.** All `javax.*` → `jakarta.*`. EJB conversions: `@Stateless` → `@ApplicationScoped` (CatalogService, ProductService, OrderService, ShippingService, ShoppingCartOrderProcessor). `@Stateful` ShoppingCartService → `@SessionScoped`. `@Singleton @Startup` DataBaseMigrationStartup → removed (Quarkus Flyway handles it). `@MessageDriven` OrderServiceMDB → `@ApplicationScoped` with `@Incoming("orders")`. Remove `@Remote` interface and JNDI lookup, replace with `@Inject ShippingService`. Replace `@TransactionAttribute` with `@Transactional`. Replace `@Resource` JMS injections with SmallRye `@Channel`/`Emitter`.

---

### 11. Design — Configuration

**Approved.** Remove `persistence.xml` — replace with `application.properties` for datasource, Hibernate, and Flyway config. Remove `web.xml`. Keep `beans.xml`, update to CDI 4.0 namespace. SmallRye in-memory connector for orders channel. Keep `keycloak.json` as static resource for frontend JS adapter only (no server-side Quarkus OIDC). Remove `Resources.java` CDI EntityManager producer — Quarkus auto-injects `EntityManager`.

---

### 12. Design — WebLogic Removal & InventoryNotificationMDB Conversion

**Approved.** Delete entire `weblogic/` package (3 stub files). Delete `StartupListener.java`, replace with `@ApplicationScoped` bean observing `StartupEvent`/`ShutdownEvent`. Convert `InventoryNotificationMDB` to `@ApplicationScoped` CDI bean with `@Incoming("orders-notification")` — fan out orders topic to both `OrderServiceMDB` and this observer via reactive messaging config. Replace WebLogic JNDI/RMI with direct CDI injection of `CatalogService`.

---

### 13. Design — Frontend & JSP → Qute

**Approved.** Move frontend files from `src/main/webapp/` to `src/main/resources/META-INF/resources/` (AngularJS app, partials, bower_components, keycloak.json, coolstore.json). Convert `index.jsp` to Qute template at `src/main/resources/templates/index.html` with a small `IndexResource` endpoint that ensures session exists and renders template. Replace `health.jsp` with `quarkus-smallrye-health` (`/q/health`). Delete `src/main/webapp/WEB-INF/`, move `beans.xml` to `src/main/resources/META-INF/beans.xml`.

---

### 14. Design — Audit Logging Library Upgrade (v1 → v2)

**Approved.** Update `pom.xml` dependency from `audit-logging-library-1.0.0.jar` to `2.0.0.jar`. In `OrderService.java`, per Konveyor rules: replace `FileSystemAuditLogger` → `StreamableAuditLogger` import and instantiation. Add async configuration if v2 API requires it.

---

### 15. Design — Files to Delete

**Approved.** Remove: `src/main/java/weblogic/` (WebLogic stubs), `StartupListener.java` (replaced by CDI observer), `ShippingServiceRemote.java` (replaced by direct injection), `Resources.java` (Quarkus auto-injects EM), `DataBaseMigrationStartup.java` (replaced by quarkus-flyway), `src/main/webapp/` (content moves to `META-INF/resources/` and `templates/`), `persistence.xml` (replaced by application.properties), `web.xml` (not needed), `health.jsp` (replaced by SmallRye Health).
