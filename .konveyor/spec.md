# Migration Spec

## Goal
Migrate Java EE 7 application from JBoss/WildFly application server to Quarkus 3 cloud-native runtime.

## Source → Target
Java EE 7 (JBoss/WildFly WAR) → Quarkus 3.x (standalone JAR)

## Scope
- Files affected: 30 Java source files + 1 pom.xml + 4 config files
- Estimated complexity: High
- Hardest areas:
  1. JMS to Kafka migration (2 message-driven beans with topic-based messaging)
  2. Stateful session bean (ShoppingCartService) - requires careful scope management
  3. WebLogic-specific lifecycle code (ApplicationLifecycleListener) - needs Quarkus replacement

## Key Decisions Applied
From questionnaire.json:

1. **Java Version**: Java 17 (LTS, Quarkus 3 minimum requirement)
2. **Database**: PostgreSQL (production-ready, excellent Quarkus support)
3. **Messaging Broker**: Apache Kafka (cloud-native, reactive messaging with SmallRye)
4. **Persistence Approach**: Hibernate ORM with Panache (simplified repository pattern)
5. **Build Mode**: JVM mode (safer migration path, custom library compatibility)
6. **REST Framework**: RESTEasy Reactive (non-blocking, better performance)
7. **Testing Strategy**: Basic JUnit 5 + REST Assured setup
8. **Custom Library**: Keep audit-logging-library-1.0.0.jar as system-scoped dependency

## Approach
Following the javaee-to-quarkus domain skill's 6-phase migration:

**Phase 1: Build Config**
- Change packaging from WAR to JAR
- Update Java version to 17
- Add Quarkus BOM and Maven plugin
- Replace Java EE dependencies with Quarkus extensions
- Migrate Flyway to quarkus-flyway

**Phase 2: App Config**
- Create application.properties with datasource, Kafka, and Flyway config
- Remove persistence.xml, web.xml, and beans.xml (replaced by Quarkus conventions)

**Phase 3: EJB to CDI**
- Replace @Stateless with @ApplicationScoped (5 services)
- Replace @Stateful with @SessionScoped (ShoppingCartService)
- Remove @Remote annotation and Remote interface
- Replace JNDI lookups with direct @Inject
- Update transaction management

**Phase 4: Messaging**
- Convert OrderServiceMDB to @Incoming reactive method
- Convert InventoryNotificationMDB to @Incoming reactive method
- Replace JMS topic "topic/orders" with Kafka topic
- Remove MessageListener implementations
- Add @Channel and Emitter for message production

**Phase 5: Lifecycle**
- Replace WebLogic ApplicationLifecycleListener with Quarkus @Observes StartupEvent
- Migrate DataBaseMigrationStartup if needed
- Remove all weblogic.* imports

**Phase 6: Cleanup**
- Delete persistence.xml, web.xml, beans.xml
- Remove WebLogic stub classes (ApplicationLifecycleEvent, ApplicationLifecycleListener, NonCatalogLogger)
- Verify all javax.* EE imports converted to jakarta.*
- Final build validation

## Domain Skill
javaee-to-quarkus (Java EE 7/8 to Quarkus 3 migration)
