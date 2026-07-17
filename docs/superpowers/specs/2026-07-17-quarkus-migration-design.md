# CoolStore Quarkus Migration Design

**Date:** 2026-07-17
**Goal:** Minimal viable migration — compile and run on Quarkus with least change, preserving existing behavior.
**Strategy:** Incremental in-place migration.
**Target:** Quarkus 3.x, Java 21, JAR packaging.

## 1. Build System (pom.xml)

Replace the existing Java EE 7 POM with a Quarkus 3.x BOM-based structure.

**Parent/BOM:** `io.quarkus.platform:quarkus-bom`
**Java:** 21 (source and target)
**Packaging:** `jar` (Quarkus uber-jar)

**Quarkus extensions:**

| Extension | Purpose |
|-----------|---------|
| `quarkus-rest` | JAX-RS (RESTEasy Reactive) |
| `quarkus-rest-jackson` | JSON serialization |
| `quarkus-hibernate-orm` | JPA |
| `quarkus-jdbc-postgresql` | PostgreSQL datasource |
| `quarkus-flyway` | Database migration (replaces manual Flyway startup) |
| `quarkus-smallrye-reactive-messaging` | In-memory channels for order topic |
| `quarkus-qute-web` | Qute templating (JSP replacement) |
| `quarkus-smallrye-health` | Health endpoint (replaces health.jsp) |
| `quarkus-undertow` | Servlet support for `@SessionScoped` REST |

**Audit logging library:** Update from `audit-logging-library-1.0.0.jar` to `audit-logging-library-2.0.0.jar` (system-scoped from `lib/`). Install via `maven-install-plugin` or local repo.

## 2. Namespace & EJB Conversion

### 2.1 Namespace migration

All `javax.*` imports → `jakarta.*` across every Java file:
- `javax.persistence` → `jakarta.persistence`
- `javax.inject` → `jakarta.inject`
- `javax.ws.rs` → `jakarta.ws.rs`
- `javax.json` → `jakarta.json`
- `javax.enterprise` → `jakarta.enterprise`
- `javax.transaction` → `jakarta.transaction`
- `javax.jms` → removed (replaced by SmallRye)

### 2.2 EJB → CDI bean conversions

| Class | Original Annotation | New Annotation |
|-------|---------------------|----------------|
| `CatalogService` | `@Stateless` | `@ApplicationScoped` |
| `ProductService` | `@Stateless` | `@ApplicationScoped` |
| `OrderService` | `@Stateless` | `@ApplicationScoped` |
| `ShippingService` | `@Stateless` | `@ApplicationScoped` |
| `ShoppingCartOrderProcessor` | `@Stateless` | `@ApplicationScoped` |
| `ShoppingCartService` | `@Stateful` | `@SessionScoped` |
| `DataBaseMigrationStartup` | `@Singleton @Startup` | **Delete** (Quarkus Flyway handles it) |
| `OrderServiceMDB` | `@MessageDriven` | `@ApplicationScoped` + `@Incoming("orders")` |

### 2.3 Remote EJB removal

- Delete `ShippingServiceRemote.java` interface
- Replace JNDI lookup in `ShoppingCartService.lookupShippingServiceRemote()` with `@Inject ShippingService`
- Remove all `@Remote` annotations from `ShippingService`

### 2.4 Transaction management

- Remove `@TransactionAttribute` annotations
- Use `@Transactional` from `jakarta.transaction` where explicit transaction control is needed

### 2.5 JMS → SmallRye Reactive Messaging

- `ShoppingCartOrderProcessor`: Replace `@Resource(lookup="java:/topic/orders")` and `JMSContext` with `@Channel("orders") Emitter<String>`
- `OrderServiceMDB`: Replace `@MessageDriven` + `MessageListener.onMessage()` with `@Incoming("orders")` method

## 3. Configuration

### 3.1 application.properties (new file)

```properties
# Datasource
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore
quarkus.datasource.username=coolstore
quarkus.datasource.password=coolstore

# Hibernate
quarkus.hibernate-orm.database.generation=none

# Flyway
quarkus.flyway.migrate-at-start=true

# Reactive Messaging (in-memory for orders)
mp.messaging.outgoing.orders.connector=smallrye-in-memory
mp.messaging.incoming.orders.connector=smallrye-in-memory
mp.messaging.incoming.orders-notification.connector=smallrye-in-memory
```

### 3.2 Files to update

- **`beans.xml`** — move to `src/main/resources/META-INF/beans.xml`, update to CDI 4.0 namespace (`jakarta.enterprise`)

### 3.3 Files to remove

- `persistence.xml` — replaced by `application.properties`
- `web.xml` — not needed in Quarkus

### 3.4 Keycloak

