# Implementation Plan

## Goal
Migrate coolstore-monolith from Java EE 7 on JBoss EAP 7.4 to Quarkus 3 with JAR packaging and Java 17.

- Domain skill: none

## Project Summary
- Type: Maven WAR → Maven JAR (Quarkus fast-jar)
- Files affected: 30 Java files + config files + 3 WebLogic stubs to delete
- Estimated complexity: Medium-High
- Hardest steps:
  1. Step 32 - COMPLEX: Migrate ShoppingCartService (@Stateful with remote EJB lookup)
  2. Step 35 - COMPLEX: Convert OrderServiceMDB to Quarkus JMS
  3. Step 36 - COMPLEX: Refactor InventoryNotificationMDB to Quarkus JMS

## Steps

### Step 1: Update pom.xml - Set Java version and properties
- Phase: Project Setup
- File: pom.xml
- Action: MODIFY
- What to do: Update maven.compiler.source and maven.compiler.target from 1.8 to 17
- Why: Quarkus 3 requires Java 17
- Depends on: none
- Verify: `grep -E "source>|target>" pom.xml` shows version 17

### Step 2: Update pom.xml - Add Quarkus BOM
- Phase: Project Setup
- File: pom.xml
- Action: MODIFY
- What to do: Add Quarkus BOM to dependencyManagement section (io.quarkus.platform:quarkus-bom:3.8.1)
- Why: Manages Quarkus extension versions
- Depends on: Step 1
- Verify: `grep quarkus-bom pom.xml` shows BOM entry

### Step 3: Update pom.xml - Change packaging from WAR to JAR
- Phase: Project Setup
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus uses JAR packaging (fast-jar format)
- Depends on: Step 2
- Verify: `grep "<packaging>" pom.xml` shows jar

### Step 4: Update pom.xml - Remove Java EE dependencies
- Phase: Project Setup
- File: pom.xml
- Action: MODIFY
- What to do: Remove javaee-web-api and javaee-api dependencies (replace with Quarkus extensions)
- Why: Quarkus provides Jakarta EE APIs through extensions
- Depends on: Step 3
- Verify: `grep javaee-api pom.xml` returns nothing

### Step 5: Update pom.xml - Add Quarkus core extensions
- Phase: Project Setup
- File: pom.xml
- Action: MODIFY
- What to do: Add dependencies: quarkus-arc, quarkus-hibernate-orm, quarkus-jdbc-postgresql, quarkus-resteasy-reactive-jackson
- Why: Core Quarkus functionality for CDI, persistence, and REST
- Depends on: Step 4
- Verify: `grep quarkus-arc pom.xml` shows extension

### Step 6: Update pom.xml - Add Quarkus JMS and transaction extensions
- Phase: Project Setup
- File: pom.xml
- Action: MODIFY
- What to do: Add dependencies: quarkus-artemis-jms, quarkus-narayana-jta
- Why: JMS messaging and transaction management
- Depends on: Step 5
- Verify: `grep quarkus-artemis-jms pom.xml` shows extension

### Step 7: Update pom.xml - Add Quarkus security extension
- Phase: Project Setup
- File: pom.xml
- Action: MODIFY
- What to do: Add dependency: quarkus-oidc
- Why: Keycloak integration via OpenID Connect
- Depends on: Step 6
- Verify: `grep quarkus-oidc pom.xml` shows extension

### Step 8: Update pom.xml - Add Quarkus Flyway extension
- Phase: Project Setup
- File: pom.xml
- Action: MODIFY
- What to do: Add dependency: quarkus-flyway, update flyway-core version to compatible version or remove (Quarkus manages it)
- Why: Database migration support
- Depends on: Step 7
- Verify: `grep quarkus-flyway pom.xml` shows extension

### Step 9: Update pom.xml - Update test dependencies
- Phase: Project Setup
- File: pom.xml
- Action: MODIFY
- What to do: Replace JUnit 4 with quarkus-junit5, update mockito-core to 5.x, add io.quarkus:quarkus-test-h2
- Why: Quarkus testing framework uses JUnit 5
- Depends on: Step 8
- Verify: `grep quarkus-junit5 pom.xml` shows test dependency

### Step 10: Update pom.xml - Fix system-scoped audit library
- Phase: Project Setup
- File: pom.xml
- Action: MODIFY
- What to do: Remove systemPath, change scope from system to compile, add instructions comment for installing to Maven repo
- Why: System-scoped dependencies are not portable
- Depends on: Step 9
- Verify: `grep systemPath pom.xml` returns nothing

