# Migration Decisions: Java EE 7 (JBoss EAP) to Quarkus 3.x

## 1. pom.xml: WAR to JAR, Java EE 7 to Quarkus 3.x BOM

**Decision:** Replace WAR packaging and Java EE 7 provided dependencies with Quarkus 3.x BOM and executable JAR packaging.

**Why Quarkus 3.x?** Quarkus is the natural target for Red Hat Java EE apps -- it's the successor platform Red Hat actively maintains. The app already has Flyway migrations and a standard JPA/JAX-RS/CDI architecture that maps 1:1 onto Quarkus extensions. Spring Boot would work too, but Quarkus has first-class support for the exact patterns this app uses (CDI, JAX-RS, JPA) without needing to rewrite them into Spring idioms.

**Why JAR not WAR?** Quarkus packages as an executable JAR by default. WAR packaging is legacy -- Quarkus doesn't deploy into an app server, it *is* the server.

**Why Java 21?** The original targets Java 8. Java 21 is the current LTS. Quarkus 3.x requires at minimum Java 17, and 21 is the recommended LTS.

### Dependency Mapping

| Original | Quarkus Extension | Why |
|---|---|---|
| `javaee-web-api` / `javaee-api` (provided) | `quarkus-bom` (BOM) | Quarkus BOM manages all Jakarta EE API versions |
| `jboss-jms-api_2.0_spec` | `quarkus-messaging` | JMS doesn't exist in Quarkus; SmallRye Reactive Messaging is the replacement |
| `flyway-core` (manual) | `quarkus-flyway` | Quarkus manages Flyway lifecycle automatically -- no need for the manual `DataBaseMigrationStartup` singleton |
| `jboss-rmi-api` | removed | RMI/IIOP is not used in Quarkus; the `@Remote` EJB pattern gets replaced with direct CDI injection |
| `audit-logging-library` 1.0.0 | 2.0.0 | v2.0.0 is available in `/lib` and uses `StreamableAuditLogger` instead of `FileSystemAuditLogger` -- the v2 API is what we need for the non-EJB lifecycle |
| (none) | `quarkus-rest` + `quarkus-rest-jackson` | RESTEasy Reactive replaces the old JAX-RS runtime; Jackson handles JSON serialization |
| (none) | `quarkus-hibernate-orm` | JPA implementation |
| (none) | `quarkus-jdbc-postgresql` | The app uses a JBoss datasource `CoolstoreDS` -- Quarkus needs an explicit JDBC driver |
| (none) | `quarkus-undertow` | Required because the app uses `@SessionScoped` on `CartEndpoint` and `ShoppingCartService`, which needs servlet session support |
| (none) | `quarkus-jsonp` | `Transformers.java` uses `javax.json.*` (Jakarta JSON Processing) -- this extension provides it |
| (none) | `quarkus-qute` | Need to serve the SPA's `index.html` -- Qute is Quarkus's built-in templating |
| (none) | `quarkus-smallrye-health` | Standard practice for Quarkus apps -- gives `/q/health` for free |

## 2. javax.* to jakarta.* Namespace Migration

**Decision:** Replace all `javax.persistence`, `javax.inject`, `javax.enterprise`, `javax.ws.rs`, `javax.json`, `javax.annotation` imports with their `jakarta.*` equivalents.

**Why?** This is non-negotiable. Quarkus 3.x uses Jakarta EE 10, which moved everything from `javax.*` to `jakarta.*`. This is a mechanical find-and-replace -- no logic changes.

## 3. EJB to CDI Replacements

**Decision:** Replace all EJB annotations with CDI equivalents. Quarkus has no EJB container.

| EJB Annotation | CDI Replacement | Reasoning |
|---|---|---|
| `@Stateless` (CatalogService, OrderService, ProductService, ShoppingCartOrderProcessor, ShippingService) | `@ApplicationScoped` | Stateless EJBs are pooled singletons -- `@ApplicationScoped` is the direct CDI equivalent. For services that write to the DB, add `@Transactional` since EJBs had implicit CMT (container-managed transactions) but CDI beans don't. |
| `@Stateful` (ShoppingCartService) | `@SessionScoped` | The `@Stateful` EJB holds per-user cart state. `@SessionScoped` preserves the exact same per-HTTP-session semantics. This is why we need `quarkus-undertow`. |
| `@Singleton @Startup` (DataBaseMigrationStartup) | **Deleted entirely** | Quarkus's `quarkus-flyway` extension handles Flyway automatically via `quarkus.flyway.migrate-at-start=true`. The manual EJB singleton that bootstraps Flyway is dead code. |
| `@Remote` (ShippingService) | **Removed** | The `@Remote` interface and JNDI lookup in `ShoppingCartService.lookupShippingServiceRemote()` is an EJB remote call pattern. In a monolith on Quarkus, we just `@Inject ShippingService` directly. The `ShippingServiceRemote` interface is deleted -- `ShippingService` becomes a plain `@ApplicationScoped` bean. |
| `@MessageDriven` (OrderServiceMDB) | `@ApplicationScoped` + `@Incoming` | See decision #4. |

**Why `@ApplicationScoped` and not `@Singleton`?** CDI `@Singleton` is similar but `@ApplicationScoped` is the Quarkus-idiomatic choice -- it's a normal-scoped bean with a client proxy, which avoids circular dependency issues and plays better with the CDI container.

## 4. JMS to SmallRye Reactive Messaging

**Decision:** Replace JMS messaging with SmallRye Reactive Messaging using the `smallrye-in-memory` connector.