Keep `keycloak.json` as a static resource for the frontend JavaScript adapter only. No server-side Quarkus OIDC integration (beyond minimal migration scope).

## 4. WebLogic Removal & InventoryNotificationMDB Conversion

### 4.1 Delete WebLogic stubs

Remove entire `src/main/java/weblogic/` package:
- `weblogic.application.ApplicationLifecycleListener`
- `weblogic.application.ApplicationLifecycleEvent`
- `weblogic.i18n.logging.NonCatalogLogger`

### 4.2 Replace StartupListener

Delete `StartupListener.java`. Create a new `@ApplicationScoped` bean with:
- `void onStart(@Observes StartupEvent ev)` — logs application started
- `void onStop(@Observes ShutdownEvent ev)` — logs application stopping

### 4.3 Convert InventoryNotificationMDB

Convert from WebLogic JMS subscriber to `@ApplicationScoped` CDI bean:
- Add `@Incoming("orders-notification")` to subscribe to orders channel
- Replace WebLogic JNDI/RMI code with direct `@Inject CatalogService`
- Preserve inventory threshold checking logic
- Fan out: `ShoppingCartOrderProcessor` emits to `orders-outgoing`. Both `OrderServiceMDB` (`@Incoming("orders")`) and `InventoryNotificationMDB` (`@Incoming("orders-notification")`) consume independently. SmallRye in-memory connector supports multiple consumers on the same channel by using separate channel names with a broadcast pattern, or we use a single `@Incoming("orders")` method in a router bean that forwards to both consumers. Implementation will determine the simplest wiring.

## 5. Frontend & JSP → Qute

### 5.1 Move static resources

Move from `src/main/webapp/` to `src/main/resources/META-INF/resources/`:
- `app/` (AngularJS application)
- `partials/` (HTML templates)
- `bower_components/` (frontend dependencies)
- `keycloak.json`
- `coolstore.json`

### 5.2 Convert index.jsp → Qute template

Create `src/main/resources/templates/index.html` (Qute template with same HTML content as `index.jsp`).

Create `IndexResource.java`:
- `@Path("/")`
- `GET /` — ensures HTTP session exists, renders Qute `index.html` template

### 5.3 Replace health.jsp

Delete `health.jsp`. The `quarkus-smallrye-health` extension provides `/q/health` automatically.

## 6. Audit Logging Library Upgrade (v1 → v2)

Per Konveyor migration rules in `rules/rule.yaml` (rules audit-logging-0001 through 0005):

- Update `pom.xml` dependency: `audit-logging-library-1.0.0.jar` → `audit-logging-library-2.0.0.jar`
- In `OrderService.java`:
  - `import com.enterprise.audit.logging.service.FileSystemAuditLogger` → `import com.enterprise.audit.logging.service.StreamableAuditLogger`
  - `new FileSystemAuditLogger(config)` → `new StreamableAuditLogger(config)` (configured for TCP streaming per rule 0003)
  - Replace any `logEvent()` calls with `logEventAsync()` (non-blocking, per rule 0004)
  - Replace any `logSuccess()`/`logFailure()` calls with full `AuditEvent` record construction + `logEventAsync()` (per rule 0005)
  - Replace any `AuditEvent.builder()` with direct record instantiation `new AuditEvent(...)` (per rule 0002)
  - Note: current `OrderService.java` only initializes the logger in `@PostConstruct` and closes in `@PreDestroy` — no `logEvent`/`logSuccess`/`logFailure` calls exist yet, so rules 0002/0004/0005 have no current application sites but the patterns are documented for future use

## 7. Files to Delete (Summary)

| File/Directory | Reason |
|---|---|
| `src/main/java/weblogic/` (entire package) | WebLogic stubs no longer needed |
| `src/main/java/.../utils/StartupListener.java` | Replaced by CDI observer |
| `src/main/java/.../service/ShippingServiceRemote.java` | Replaced by direct CDI injection |
| `src/main/java/.../persistence/Resources.java` | Quarkus auto-injects EntityManager |
| `src/main/java/.../utils/DataBaseMigrationStartup.java` | Replaced by quarkus-flyway |
| `src/main/webapp/` (entire directory) | Content moves to `META-INF/resources/` and `templates/` |
| `src/main/resources/META-INF/persistence.xml` | Replaced by application.properties |
| `src/main/webapp/WEB-INF/web.xml` | Not needed in Quarkus |
| `src/main/webapp/health.jsp` | Replaced by SmallRye Health |

## 8. Out of Scope

- Server-side Keycloak/OIDC integration (frontend JS adapter only)
- Adding tests (none exist currently)
- Docker/container image build
- OpenShift deployment configuration
- Frontend framework upgrade (AngularJS stays as-is)
- Performance optimization