### Step 11: Update pom.xml - Add Quarkus Maven plugin
- Phase: Project Setup
- File: pom.xml
- Action: MODIFY
- What to do: Add quarkus-maven-plugin in build/plugins section with executions for build and dev mode
- Why: Enables `mvn quarkus:dev` and Quarkus packaging
- Depends on: Step 10
- Verify: `grep quarkus-maven-plugin pom.xml` shows plugin configuration

### Step 12: Update pom.xml - Remove JBoss-specific dependencies
- Phase: Project Setup
- File: pom.xml
- Action: MODIFY
- What to do: Remove jboss-jms-api_2.0_spec and jboss-rmi-api_1.0_spec (Quarkus provides these)
- Why: Quarkus includes necessary Jakarta EE specs
- Depends on: Step 11
- Verify: `grep jboss-jms-api pom.xml` returns nothing

### Step 13: Create application.properties for Quarkus configuration
- Phase: Project Setup
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create file with datasource, Hibernate, Flyway, Artemis JMS, and OIDC configuration migrated from persistence.xml and keycloak.json
- Why: Quarkus uses application.properties for unified configuration
- Depends on: Step 12
- Verify: File exists with datasource URL configuration

### Step 14: Migrate CatalogItemEntity.java
- Phase: Model & Persistence
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Update imports from javax.persistence.* to jakarta.persistence.* (verify annotations remain same)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 13
- Verify: `grep jakarta.persistence CatalogItemEntity.java` shows imports

### Step 15: Migrate InventoryEntity.java
- Phase: Model & Persistence
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Update imports from javax.persistence.* to jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 13
- Verify: `grep jakarta.persistence InventoryEntity.java` shows imports

### Step 16: Migrate Order.java
- Phase: Model & Persistence
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Update imports from javax.persistence.* to jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 13
- Verify: `grep jakarta.persistence Order.java` shows imports

### Step 17: Migrate OrderItem.java
- Phase: Model & Persistence
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Update imports from javax.persistence.* to jakarta.persistence.*
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 13
- Verify: `grep jakarta.persistence OrderItem.java` shows imports

### Step 18: Migrate Product.java
- Phase: Model & Persistence
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Update imports from javax to jakarta (if any JPA/Bean Validation annotations)
- Why: Jakarta namespace migration
- Depends on: Step 13
- Verify: No compilation errors

### Step 19: Migrate Promotion.java
- Phase: Model & Persistence
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Update imports from javax to jakarta (if any annotations)
- Why: Jakarta namespace migration
- Depends on: Step 13
- Verify: No compilation errors

### Step 20: Migrate ShoppingCart.java
- Phase: Model & Persistence
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Update imports from javax to jakarta (if any annotations)
- Why: Jakarta namespace migration
- Depends on: Step 13
- Verify: No compilation errors

### Step 21: Migrate ShoppingCartItem.java
- Phase: Model & Persistence
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Update imports from javax to jakarta (if any annotations)
- Why: Jakarta namespace migration
- Depends on: Step 13
- Verify: No compilation errors

### Step 22: Migrate Resources.java EntityManager producer
- Phase: Model & Persistence
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do: Update imports from javax.enterprise.* to jakarta.enterprise.*, javax.persistence.* to jakarta.persistence.*
- Why: Jakarta namespace migration for CDI and JPA
- Depends on: Step 21
- Verify: `grep jakarta.persistence Resources.java` shows imports

### Step 23: Update or remove persistence.xml
- Phase: Model & Persistence
- File: src/main/resources/META-INF/persistence.xml
- Action: MODIFY
- What to do: Either update to Jakarta persistence schema OR remove (Quarkus can work without persistence.xml if datasource configured in application.properties)
- Why: Datasource configuration moved to application.properties
- Depends on: Step 22
- Verify: Check application.properties has quarkus.datasource.* properties

### Step 24: Migrate CatalogService.java
- Phase: Service Layer - Simple
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do: Replace @Stateless with @ApplicationScoped, update imports from javax.ejb to jakarta.enterprise.context, add @Transactional where needed
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 23
- Verify: `grep @ApplicationScoped CatalogService.java` shows annotation

### Step 25: Migrate OrderService.java
- Phase: Service Layer - Simple
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do: Replace @Stateless with @ApplicationScoped, update javax imports to jakarta, add @Transactional for database operations
- Why: Quarkus uses CDI with explicit transaction management
- Depends on: Step 24
- Verify: `grep @ApplicationScoped OrderService.java` and `grep @Transactional` show annotations

