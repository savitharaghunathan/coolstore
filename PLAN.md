# PLAN.md

## Goal
Migrate a Java EE 7 WebLogic/WildFly monolith application to Quarkus 3, replacing EJB with CDI, JMS/MDB with SmallRye Reactive Messaging, JNDI lookups with injection, WAR packaging with JAR, and WebLogic lifecycle listeners with Quarkus events.

- Reference used: javaee-to-quarkus skill (Java EE 7/8 to Quarkus 3 migration patterns)

## Project Summary
- Type: Maven WAR application (Java EE 7)
- Architecture: E-commerce monolith with models, persistence, services, REST endpoints
- Files affected: ~25 Java files, 2 config files (pom.xml, persistence.xml), 3 deletions
- Estimated complexity: **High**
- Hardest steps:
  1. Converting OrderServiceMDB and InventoryNotificationMDB to SmallRye Reactive Messaging (@Incoming)
  2. Replacing JNDI lookups in ShoppingCartService and InventoryNotificationMDB with direct CDI injection
  3. Converting WebLogic ApplicationLifecycleListener to Quarkus startup/shutdown events

## Steps

### Step 1: Restructure pom.xml for Quarkus
- File: pom.xml
- Action: MODIFY
- What to do:
  - Change packaging from `war` to `jar`
  - Set source/target from 1.8 to 17 (Quarkus 3 minimum)
  - Add Quarkus BOM (io.quarkus.platform:quarkus-bom:3.8.0)
  - Replace javaee-web-api + javaee-api with Quarkus extensions:
    - quarkus-resteasy-reactive-jackson (REST + JSON)
    - quarkus-hibernate-orm-panache (JPA)
    - quarkus-jdbc-postgresql (database)
    - quarkus-smallrye-reactive-messaging-amqp (messaging)
    - quarkus-arc (CDI)
    - quarkus-flyway (database migration)
  - Remove jboss-jms-api_2.0_spec and jboss-rmi-api_1.0_spec
  - Add quarkus-maven-plugin
  - Remove maven-war-plugin
  - Update audit-logging-library from system scope to install in local Maven repo
- Why: Quarkus uses a different runtime model (no app server, JAR packaging)
- Depends on: none
- Verify: `mvn clean compile` succeeds

### Step 2: Create application.properties
- File: src/main/resources/application.properties
- Action: CREATE
- What to do:
  - Create new file with datasource configuration:
    ```
    quarkus.datasource.db-kind=postgresql
    quarkus.datasource.username=coolstore
    quarkus.datasource.password=coolstore
    quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstoredb
    quarkus.hibernate-orm.database.generation=none
    quarkus.hibernate-orm.log.sql=false
    quarkus.flyway.migrate-at-start=true
    
    # Messaging
    mp.messaging.incoming.orders.connector=smallrye-amqp
    mp.messaging.incoming.orders.address=orders
    mp.messaging.outgoing.orders-out.connector=smallrye-amqp
    mp.messaging.outgoing.orders-out.address=orders
    
    # HTTP
    quarkus.http.port=8080
    ```
- Why: Quarkus uses application.properties instead of persistence.xml and standalone server config
- Depends on: Step 1
- Verify: File exists and is valid properties format

### Step 3: Delete persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Remove file entirely
- Why: Quarkus configures JPA via application.properties
- Depends on: Step 2
- Verify: `ls src/main/resources/META-INF/persistence.xml` returns "No such file"

### Step 4: Delete web.xml
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Remove file entirely
- Why: Quarkus doesn't use WAR deployment descriptors
- Depends on: Step 1
- Verify: `ls src/main/webapp/WEB-INF/web.xml` returns "No such file"

### Step 5: Migrate Resources.java EntityManager producer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Replace `javax.enterprise.context.Dependent` → `jakarta.enterprise.context.Dependent`
  - Replace `javax.enterprise.inject.Produces` → `jakarta.enterprise.inject.Produces`
  - Replace `javax.persistence.EntityManager` → `jakarta.persistence.EntityManager`
  - Replace `javax.persistence.PersistenceContext` → `jakarta.persistence.PersistenceContext`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 1
- Verify: No `javax.` imports remain in file

