# Implementation Plan

## Goal
Migrate a Java EE 7 monolithic web application from JBoss EAP 7.4 to Quarkus 3.x with minimal code changes and architectural disruption.

- Domain skill: none (general Java EE to Quarkus migration patterns)

## Project Summary
- Type: Maven / Java EE 7 Web Application
- Files affected: ~50 files (Java sources + configuration + frontend)
- Estimated complexity: Medium
- Hardest steps: 
  1. Message-Driven Bean migration to Quarkus Artemis JMS
  2. WebLogic lifecycle listener replacement
  3. Session-scoped REST endpoint migration

## Steps

### Step 1: Update Maven POM - Remove Java EE dependencies
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do: Remove Java EE API dependencies (javaee-web-api, javaee-api), change packaging from war to jar, update Java version from 1.8 to 17
- Why: Quarkus 3 uses JAR packaging and Java 17, and provides its own API implementations
- Depends on: none
- Verify: `mvn clean` completes without errors

### Step 2: Add Quarkus BOM and core extensions
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add Quarkus BOM in dependencyManagement section (io.quarkus.platform:quarkus-bom:3.x)
  - Add quarkus-maven-plugin
  - Add quarkus-arc (CDI)
  - Add quarkus-resteasy-reactive-jackson (REST + JSON)
  - Add quarkus-hibernate-orm (JPA)
  - Add quarkus-jdbc-postgresql (database driver)
  - Remove maven-war-plugin
- Why: Quarkus requires its BOM and plugins for dependency management and build process
- Depends on: Step 1
- Verify: `mvn dependency:tree` shows Quarkus dependencies

### Step 3: Add messaging, Flyway, and observability extensions
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add quarkus-artemis-jms (JMS messaging)
  - Add quarkus-flyway (database migrations)
  - Add quarkus-smallrye-health (health checks)
  - Add quarkus-micrometer-registry-prometheus (metrics)
  - Update flyway-core version to latest compatible with Quarkus
  - Remove jboss-jms-api_2.0_spec (replaced by quarkus-artemis-jms)
  - Remove jboss-rmi-api_1.0_spec (not needed)
- Why: These extensions provide Quarkus equivalents for Java EE JMS, database migrations, and cloud-native observability
- Depends on: Step 2
- Verify: `mvn compile` succeeds

### Step 4: Add Keycloak OIDC and test dependencies
- Phase: Build Configuration
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add quarkus-oidc (Keycloak integration)
  - Replace JUnit 4 with quarkus-junit5
  - Add io.rest-assured:rest-assured for REST testing
  - Update mockito-core to latest version compatible with JUnit 5
  - Remove hibernate-entitymanager from test scope (provided by quarkus-hibernate-orm)
- Why: Quarkus uses OIDC for Keycloak and JUnit 5 for testing
- Depends on: Step 3
- Verify: Test dependencies resolve correctly

### Step 5: Migrate CatalogItemEntity
- Phase: Data Layer
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: No changes required - standard JPA annotations are compatible with Quarkus
- Why: Quarkus Hibernate ORM supports standard JPA entity annotations
- Depends on: Step 4
- Verify: Entity compiles without errors

### Step 6: Migrate InventoryEntity
- Phase: Data Layer
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: No changes required - standard JPA annotations are compatible with Quarkus
- Why: Quarkus Hibernate ORM supports standard JPA entity annotations
- Depends on: Step 4
- Verify: Entity compiles without errors

### Step 7: Migrate Order entity
- Phase: Data Layer
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: No changes required - standard JPA annotations are compatible with Quarkus
- Why: Quarkus Hibernate ORM supports standard JPA entity annotations
- Depends on: Step 4
- Verify: Entity compiles without errors

### Step 8: Migrate OrderItem entity
- Phase: Data Layer
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: No changes required - standard JPA annotations are compatible with Quarkus
- Why: Quarkus Hibernate ORM supports standard JPA entity annotations
- Depends on: Step 4
- Verify: Entity compiles without errors

### Step 9: Migrate Product entity
- Phase: Data Layer
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: No changes required - standard JPA annotations are compatible with Quarkus
- Why: Quarkus Hibernate ORM supports standard JPA entity annotations
- Depends on: Step 4
- Verify: Entity compiles without errors

### Step 10: Migrate Promotion entity
- Phase: Data Layer
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: No changes required - standard JPA annotations are compatible with Quarkus
- Why: Quarkus Hibernate ORM supports standard JPA entity annotations
- Depends on: Step 4
- Verify: Entity compiles without errors

