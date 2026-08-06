# Migration Spec

## Goal
Migrate a Java EE 7 monolith application from WebLogic/JBoss to Quarkus 3, transforming EJBs to CDI beans, MDBs to SmallRye Reactive Messaging, and removing application server dependencies.

## Source → Target
Java EE 7 (WebLogic/JBoss) → Quarkus 3

## Scope
- Files affected: 27 Java source files + build/config files (~35 total)
- Estimated complexity: Medium-High
- Hardest areas:
  1. Message-Driven Bean conversion to SmallRye Reactive Messaging (2 MDBs with different patterns)
  2. JNDI lookup removal and replacement with CDI injection
  3. WebLogic-specific lifecycle listener migration to Quarkus events

## Key Decisions Applied
(No questionnaire.json found - proceeding with standard Java EE to Quarkus migration)

## Approach
This migration follows the javaee-to-quarkus domain skill with 6 phases executed in order:

### Phase 1: Build Config
- Transform pom.xml from WAR to JAR packaging
- Add Quarkus BOM (3.x) and maven plugin
- Replace Java EE 7 dependencies with Quarkus extensions:
  - `javaee-web-api` → `quarkus-resteasy-reactive-jackson`
  - `javaee-api` → `quarkus-arc` (CDI)
  - JMS API → `quarkus-smallrye-reactive-messaging-amqp` or `quarkus-artemis-jms`
  - JPA → `quarkus-hibernate-orm-panache` + `quarkus-jdbc-postgresql`
- Remove maven-war-plugin, add quarkus-maven-plugin
- Update Java version from 8 to 17 (minimum for Quarkus 3)
- Handle system-scoped dependency (audit-logging-library)

### Phase 2: App Config
- Create `src/main/resources/application.properties` with:
  - Datasource configuration (from persistence.xml JNDI reference)
  - Hibernate properties
  - SmallRye Messaging configuration for topics
  - Flyway configuration
- Delete `src/main/webapp/WEB-INF/web.xml` (empty, not needed)
- Delete `src/main/webapp/WEB-INF/beans.xml` (CDI auto-enabled in Quarkus)
- Delete `src/main/resources/META-INF/persistence.xml` (replaced by application.properties)

### Phase 3: EJB to CDI
- Remove EJB annotations from 8 classes:
  - `@Stateless` → `@ApplicationScoped` (6 service classes)
  - `@Stateful` → `@SessionScoped` (ShoppingCartService)
  - `@Singleton` → `@Singleton` (DataBaseMigrationStartup - CDI version)
  - `@Startup` → `@Observes StartupEvent`
- Remove `@Remote` interface and implementation (ShippingService)
- Replace JNDI lookups with `@Inject`:
  - ShoppingCartService.lookupShippingServiceRemote() → direct injection
  - InventoryNotificationMDB JNDI setup → configuration-based
- Replace `@Resource(mappedName="...")` with `@Inject @Named` or application.properties references

### Phase 4: Messaging
- Convert OrderServiceMDB from `@MessageDriven` to SmallRye Reactive Messaging:
  - Remove JMS imports and Message/TextMessage handling
  - Use `@Incoming("orders")` with String payload
  - Configure channel in application.properties
- Convert InventoryNotificationMDB (WebLogic JNDI pattern):
  - Remove manual JNDI context creation and topic subscription
  - Use `@Incoming("orders")` annotation
  - Remove init() and close() methods
- Convert ShoppingCartOrderProcessor from JMS producer:
  - Remove `@Resource(lookup="java:/topic/orders")` Topic
  - Remove `JMSContext` injection
  - Use `@Channel("orders") Emitter<String>`

### Phase 5: Lifecycle
- Convert StartupListener (WebLogic ApplicationLifecycleListener):
  - Remove weblogic.application imports
  - Use `@ApplicationScoped` bean with `@Observes StartupEvent` and `@Observes ShutdownEvent`
  - Map postStart() → startup event observer
  - Map preStop() → shutdown event observer
- Convert DataBaseMigrationStartup:
  - Remove `@Singleton @Startup` EJB annotations
  - Use CDI `@Singleton` with `@Observes StartupEvent`
  - Replace `@PostConstruct` with startup event observer method
  - Replace `@Resource(mappedName="...")` DataSource with Quarkus datasource injection

### Phase 6: Cleanup
- Delete WebLogic stub classes:
  - `src/main/java/weblogic/application/ApplicationLifecycleEvent.java`
  - `src/main/java/weblogic/application/ApplicationLifecycleListener.java`
  - `src/main/java/weblogic/i18n/logging/NonCatalogLogger.java`
- Delete legacy deployment descriptors (already removed in Phase 2)
- Verify no remaining `javax.*` EE imports (should be `jakarta.*` or Quarkus-specific)
- Remove ShippingServiceRemote interface (no longer needed without EJB @Remote)

## Domain Skill
**Name:** javaee-to-quarkus  
**Description:** Migrates Java EE 7/8 applications (WebLogic, JBoss, WildFly) to Quarkus 3. Transforms EJB to CDI, MDB to SmallRye Reactive Messaging, WAR to JAR packaging, removes JNDI, and eliminates application server dependencies.

## Build Tool
Maven (`mvn compile`)

## Verification Strategy
After each phase, run `mvn compile` to ensure the build succeeds before proceeding. Final verification includes:
- `mvn clean compile` - successful compilation
- `mvn test` - unit tests pass (may need updates)
- `mvn quarkus:dev` - application starts successfully
- Manual testing of REST endpoints and messaging functionality
