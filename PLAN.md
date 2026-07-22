# PLAN.md

## Goal
Migrate a Java EE 7 WAR application from WebLogic/JBoss to a standalone Quarkus 3 JAR application.

- Reference used: javaee-to-quarkus migration skill (modules: build-config, app-config, ejb-to-cdi, messaging, lifecycle, cleanup)
- Project: coolstore-monolith (30 Java files, ~1.6M words across 848 files)

## Project Summary
- Type: Maven WAR (Java EE 7)
- Target: Quarkus 3 JAR (Jakarta EE 10)
- Files affected: ~35 files (30 Java + 5 config/build files)
- Estimated complexity: **High**
- Hardest steps:
  1. **InventoryNotificationMDB** - Manual WebLogic JNDI/JMS code → SmallRye Reactive Messaging
  2. **JMS Producer refactoring** - @Resource JNDI lookups → Emitter pattern
  3. **Lifecycle listener migration** - WebLogic ApplicationLifecycleListener → Quarkus startup events

## Steps

### Phase 1: Build Configuration

#### Step 1: Update pom.xml packaging and properties
- File: pom.xml
- Action: MODIFY
- What to do:
  - Change packaging from `<packaging>war</packaging>` to `<packaging>jar</packaging>`
  - Update Java version from 1.8 to 17+ (add `<maven.compiler.source>17</maven.compiler.source>` and `<maven.compiler.target>17</maven.compiler.target>`)
  - Add Quarkus properties:
    ```xml
    <quarkus.platform.version>3.0.0.Final</quarkus.platform.version>
    <compiler-plugin.version>3.11.0</compiler-plugin.version>
    <surefire-plugin.version>3.0.0</surefire-plugin.version>
    ```
- Why: Quarkus applications are packaged as JAR (fast-jar or uber-jar), not WAR
- Depends on: none
- Verify: Check `<packaging>jar</packaging>` exists in pom.xml

#### Step 2: Add Quarkus BOM and remove Java EE dependencies
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add Quarkus BOM in `<dependencyManagement>`:
    ```xml
    <dependencyManagement>
      <dependencies>
        <dependency>
          <groupId>io.quarkus.platform</groupId>
          <artifactId>quarkus-bom</artifactId>
          <version>${quarkus.platform.version}</version>
          <type>pom</type>
          <scope>import</scope>
        </dependency>
      </dependencies>
    </dependencyManagement>
    ```
  - Remove these dependencies:
    - `javax:javaee-web-api:7.0`
    - `javax:javaee-api:7.0`
    - `org.jboss.spec.javax.jms:jboss-jms-api_2.0_spec`
    - `org.jboss.spec.javax.rmi:jboss-rmi-api_1.0_spec`
- Why: Replace Java EE APIs with Quarkus-managed Jakarta EE
- Depends on: Step 1
- Verify: `mvn dependency:tree` shows no javax.javaee dependencies

#### Step 3: Add Quarkus extensions
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add these Quarkus extension dependencies (remove `<version>` - managed by BOM):
    ```xml
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-hibernate-orm-panache</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-jdbc-h2</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-smallrye-reactive-messaging</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-smallrye-reactive-messaging-amqp</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-arc</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
      <version>9.16.3</version>
    </dependency>
    ```
  - Keep the system-scoped audit-logging-library dependency (will address in cleanup phase)
- Why: Quarkus extensions provide Jakarta EE APIs, JPA, REST, messaging
- Depends on: Step 2
- Verify: `mvn dependency:tree | grep quarkus` shows Quarkus extensions