### Step 11: Migrate ShoppingCart entity
- Phase: Data Layer
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: No changes required - standard JPA annotations are compatible with Quarkus
- Why: Quarkus Hibernate ORM supports standard JPA entity annotations
- Depends on: Step 4
- Verify: Entity compiles without errors

### Step 12: Migrate ShoppingCartItem entity
- Phase: Data Layer
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: No changes required - standard JPA annotations are compatible with Quarkus
- Why: Quarkus Hibernate ORM supports standard JPA entity annotations
- Depends on: Step 4
- Verify: Entity compiles without errors

### Step 13: Migrate Resources CDI producer
- Phase: Data Layer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - BEFORE: @PersistenceContext private EntityManager em; @Produces public EntityManager getEntityManager()
  - AFTER: @Inject EntityManager em; @Produces public EntityManager getEntityManager()
  - Or simply remove this class entirely and inject EntityManager directly in services
  - Specific changes:
    1. Replace @PersistenceContext with @Inject
    2. Alternatively, delete this file and update all usages to inject EntityManager directly
- Why: Quarkus ArC automatically provides EntityManager via CDI, @PersistenceContext is not the standard pattern
- Depends on: Step 4
- Verify: Class compiles, or if deleted, dependent services still compile

### Step 14: COMPLEX - Migrate DataBaseMigrationStartup
- Phase: Data Layer
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - BEFORE: @Singleton @Startup @TransactionManagement EJB with @Resource DataSource and manual Flyway setup
  - AFTER: Delete this entire class - Quarkus Flyway handles migrations automatically
  - Specific changes:
    1. DELETE this entire file
    2. Flyway migrations will be configured in application.properties
    3. Quarkus automatically runs Flyway on startup when quarkus-flyway extension is present
- Why: Quarkus Flyway extension automatically discovers and runs migrations from src/main/resources/db/migration without manual setup
- Depends on: Step 3, Step 13
- Verify: File is deleted, application.properties has flyway configuration

### Step 15: Migrate Producers utility
- Phase: Data Layer
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do: Replace Logger producer with JBoss Logging injection pattern or remove if using @Inject Logger directly
- Why: Quarkus uses JBoss Logging and can inject loggers directly
- Depends on: Step 4
- Verify: Logging works in services that use @Inject Logger

### Step 16: Migrate Transformers utility
- Phase: Data Layer
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: No changes required - utility class with static JSON transformation methods
- Why: Pure utility class with no Java EE dependencies
- Depends on: Step 4
- Verify: Class compiles without errors

### Step 17: COMPLEX - Migrate OrderServiceMDB
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: @MessageDriven with @ActivationConfigProperty annotations, implements MessageListener
  - AFTER: Quarkus JMS listener using @JMSConnectionFactory and @Incoming or @ConsumeEvent
  - Specific changes:
    1. Remove: @MessageDriven annotation and all @ActivationConfigProperty
    2. Remove: implements MessageListener
    3. Add: @ApplicationScoped class scope
    4. Rename method: onMessage() → consumeOrder() or similar
    5. Add: @Incoming("orders") or use Quarkus Artemis JMS annotations
    6. Keep: @Inject OrderService and CatalogService
    7. Keep: Business logic in message handler
- Why: Quarkus doesn't support Java EE Message-Driven Beans; use Quarkus Artemis JMS or Reactive Messaging instead
- Depends on: Step 3, Step 13
- Verify: `grep -n "@MessageDriven" src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java` returns no results

### Step 18: COMPLEX - Migrate InventoryNotificationMDB
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Manual WebLogic JNDI lookup, TopicConnection setup, implements MessageListener
  - AFTER: Quarkus JMS listener similar to OrderServiceMDB
  - Specific changes:
    1. Remove: All WebLogic JNDI constants and InitialContext code
    2. Remove: init() and close() methods with manual connection management
    3. Remove: implements MessageListener
    4. Add: @ApplicationScoped class scope
    5. Add: @Incoming("orders") or Quarkus Artemis JMS listener annotation
    6. Keep: @Inject CatalogService
    7. Keep: Business logic for LOW_THRESHOLD checking
    8. Simplify: onMessage() to just process the message without connection management
- Why: Quarkus manages JMS connections automatically; manual JNDI lookup and WebLogic-specific code must be removed
- Depends on: Step 3, Step 13
- Verify: No references to weblogic.jndi or manual TopicConnection management remain

