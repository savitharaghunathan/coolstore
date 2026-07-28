# Migration Spec

## Goal
Migrate a Java EE 7 monolithic WAR application from WebLogic/JBoss to Quarkus 3 standalone JAR runtime.

## Source → Target
Java EE 7 (WebLogic/JBoss) → Quarkus 3.8.4

## Scope
- Files affected: 34
  - 27 Java source files
  - 1 pom.xml
  - 3 config files (persistence.xml, web.xml, beans.xml)
  - 2 WebLogic stub files
  - 1 new application.properties
- Estimated complexity: **Medium**
- Hardest areas:
  1. **Messaging transformation** — 2 MDB classes with different patterns (standard @MessageDriven + manual JNDI-based listener)
  2. **JNDI removal** — ShoppingCartService has hardcoded WildFly JNDI lookups for EJB remote interface
  3. **Lifecycle migration** — WebLogic-specific ApplicationLifecycleListener needs conversion

## Key Decisions Applied
No prior questionnaire.json found — using default migration strategy:
- Replace all EJB with CDI managed beans
- Convert MDB to SmallRye Reactive Messaging
- Replace persistence.xml with application.properties
- Remove all JNDI lookups with direct injection
- Delete WebLogic lifecycle stubs

## Approach

### Phase 1: Build Config
- Change packaging from WAR to JAR
- Add Quarkus BOM (3.8.4) and quarkus-maven-plugin
- Replace javaee-api dependency with Quarkus extensions:
  - quarkus-arc (CDI)
  - quarkus-rest-jackson (JAX-RS + JSON)
  - quarkus-hibernate-orm (JPA)
  - quarkus-jdbc-h2 (dev database)
  - quarkus-jdbc-postgresql (production database)
  - quarkus-flyway (database migrations)
  - quarkus-smallrye-reactive-messaging-amqp (messaging)
- Remove maven-war-plugin
- Handle system-scoped audit-logging-library dependency

### Phase 2: App Config
- Create application.properties with datasource and Flyway config
- Configure messaging channels (orders, orders-out)
- Delete persistence.xml
- Delete beans.xml
- Delete web.xml

### Phase 3: EJB to CDI
- Replace @Stateless/@Stateful with @ApplicationScoped
- Replace @EJB with @Inject
- Remove @Remote/@Local annotations
- Update all javax.* imports to jakarta.*
- Delete ShippingServiceRemote interface
- Replace JNDI lookups with direct injection in ShoppingCartService

### Phase 4: Messaging
- Convert OrderServiceMDB from @MessageDriven to @Incoming
- Convert InventoryNotificationMDB from manual MessageListener to @Incoming
- Replace ShoppingCartOrderProcessor's JMS producer with @Outgoing/Emitter
- Remove all javax.jms.* imports

### Phase 5: Lifecycle
- Convert StartupListener from WebLogic ApplicationLifecycleListener to Quarkus @Observes events
- Replace DataBaseMigrationStartup @PostConstruct with Flyway auto-migration config

### Phase 6: Cleanup
- Delete src/main/java/weblogic/ directory (2 stub files)
- Verify no javax.* EE imports remain (except javax.sql, etc.)
- Verify no JNDI lookups remain
- Verify no EJB annotations remain
- Final build verification

## Domain Skill
**javaee-to-quarkus** — Migrates Java EE 7/8 applications (WebLogic, JBoss, WildFly) to Quarkus 3. This migration replaces the entire Java EE programming model with Quarkus, not just namespace changes.