#### Step 4: Replace Maven plugins
- File: pom.xml
- Action: MODIFY
- What to do:
  - Remove `maven-war-plugin`
  - Update `maven-compiler-plugin` to version 3.11.0
  - Add Quarkus Maven plugin:
    ```xml
    <plugin>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-maven-plugin</artifactId>
      <version>${quarkus.platform.version}</version>
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
- Why: Quarkus plugin handles JAR packaging and build process
- Depends on: Step 3
- Verify: `mvn clean compile` succeeds (may have Java errors - that's OK for now)

### Phase 2: Application Configuration

#### Step 5: Create application.properties
- File: src/main/resources/application.properties
- Action: CREATE
- What to do:
  - Create new file with datasource and JPA configuration from persistence.xml:
    ```properties
    # Datasource configuration
    quarkus.datasource.db-kind=h2
    quarkus.datasource.jdbc.url=jdbc:h2:mem:coolstore;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1
    quarkus.datasource.username=sa
    quarkus.datasource.password=
    
    # Hibernate configuration
    quarkus.hibernate-orm.database.generation=none
    quarkus.hibernate-orm.log.sql=false
    quarkus.hibernate-orm.log.format-sql=true
    quarkus.hibernate-orm.jdbc.statement-batch-size=0
    
    # Flyway configuration
    quarkus.flyway.migrate-at-start=true
    
    # Messaging configuration
    mp.messaging.incoming.orders.connector=smallrye-amqp
    mp.messaging.incoming.orders.address=orders
    mp.messaging.outgoing.orders-out.connector=smallrye-amqp
    mp.messaging.outgoing.orders-out.address=orders
    
    # AMQP broker configuration (replace with actual broker)
    amqp-host=localhost
    amqp-port=5672
    amqp-username=admin
    amqp-password=admin
    ```
- Why: Quarkus uses application.properties instead of persistence.xml and JNDI lookups
- Depends on: Step 4
- Verify: File exists and contains datasource config

#### Step 6: Mark persistence.xml for deletion (keep for reference during migration)
- File: src/main/resources/META-INF/persistence.xml
- Action: MODIFY (comment out, will delete in cleanup phase)
- What to do:
  - Rename to `persistence.xml.old` OR add XML comment:
    ```xml
    <!-- MIGRATED TO application.properties - DELETE AFTER VERIFICATION -->
    ```
- Why: Quarkus uses application.properties; keeping for reference during migration
- Depends on: Step 5
- Verify: Configuration moved to application.properties

#### Step 7: Mark web.xml for deletion
- File: src/main/webapp/WEB-INF/web.xml
- Action: MODIFY (will delete in cleanup phase)
- What to do:
  - Rename to `web.xml.old` OR add comment at top
- Why: Quarkus doesn't use web.xml (JAX-RS application auto-configured)
- Depends on: none
- Verify: File marked for deletion

#### Step 8: Mark beans.xml for deletion
- File: src/main/webapp/WEB-INF/beans.xml
- Action: MODIFY (will delete in cleanup phase)
- What to do:
  - Can be kept if using CDI discovery mode, but move to src/main/resources/META-INF/beans.xml
  - OR delete entirely (Quarkus has CDI enabled by default)
- Why: Quarkus auto-configures CDI
- Depends on: none
- Verify: CDI works without beans.xml or file moved to correct location

### Phase 3: EJB to CDI Migration

#### Step 9: Remove Resources.java @PersistenceContext producer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: DELETE
- What to do:
  - Delete entire file
  - In Quarkus, EntityManager is directly injectable without producer
- Why: Quarkus provides EntityManager injection by default
- Depends on: Step 5
- Verify: File deleted

#### Step 10: Convert CatalogService from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace imports:
    - Remove: `import javax.ejb.Stateless;`
    - Add: `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace annotations:
    - Remove: `@Stateless`
    - Add: `@ApplicationScoped`
  - Update all `javax.persistence.*` → `jakarta.persistence.*`
  - Update all `javax.inject.*` → `jakarta.inject.*`
- Why: Quarkus uses CDI beans instead of EJBs
- Depends on: Step 9
- Verify: `grep -n "@Stateless" src/main/java/com/redhat/coolstore/service/CatalogService.java` returns nothing

#### Step 11: Convert ProductService from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` → `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` → `@ApplicationScoped`
  - Update all `javax.*` imports to `jakarta.*`