### Step 26: Migrate ProductService.java
- Phase: Service Layer - Simple
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do: Replace @Stateless with @ApplicationScoped, update imports, add @Transactional
- Why: CDI bean migration
- Depends on: Step 25
- Verify: `grep @ApplicationScoped ProductService.java` shows annotation

### Step 27: Migrate ShippingService.java
- Phase: Service Layer - Simple
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do: Replace @Stateless with @ApplicationScoped, update imports
- Why: CDI bean migration
- Depends on: Step 26
- Verify: `grep @ApplicationScoped ShippingService.java` shows annotation

### Step 28: Migrate ShippingServiceRemote.java
- Phase: Service Layer - Simple
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: MODIFY
- What to do: Remove @Remote annotation, convert to plain Java interface (will be used locally via CDI)
- Why: No remote EJB in Quarkus, use CDI injection
- Depends on: Step 27
- Verify: `grep @Remote ShippingServiceRemote.java` returns nothing

### Step 29: Migrate PromoService.java
- Phase: Service Layer - Simple
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do: If has @Stateless, replace with @ApplicationScoped, update imports
- Why: CDI bean migration
- Depends on: Step 28
- Verify: No EJB annotations remain

### Step 30: Migrate DataBaseMigrationStartup.java
- Phase: Service Layer - Simple
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do: Replace @Singleton with @ApplicationScoped, replace @Startup with @Observes StartupEvent, update imports
- Why: Quarkus lifecycle event replaces EJB startup
- Depends on: Step 29
- Verify: `grep "StartupEvent" DataBaseMigrationStartup.java` shows observer method

### Step 31: Migrate ShoppingCartOrderProcessor.java
- Phase: Service Layer - Complex
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do: Replace @Stateless with @ApplicationScoped, update javax.jms to jakarta.jms, update @Resource lookup to use Quarkus Artemis config
- Why: CDI bean with JMS producer
- Depends on: Step 30
- Verify: `grep jakarta.jms ShoppingCartOrderProcessor.java` shows imports

### Step 32: COMPLEX - Migrate ShoppingCartService.java
- Phase: Service Layer - Complex
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
    - BEFORE: @Stateful bean with remote EJB lookup via JNDI (lookupShippingServiceRemote method)
    - AFTER: @SessionScoped CDI bean with @Inject ShippingService
    - Specific changes:
        1. Replace @Stateful with @SessionScoped (jakarta.enterprise.context.SessionScoped)
        2. Update all javax imports to jakarta
        3. Remove lookupShippingServiceRemote() method entirely
        4. Add: `@Inject ShippingService shippingService;`
        5. Replace all `lookupShippingServiceRemote().calculateShipping(sc)` calls with `shippingService.calculateShipping(sc)`
        6. Replace all `lookupShippingServiceRemote().calculateShippingInsurance(sc)` calls with `shippingService.calculateShippingInsurance(sc)`
        7. Remove JNDI-related imports (Context, InitialContext, NamingException, Hashtable)
- Why: Quarkus doesn't support remote EJB or JNDI lookups; use CDI injection instead
- Depends on: Step 31
- Verify: `grep lookupShippingServiceRemote ShoppingCartService.java` returns nothing, `grep @SessionScoped` shows annotation

### Step 33: Update ShippingService to implement ShippingServiceRemote
- Phase: Service Layer - Complex
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do: Add `implements ShippingServiceRemote` to class declaration (if not already present)
- Why: Ensures interface contract is maintained for CDI injection
- Depends on: Step 32
- Verify: `grep "implements ShippingServiceRemote" ShippingService.java` shows implementation

### Step 34: Migrate Transformers.java
- Phase: Service Layer - Complex
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: Update javax imports to jakarta (for JSON processing if applicable)
- Why: Jakarta namespace migration
- Depends on: Step 33
- Verify: No compilation errors

### Step 35: COMPLEX - Convert OrderServiceMDB to Quarkus JMS
- Phase: Service Layer - Complex
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
    - BEFORE: @MessageDriven with @ActivationConfigProperty annotations
    - AFTER: @MessageDriven with Quarkus Artemis configuration
    - Specific changes:
        1. Update imports from javax.ejb to jakarta.ejb
        2. Update imports from javax.jms to jakarta.jms
        3. Update imports from javax.inject to jakarta.inject
        4. Update @ActivationConfigProperty values to match Quarkus Artemis:
           - destinationLookup: "topic/orders" may need to be "orders" (check application.properties)
           - Ensure destination is configured in application.properties as: quarkus.artemis.url, quarkus.artemis.destinations.orders.type=topic
        5. Add @Transactional to onMessage method for transaction management
