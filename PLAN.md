# PLAN.md

## Goal
Migrate Java EE 7 application from WebLogic/JBoss to Quarkus 3, converting EJB/JMS/MDB patterns to CDI/SmallRye Reactive Messaging
- Reference used: javaee-to-quarkus skill (modules: build-config, app-config, ejb-to-cdi, messaging, lifecycle, cleanup)

## Project Summary
- Type: Maven WAR (Java EE 7)
- Files affected: 30 Java files + build/config files
- Estimated complexity: Medium-High
- Hardest steps:
  1. COMPLEX — Convert InventoryNotificationMDB (WebLogic JNDI/JMS to Quarkus Reactive Messaging)
  2. COMPLEX — Convert OrderServiceMDB (@MessageDriven to @Incoming)
  3. COMPLEX — Replace WebLogic ApplicationLifecycleListener with Quarkus events
  4. Replace JMS producer in ShoppingCartOrderProcessor with Emitter

## Steps

### Step 1: Update pom.xml — Change packaging to JAR
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus produces standalone JAR files, not WAR files for app servers
- Depends on: none
- Verify: `grep '<packaging>jar</packaging>' pom.xml`

### Step 2: Update pom.xml — Add Quarkus BOM
- File: pom.xml
- Action: MODIFY
- What to do:
    - Add `<dependencyManagement>` section with Quarkus BOM 3.8.4
    - Insert after `<properties>` section, before `<dependencies>`
    ```xml
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.quarkus.platform</groupId>
                <artifactId>quarkus-bom</artifactId>
                <version>3.8.4</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    ```
- Why: Quarkus BOM manages all extension versions centrally
- Depends on: Step 1
- Verify: `grep 'quarkus-bom' pom.xml`

### Step 3: Update pom.xml — Replace Java EE dependencies with Quarkus extensions
- File: pom.xml
- Action: MODIFY
- What to do:
    - REMOVE: `javax:javaee-web-api`, `javax:javaee-api`, `org.jboss.spec.javax.jms`, `org.jboss.spec.javax.rmi`
    - ADD (no version needed, managed by BOM):
        - `io.quarkus:quarkus-arc` (CDI)
        - `io.quarkus:quarkus-rest-jackson` (JAX-RS + JSON)
        - `io.quarkus:quarkus-hibernate-orm` (JPA)
        - `io.quarkus:quarkus-jdbc-h2` (H2 database)
        - `io.quarkus:quarkus-narayana-jta` (Transactions)
        - `io.quarkus:quarkus-smallrye-reactive-messaging-amqp` (Messaging - replaces JMS)
        - `io.quarkus:quarkus-flyway` (Database migration)
    - KEEP: `com.enterprise:audit-logging-library` (custom dependency)
    - UPDATE test dependencies to latest versions (JUnit 4.13.2, Mockito, H2)
- Why: Quarkus uses modular extensions instead of monolithic Java EE API
- Depends on: Step 2
- Verify: `grep 'quarkus-arc' pom.xml && ! grep 'javaee-api' pom.xml`