- Why: Quarkus uses CDI beans instead of EJBs
- Depends on: Step 10
- Verify: No `javax.ejb` imports remain

#### Step 12: Convert OrderService from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` → `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` → `@ApplicationScoped`
  - Update all `javax.*` imports to `jakarta.*`
- Why: Quarkus uses CDI beans instead of EJBs
- Depends on: Step 10
- Verify: No `javax.ejb` imports remain

#### Step 13: COMPLEX - Convert ShippingService from @Stateless @Remote to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Remove `import javax.ejb.Remote;` and `import javax.ejb.Stateless;`
  - Add `import jakarta.enterprise.context.ApplicationScoped;`
  - Remove `@Stateless` and `@Remote` annotations
  - Add `@ApplicationScoped` annotation
  - Keep `implements ShippingServiceRemote` interface (will handle interface separately)
- Why: Quarkus doesn't support EJB remote interfaces; service will be local CDI bean
- Depends on: Step 10
- Verify: Class compiles with @ApplicationScoped

#### Step 14: Mark ShippingServiceRemote interface for deletion
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: MODIFY (will delete in cleanup phase)
- What to do:
  - Keep interface for now (will verify no remote callers exist)
  - If interface is only used locally, ShippingService can just be called directly
- Why: Quarkus doesn't support EJB Remote interfaces
- Depends on: Step 13
- Verify: Verify no external/remote callers exist

#### Step 15: Convert ShoppingCartService from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` → `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` → `@ApplicationScoped`
  - Update all `javax.*` imports to `jakarta.*`
- Why: Quarkus uses CDI beans instead of EJBs
- Depends on: Step 10
- Verify: No `javax.ejb` imports remain

### Phase 4: Messaging Migration

#### Step 16: COMPLEX - Convert ShoppingCartOrderProcessor JMS producer to SmallRye Emitter
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - **BEFORE**: JMS producer with @Resource JNDI lookup and JMSContext
    ```java
    @Inject
    private transient JMSContext context;
    
    @Resource(lookup = "java:/topic/orders")
    private Topic ordersTopic;
    
    public void process(ShoppingCart cart) {
        context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));
    }
    ```
  - **AFTER**: SmallRye Reactive Messaging Emitter
    ```java
    import org.eclipse.microprofile.reactive.messaging.Channel;
    import org.eclipse.microprofile.reactive.messaging.Emitter;
    
    @Inject
    @Channel("orders-out")
    Emitter<String> ordersEmitter;
    
    public void process(ShoppingCart cart) {
        log.info("Sending order from processor");
        ordersEmitter.send(Transformers.shoppingCartToJson(cart));
    }
    ```
  - Update imports:
    - Remove: `javax.jms.*`, `javax.annotation.Resource`
    - Add: `org.eclipse.microprofile.reactive.messaging.*`
  - Remove `@Stateless` annotation
  - Add `@ApplicationScoped`
  - Update `javax.*` → `jakarta.*` for remaining imports
- Why: Quarkus uses SmallRye Reactive Messaging instead of JMS API
- Depends on: Step 5 (messaging config in application.properties)
- Verify: `grep "@Resource" src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java` returns nothing

#### Step 17: COMPLEX - Convert OrderServiceMDB to SmallRye Reactive Messaging consumer
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - **BEFORE**: MessageDriven bean with MessageListener
    ```java
    @MessageDriven(name = "OrderServiceMDB", activationConfig = {...})
    public class OrderServiceMDB implements MessageListener {
        @Override
        public void onMessage(Message rcvMessage) {
            TextMessage msg = (TextMessage) rcvMessage;
            String orderStr = msg.getBody(String.class);
            // process...
        }
    }
    ```
  - **AFTER**: CDI bean with @Incoming method
    ```java
    import org.eclipse.microprofile.reactive.messaging.Incoming;
    import jakarta.enterprise.context.ApplicationScoped;
    
    @ApplicationScoped
    public class OrderServiceMDB {
        
        @Inject
        OrderService orderService;
        
        @Inject
        CatalogService catalogService;
        
        @Incoming("orders")
        public void onMessage(String orderStr) {
            System.out.println("Received order: " + orderStr);
            try {
                Order order = Transformers.jsonToOrder(orderStr);
                System.out.println("Order object is " + order);
                orderService.save(order);
                order.getItemList().forEach(orderItem -> {
                    catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    ```
  - Remove imports: `javax.ejb.*`, `javax.jms.*`
  - Add imports: `org.eclipse.microprofile.reactive.messaging.Incoming`, `jakarta.enterprise.context.ApplicationScoped`
  - Update remaining `javax.*` → `jakarta.*`
  - Remove `implements MessageListener`
  - Change method signature from `onMessage(Message rcvMessage)` to `onMessage(String orderStr)`