### Step 6: Migrate model entities (8 files)
- Files:
  - src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
  - src/main/java/com/redhat/coolstore/model/InventoryEntity.java
  - src/main/java/com/redhat/coolstore/model/Order.java
  - src/main/java/com/redhat/coolstore/model/OrderItem.java
  - src/main/java/com/redhat/coolstore/model/Product.java
  - src/main/java/com/redhat/coolstore/model/Promotion.java
  - src/main/java/com/redhat/coolstore/model/ShoppingCart.java
  - src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY (all files)
- What to do: For each file, replace all JPA imports:
  - `javax.persistence.*` → `jakarta.persistence.*`
  - Keep all annotations and code logic unchanged
- Why: Quarkus 3 uses Jakarta Persistence API
- Depends on: Step 1
- Verify: `grep -r "import javax.persistence" src/main/java/com/redhat/coolstore/model/` returns nothing

### Step 7: Migrate CatalogService from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless` → `import jakarta.enterprise.context.ApplicationScoped`
  - Replace `@Stateless` → `@ApplicationScoped`
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
  - Keep all business logic unchanged
- Why: Quarkus uses CDI managed beans instead of EJBs
- Depends on: Step 5, Step 6
- Verify: No `@Stateless` or `javax.ejb` imports remain

### Step 8: Migrate ProductService from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless` → `import jakarta.enterprise.context.ApplicationScoped`
  - Replace `@Stateless` → `@ApplicationScoped`
  - Replace all `javax.*` imports → `jakarta.*` equivalents
- Why: Quarkus uses CDI instead of EJBs
- Depends on: Step 7
- Verify: No EJB annotations remain

### Step 9: Migrate PromoService from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - Replace `@Stateless` → `@ApplicationScoped`
  - Replace all `javax.*` imports → `jakarta.*` equivalents
- Why: CDI bean conversion
- Depends on: Step 7
- Verify: No EJB annotations remain

### Step 10: Migrate OrderService from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace `@Stateless` → `@ApplicationScoped`
  - Replace all `javax.*` imports → `jakarta.*` equivalents
- Why: CDI bean conversion
- Depends on: Step 7
- Verify: No EJB annotations remain