### Step 19: Migrate OrderService
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - BEFORE: May have @Stateless or other EJB annotations
  - AFTER: @ApplicationScoped with @Transactional for transaction management
  - Specific changes:
    1. Remove: @Stateless or any EJB annotations
    2. Add: @ApplicationScoped
    3. Add: @Transactional on methods that modify data
    4. Keep: @Inject EntityManager and other dependencies
- Why: Quarkus uses CDI scopes and declarative transaction management instead of EJB
- Depends on: Step 13
- Verify: Service compiles and has proper scope annotation

### Step 20: Migrate CatalogService
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - BEFORE: May have @Stateless or other EJB annotations
  - AFTER: @ApplicationScoped with @Transactional
  - Specific changes:
    1. Remove: @Stateless or any EJB annotations
    2. Add: @ApplicationScoped
    3. Add: @Transactional on methods that modify data
    4. Keep: @Inject EntityManager
- Why: Quarkus uses CDI scopes instead of EJB
- Depends on: Step 13
- Verify: Service compiles and has proper scope annotation

### Step 21: Migrate ProductService
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do: Replace @Stateless with @ApplicationScoped, add @Transactional if needed
- Why: Quarkus uses CDI scopes instead of EJB
- Depends on: Step 13
- Verify: Service compiles and has proper scope annotation

### Step 22: Migrate PromoService
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do: Replace @Stateless with @ApplicationScoped, add @Transactional if needed
- Why: Quarkus uses CDI scopes instead of EJB
- Depends on: Step 13
- Verify: Service compiles and has proper scope annotation

### Step 23: Migrate ShippingService
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do: Replace @Stateless with @ApplicationScoped, remove @Remote if present
- Why: Quarkus uses CDI scopes instead of EJB, remote interfaces not supported
- Depends on: Step 13
- Verify: Service compiles and has proper scope annotation

### Step 24: Remove ShippingServiceRemote interface
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Delete this file - remote EJB interfaces not used in Quarkus
- Why: Quarkus doesn't support EJB remote interfaces; if remote access needed, expose via REST
- Depends on: Step 23
- Verify: File no longer exists

### Step 25: Migrate ShoppingCartOrderProcessor
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do: Replace @Stateless with @ApplicationScoped, add @Transactional if needed
- Why: Quarkus uses CDI scopes instead of EJB
- Depends on: Step 13
- Verify: Service compiles and has proper scope annotation

### Step 26: Migrate ShoppingCartService
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do: Replace @Stateless with @ApplicationScoped, add @Transactional if needed
- Why: Quarkus uses CDI scopes instead of EJB
- Depends on: Step 13
- Verify: Service compiles and has proper scope annotation

### Step 27: COMPLEX - Replace StartupListener with Quarkus lifecycle events
- Phase: Service Layer
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - BEFORE: extends ApplicationLifecycleListener with postStart/preStop methods
  - AFTER: @ApplicationScoped class with @Observes StartupEvent and ShutdownEvent
  - Specific changes:
    1. Remove: extends ApplicationLifecycleListener
    2. Remove: import weblogic.application.*
    3. Add: @ApplicationScoped annotation
    4. Replace: postStart(ApplicationLifecycleEvent evt) with void onStart(@Observes StartupEvent evt)
    5. Replace: preStop(ApplicationLifecycleEvent evt) with void onStop(@Observes ShutdownEvent evt)
    6. Add: import io.quarkus.runtime.StartupEvent and ShutdownEvent
    7. Keep: @Inject Logger and logging statements
- Why: Quarkus uses CDI event observers for lifecycle events instead of WebLogic-specific listeners
- Depends on: Step 4
- Verify: No weblogic imports remain, class uses Quarkus lifecycle events

### Step 28: Migrate RestApplication
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do: Keep as-is or simplify - @ApplicationPath("/services") is supported in Quarkus
- Why: Quarkus RESTEasy Reactive supports JAX-RS Application class
- Depends on: Step 2
- Verify: Application path is correctly configured

