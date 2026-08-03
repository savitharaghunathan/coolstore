# PLAN.md

## Goal
Migrate Java EE 7 monolith application to Quarkus 3, replacing EJB with CDI, JMS/MDB with SmallRye Reactive Messaging, WAR packaging with JAR, and removing all JNDI lookups.
- Reference used: javaee-to-quarkus skill (modules: build-config, app-config, ejb-to-cdi, messaging, lifecycle, cleanup)

## Project Summary
- Type: Maven WAR (Java EE 7)
- Files affected: ~50+ Java files, pom.xml, persistence.xml, and webapp config files
- Estimated complexity: **High**
- Hardest steps: 
  1. Converting 2 MDBs to SmallRye Reactive Messaging (OrderServiceMDB, InventoryNotificationMDB)
  2. Removing WebLogic-specific JNDI lookups (InventoryNotificationMDB has hardcoded WebLogic InitialContextFactory)
  3. Replacing Remote EJB lookup in ShoppingCartService (lookupShippingServiceRemote)
  4. Migrating @Stateful ShoppingCartService session state management

## Steps

### Step 1: Update pom.xml packaging from WAR to JAR
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus applications are packaged as JAR, not WAR (no application server needed)
- Depends on: none
- Verify: `grep '<packaging>jar</packaging>' pom.xml`

### Step 2: Update pom.xml Java version to 17
- File: pom.xml
- Action: MODIFY
- What to do: 
  - Change `<source>1.8</source>` to `<source>17</source>`
  - Change `<target>1.8</target>` to `<target>17</target>`
- Why: Quarkus 3 requires Java 17 minimum
- Depends on: none
- Verify: `grep '<source>17</source>' pom.xml`

### Step 3: Add Quarkus BOM to pom.xml
- File: pom.xml
- Action: MODIFY
- What to do: Add to `<dependencyManagement>` section (create if missing):
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.quarkus.platform</groupId>
            <artifactId>quarkus-bom</artifactId>
            <version>3.8.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```
- Why: Quarkus BOM manages all Quarkus extension versions
- Depends on: none
- Verify: `grep 'quarkus-bom' pom.xml`

### Step 4: Replace Java EE dependencies with Quarkus extensions in pom.xml
- File: pom.xml
- Action: MODIFY
- What to do:
  - **REMOVE**: `javaee-web-api`, `javaee-api`, `jboss-jms-api_2.0_spec`, `jboss-rmi-api_1.0_spec`
  - **ADD**: 
    - `quarkus-hibernate-orm-panache` (for JPA)
    - `quarkus-jdbc-postgresql` (for database)
    - `quarkus-resteasy-reactive-jackson` (for JAX-RS)
    - `quarkus-smallrye-reactive-messaging-amqp` (for messaging)
    - `quarkus-arc` (for CDI)
    - `quarkus-scheduler` (for @Startup/@Schedule)
    - `quarkus-flyway` (keep Flyway for migrations)
  - **KEEP**: `audit-logging-library` (system scoped dependency)
  - **UPDATE**: Test dependencies to Quarkus test framework
- Why: Quarkus uses extensions instead of Java EE APIs
- Depends on: Step 3
- Verify: `mvn dependency:tree | grep quarkus`

### Step 5: Add Quarkus Maven plugin to pom.xml
- File: pom.xml
- Action: MODIFY
- What to do: Replace `maven-war-plugin` with `quarkus-maven-plugin`:
```xml
<plugin>
    <groupId>io.quarkus.platform</groupId>
    <artifactId>quarkus-maven-plugin</artifactId>
    <version>3.8.0</version>
    <extensions>true</extensions>
    <executions>
        <execution>
            <goals>
                <goal>build</goal>
                <goal>generate-code</goal>
                <goal>generate-code-tests</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```
- Why: Quarkus Maven plugin handles build, dev mode, and native compilation
- Depends on: Step 4
- Verify: `grep 'quarkus-maven-plugin' pom.xml`

### Step 6: Create application.properties for datasource configuration
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create file with datasource configuration to replace JNDI lookup:
```properties
# Datasource
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=coolstore
quarkus.datasource.password=coolstore
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore

# Hibernate
quarkus.hibernate-orm.database.generation=none
quarkus.hibernate-orm.log.sql=false
quarkus.hibernate-orm.log.format-sql=true