- Why: Quarkus Artemis JMS requires Jakarta namespace and Quarkus-specific configuration
- Depends on: Step 34
- Verify: `grep jakarta.jms OrderServiceMDB.java` shows imports, `grep @Transactional` shows annotation

### Step 36: COMPLEX - Refactor InventoryNotificationMDB to Quarkus JMS
- Phase: Service Layer - Complex
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
    - BEFORE: Manual JMS setup with WebLogic JNDI, no @MessageDriven annotation
    - AFTER: @MessageDriven bean with Quarkus Artemis configuration
    - Specific changes:
        1. Add @MessageDriven annotation with activation config:
           ```java
           @MessageDriven(name = "InventoryNotificationMDB", activationConfig = {
               @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "orders"),
               @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
               @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
           })
           ```
        2. Update all imports from javax to jakarta
        3. Remove all WebLogic-specific constants (JNDI_FACTORY, JMS_FACTORY, TOPIC)
        4. Remove init() method (container manages lifecycle)
        5. Remove close() method (container manages lifecycle)
        6. Remove getInitialContext() method (no JNDI needed)
        7. Remove tcon, tsession, tsubscriber fields
        8. Keep onMessage() method, update to use jakarta.jms imports
        9. Add @Inject for CatalogService
        10. Add @Transactional to onMessage method
- Why: Quarkus manages MDB lifecycle; WebLogic JNDI is not compatible
- Depends on: Step 35
- Verify: `grep @MessageDriven InventoryNotificationMDB.java` shows annotation, `grep weblogic` returns nothing

### Step 37: Migrate Producers.java
- Phase: Utilities & Lifecycle
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do: Update imports from javax.enterprise to jakarta.enterprise, verify @Produces methods are compatible
- Why: Jakarta namespace migration
- Depends on: Step 36
- Verify: `grep jakarta.enterprise Producers.java` shows imports

### Step 38: COMPLEX - Replace StartupListener with Quarkus lifecycle events
- Phase: Utilities & Lifecycle
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
    - BEFORE: extends ApplicationLifecycleListener with postStart/preStop methods
    - AFTER: CDI bean with @Observes StartupEvent and ShutdownEvent
    - Specific changes:
        1. Remove: `extends ApplicationLifecycleListener`
        2. Remove imports: weblogic.application.*
        3. Add imports: io.quarkus.runtime.StartupEvent, io.quarkus.runtime.ShutdownEvent, jakarta.enterprise.event.Observes
        4. Replace postStart method:
           ```java
           void onStart(@Observes StartupEvent event) {
               log.info("AppListener(postStart)");
           }
           ```
        5. Replace preStop method:
           ```java
           void onStop(@Observes ShutdownEvent event) {
               log.info("AppListener(preStop)");
           }
           ```
        6. Add @ApplicationScoped annotation to class
        7. Update Logger injection to use jakarta.inject.Inject
- Why: Quarkus uses CDI observer pattern for lifecycle events instead of WebLogic APIs
- Depends on: Step 37
- Verify: `grep @Observes StartupListener.java` shows event observers, `grep weblogic` returns nothing

### Step 39: Migrate CartEndpoint.java
- Phase: REST API
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do: Update imports from javax.ws.rs to jakarta.ws.rs, javax.inject to jakarta.inject
- Why: Jakarta namespace for JAX-RS
- Depends on: Step 38
- Verify: `grep jakarta.ws.rs CartEndpoint.java` shows imports

### Step 40: Migrate OrderEndpoint.java
- Phase: REST API
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do: Update imports from javax.ws.rs to jakarta.ws.rs, javax.inject to jakarta.inject
- Why: Jakarta namespace for JAX-RS
- Depends on: Step 39
- Verify: `grep jakarta.ws.rs OrderEndpoint.java` shows imports

### Step 41: Migrate ProductEndpoint.java
- Phase: REST API
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do: Update imports from javax.ws.rs to jakarta.ws.rs, javax.inject to jakarta.inject
- Why: Jakarta namespace for JAX-RS
- Depends on: Step 40
- Verify: `grep jakarta.ws.rs ProductEndpoint.java` shows imports