**Why?** JMS requires a broker (the original app used JBoss's embedded HornetQ). Quarkus doesn't ship an embedded JMS broker. SmallRye Reactive Messaging is the standard Quarkus replacement, and the `smallrye-in-memory` connector lets messages flow in-process without needing Kafka/AMQP -- which preserves the monolith's original behavior where the JMS topic was embedded in the app server.

### Specific Changes

- **ShoppingCartOrderProcessor**: `@Inject JMSContext` + `@Resource Topic` replaced with `@Inject @Channel("orders-outgoing") Emitter<String>`. Same semantics: fire-and-forget a JSON string.
- **OrderServiceMDB**: `@MessageDriven` + `MessageListener.onMessage(Message)` replaced with `@ApplicationScoped` + `@Incoming("orders")` method that receives `String` directly. No more `TextMessage` casting.
- **InventoryNotificationMDB**: Same pattern. The WebLogic JNDI/TopicConnection code is entirely dead -- it was never invoked in the JBoss deployment anyway (the `init()` method was never called). Replace with `@Incoming("orders-notification")`.

### Channel Routing (application.properties)

The `orders-outgoing` channel feeds both `orders` and `orders-notification` incoming channels -- replicating the JMS topic's pub/sub behavior where both `OrderServiceMDB` and `InventoryNotificationMDB` received every message.

## 5. Remove WebLogic/JBoss-Specific Code

**Decision:** Delete vendor-specific code that has no equivalent in Quarkus.

- **`weblogic.*` package** (3 files): Stub implementations of WebLogic APIs vendored to make the code compile outside WebLogic. No purpose in Quarkus -- deleted.
- **`StartupListener`**: Extends `ApplicationLifecycleListener` (WebLogic). Replaced with a Quarkus `@Observes StartupEvent` / `@Observes ShutdownEvent` bean -- same lifecycle hooks, Quarkus-native.
- **`DataBaseMigrationStartup`**: Manual Flyway bootstrap EJB. Quarkus handles this with config (`quarkus.flyway.migrate-at-start=true`). Deleted.
- **`Resources.java`** (EntityManager producer): In Java EE, `@PersistenceContext` injection only works in EJBs, so this CDI producer existed to make `EntityManager` injectable elsewhere. In Quarkus, `@Inject EntityManager` works everywhere out of the box -- the producer is unnecessary and would actually conflict. Deleted.
- **JNDI lookup in ShoppingCartService**: `lookupShippingServiceRemote()` does a JNDI lookup to find `ShippingService` via EJB remote interface. Replaced with `@Inject ShippingService` -- direct CDI injection.

## 6. Configuration and Resource Restructuring

**Decision:** Replace Java EE deployment descriptors with Quarkus configuration.

- **`persistence.xml` -> `application.properties`**: Quarkus configures JPA through `application.properties`, not `persistence.xml`. The JNDI datasource `java:jboss/datasources/CoolstoreDS` becomes `quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore`. Delete `persistence.xml`.
- **`webapp/` -> `resources/META-INF/resources/`**: Quarkus serves static resources from `META-INF/resources` on the classpath, not from a `webapp` directory (that's WAR packaging). All frontend files (JS, CSS, HTML partials, images, bower_components) move.
- **`beans.xml`**: Updated from CDI 1.1 (`xmlns.jcp.org/xml/ns/javaee`) to CDI 4.0 (`jakarta.ee/xml/ns/jakartaee`). Moved from `WEB-INF/` to `META-INF/`.
- **`web.xml`**: Deleted -- Quarkus doesn't use deployment descriptors.
- **`IndexResource.java`**: New JAX-RS resource that serves the SPA's `index.html` via Qute template at `/`. Creates an HTTP session to support `@SessionScoped` beans. Without this, the first request wouldn't have a session and `@SessionScoped` injection would fail.

## 7. JPA Entity Fixes

**Decision:** Fix entity annotation issues for Quarkus compatibility.

- **`Order.java`**: There's a bug in the original -- `@Column(name="TOTAL_PRICE")` is dangling (not attached to any field), with `@OneToMany` immediately after. This compiles but the `@Column` annotation silently applies to the wrong thing. Fix by removing the stray `@Column(name="TOTAL_PRICE")`.
- **`@GeneratedValue`**: The original uses `@GeneratedValue` without a strategy, which defaults to `AUTO`. Quarkus with PostgreSQL works with `AUTO` and the `hibernate_sequence` that the Flyway schema already creates -- so this is kept as-is.
- **`@XmlRootElement`** on `InventoryEntity`: This is a JAXB annotation. Quarkus uses Jackson for JSON, not JAXB. Removed (along with its `javax.xml.bind` import).

## Summary: Files Deleted vs. Transformed vs. Created

### Deleted (dead code / replaced by Quarkus features)
- `weblogic/` package (3 files) -- vendor stubs
- `StartupListener.java` -- WebLogic lifecycle
- `DataBaseMigrationStartup.java` -- manual Flyway, replaced by `quarkus-flyway`
- `Resources.java` -- EntityManager producer, built into Quarkus
- `ShippingServiceRemote.java` -- EJB remote interface
- `persistence.xml` -- replaced by `application.properties`
- `web.xml` -- not used in Quarkus

### New Files
- `IndexResource.java` -- serves the SPA
- `StartupObserver.java` -- Quarkus lifecycle events
- `application.properties` -- all Quarkus config
- `templates/index.html` -- Qute template for SPA

### Goal
Functional equivalence: same REST endpoints at `/services/*`, same JPA entities, same shopping cart logic, same messaging flow, same frontend. Just running on Quarkus instead of JBoss EAP.