# Flyway
quarkus.flyway.migrate-at-start=true

# Messaging - AMQP channels
mp.messaging.incoming.order-queue.connector=smallrye-amqp
mp.messaging.incoming.order-queue.address=orders
mp.messaging.incoming.order-queue.durable=true

mp.messaging.outgoing.notifications-out.connector=smallrye-amqp
mp.messaging.outgoing.notifications-out.address=notifications
```
- Why: Quarkus uses application.properties instead of persistence.xml for configuration
- Depends on: Step 4
- Verify: `test -f src/main/resources/application.properties`

### Step 7: Delete persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Remove file (configuration moved to application.properties)
- Why: Quarkus configures Hibernate via application.properties, not persistence.xml
- Depends on: Step 6
- Verify: `test ! -f src/main/resources/META-INF/persistence.xml`

### Step 8: Delete beans.xml if exists
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Remove file (CDI is enabled by default in Quarkus)
- Why: Quarkus Arc (CDI implementation) doesn't require beans.xml
- Depends on: none
- Verify: `test ! -f src/main/webapp/WEB-INF/beans.xml`

### Step 9: Update imports in CatalogItemEntity.java
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `grep -L 'javax.persistence' src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`

### Step 10: Update imports in InventoryEntity.java
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `grep -L 'javax.persistence' src/main/java/com/redhat/coolstore/model/InventoryEntity.java`

### Step 11: Update imports in Order.java
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `grep -L 'javax.persistence' src/main/java/com/redhat/coolstore/model/Order.java`

### Step 12: Update imports in OrderItem.java
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `grep -L 'javax.persistence' src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 13: Update imports in Product.java
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `grep -L 'javax.persistence' src/main/java/com/redhat/coolstore/model/Product.java`

### Step 14: Update imports in Promotion.java
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `grep -L 'javax.persistence' src/main/java/com/redhat/coolstore/model/Promotion.java`

### Step 15: Update Resources.java to remove JNDI EntityManager
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Remove `@PersistenceContext` annotation
  - Remove `@Produces` annotation from EntityManager
  - Simply inject EntityManager: `@Inject EntityManager em;`
  - Or delete this file if it only produces EntityManager (direct injection works in Quarkus)
- Why: Quarkus uses direct CDI injection, not producer methods for EntityManager
- Depends on: Step 4
- Verify: `grep -L '@PersistenceContext' src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 16: Convert CatalogService from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` → `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` → `@ApplicationScoped`
  - Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
  - Replace `import javax.persistence.*;` → `import jakarta.persistence.*;`
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 4
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 17: Convert OrderService from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` → `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` → `@ApplicationScoped`
  - Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
  - Replace `import javax.persistence.*;` → `import jakarta.persistence.*;`
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 4
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 18: Convert ProductService from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` → `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` → `@ApplicationScoped`
  - Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
  - Replace `import javax.persistence.*;` → `import jakarta.persistence.*;`
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 4
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 19: Convert ShippingService from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` → `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` → `@ApplicationScoped`
  - Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 4
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 20: Convert ShoppingCartOrderProcessor from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` → `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` → `@ApplicationScoped`
  - Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 4
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 21: COMPLEX — Convert ShoppingCartService from @Stateful to @ApplicationScoped with session state
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - **BEFORE**: `@Stateful` EJB with instance variable `private ShoppingCart cart`
  - **AFTER**: `@ApplicationScoped` CDI bean with session-scoped state or request-scoped parameter
  - Specific changes:
    1. Replace `import javax.ejb.Stateful;` → `import jakarta.enterprise.context.ApplicationScoped;`
    2. Replace `@Stateful` → `@ApplicationScoped`
    3. Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
    4. Remove `private ShoppingCart cart` instance variable
    5. Modify methods to accept `cartId` and use a cache/map to store carts by ID
    6. **CRITICAL**: Remove `lookupShippingServiceRemote()` JNDI lookup - replace with direct `@Inject ShippingService`
    7. Remove all `javax.naming.*` imports
  - Affected files: This is a session state management pattern change