### Step 42: Migrate RestApplication.java
- Phase: REST API
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do: Update imports from javax.ws.rs to jakarta.ws.rs, verify @ApplicationPath works with Quarkus
- Why: Jakarta namespace, Quarkus RESTEasy Reactive compatibility
- Depends on: Step 41
- Verify: `grep jakarta.ws.rs RestApplication.java` shows imports

### Step 43: Update application.properties with datasource configuration
- Phase: Configuration
- File: src/main/resources/application.properties
- Action: MODIFY
- What to do: Add datasource properties from persistence.xml: quarkus.datasource.db-kind=postgresql, quarkus.datasource.jdbc.url, quarkus.datasource.username, quarkus.datasource.password
- Why: Quarkus datasource configuration
- Depends on: Step 42
- Verify: `grep quarkus.datasource application.properties` shows configuration

### Step 44: Update application.properties with Hibernate configuration
- Phase: Configuration
- File: src/main/resources/application.properties
- Action: MODIFY
- What to do: Add Hibernate properties: quarkus.hibernate-orm.database.generation=none, quarkus.hibernate-orm.log.sql=false, quarkus.hibernate-orm.dialect=org.hibernate.dialect.PostgreSQLDialect
- Why: Migrate persistence.xml Hibernate settings
- Depends on: Step 43
- Verify: `grep quarkus.hibernate-orm application.properties` shows configuration

### Step 45: Update application.properties with Flyway configuration
- Phase: Configuration
- File: src/main/resources/application.properties
- Action: MODIFY
- What to do: Add Flyway properties: quarkus.flyway.migrate-at-start=true, quarkus.flyway.locations=classpath:db
- Why: Configure database migrations
- Depends on: Step 44
- Verify: `grep quarkus.flyway application.properties` shows configuration

### Step 46: Update application.properties with Artemis JMS configuration
- Phase: Configuration
- File: src/main/resources/application.properties
- Action: MODIFY
- What to do: Add Artemis properties: quarkus.artemis.url, quarkus.artemis.username, quarkus.artemis.password, configure topic/orders destination
- Why: JMS messaging configuration for MDBs
- Depends on: Step 45
- Verify: `grep quarkus.artemis application.properties` shows configuration

### Step 47: Update application.properties with OIDC/Keycloak configuration
- Phase: Configuration
- File: src/main/resources/application.properties
- Action: MODIFY
- What to do: Migrate keycloak.json settings to OIDC properties: quarkus.oidc.auth-server-url, quarkus.oidc.client-id, quarkus.oidc.credentials.secret
- Why: Replace Keycloak adapter with quarkus-oidc
- Depends on: Step 46
- Verify: `grep quarkus.oidc application.properties` shows configuration

### Step 48: Update test dependencies in pom.xml
- Phase: Testing
- File: pom.xml
- Action: MODIFY
- What to do: Ensure rest-assured, assertj, or other test libraries are compatible with Quarkus testing
- Why: Support @QuarkusTest framework
- Depends on: Step 47
- Verify: Tests compile successfully

### Step 49: Migrate test classes to JUnit 5
- Phase: Testing
- File: src/test/java/**/*Test.java (all test files)
- Action: MODIFY
- What to do: Replace @org.junit.Test with @org.junit.jupiter.api.Test, update imports, add @QuarkusTest to integration tests, update assertion imports
- Why: Quarkus uses JUnit 5 for testing
- Depends on: Step 48
- Verify: `mvn test` runs successfully with JUnit 5

### Step 50: Remove or update web.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/web.xml
- Action: MODIFY
- What to do: Remove if not needed (Quarkus handles servlet config), or update to minimal Jakarta Servlet schema if webapp resources are needed
- Why: Quarkus doesn't require web.xml for most configurations
- Depends on: Step 49
- Verify: Application builds without web.xml errors

### Step 51: Remove or update beans.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/beans.xml
- Action: MODIFY
- What to do: Move to src/main/resources/META-INF/beans.xml if needed, or remove (Quarkus Arc discovers beans automatically)
- Why: Different CDI configuration location for JAR packaging
- Depends on: Step 50
- Verify: CDI beans are discovered properly

### Step 52: Delete ApplicationLifecycleEvent.java stub
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this file - no longer needed after StartupListener migration
- Why: WebLogic stub replaced by Quarkus lifecycle events
- Depends on: Step 38
- Verify: File does not exist