### Step 4: Update pom.xml — Replace maven-war-plugin with quarkus-maven-plugin
- File: pom.xml
- Action: MODIFY
- What to do:
    - REMOVE: `maven-war-plugin`
    - ADD in `<build><plugins>`:
    ```xml
    <plugin>
        <groupId>io.quarkus.platform</groupId>
        <artifactId>quarkus-maven-plugin</artifactId>
        <version>3.8.4</version>
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
    - UPDATE `maven-compiler-plugin`: change `<source>1.8</source>` and `<target>1.8</target>` to `<release>17</release>`
- Why: Quarkus requires its own build plugin; Java 17 is minimum for Quarkus 3
- Depends on: Step 3
- Verify: `grep 'quarkus-maven-plugin' pom.xml`

### Step 5: Create application.properties
- File: src/main/resources/application.properties
- Action: CREATE
- What to do:
    ```properties
    # Datasource configuration (replaces persistence.xml JNDI lookup)
    quarkus.datasource.db-kind=h2
    quarkus.datasource.jdbc.url=jdbc:h2:mem:coolstore;DB_CLOSE_DELAY=-1
    quarkus.datasource.username=sa
    quarkus.datasource.password=

    # Hibernate ORM configuration
    quarkus.hibernate-orm.database.generation=none
    quarkus.hibernate-orm.log.sql=false
    quarkus.hibernate-orm.sql-load-script=no-file

    # Flyway configuration
    quarkus.flyway.migrate-at-start=true

    # AMQP/JMS configuration (replaces topic/orders JNDI lookup)
    mp.messaging.outgoing.orders-out.connector=smallrye-amqp
    mp.messaging.outgoing.orders-out.address=orders
    mp.messaging.outgoing.orders-out.durable=true

    mp.messaging.incoming.order-queue.connector=smallrye-amqp
    mp.messaging.incoming.order-queue.address=orders
    mp.messaging.incoming.order-queue.durable=true

    mp.messaging.incoming.inventory-queue.connector=smallrye-amqp
    mp.messaging.incoming.inventory-queue.address=orders
    mp.messaging.incoming.inventory-queue.durable=true

    # Audit logging directory
    audit.log.directory=./device-inventory-audit-logs
    ```
- Why: Quarkus uses application.properties instead of XML configuration files
- Depends on: Step 4
- Verify: `test -f src/main/resources/application.properties`

### Step 6: Delete persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Remove the file (configuration moved to application.properties)
- Why: Quarkus configures JPA via application.properties
- Depends on: Step 5
- Verify: `! test -f src/main/resources/META-INF/persistence.xml`

### Step 7: Delete web.xml
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Remove the file (not needed in Quarkus)
- Why: Quarkus doesn't use web.xml; JAX-RS configuration is automatic
- Depends on: Step 5
- Verify: `! test -f src/main/webapp/WEB-INF/web.xml`

### Step 8: Delete beans.xml
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Remove the file (CDI is enabled by default in Quarkus)
- Why: Quarkus Arc enables CDI automatically without beans.xml
- Depends on: Step 5
- Verify: `! test -f src/main/webapp/WEB-INF/beans.xml`

### Step 9: Migrate Resources.java — Replace @Produces EntityManager
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
    - REMOVE: `@PersistenceContext` annotation
    - REMOVE: `@Produces` method
    - The file can be deleted entirely (EntityManager is auto-injected in Quarkus)
    - Or simplify to just a CDI producer for Logger if needed elsewhere
- Why: Quarkus auto-provides EntityManager injection without custom @Produces
- Depends on: Step 5
- Verify: `! grep '@PersistenceContext' src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 10: Migrate RestApplication.java — Update JAX-RS imports
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
    - Replace `javax.ws.rs.ApplicationPath` → `jakarta.ws.rs.ApplicationPath`
    - Replace `javax.ws.rs.core.Application` → `jakarta.ws.rs.core.Application`