- Why: Quarkus doesn't support @Stateful EJBs; state must be managed explicitly (session scope, cache, or database)
- Depends on: Step 19
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java && grep -L 'lookupShippingServiceRemote' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 22: COMPLEX — Convert OrderServiceMDB to SmallRye Reactive Messaging
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - **BEFORE**: `@MessageDriven` MDB with `MessageListener` interface, Topic destination
  - **AFTER**: `@ApplicationScoped` CDI bean with `@Incoming` reactive consumer
  - Specific changes:
    1. Remove `@MessageDriven` and `activationConfig` properties
    2. Remove `implements MessageListener`
    3. Replace with `@ApplicationScoped`
    4. Replace imports:
       - Remove: `javax.ejb.*`, `javax.jms.*`
       - Add: `import jakarta.enterprise.context.ApplicationScoped;`
       - Add: `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    5. Replace `onMessage(Message rcvMessage)` method:
       - OLD: `public void onMessage(Message rcvMessage) { TextMessage msg = ...; String orderStr = msg.getBody(String.class); ... }`
       - NEW: `@Incoming("order-queue") public void onMessage(String orderStr) { ... }`
    6. Remove JMS exception handling (no longer needed with String payload)
    7. Update `@Inject` imports: `javax.inject.Inject` → `jakarta.inject.Inject`
  - Configuration: Channel "order-queue" defined in application.properties (Step 6)
- Why: Quarkus uses SmallRye Reactive Messaging instead of JMS MDBs
- Depends on: Step 6, Step 16, Step 17
- Verify: `grep '@Incoming("order-queue")' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 23: COMPLEX — Convert InventoryNotificationMDB to SmallRye Reactive Messaging
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - **BEFORE**: Plain `MessageListener` with WebLogic-specific JNDI lookup and manual topic subscription
  - **AFTER**: `@ApplicationScoped` CDI bean with `@Incoming` reactive consumer
  - Specific changes:
    1. Remove all WebLogic JNDI code: `JNDI_FACTORY`, `getInitialContext()`, `init()`, `close()` methods
    2. Remove: `javax.jms.*`, `javax.naming.*`, `javax.rmi.PortableRemoteObject` imports
    3. Add `@ApplicationScoped` annotation
    4. Add imports:
       - `import jakarta.enterprise.context.ApplicationScoped;`
       - `import org.eclipse.microprofile.reactive.messaging.Incoming;`
       - `import jakarta.inject.Inject;`
    5. Replace `onMessage(Message rcvMessage)` method:
       - OLD: `public void onMessage(Message rcvMessage) { TextMessage msg = ...; String orderStr = msg.getBody(String.class); ... }`
       - NEW: `@Incoming("order-queue") public void onMessage(String orderStr) { ... }`
    6. Remove JMS exception handling
    7. Keep business logic (LOW_THRESHOLD check, catalogService interaction)
  - Configuration: Uses same "order-queue" channel as OrderServiceMDB (both listen to same topic)
- Why: Remove WebLogic-specific code and use Quarkus Reactive Messaging
- Depends on: Step 6, Step 16
- Verify: `grep '@Incoming("order-queue")' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java && grep -L 'weblogic.jndi' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 24: COMPLEX — Convert DataBaseMigrationStartup to Quarkus lifecycle
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - **BEFORE**: `@Singleton @Startup` EJB with `@PostConstruct`, `@Resource` JNDI datasource lookup
  - **AFTER**: Quarkus Flyway auto-migration (no custom code needed)
  - Specific changes:
    1. **OPTION 1 (Recommended)**: Delete this entire file - Quarkus Flyway extension handles migration at startup via `quarkus.flyway.migrate-at-start=true` in application.properties
    2. **OPTION 2**: Convert to Quarkus startup event if custom logic needed:
       - Replace `@Singleton @Startup` → `@ApplicationScoped`
       - Replace `@PostConstruct` → method with `void onStart(@Observes StartupEvent event)`
       - Remove `@Resource(mappedName = "...")` DataSource lookup
       - Inject DataSource: `@Inject AgroalDataSource dataSource;`
       - Update imports: `javax.*` → `jakarta.*`
  - Recommendation: Use OPTION 1 (delete file)
- Why: Quarkus Flyway extension auto-migrates at startup; custom EJB startup class not needed
- Depends on: Step 6
- Verify: `test ! -f src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java` OR `grep '@Observes StartupEvent' src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`

### Step 25: Update RestApplication imports
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do: Replace `import javax.ws.rs.*` → `import jakarta.ws.rs.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/RestApplication.java`

### Step 26: Update CartEndpoint imports
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ws.rs.*` → `import jakarta.ws.rs.*`
  - Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 27: Update OrderEndpoint imports
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ws.rs.*` → `import jakarta.ws.rs.*`
  - Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
  - Replace `import javax.persistence.*` → `import jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 28: Update ProductEndpoint imports
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ws.rs.*` → `import jakarta.ws.rs.*`
  - Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 29: Update all remaining service and util classes
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
  - Replace any `javax.enterprise.*` → `jakarta.enterprise.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `grep 'jakarta.inject' src/main/java/com/redhat/coolstore/service/PromoService.java`

