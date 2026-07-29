# Migration Spec

## Goal
Migrate a Java EE 7 monolithic application from WebLogic to Quarkus 3, transforming it from a WAR-packaged application server deployment to a standalone fast-jar Quarkus application.

## Source → Target
Java EE 7 on WebLogic (WAR packaging) → Quarkus 3 (fast-jar packaging) on Java 17

## Scope
- **Files affected:** 30 Java source files + configuration files (pom.xml, persistence.xml, web.xml, beans.xml)
- **Estimated complexity:** High
- **Hardest areas:**
  1. **JMS/MDB Migration** - Two message-driven beans with different patterns (annotation-based OrderServiceMDB vs manual JNDI setup InventoryNotificationMDB) must convert to SmallRye Reactive Messaging with AMQP
  2. **EJB to CDI** - Seven EJB components including @Stateful session state, @Remote interface, and JNDI lookups require architectural changes
  3. **Session State Management** - @SessionScoped CartEndpoint and @Stateful ShoppingCartService must maintain session semantics in Quarkus

## Key Decisions Applied

From `.konveyor/questionnaire.json`:

1. **java_version: java17**
   - Target: Java 17 (LTS)
   - Reasoning: Minimum requirement for Quarkus 3, most conservative choice with well-tested stability

2. **messaging_backend: amqp_artemis**
   - Target: AMQP (ActiveMQ Artemis) with SmallRye Reactive Messaging
   - Reasoning: Provides closest semantics to JMS Topics, Red Hat/JBoss recommended broker, simplifies migration from existing pub/sub pattern
   - Configuration needed: ActiveMQ Artemis connection URL, topic/orders → AMQP exchange/queue mapping

3. **database_migration_tool: quarkus_flyway**
   - Target: Quarkus Flyway extension
   - Reasoning: Reuses existing SQL migration files while providing seamless Quarkus integration and native support
   - Migration files reusable: Yes (V1_1__CreateSchema.sql, V1_2__AddInitialData.sql)

4. **remote_ejb_strategy: rest_endpoint**
   - Target: Convert ShippingService @Remote EJB to REST endpoint
   - Reasoning: Maintains remote access capability using standard HTTP, leverages existing REST infrastructure
   - New endpoint: /services/shipping

5. **session_management: session_scoped_beans**
   - Target: Keep Quarkus session-scoped beans
   - Reasoning: Minimizes initial migration scope, preserves existing behavior without frontend changes
   - Production consideration: Requires sticky sessions or session replication
   - Future recommendation: Migrate to JWT-based stateless authentication for cloud-native scalability

6. **lifecycle_events: quarkus_events**
   - Target: Quarkus lifecycle events (@Observes StartupEvent/ShutdownEvent)
   - Reasoning: Quarkus-native approach with clear semantics, straightforward conversion from logging-only listener

7. **packaging_format: fast_jar**
   - Target: Fast-jar (Quarkus default)
   - Reasoning: Optimized startup, best balance for containerized deployments
   - Deployment target: Container (Docker/Kubernetes recommended)

8. **audit_library: local_maven_install**
   - Target: Install system-scoped JAR to local Maven repository
   - Reasoning: System-scoped dependencies don't work well with Quarkus, this maintains dependency while improving portability
   - Action required: `mvn install:install-file -Dfile=lib/audit-logging-library-1.0.0.jar -DgroupId=com.enterprise -DartifactId=audit-logging-library -Dversion=1.0.0 -Dpackaging=jar`

## Approach

Following the javaee-to-quarkus domain skill's 6-phase approach:

### Phase 1: Build Config
- Transform pom.xml from WAR to JAR packaging
- Add Quarkus BOM (version 3.x) and quarkus-maven-plugin
- Replace Java EE 7 dependencies with Quarkus extensions:
  - quarkus-hibernate-orm (JPA)
  - quarkus-jdbc-postgresql (datasource)
  - quarkus-flyway (database migration)
  - quarkus-resteasy-reactive-jackson (JAX-RS)
  - quarkus-smallrye-reactive-messaging-amqp (messaging)
  - quarkus-arc (CDI)
  - quarkus-undertow (session support)
- Install audit-logging-library to local Maven repo
- Set Java version to 17

### Phase 2: App Config
- Replace persistence.xml with application.properties for Hibernate configuration
- Map datasource JNDI (java:jboss/datasources/CoolstoreDS) to quarkus.datasource.* properties
- Configure AMQP messaging for ActiveMQ Artemis
- Configure Flyway for database migrations
- Remove web.xml and beans.xml (not needed in Quarkus)

### Phase 3: EJB to CDI
- Convert @Stateless EJBs to @ApplicationScoped CDI beans (5 services)
- Convert @Stateful ShoppingCartService to @SessionScoped CDI bean
- Convert @Singleton DataBaseMigrationStartup to @ApplicationScoped with @Observes StartupEvent
- Remove @Remote annotation from ShippingService, convert to local CDI bean
- Create new REST endpoint (ShippingEndpoint) to expose ShippingService remotely via HTTP
- Replace JNDI lookup (lookupShippingServiceRemote) with direct CDI injection
- Replace @Resource datasource injection with Quarkus @Inject
- Update @PostConstruct to work with Quarkus lifecycle

### Phase 4: Messaging
- Convert OrderServiceMDB (@MessageDriven) to @Incoming SmallRye Reactive Messaging consumer
- Convert InventoryNotificationMDB (manual MessageListener) to @Incoming consumer
- Remove JMS-specific activation config properties
- Replace javax.jms.Message/TextMessage with Reactive Messaging Message<String>
- Configure AMQP channels in application.properties (topic/orders → incoming channels)
- Replace manual JNDI topic setup with declarative configuration
- Update ShoppingCartOrderProcessor to use @Channel Emitter for publishing orders

### Phase 5: Lifecycle
- Replace weblogic.application.ApplicationLifecycleListener (StartupListener) with Quarkus events
- Convert postStart() to void onStart(@Observes StartupEvent event)
- Convert preStop() to void onStop(@Observes ShutdownEvent event)
- Delete WebLogic stub classes (ApplicationLifecycleEvent, ApplicationLifecycleListener, NonCatalogLogger)

### Phase 6: Cleanup
- Delete src/main/webapp/WEB-INF/ directory (web.xml, beans.xml)
- Delete src/main/resources/META-INF/persistence.xml
- Delete weblogic/* package (stub classes)
- Verify no javax.ejb.*, javax.jms.*, or weblogic.* imports remain
- Verify all imports use jakarta.* for persistence, CDI, REST
- Run final build verification: `mvn clean package`

## Domain Skill
**Name:** javaee-to-quarkus

**Description:** Migrates Java EE 7/8 applications (WebLogic, JBoss, WildFly) to Quarkus 3. This is a comprehensive programming model migration, not just a namespace rename. It replaces EJB → CDI, JMS/MDB → SmallRye Reactive Messaging, WAR → JAR packaging, persistence.xml → application.properties, JNDI lookups → direct injection, and app server lifecycle → Quarkus lifecycle events.

**Phases:** 6 (Build Config, App Config, EJB to CDI, Messaging, Lifecycle, Cleanup)

**Build gate:** After each phase, run `mvn compile` and stop if it fails.