### Step 29: COMPLEX - Migrate CartEndpoint
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - BEFORE: @SessionScoped REST endpoint
  - AFTER: @RequestScoped or @ApplicationScoped with proper cart state management
  - Specific changes:
    1. Replace: @SessionScoped with @RequestScoped (Quarkus doesn't support HTTP session scope in same way)
    2. Consider: Moving session state to client-side or database
    3. Alternative: Use Quarkus session management if HTTP sessions are required
    4. Keep: All JAX-RS annotations (@GET, @POST, @DELETE, @Path, @Produces)
    5. Keep: @Inject ShoppingCartService
    6. Keep: All business logic
- Why: Quarkus has different session management; @SessionScoped may not work as expected for REST endpoints
- Depends on: Step 26
- Verify: Endpoint compiles, consider session management strategy

### Step 30: Migrate ProductEndpoint
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do: No changes required - standard JAX-RS annotations work in Quarkus
- Why: Quarkus RESTEasy Reactive supports standard JAX-RS
- Depends on: Step 2
- Verify: Endpoint compiles without errors

### Step 31: Migrate OrderEndpoint
- Phase: REST API Layer
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do: No changes required - standard JAX-RS annotations work in Quarkus
- Why: Quarkus RESTEasy Reactive supports standard JAX-RS
- Depends on: Step 2
- Verify: Endpoint compiles without errors

### Step 32: Create application.properties configuration
- Phase: Configuration & Integration
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create file with Quarkus configuration for datasource, Hibernate, Flyway, JMS, OIDC, and observability
  - Datasource: quarkus.datasource.db-kind=postgresql, jdbc.url, username, password
  - Hibernate: quarkus.hibernate-orm.database.generation=none, log.sql=false
  - Flyway: quarkus.flyway.migrate-at-start=true, locations=classpath:db/migration
  - JMS: quarkus.artemis.url, username, password, topic configuration for "orders"
  - OIDC: quarkus.oidc.auth-server-url, client-id, credentials.secret
  - Health: quarkus.health.extensions.enabled=true
  - Metrics: quarkus.micrometer.enabled=true
  - Dev Services: quarkus.datasource.devservices.enabled=true, quarkus.artemis.devservices.enabled=true
- Why: Quarkus uses application.properties instead of Java EE XML descriptors
- Depends on: Step 3, Step 4
- Verify: File exists with all required properties

### Step 33: Remove persistence.xml
- Phase: Configuration & Integration
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file - datasource and Hibernate configuration moved to application.properties
- Why: Quarkus configures JPA via application.properties, not persistence.xml
- Depends on: Step 32
- Verify: File no longer exists

### Step 34: Create Keycloak OIDC configuration
- Phase: Configuration & Integration
- File: src/main/resources/application.properties
- Action: MODIFY
- What to do: Add OIDC configuration based on existing keycloak.json (if present in src/main/webapp)
- Why: Quarkus OIDC uses application.properties instead of keycloak.json
- Depends on: Step 32
- Verify: OIDC properties are present in application.properties

### Step 35: Convert index.jsp to static HTML
- Phase: Frontend & Static Resources
- File: src/main/resources/META-INF/resources/index.html
- Action: CREATE
- What to do: Convert src/main/webapp/index.jsp to static HTML, move to src/main/resources/META-INF/resources/
- Why: Quarkus serves static content from META-INF/resources, JSP not recommended
- Depends on: Step 32
- Verify: Static HTML file exists and serves correctly

### Step 36: Convert health.jsp to REST health endpoint
- Phase: Frontend & Static Resources
- File: N/A (replaced by Quarkus SmallRye Health)
- Action: DELETE
- What to do: Delete src/main/webapp/health.jsp - replaced by /q/health endpoint from quarkus-smallrye-health
- Why: Quarkus provides standard health endpoints automatically
- Depends on: Step 3
- Verify: /q/health endpoint is accessible

### Step 37: Move AngularJS app to static resources
- Phase: Frontend & Static Resources
- File: src/main/resources/META-INF/resources/
- Action: MODIFY
- What to do: Move all AngularJS JavaScript, CSS, and bower_components from src/main/webapp to src/main/resources/META-INF/resources/
- Why: Quarkus serves static content from META-INF/resources
- Depends on: Step 35
- Verify: Static resources are accessible under /

### Step 38: Delete WebLogic ApplicationLifecycleEvent
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this WebLogic stub class - no longer needed
- Why: Replaced by Quarkus lifecycle events in Step 27
- Depends on: Step 27
- Verify: File no longer exists

### Step 39: Delete WebLogic ApplicationLifecycleListener
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this WebLogic stub class - no longer needed
- Why: Replaced by Quarkus lifecycle events in Step 27
- Depends on: Step 27
- Verify: File no longer exists

### Step 40: Delete WebLogic NonCatalogLogger
- Phase: Cleanup
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Delete this WebLogic stub class - no longer needed
- Why: Quarkus uses JBoss Logging, WebLogic logging classes not needed
- Depends on: Step 15
- Verify: File no longer exists

### Step 41: Remove weblogic directory
- Phase: Cleanup
- File: src/main/java/weblogic/
- Action: DELETE
- What to do: Remove entire weblogic package directory after all stub classes are deleted
- Why: All WebLogic-specific code has been removed
- Depends on: Step 38, Step 39, Step 40
- Verify: `ls src/main/java/weblogic` returns "no such file or directory"

### Step 42: Update test classes for JUnit 5
- Phase: Testing & Verification
- File: src/test/java/**/*.java
- Action: MODIFY
- What to do:
  - Replace: org.junit.Test with org.junit.jupiter.api.Test
  - Replace: @Before with @BeforeEach, @After with @AfterEach
  - Replace: @BeforeClass with @BeforeAll, @AfterClass with @AfterAll
  - Add: @QuarkusTest annotation to integration test classes
  - Update: Assertions from org.junit.Assert to org.junit.jupiter.api.Assertions
- Why: Quarkus uses JUnit 5 for testing
- Depends on: Step 4
- Verify: Tests compile with JUnit 5 annotations

### Step 43: Create health check endpoint
- Phase: Testing & Verification
- File: N/A (auto-generated by quarkus-smallrye-health)
- Action: CREATE
- What to do: No action needed - /q/health is automatically available with quarkus-smallrye-health extension
- Why: Quarkus SmallRye Health provides readiness and liveness probes automatically
- Depends on: Step 3
- Verify: `curl http://localhost:8080/q/health` returns health status

### Step 44: Create metrics endpoint
- Phase: Testing & Verification
- File: N/A (auto-generated by quarkus-micrometer)
- Action: CREATE
- What to do: No action needed - /q/metrics is automatically available with quarkus-micrometer extension
- Why: Quarkus Micrometer provides Prometheus-compatible metrics automatically
- Depends on: Step 3
- Verify: `curl http://localhost:8080/q/metrics` returns Prometheus metrics

### Step 45: Create application-dev.properties for development
- Phase: Testing & Verification
- File: src/main/resources/application-dev.properties
- Action: CREATE
- What to do: Create dev profile configuration with Dev Services enabled, debug logging, and development-friendly settings
- Why: Separates development configuration from production
- Depends on: Step 32
- Verify: File exists with dev-specific properties

### Step 46: Final build verification
- Phase: Testing & Verification
- File: N/A
- Action: MODIFY
- What to do: Run `mvn clean package` to verify complete build
- Why: Ensures all code compiles and packages correctly
- Depends on: All previous steps
- Verify: Build completes with "BUILD SUCCESS"

### Step 47: Dev mode verification
- Phase: Testing & Verification
- File: N/A
- Action: MODIFY
- What to do: Run `mvn quarkus:dev` to start application in dev mode
- Why: Verifies application starts and runs correctly
- Depends on: Step 46
- Verify: Application starts, logs show successful startup, dev UI accessible at /q/dev

## Verification

Final verification command:
```bash
mvn clean quarkus:dev
```

Expected outcomes:
- Application builds successfully
- Application starts in Quarkus dev mode
- No Java EE dependencies remain
- REST endpoints respond at /services/*
- Health endpoint accessible at /q/health
- Metrics endpoint accessible at /q/metrics
- JMS messaging works with Artemis (Dev Services)
- Database migrations execute successfully
- Static frontend serves from /

## Notes

### Session Management for CartEndpoint
The original CartEndpoint uses @SessionScoped which may not work the same way in Quarkus. Consider these alternatives:
1. Use client-side session tokens
2. Store cart state in database with session ID
3. Use Quarkus HTTP session if truly needed (requires additional configuration)

### Message-Driven Bean Alternatives
Quarkus supports two approaches for JMS:
1. **Quarkus Artemis JMS**: Direct JMS API with @JMSListener or manual consumer setup
2. **SmallRye Reactive Messaging**: Modern reactive approach with @Incoming/@Outgoing

This plan uses Artemis JMS for minimal code changes. Consider Reactive Messaging for new features.

### WebLogic Legacy Code
The InventoryNotificationMDB contains significant WebLogic-specific code including:
- Manual JNDI lookups with weblogic.jndi.WLInitialContextFactory
- Manual TopicConnection management
- init() and close() methods

All of this should be removed in favor of Quarkus-managed JMS connections.

### Flyway Migration Scripts
Existing Flyway scripts in src/main/resources/db/ should work without modification. Verify migration history is preserved when first running against existing database.

### System-scoped Dependencies
The audit-logging-library JAR in lib/ will continue to work as a system-scoped dependency. For production, consider publishing to a Maven repository.

### Frontend Conversion
Converting from JSP to static SPA is listed as one step but may require frontend development work to ensure AngularJS app works correctly without JSP dynamic content. Test thoroughly.