### Step 30: Update Transformers utility class
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: Replace any `javax.*` → `jakarta.*` imports if present
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `grep -L 'import javax\.' src/main/java/com/redhat/coolstore/utils/Transformers.java`

### Step 31: Delete WEB-INF directory
- File: src/main/webapp/WEB-INF/
- Action: DELETE
- What to do: Remove entire directory (contains web.xml, beans.xml, jboss-web.xml, etc.)
- Why: Quarkus JAR packaging doesn't use WAR structure or web.xml
- Depends on: Step 1
- Verify: `test ! -d src/main/webapp/WEB-INF`

### Step 32: Delete or move static webapp resources
- File: src/main/webapp/
- Action: MODIFY or DELETE
- What to do:
  - If static resources exist in `src/main/webapp/`, move to `src/main/resources/META-INF/resources/`
  - If no static resources, delete entire `src/main/webapp/` directory
- Why: Quarkus serves static resources from META-INF/resources, not webapp
- Depends on: Step 31
- Verify: `test ! -d src/main/webapp` OR `test -d src/main/resources/META-INF/resources`

### Step 33: Create ShippingServiceRemote interface or remove Remote EJB
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE or MODIFY
- What to do:
  - **OPTION 1**: If ShippingServiceRemote is a Remote EJB interface, delete it (use direct ShippingService injection)
  - **OPTION 2**: If it's a separate service, convert to REST client with Quarkus REST Client extension
  - Recommendation: Check if interface exists; likely should delete and use direct injection
- Why: Quarkus doesn't support Remote EJB; use REST APIs or direct injection for local services
- Depends on: Step 21
- Verify: `test ! -f src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java`

### Step 34: Remove test scope Hibernate dependency from pom.xml
- File: pom.xml
- Action: MODIFY
- What to do: Remove `hibernate-entitymanager` test dependency (Quarkus BOM provides Hibernate)
- Why: Quarkus extensions include Hibernate; separate test dependency causes conflicts
- Depends on: Step 4
- Verify: `grep -L 'hibernate-entitymanager' pom.xml`

### Step 35: Update test dependencies to Quarkus test framework
- File: pom.xml
- Action: MODIFY
- What to do:
  - **REMOVE**: `javax.json` test dependency
  - **ADD**:
    - `quarkus-junit5` (scope: test)
    - `rest-assured` (scope: test)
  - **KEEP**: `junit`, `mockito-core`, `h2` (but update JUnit to 5.x if needed)
- Why: Quarkus uses JUnit 5 and REST Assured for testing
- Depends on: Step 4
- Verify: `grep 'quarkus-junit5' pom.xml`

### Step 36: Verify and update Logger injection
- File: Multiple files (scan all services and utils)
- Action: MODIFY
- What to do:
  - Find all `@Inject Logger log;` or similar
  - Replace `import java.util.logging.Logger;` → `import org.jboss.logging.Logger;`
  - Or use Quarkus `@Inject Logger log;` with `import org.jboss.logging.Logger;`
  - Ensure `Logger.getLogger(ClassName.class)` for static loggers
- Why: Quarkus uses JBoss Logging by default; injection pattern changes slightly
- Depends on: Step 4
- Verify: `grep 'org.jboss.logging.Logger' src/main/java/com/redhat/coolstore -r`

### Step 37: Build verification - First compile
- File: N/A (command execution)
- Action: VERIFY
- What to do: Run `mvn clean compile`
- Why: Verify all imports and dependencies resolve correctly
- Depends on: All previous steps
- Verify: Exit code 0

### Step 38: Build verification - Run tests
- File: N/A (command execution)
- Action: VERIFY
- What to do: Run `mvn test` (expect some test failures due to test code changes needed)
- Why: Identify test code that needs migration
- Depends on: Step 37
- Verify: Tests compile (may fail at runtime until test code is migrated)