- Why: Jakarta EE namespace migration (javax → jakarta)
- Depends on: Step 5
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/RestApplication.java`

### Step 11: Migrate model entities — Update JPA imports (8 files)
- File: src/main/java/com/redhat/coolstore/model/*.java (CatalogItemEntity, InventoryEntity, Order, OrderItem, Product, Promotion, ShoppingCart, ShoppingCartItem)
- Action: MODIFY
- What to do: In all entity files, replace:
    - `javax.persistence.*` → `jakarta.persistence.*`
    - Keep all annotations the same (@Entity, @Id, @Column, etc.)
- Why: Jakarta EE namespace migration
- Depends on: Step 5
- Verify: `! grep 'javax.persistence' src/main/java/com/redhat/coolstore/model/*.java`

### Step 12: Migrate CatalogService.java — EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
    - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
    - Replace `javax.persistence.EntityManager` → `jakarta.persistence.EntityManager`
    - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
    - Update all other javax imports → jakarta
- Why: EJB @Stateless → CDI @ApplicationScoped; namespace migration
- Depends on: Step 11
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 13: Migrate OrderService.java — EJB to CDI with @Transactional
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
    - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
    - ADD: `@jakarta.transaction.Transactional` on class level
    - Replace `javax.persistence.EntityManager` → `jakarta.persistence.EntityManager`
    - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
    - Replace `javax.annotation.PostConstruct` → `jakarta.annotation.PostConstruct`
    - Replace `javax.annotation.PreDestroy` → `jakarta.annotation.PreDestroy`
    - Update audit logger configuration to use `@ConfigProperty(name = "audit.log.directory")`
- Why: EJB @Stateless provides automatic transactions; need explicit @Transactional in CDI
- Depends on: Step 11
- Verify: `grep '@Transactional' src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 14: Migrate ProductService.java — EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
    - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
    - Update all javax imports → jakarta
- Why: EJB to CDI migration
- Depends on: Step 11
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 15: Migrate PromoService.java — EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
    - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
    - Update all javax imports → jakarta
- Why: EJB to CDI migration
- Depends on: Step 11
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/PromoService.java`

### Step 16: Migrate ShippingService.java — EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
    - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
    - Update all javax imports → jakarta
- Why: EJB to CDI migration
- Depends on: Step 11
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 17: Delete ShippingServiceRemote.java
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Remove the @Remote interface (no longer needed in Quarkus)
- Why: Remote EJB interfaces are Java EE concepts; Quarkus uses direct CDI injection
- Depends on: Step 16
- Verify: `! test -f src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java`

### Step 18: Migrate ShoppingCartService.java — EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
    - Replace `javax.ejb.Stateful` → `jakarta.enterprise.context.ApplicationScoped`
    - Update all javax imports → jakarta
    - Update any @EJB injections → @Inject
- Why: EJB to CDI migration
- Depends on: Step 11
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 19: COMPLEX — Convert ShoppingCartOrderProcessor.java — JMS producer to Emitter
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
    - BEFORE:
    ```java
    @Stateless
    public class ShoppingCartOrderProcessor {
        @Inject
        private transient JMSContext context;
        
        @Resource(lookup = "java:/topic/orders")
        private Topic ordersTopic;
        
        public void process(ShoppingCart cart) {
            context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));
        }
    }
    ```
    - AFTER:
    ```java
    @ApplicationScoped
    public class ShoppingCartOrderProcessor {
        @Inject
        Logger log;
        
        @Inject
        @Channel("orders-out")
        Emitter<String> ordersEmitter;
        
        public void process(ShoppingCart cart) {
            log.info("Sending order from processor");
            ordersEmitter.send(Transformers.shoppingCartToJson(cart));
        }
    }
    ```
    - Replace imports:
        - REMOVE: `javax.ejb.Stateless`, `javax.annotation.Resource`, `javax.jms.*`
        - ADD: `jakarta.enterprise.context.ApplicationScoped`, `jakarta.inject.Inject`, `org.eclipse.microprofile.reactive.messaging.Channel`, `org.eclipse.microprofile.reactive.messaging.Emitter`
- Why: Quarkus uses SmallRye Reactive Messaging Emitter instead of JMS producer API
- Depends on: Step 5
- Verify: `grep '@Channel("orders-out")' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 20: COMPLEX — Convert OrderServiceMDB.java — @MessageDriven to @Incoming
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
    - BEFORE:
    ```java
    @MessageDriven(name = "OrderServiceMDB", activationConfig = {...})
    public class OrderServiceMDB implements MessageListener {
        @Inject OrderService orderService;
        @Inject CatalogService catalogService;
        
        @Override
        public void onMessage(Message rcvMessage) {
            TextMessage msg = (TextMessage) rcvMessage;
            String orderStr = msg.getBody(String.class);
            Order order = Transformers.jsonToOrder(orderStr);
            orderService.save(order);
            order.getItemList().forEach(orderItem -> {
                catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
            });
        }
    }
    ```
    - AFTER:
    ```java
    @ApplicationScoped
    public class OrderServiceMDB {
        @Inject OrderService orderService;
        @Inject CatalogService catalogService;
        
        @Incoming("order-queue")
        @Transactional
        public void onMessage(String orderStr) {
            System.out.println("\nMessage recd !");
            System.out.println("Received order: " + orderStr);
            Order order = Transformers.jsonToOrder(orderStr);
            System.out.println("Order object is " + order);
            orderService.save(order);
            order.getItemList().forEach(orderItem -> {
                catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
            });
        }
    }
    ```
    - Replace imports:
        - REMOVE: `javax.ejb.*`, `javax.jms.*`
        - ADD: `jakarta.enterprise.context.ApplicationScoped`, `jakarta.inject.Inject`, `jakarta.transaction.Transactional`, `org.eclipse.microprofile.reactive.messaging.Incoming`
- Why: Quarkus uses @Incoming with reactive messaging instead of @MessageDriven MDB
- Depends on: Step 5, Step 13
- Verify: `grep '@Incoming("order-queue")' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 21: COMPLEX — Convert InventoryNotificationMDB.java — WebLogic JNDI/JMS to Reactive Messaging
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
    - BEFORE: Manual JNDI lookup with WebLogic-specific context factory, manual JMS topic subscription
    - AFTER:
    ```java
    @ApplicationScoped
    public class InventoryNotificationMDB {
        private static final int LOW_THRESHOLD = 50;
        
        @Inject
        private CatalogService catalogService;
        
        @Incoming("inventory-queue")
        public void onMessage(String orderStr) {
            try {
                System.out.println("received message inventory");
                Order order = Transformers.jsonToOrder(orderStr);
                order.getItemList().forEach(orderItem -> {
                    int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId()).getInventory().getQuantity();
                    int new_quantity = old_quantity - orderItem.getQuantity();
                    if (new_quantity < LOW_THRESHOLD) {
                        System.out.println("Inventory for item " + orderItem.getProductId() + " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
                    } else {
                        orderItem.setQuantity(new_quantity);
                    }
                });
            } catch (Exception e) {
                System.err.println("An exception occurred: " + e.getMessage());
            }
        }
    }
    ```
    - REMOVE all: JNDI lookups, WebLogic context factory, manual JMS connections, init/close methods
    - Replace imports:
        - REMOVE: `javax.inject.Inject`, `javax.jms.*`, `javax.naming.*`, `javax.rmi.*`
        - ADD: `jakarta.enterprise.context.ApplicationScoped`, `jakarta.inject.Inject`, `org.eclipse.microprofile.reactive.messaging.Incoming`
- Why: Quarkus completely removes JNDI and manual JMS setup; uses declarative @Incoming
- Depends on: Step 5, Step 12
- Verify: `! grep 'InitialContext' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 22: COMPLEX — Replace StartupListener.java — WebLogic lifecycle to Quarkus events
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
    - BEFORE:
    ```java
    public class StartupListener extends ApplicationLifecycleListener {
        @Inject Logger log;
        
        @Override
        public void postStart(ApplicationLifecycleEvent evt) {
            log.info("AppListener(postStart)");
        }
        
        @Override
        public void preStop(ApplicationLifecycleEvent evt) {
            log.info("AppListener(preStop)");
        }
    }
    ```
    - AFTER:
    ```java
    @ApplicationScoped
    public class StartupListener {
        @Inject Logger log;
        
        void onStart(@Observes StartupEvent event) {
            log.info("AppListener(postStart)");
        }
        
        void onStop(@Observes ShutdownEvent event) {
            log.info("AppListener(preStop)");
        }
    }
    ```
    - Replace imports:
        - REMOVE: `weblogic.application.*`
        - ADD: `jakarta.enterprise.context.ApplicationScoped`, `jakarta.enterprise.event.Observes`, `io.quarkus.runtime.StartupEvent`, `io.quarkus.runtime.ShutdownEvent`
- Why: WebLogic ApplicationLifecycleListener is vendor-specific; Quarkus uses CDI observer pattern
- Depends on: Step 5
- Verify: `grep 'StartupEvent' src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Step 23: Delete WebLogic stub classes
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Remove WebLogic stub implementation
- Why: No longer needed after migrating to Quarkus lifecycle events
- Depends on: Step 22
- Verify: `! test -d src/main/java/weblogic`

### Step 24: Delete WebLogic stub classes
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Remove WebLogic stub implementation
- Why: No longer needed after migrating to Quarkus lifecycle events
- Depends on: Step 22
- Verify: `! test -d src/main/java/weblogic`

### Step 25: Delete WebLogic stub classes
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Remove WebLogic stub implementation
- Why: No longer needed; use standard java.util.logging.Logger
- Depends on: Step 22
- Verify: `! test -d src/main/java/weblogic`

### Step 26: Migrate REST endpoints — Update JAX-RS imports (CartEndpoint)
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
    - Replace `javax.enterprise.context.SessionScoped` → `jakarta.enterprise.context.SessionScoped`
    - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
    - Replace all `javax.ws.rs.*` → `jakarta.ws.rs.*`
- Why: Jakarta EE namespace migration
- Depends on: Step 10
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 27: Migrate REST endpoints — Update JAX-RS imports (OrderEndpoint)
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
    - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
    - Replace all `javax.ws.rs.*` → `jakarta.ws.rs.*`
- Why: Jakarta EE namespace migration
- Depends on: Step 10
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 28: Migrate REST endpoints — Update JAX-RS imports (ProductEndpoint)
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
    - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
    - Replace all `javax.ws.rs.*` → `jakarta.ws.rs.*`
- Why: Jakarta EE namespace migration
- Depends on: Step 10
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 29: Migrate utility classes — Update imports (Producers, Transformers, DataBaseMigrationStartup)
- File: src/main/java/com/redhat/coolstore/utils/*.java
- Action: MODIFY
- What to do: Replace all javax → jakarta imports in:
    - Producers.java
    - Transformers.java
    - DataBaseMigrationStartup.java
- Why: Jakarta EE namespace migration
- Depends on: Step 5
- Verify: `! grep 'javax.enterprise\|javax.inject' src/main/java/com/redhat/coolstore/utils/*.java`

### Step 30: Update .gitignore
- File: .gitignore
- Action: MODIFY
- What to do: Add Quarkus-specific build artifacts:
    ```
    target/
    graphify-out/
    .goose/
    **/device-inventory-audit-logs/
    ```
- Why: Exclude generated files from version control
- Depends on: none
- Verify: `grep 'graphify-out' .gitignore`

## Verification
After all steps are complete:
1. Build the project: `mvn clean compile`
2. Verify no javax.* Java EE imports remain: `! grep -r 'javax\.persistence\|javax\.ejb\|javax\.jms' src/main/java --include='*.java'`
3. Verify Quarkus extensions are present: `grep 'quarkus-arc\|quarkus-rest\|quarkus-hibernate-orm' pom.xml`
4. Verify messaging configuration: `grep 'mp.messaging' src/main/resources/application.properties`
5. Run tests: `mvn test`
6. Package the application: `mvn package` (produces a runnable JAR in target/)

## Notes

### Migration Complexity
- **WebLogic-specific code**: InventoryNotificationMDB uses WebLogic JNDI context factory and manual JMS setup. This requires complete rewrite to use Quarkus reactive messaging.
- **MDB conversions**: Two MDB classes need conversion from @MessageDriven to @Incoming pattern
- **JMS producer**: ShoppingCartOrderProcessor uses JMS API directly, needs Emitter pattern
- **Lifecycle hooks**: WebLogic ApplicationLifecycleListener is vendor-specific, needs Quarkus observer pattern

### Gotchas
1. **System-scoped dependency**: `audit-logging-library` uses `<scope>system</scope>` which is discouraged. Consider installing to local Maven repo or using a dependency management system.
2. **Flyway version**: Update to Flyway 9.x for Quarkus 3 compatibility if migration scripts exist.
3. **Reactive Messaging**: The application.properties assumes AMQP broker. If using different broker (Kafka, in-memory), adjust `mp.messaging.*` properties accordingly.
4. **EntityManager injection**: After removing Resources.java @Produces, ensure all services inject EntityManager with `@Inject` (Quarkus provides this automatically).
5. **Transaction boundaries**: EJB @Stateless provides automatic transactions. Add explicit `@Transactional` on service methods that modify data.
6. **Session scope**: CartEndpoint uses `@SessionScoped` which requires HTTP session tracking. Verify this works in Quarkus or consider alternative state management.

### Testing Strategy
- Unit tests should continue to work with minimal changes (update javax → jakarta imports)
- Integration tests may need updates to use Quarkus test framework (`@QuarkusTest`)
- Test with embedded AMQP broker or use `quarkus-smallrye-reactive-messaging-in-memory` for testing

### Deployment
- Quarkus produces: `target/monolith-1.0.0-SNAPSHOT-runner.jar`
- Run with: `java -jar target/monolith-1.0.0-SNAPSHOT-runner.jar`
- Or use dev mode during development: `mvn quarkus:dev`
- For containers: Use generated Dockerfile in `src/main/docker/`

### Post-Migration Optimizations (Optional)
1. Consider Panache for simplified JPA: Replace boilerplate DAO code
2. Use Quarkus OpenAPI extension for automatic REST API documentation
3. Add health checks: `quarkus-smallrye-health`
4. Add metrics: `quarkus-micrometer-registry-prometheus`
5. Native compilation: Build native executable with GraalVM for faster startup