### Step 11: COMPLEX — Convert ShoppingCartOrderProcessor from JMS to SmallRye Emitter
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: Uses `@Stateless`, `@Inject JMSContext`, `@Resource Topic`, and `context.createProducer().send()`
  - AFTER: Uses `@ApplicationScoped`, `@Inject @Channel("orders-out") Emitter<String>`, and `emitter.send()`
  - Specific changes:
    1. Remove: `import javax.ejb.Stateless`, `import javax.annotation.Resource`, `import javax.jms.*`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped`, `import org.eclipse.microprofile.reactive.messaging.Channel`, `import org.eclipse.microprofile.reactive.messaging.Emitter`
    3. Replace `@Stateless` → `@ApplicationScoped`
    4. Replace:
       ```java
       @Inject
       private transient JMSContext context;
       @Resource(lookup = "java:/topic/orders")
       private Topic ordersTopic;
       ```
       with:
       ```java
       @Inject
       @Channel("orders-out")
       Emitter<String> ordersEmitter;
       ```
    5. Replace `context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart))` 
       with `ordersEmitter.send(Transformers.shoppingCartToJson(cart))`
  - Related config: application.properties already has `mp.messaging.outgoing.orders-out` channel
- Why: Quarkus uses SmallRye Reactive Messaging instead of JMS API
- Depends on: Step 2
- Verify: No JMS imports remain, emitter field is injected

### Step 12: COMPLEX — Replace JNDI lookup in ShoppingCartService with direct injection
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: Uses `@Stateful`, JNDI lookup for ShippingServiceRemote via InitialContext
  - AFTER: Uses `@ApplicationScoped`, direct CDI injection of ShippingService
  - Specific changes:
    1. Replace `import javax.ejb.Stateful` → `import jakarta.enterprise.context.ApplicationScoped`
    2. Replace `@Stateful` → `@ApplicationScoped`
    3. Remove: `import javax.naming.*`, `import java.util.Hashtable`
    4. Remove entire `lookupShippingServiceRemote()` method
    5. Add injection at top of class:
       ```java
       @Inject
       ShippingService shippingService;
       ```
    6. Replace all calls to `lookupShippingServiceRemote().calculateShipping(sc)` 
       with `shippingService.calculateShipping(sc)`
    7. Replace all calls to `lookupShippingServiceRemote().calculateShippingInsurance(sc)` 
       with `shippingService.calculateShippingInsurance(sc)`
    8. Replace all `javax.*` imports → `jakarta.*` equivalents
  - Note: ShippingService must be changed from Remote EJB to regular @ApplicationScoped bean
- Why: Quarkus uses CDI injection, not JNDI lookups. @Stateful is incompatible with Quarkus programming model.
- Depends on: Step 13 (ShippingService conversion must happen first)
- Verify: No JNDI imports, no lookupShippingServiceRemote method exists

### Step 13: Migrate ShippingService from @Stateless Remote to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Replace `@Stateless` → `@ApplicationScoped`
  - Remove `@Remote` annotation and ShippingServiceRemote interface (if exists)
  - Replace all `javax.*` imports → `jakarta.*` equivalents
  - Change class to implement regular interface or none (remove Remote interface)
- Why: Quarkus doesn't support EJB Remote interfaces, uses local CDI injection
- Depends on: Step 7
- Verify: No `@Remote` or EJB annotations remain

### Step 14: COMPLEX — Convert OrderServiceMDB to SmallRye @Incoming
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: `@MessageDriven` with `activationConfig`, implements `MessageListener`, processes JMS `Message`
  - AFTER: Plain CDI bean with `@Incoming("orders")` method consuming String
  - Specific changes:
    1. Remove: `import javax.ejb.*`, `import javax.jms.*`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped`, `import org.eclipse.microprofile.reactive.messaging.Incoming`
    3. Remove `@MessageDriven` annotation entirely (with all activationConfig properties)
    4. Add `@ApplicationScoped` to class
    5. Remove `implements MessageListener`
    6. Replace `onMessage(Message rcvMessage)` signature with:
       ```java
       @Incoming("orders")
       public void processOrder(String orderStr) {
       ```
    7. Remove all JMS message unwrapping code:
       - Remove: `TextMessage msg = null;`, `if (rcvMessage instanceof TextMessage)`, `msg = (TextMessage) rcvMessage;`
       - Remove: `String orderStr = msg.getBody(String.class);`
       - Use method parameter `orderStr` directly
    8. Remove try/catch for JMSException, add general exception handling if needed
    9. Keep business logic (parsing order, saving, updating inventory)
    10. Replace all `javax.inject.Inject` → `jakarta.inject.Inject`
  - Related config: application.properties has `mp.messaging.incoming.orders` channel
- Why: Quarkus uses SmallRye Reactive Messaging with @Incoming instead of MDB
- Depends on: Step 2, Step 7, Step 10
- Verify: No `@MessageDriven` annotation, method has `@Incoming("orders")`, no JMS imports