### Step 39: Build verification - Full package
- File: N/A (command execution)
- Action: VERIFY
- What to do: Run `mvn clean package`
- Why: Verify application builds as Quarkus JAR
- Depends on: Step 37
- Verify: `test -f target/quarkus-app/quarkus-run.jar`

### Step 40: Runtime verification - Dev mode
- File: N/A (command execution)
- Action: VERIFY
- What to do: Run `mvn quarkus:dev` and test REST endpoints
- Why: Verify application starts and runs in Quarkus dev mode
- Depends on: Step 39
- Verify: Application starts without errors, REST endpoints respond

## Verification

After completing all steps, run these commands in order:

1. **Build**: `mvn clean package`
   - Expected: SUCCESS, JAR created in `target/quarkus-app/quarkus-run.jar`

2. **Dev Mode**: `mvn quarkus:dev`
   - Expected: Application starts on port 8080, live reload enabled

3. **REST API Test**:
   ```bash
   curl http://localhost:8080/services/products
   curl http://localhost:8080/services/cart/123456
   ```
   - Expected: JSON responses from endpoints

4. **Messaging Test**: 
   - Ensure AMQP broker (ActiveMQ Artemis) is running
   - Send test message to `orders` queue/topic
   - Check logs for MDB processing

5. **Database Test**:
   - Ensure PostgreSQL is running with `coolstore` database
   - Verify Flyway migrations run at startup
   - Check tables are created

## Notes

### Critical Decisions Made

1. **Messaging**: Using AMQP (ActiveMQ Artemis) instead of in-memory channels for production-ready messaging
2. **Session State**: ShoppingCartService @Stateful needs application-level state management (recommend adding a cache layer)
3. **Remote EJB**: Removed Remote EJB lookups - assumes ShippingService is local (if remote, needs REST Client migration)
4. **Flyway**: Keeping Flyway for database migrations (Quarkus Flyway extension handles it)
5. **Database**: Assuming PostgreSQL (was configured for JBoss datasource); adjust if different

### Complex Patterns Identified

1. **OrderServiceMDB** (Step 22): Topic-based MDB → Reactive Messaging consumer
2. **InventoryNotificationMDB** (Step 23): WebLogic-specific JNDI + manual topic subscription → Reactive Messaging
3. **ShoppingCartService** (Step 21): @Stateful EJB with session state + Remote EJB lookup → ApplicationScoped with state management
4. **DataBaseMigrationStartup** (Step 24): @Singleton @Startup with JNDI datasource → Quarkus Flyway auto-migration

### Migration Order Rationale

1. **Build Config (Steps 1-5)**: Foundation - must be first
2. **App Config (Steps 6-8)**: Configuration before code changes
3. **Model Layer (Steps 9-15)**: Entities have no dependencies
4. **Persistence (Step 15)**: Resources/producers before services
5. **Services (Steps 16-21)**: Business logic, ordered by dependencies
6. **Messaging (Steps 22-23)**: After services they depend on
7. **Lifecycle (Step 24)**: After core services
8. **REST Layer (Steps 25-28)**: After services
9. **Utilities (Steps 29-30)**: Low dependency
10. **Cleanup (Steps 31-36)**: After all code changes
11. **Verification (Steps 37-40)**: Final validation

### Gotchas and Special Cases

- **System-scoped JAR**: `audit-logging-library-1.0.0.jar` needs special handling in Quarkus; verify it's on classpath
- **Flyway Version**: Update Flyway to version compatible with Quarkus (4.1.2 is old)
- **Multiple MDBs on Same Topic**: Both OrderServiceMDB and InventoryNotificationMDB listen to same topic - ensure both use `@Incoming("order-queue")`
- **Logger Injection**: May need to replace JUL Logger with JBoss Logging throughout
- **Test Updates**: Test code will need separate migration (not covered in detail here)
- **WebLogic Specifics**: InventoryNotificationMDB has hardcoded WebLogic connection details - these must be removed

### Post-Migration Tasks (Not in Scope)

- Migrate test code to Quarkus test framework
- Add health checks (`quarkus-smallrye-health`)
- Add metrics (`quarkus-micrometer`)
- Configure OpenShift deployment (profile TODO in pom.xml)
- Update `audit-logging-library` to Quarkus-compatible version if needed
- Performance tuning and native compilation testing