- Why: Quarkus uses SmallRye Reactive Messaging for async message processing
- Depends on: Step 5 (messaging config), Step 12 (OrderService converted)
- Verify: `grep "@MessageDriven" src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java` returns nothing

#### Step 18: COMPLEX - Convert InventoryNotificationMDB from WebLogic JNDI to SmallRye Reactive Messaging
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - **BEFORE**: Manual WebLogic JNDI lookup with JMS APIs
    ```java
    public class InventoryNotificationMDB implements MessageListener {
        private final static String JNDI_FACTORY = "weblogic.jndi.WLInitialContextFactory";
        // manual JNDI setup, topic connection, session...
        public void onMessage(Message rcvMessage) {
            TextMessage msg = (TextMessage) rcvMessage;
            // process...
        }
        public void init() throws NamingException, JMSException {
            // manual JNDI lookup and JMS setup
        }
    }
    ```
  - **AFTER**: SmallRye Reactive Messaging consumer
    ```java
    import org.eclipse.microprofile.reactive.messaging.Incoming;
    import jakarta.enterprise.context.ApplicationScoped;
    
    @ApplicationScoped
    public class InventoryNotificationMDB {
        
        private static final int LOW_THRESHOLD = 50;
        
        @Inject
        private CatalogService catalogService;
        
        @Incoming("orders")
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
  - Remove ALL WebLogic/JNDI code:
    - Remove imports: `javax.naming.*`, `javax.rmi.*`, `javax.jms.*`, `java.util.Hashtable`
    - Remove: JNDI_FACTORY, JMS_FACTORY, TOPIC constants
    - Remove: tcon, tsession, tsubscriber fields
    - Remove: init() and close() methods
    - Remove: getInitialContext() method
    - Remove: `implements MessageListener`
  - Add imports: `org.eclipse.microprofile.reactive.messaging.Incoming`, `jakarta.enterprise.context.ApplicationScoped`
  - Add: `@ApplicationScoped` annotation
  - Change method signature to accept `String orderStr` directly
  - Update remaining `javax.*` → `jakarta.*`
  
  **NOTE**: Both OrderServiceMDB and InventoryNotificationMDB listen to same channel "orders" - this creates two consumers on same topic (broadcast pattern)
- Why: Removes WebLogic-specific JNDI code, modernizes to SmallRye Reactive Messaging
- Depends on: Step 5 (messaging config), Step 10 (CatalogService converted)
- Verify: `grep -E "weblogic|JNDI|InitialContext" src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java` returns nothing

### Phase 5: Lifecycle Migration

#### Step 19: COMPLEX - Convert StartupListener from WebLogic lifecycle to Quarkus events
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - **BEFORE**: WebLogic ApplicationLifecycleListener
    ```java
    import weblogic.application.ApplicationLifecycleEvent;
    import weblogic.application.ApplicationLifecycleListener;
    
    public class StartupListener extends ApplicationLifecycleListener {
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
  - **AFTER**: Quarkus lifecycle events
    ```java
    import io.quarkus.runtime.ShutdownEvent;
    import io.quarkus.runtime.StartupEvent;
    import jakarta.enterprise.context.ApplicationScoped;
    import jakarta.enterprise.event.Observes;
    import jakarta.inject.Inject;
    import java.util.logging.Logger;
    
    @ApplicationScoped
    public class StartupListener {
        
        @Inject
        Logger log;
        
        void onStart(@Observes StartupEvent event) {
            log.info("AppListener(postStart)");
        }
        
        void onStop(@Observes ShutdownEvent event) {
            log.info("AppListener(preStop)");
        }
    }
    ```
  - Remove: `extends ApplicationLifecycleListener`, WebLogic imports
  - Add: `@ApplicationScoped`, Quarkus lifecycle imports
  - Convert methods to observer methods with `@Observes` parameter
  - Update `javax.inject.*` → `jakarta.inject.*`
- Why: Quarkus has its own lifecycle events, no WebLogic dependency
- Depends on: none
- Verify: `grep "weblogic" src/main/java/com/redhat/coolstore/utils/StartupListener.java` returns nothing

#### Step 20: Update DataBaseMigrationStartup lifecycle
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - If this class uses `@Singleton` + `@Startup` → keep but update imports to `jakarta.*`
  - If it uses WebLogic lifecycle hooks → convert like Step 19
  - Verify Flyway integration works with Quarkus (may need to use quarkus.flyway.migrate-at-start=true from Step 5)
- Why: Ensure database migration runs at startup
- Depends on: Step 5
- Verify: Application starts and Flyway migrations run

### Phase 6: REST Endpoints Migration

#### Step 21: Update CartEndpoint imports to Jakarta
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - Replace all `javax.inject.*` → `jakarta.inject.*`
  - Replace all `javax.enterprise.*` → `jakarta.enterprise.*`
  - No annotation changes needed (JAX-RS annotations same in Jakarta)
- Why: Quarkus 3 uses Jakarta EE namespaces
- Depends on: Step 15 (ShoppingCartService converted)
- Verify: `grep "import javax" src/main/java/com/redhat/coolstore/rest/CartEndpoint.java` returns nothing

#### Step 22: Update OrderEndpoint imports to Jakarta
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - Replace all `javax.inject.*` → `jakarta.inject.*`
- Why: Quarkus 3 uses Jakarta EE namespaces
- Depends on: Step 12 (OrderService converted)
- Verify: `grep "import javax" src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java` returns nothing

#### Step 23: Update ProductEndpoint imports to Jakarta
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - Replace all `javax.inject.*` → `jakarta.inject.*`
- Why: Quarkus 3 uses Jakarta EE namespaces
- Depends on: Step 11 (ProductService converted)
- Verify: `grep "import javax" src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java` returns nothing

#### Step 24: Update RestApplication class
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ws.rs.ApplicationPath;` → `import jakarta.ws.rs.ApplicationPath;`
  - Replace `import javax.ws.rs.core.Application;` → `import jakarta.ws.rs.core.Application;`
  - Keep `@ApplicationPath("/api")` annotation
- Why: Quarkus 3 uses Jakarta EE namespaces
- Depends on: none
- Verify: `grep "import javax" src/main/java/com/redhat/coolstore/rest/RestApplication.java` returns nothing

### Phase 7: Model/Entity Migration

#### Step 25: Update all entity classes to Jakarta persistence
- File: src/main/java/com/redhat/coolstore/model/*.java (7 files: CatalogItemEntity, InventoryEntity, Order, OrderItem, Product, Promotion, ShoppingCart, ShoppingCartItem)
- Action: MODIFY
- What to do:
  - For each entity file, replace all `javax.persistence.*` → `jakarta.persistence.*`
  - Files affected:
    - CatalogItemEntity.java
    - InventoryEntity.java
    - Order.java
    - OrderItem.java
    - Product.java
    - Promotion.java
    - ShoppingCart.java
    - ShoppingCartItem.java
  - No annotation changes needed, only package names
- Why: Quarkus 3 uses Jakarta Persistence
- Depends on: Step 5 (application.properties with Hibernate config)
- Verify: `grep -r "import javax.persistence" src/main/java/com/redhat/coolstore/model/` returns nothing

### Phase 8: Utilities Migration

#### Step 26: Update Producers utility class
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Replace all `javax.enterprise.*` → `jakarta.enterprise.*`
  - Replace all `javax.inject.*` → `jakarta.inject.*`
  - If producing Logger, keep as-is
- Why: Jakarta CDI namespace
- Depends on: none
- Verify: `grep "import javax" src/main/java/com/redhat/coolstore/utils/Producers.java` returns nothing

#### Step 27: Update Transformers utility class
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do:
  - Update any `javax.*` imports to `jakarta.*`
  - This is likely a utility class with JSON/object transformations - minimal changes
- Why: Consistent Jakarta namespaces
- Depends on: none
- Verify: `grep "import javax" src/main/java/com/redhat/coolstore/utils/Transformers.java` returns nothing

### Phase 9: Cleanup

#### Step 28: Delete WebLogic stub classes
- File: weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do:
  - Delete entire weblogic package (weblogic/application/ directory)
- Why: No longer needed after Step 19 migration
- Depends on: Step 19
- Verify: `find src -name "weblogic" -type d` returns nothing

#### Step 29: Delete WebLogic ApplicationLifecycleEvent
- File: weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do:
  - Delete file (part of weblogic package cleanup from Step 28)
- Why: No longer needed
- Depends on: Step 28
- Verify: Included in Step 28 verification

#### Step 30: Delete WebLogic NonCatalogLogger stub
- File: weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do:
  - Delete entire weblogic directory
- Why: No longer needed
- Depends on: Step 28
- Verify: `ls src/main/java/weblogic` returns "No such file or directory"

#### Step 31: Delete or comment out persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do:
  - Delete file entirely (configuration moved to application.properties in Step 5)
- Why: Quarkus uses application.properties
- Depends on: Step 5, Step 25 (all entities migrated)
- Verify: `ls src/main/resources/META-INF/persistence.xml` returns "No such file"

#### Step 32: Delete WEB-INF directory
- File: src/main/webapp/WEB-INF/
- Action: DELETE
- What to do:
  - Delete entire WEB-INF directory (contains web.xml and beans.xml)
- Why: Not needed for Quarkus JAR packaging
- Depends on: Step 7, Step 8
- Verify: `ls src/main/webapp/WEB-INF` returns "No such file or directory"

#### Step 33: Handle system-scoped audit-logging-library dependency
- File: pom.xml
- Action: MODIFY
- What to do:
  - Option A: Install to local Maven repo:
    ```bash
    mvn install:install-file -Dfile=lib/audit-logging-library-1.0.0.jar \
      -DgroupId=com.enterprise -DartifactId=audit-logging-library \
      -Dversion=1.0.0 -Dpackaging=jar
    ```
    Then change dependency from `<scope>system</scope>` to normal dependency
  - Option B: Keep as system scope (works but not recommended)
  - Remove `<systemPath>` if using Option A
- Why: System scope dependencies can cause issues with Quarkus packaging
- Depends on: none
- Verify: `mvn clean package` succeeds without warnings about system scope

#### Step 34: Delete ShippingServiceRemote interface (if not needed)
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE (conditional)
- What to do:
  - Verify ShippingService is only called locally (not remotely)
  - If so, delete interface and remove `implements ShippingServiceRemote` from ShippingService
  - If remote calls exist, refactor to REST API instead
- Why: Quarkus doesn't support EJB Remote
- Depends on: Step 13, Step 14
- Verify: `grep -r "ShippingServiceRemote" src` returns nothing (except the interface file itself before deletion)

#### Step 35: Verify no javax.* Java EE imports remain
- File: All Java files
- Action: VERIFY
- What to do:
  - Run: `grep -r "import javax\." src/main/java --include="*.java" | grep -v "javax.annotation.processing" | grep -v "javax.crypto" | grep -v "javax.net" | grep -v "javax.security.cert" | grep -v "javax.sql" | grep -v "javax.xml"`
  - Should return nothing (only non-EE javax packages allowed)
  - If any remain, update to jakarta.*
- Why: Ensure complete migration to Jakarta EE
- Depends on: Steps 10-27
- Verify: Command returns no Java EE javax imports

## Verification

After completing all steps, run these commands in order:

### Build Verification
```bash
# Clean build
mvn clean compile

# Full package
mvn clean package

# Verify JAR created (not WAR)
ls -lh target/*.jar
```

### Import Verification
```bash
# Verify no javax.* Java EE imports remain
grep -r "import javax\." src/main/java --include="*.java" | \
  grep -v "javax.annotation.processing" | \
  grep -v "javax.crypto" | \
  grep -v "javax.net" | \
  grep -v "javax.security" | \
  grep -v "javax.sql" | \
  grep -v "javax.xml"

# Should return nothing

# Verify Jakarta imports exist
grep -r "import jakarta\." src/main/java --include="*.java" | wc -l
# Should return > 0
```

### EJB Verification
```bash
# Verify no EJB annotations remain
grep -r "@Stateless\|@Stateful\|@MessageDriven\|@Remote\|@Local" \
  src/main/java --include="*.java"

# Should return nothing
```

### WebLogic Verification
```bash
# Verify no WebLogic code remains
grep -r "weblogic\|JNDI\|InitialContext" src/main/java --include="*.java"

# Should return nothing
```

### Run Application
```bash
# Start in dev mode
mvn quarkus:dev

# Check application starts on http://localhost:8080
# Check REST endpoints: http://localhost:8080/api/products
```

### Messaging Verification
```bash
# Ensure AMQP broker is running (Artemis, RabbitMQ, etc.)
# Test order creation triggers both MDB consumers
# Check logs for "Received order:" and "received message inventory"
```

## Notes

### Complex Migration Items
1. **InventoryNotificationMDB**: Most complex - manual WebLogic JNDI/JMS removed, replaced with SmallRye Reactive Messaging
2. **Two MDB consumers on same topic**: Both OrderServiceMDB and InventoryNotificationMDB listen to "orders" channel - this creates broadcast pattern where both receive all messages
3. **System scope dependency**: audit-logging-library should be installed to local Maven repo for better Quarkus compatibility

### Migration Order Rationale
- **Build first**: Infrastructure must be in place before code changes
- **Config second**: Application properties needed before runtime changes
- **EJB → CDI third**: Foundation beans before dependent services
- **Messaging fourth**: Depends on CDI beans being ready
- **Lifecycle fifth**: Depends on beans and messaging
- **REST sixth**: Depends on all service layers
- **Models seventh**: Can be done earlier but safer after service layer
- **Cleanup last**: Only after everything works

### Dependencies to Update
- Java 8 → 17 (minimum for Quarkus 3)
- Flyway 4.1.2 → 9.16.3 (Quarkus 3 compatibility)
- Maven compiler plugin → 3.11.0

### AMQP Broker Setup
- Quarkus SmallRye Reactive Messaging requires an AMQP broker (Artemis, RabbitMQ, etc.)
- Update application.properties with actual broker connection details
- For development: `docker run -it --rm -p 5672:5672 -p 8161:8161 apache/activemq-artemis:latest-alpine`

### Gotchas
- **JMS Topic → AMQP Address**: JNDI topic `java:/topic/orders` becomes AMQP address `orders`
- **Two consumers, one topic**: Both MDBs will receive all messages (broadcast)
- **EntityManager injection**: No producer needed in Quarkus, inject directly
- **Logger injection**: May need to update Producers.java to use Quarkus logger pattern
- **Packaging change**: WAR → JAR means no webapp directory in final artifact

### Post-Migration Recommendations
1. Add Quarkus tests (quarkus-junit5)
2. Configure health checks (quarkus-smallrye-health)
3. Add metrics (quarkus-micrometer)
4. Consider Panache for entity repositories
5. Review reactive vs blocking patterns
6. Add OpenAPI/Swagger documentation (quarkus-smallrye-openapi)