### Step 15: COMPLEX — Convert InventoryNotificationMDB to SmallRye @Incoming and remove JNDI
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Implements `MessageListener`, uses JNDI InitialContext for WebLogic, manual Topic subscription
  - AFTER: `@ApplicationScoped` bean with `@Incoming("orders")` method
  - Specific changes:
    1. Remove: `import javax.jms.*`, `import javax.naming.*`, `import javax.rmi.PortableRemoteObject`, `import java.util.Hashtable`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped`, `import jakarta.inject.Inject`, `import org.eclipse.microprofile.reactive.messaging.Incoming`
    3. Add `@ApplicationScoped` to class
    4. Remove: All JNDI constants (JNDI_FACTORY, JMS_FACTORY, TOPIC)
    5. Remove: All JMS fields (tcon, tsession, tsubscriber)
    6. Remove: `init()` and `close()` methods entirely
    7. Remove: `getInitialContext()` method entirely
    8. Remove `implements MessageListener`
    9. Replace `onMessage(Message rcvMessage)` with:
       ```java
       @Incoming("orders")
       public void checkInventory(String orderStr) {
       ```
    10. Remove JMS message unwrapping code (same as Step 14)
    11. Keep business logic (checking inventory threshold)
    12. Ensure `@Inject CatalogService catalogService` is present
  - Related config: application.properties has `mp.messaging.incoming.orders` channel (same topic as OrderServiceMDB)
- Why: Quarkus uses SmallRye Reactive Messaging, no JNDI, no manual JMS setup
- Depends on: Step 2, Step 7
- Verify: No JNDI imports, no init/close methods, has `@Incoming("orders")`

### Step 16: Migrate REST endpoints (3 files)
- Files:
  - src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
  - src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
  - src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY (all files)
- What to do: For each file:
  - Replace all `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - Replace all `javax.inject.Inject` → `jakarta.inject.Inject`
  - Keep all JAX-RS annotations (@Path, @GET, @POST, etc.) unchanged
- Why: Quarkus 3 uses Jakarta REST API
- Depends on: Step 7-15 (services must be migrated first)
- Verify: `grep -r "import javax.ws.rs" src/main/java/com/redhat/coolstore/rest/` returns nothing

### Step 17: Migrate RestApplication
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ws.rs.ApplicationPath` → `import jakarta.ws.rs.ApplicationPath`
  - Replace `import javax.ws.rs.core.Application` → `import jakarta.ws.rs.core.Application`
- Why: Jakarta namespace migration
- Depends on: Step 16
- Verify: No `javax.ws.rs` imports remain

### Step 18: COMPLEX — Replace WebLogic lifecycle listener with Quarkus events
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - BEFORE: Extends `weblogic.application.ApplicationLifecycleListener` with `postStart()` and `preStop()` methods
  - AFTER: Plain CDI bean with `@Observes` methods for Quarkus lifecycle events
  - Specific changes:
    1. Remove: `import weblogic.application.*`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped`, `import jakarta.enterprise.event.Observes`, `import io.quarkus.runtime.StartupEvent`, `import io.quarkus.runtime.ShutdownEvent`
    3. Add `@ApplicationScoped` to class
    4. Remove: `extends ApplicationLifecycleListener`
    5. Replace `postStart(ApplicationLifecycleEvent evt)` with:
       ```java
       void onStart(@Observes StartupEvent evt) {
           log.info("AppListener(postStart)");
       }
       ```
    6. Replace `preStop(ApplicationLifecycleEvent evt)` with:
       ```java
       void onStop(@Observes ShutdownEvent evt) {
           log.info("AppListener(preStop)");
       }
       ```
    7. Replace `@Inject Logger log` with proper Jakarta inject
- Why: Quarkus uses CDI events instead of WebLogic lifecycle listeners
- Depends on: Step 1
- Verify: No weblogic imports, has @Observes StartupEvent and ShutdownEvent

### Step 19: Migrate DataBaseMigrationStartup
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - Replace `@Stateless` → `@ApplicationScoped`
  - Replace all `javax.*` imports → `jakarta.*` equivalents
  - If uses EJB `@Startup` → replace with Quarkus `@Observes StartupEvent` pattern
- Why: CDI bean conversion
- Depends on: Step 1
- Verify: No EJB annotations remain

### Step 20: Migrate utility classes
- Files: 
  - src/main/java/com/redhat/coolstore/utils/Transformers.java
  - Any other utility classes without EJB/JPA dependencies
- Action: MODIFY
- What to do:
  - Replace any remaining `javax.*` imports → `jakarta.*` equivalents
  - Usually just JSON-P or inject annotations
- Why: Namespace consistency
- Depends on: Step 1
- Verify: No `javax.` imports remain (except possibly javax.json if present)

### Step 21: Delete WebLogic stub classes
- Files:
  - src/main/java/weblogic/application/ApplicationLifecycleEvent.java
  - src/main/java/weblogic/application/ApplicationLifecycleListener.java
  - src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE (all files)
- What to do: Remove entire `src/main/java/weblogic/` directory
- Why: These are WebLogic stubs, not needed in Quarkus
- Depends on: Step 18 (lifecycle listener must be converted first)
- Verify: `ls src/main/java/weblogic/` returns "No such file or directory"

### Step 22: Migrate test files
- Files: All files in src/test/java/com/redhat/coolstore/
- Action: MODIFY
- What to do:
  - Replace all `javax.*` imports → `jakarta.*` equivalents
  - Update test setup if using Arquillian → switch to @QuarkusTest
  - Update H2 setup to work with Quarkus test profiles
- Why: Test framework alignment with Quarkus
- Depends on: Steps 1-21 (all source code migrated)
- Verify: `mvn test` succeeds

### Step 23: Update .gitignore
- File: .gitignore (create if doesn't exist)
- Action: CREATE or MODIFY
- What to do: Add Quarkus-specific entries:
  ```
  target/
  .quarkus/
  quarkus.log
  ```
- Why: Quarkus generates different build artifacts
- Depends on: none
- Verify: File exists with entries

### Step 24: Create README update section
- File: README.md
- Action: MODIFY
- What to do: Add section documenting Quarkus migration:
  - How to run: `mvn quarkus:dev`
  - How to build: `mvn clean package`
  - How to run native: `mvn clean package -Pnative`
  - Configuration: point to application.properties
  - Required services: PostgreSQL, AMQP broker (Artemis/RabbitMQ)
- Why: Documentation for developers
- Depends on: none
- Verify: README has Quarkus usage section

### Step 25: Final verification build
- File: N/A (build verification)
- Action: N/A
- What to do: Run complete build with tests
- Why: Ensure all migrations are correct
- Depends on: Steps 1-24
- Verify: `mvn clean package` succeeds without errors

## Verification

After all steps are complete, run:
```bash
# Full build with tests
mvn clean package

# Development mode (hot reload)
mvn quarkus:dev

# Verify no javax.* imports remain in Java EE APIs
grep -r "import javax.ejb\|import javax.jms\|import javax.persistence\|import javax.ws.rs" src/main/java/

# Check application starts
mvn quarkus:dev
# Then access http://localhost:8080/health to confirm startup
```

Expected outcomes:
- Build succeeds with no errors
- No `javax.ejb`, `javax.jms`, `javax.persistence`, or `javax.ws.rs` imports in source code
- Application starts in Quarkus dev mode
- All REST endpoints accessible at http://localhost:8080/api/*
- Messaging works with AMQP broker

## Notes

### Migration Complexity Points

1. **MDB Conversion (Steps 14, 15)**: Two message-driven beans need complete structural changes:
   - `OrderServiceMDB`: Straightforward conversion to @Incoming
   - `InventoryNotificationMDB`: More complex due to manual JNDI/JMS setup code that must be completely removed

2. **JNDI Removal (Steps 12, 15)**: Two instances of JNDI lookups:
   - `ShoppingCartService`: WildFly JNDI lookup for remote EJB → direct injection
   - `InventoryNotificationMDB`: WebLogic JNDI lookup for JMS → SmallRye Reactive Messaging

3. **ShoppingCartService Stateful→Stateless**: Original uses `@Stateful` with instance-scoped cart. In Quarkus, this becomes `@ApplicationScoped` with proper session management (likely HTTP session or explicit cart storage).

4. **Messaging Architecture**: Both MDBs listen to the same "orders" topic. In SmallRye, both can have `@Incoming("orders")` methods - they will both receive each message (fanout pattern maintained).

5. **Remote EJB Interface**: `ShippingService` with `ShippingServiceRemote` interface must be converted to local CDI bean. No remote invocation in Quarkus monolith.

6. **WebLogic Dependencies**: Complete removal of WebLogic-specific code:
   - Lifecycle listeners → Quarkus events
   - JNDI context factory → removed
   - Stub classes in `weblogic.*` package → deleted

### Dependencies Order

Critical path: Build config (Step 1) → App config (Step 2) → Models (Step 6) → Persistence producer (Step 5) → Services (Steps 7-13) → Messaging (Steps 11, 14, 15) → REST (Steps 16-17) → Lifecycle (Step 18) → Cleanup (Step 21)

The JNDI removal in ShoppingCartService (Step 12) MUST come after ShippingService conversion (Step 13) to ensure the injected service is available.

### Gotchas

- **audit-logging-library**: System-scoped dependency needs to be properly installed in local Maven repo or converted to a Quarkus extension
- **Flyway**: Already present, ensure migration scripts work with Quarkus Flyway extension
- **PostgreSQL vs H2**: persistence.xml uses JBoss datasource; application.properties should configure PostgreSQL (adjust if different DB needed)
- **AMQP Broker**: SmallRye Reactive Messaging needs external broker (Artemis, RabbitMQ). Configuration in application.properties must match deployment environment.
- **Native compilation**: If targeting native, audit-logging-library may need reflection configuration

### Testing Strategy

After migration:
1. Unit tests should pass with @QuarkusTest
2. Integration test with Quarkus Dev Services (automatic containers for PostgreSQL, Artemis)
3. Verify message flow: Place order → OrderServiceMDB processes → InventoryNotificationMDB checks threshold
4. Verify REST endpoints work with same API contracts
5. Verify lifecycle events fire on startup/shutdown