### Step 53: Delete ApplicationLifecycleListener.java stub
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this file - no longer needed
- Why: WebLogic stub replaced by Quarkus lifecycle events
- Depends on: Step 52
- Verify: File does not exist

### Step 54: Delete NonCatalogLogger.java stub
- Phase: Cleanup
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Delete this file - no longer needed (if it was only a stub and not actually used)
- Why: WebLogic-specific API not needed in Quarkus
- Depends on: Step 53
- Verify: File does not exist, no compilation errors

## Verification

**Build command**: `mvn clean package`

**Development mode**: `mvn quarkus:dev`

**Testing**: `mvn test`

**Final verification checklist**:
- [ ] `mvn clean package` succeeds
- [ ] Application starts with `mvn quarkus:dev`
- [ ] REST endpoints accessible at http://localhost:8080/services/*
- [ ] Database connection works (check logs)
- [ ] Flyway migrations execute
- [ ] JMS message-driven beans consume messages
- [ ] No EJB annotations remain (@Stateless, @Stateful, @Singleton, @MessageDriven replaced)
- [ ] No javax imports remain (all migrated to jakarta)
- [ ] No WebLogic classes remain
- [ ] Tests pass with JUnit 5

## Notes

### Critical Migration Points

1. **ShoppingCartService remote EJB lookup**: This is the most complex change. The remote EJB lookup via JNDI must be completely replaced with CDI injection. Test thoroughly to ensure ShippingService is properly injected and session scope works correctly.

2. **InventoryNotificationMDB**: This bean was manually wiring JMS with WebLogic APIs. It must be converted to a proper @MessageDriven bean with Artemis configuration. The manual TopicConnection/TopicSession management is removed in favor of container-managed lifecycle.

3. **Transaction boundaries**: EJB container-managed transactions are replaced with explicit @Transactional annotations. Review all service methods that perform database operations and add @Transactional where needed.

4. **Namespace migration**: All javax.* imports must be updated to jakarta.* for Jakarta EE 9+/Quarkus 3 compatibility. This includes: persistence, inject, enterprise, ws.rs, jms, ejb, servlet.

5. **Configuration consolidation**: All configuration migrates from XML files (persistence.xml, web.xml, keycloak.json) to application.properties. Ensure all datasource, JMS, security, and framework properties are properly configured.

6. **JMS destination naming**: Topic/queue names may need adjustment between JBoss EAP ("topic/orders") and Quarkus Artemis ("orders"). Verify destination configuration in application.properties matches MDB activation config.

7. **Session scope**: @Stateful → @SessionScoped requires HTTP session support. Ensure Quarkus has session management configured if needed for stateful shopping cart behavior.

8. **Testing migration**: JUnit 4 → JUnit 5 requires updating all test annotations and imports. @QuarkusTest provides excellent integration testing with full CDI container, but tests must be adapted to new assertion and lifecycle APIs.

### Application.properties Template

```properties
# Datasource
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore
quarkus.datasource.username=coolstore
quarkus.datasource.password=coolstore

# Hibernate ORM
quarkus.hibernate-orm.database.generation=none
quarkus.hibernate-orm.log.sql=false

# Flyway
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db

# Artemis JMS
quarkus.artemis.url=tcp://localhost:61616
quarkus.artemis.username=admin
quarkus.artemis.password=admin

# OIDC/Keycloak
quarkus.oidc.auth-server-url=http://localhost:8180/auth/realms/coolstore
quarkus.oidc.client-id=coolstore-app
quarkus.oidc.credentials.secret=<client-secret>

# HTTP
quarkus.http.port=8080
quarkus.http.root-path=/

# Dev mode
%dev.quarkus.log.level=INFO
```

### Dependency Order Rationale

The steps follow this dependency order:
1. **Project Setup** (Steps 1-13): Foundation - pom.xml and configuration before code changes
2. **Model & Persistence** (Steps 14-23): Data layer first - entities have no business logic dependencies
3. **Service Layer - Simple** (Steps 24-30): Simple EJBs before complex ones
4. **Service Layer - Complex** (Steps 31-36): Complex components that depend on simple services
5. **Utilities & Lifecycle** (Steps 37-38): Support classes that may be used by services
6. **REST API** (Steps 39-42): API layer depends on service layer
7. **Configuration** (Steps 43-47): Finalize all configuration
8. **Testing** (Steps 48-49): Test infrastructure
9. **Cleanup** (Steps 50-54): Remove obsolete files after migration complete

This ordering ensures that each step can be completed and verified before proceeding to dependent steps.
