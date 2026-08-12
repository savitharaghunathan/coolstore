# Migration Plan: CoolStore Monolith (Java EE 7 → Quarkus 3)

## Goal
Migrate the CoolStore Monolith application from Java EE 7/JBoss EAP 7.4 to Quarkus 3, removing WebLogic-specific code and converting EJB/JMS components to modern Jakarta EE/MicroProfile equivalents.

- **Reference used:** Java EE to Quarkus migration references (annotation-map.md, dependency-map.md, pattern-map.md, config-map.md)

## Project Summary
- **Type:** Maven WAR application
- **Files affected:** 54 files (30 Java sources, 4 config files, 1 pom.xml, 3 WebLogic files to delete, 16 new files to create)
- **Estimated complexity:** HIGH
- **Hardest steps:** 
  1. MDB conversion to reactive messaging (OrderServiceMDB, InventoryNotificationMDB)
  2. Removing Remote EJB lookup with JNDI in ShoppingCartService
  3. WebLogic lifecycle listener conversion (StartupListener)
  4. External audit library compatibility verification (audit-logging-library-1.0.0.jar)

## Migration Layer Order

Based on graph analysis, migration follows dependency order:

1. **Build Config** (pom.xml)
2. **App Config** (persistence.xml, application.properties, messaging config)
3. **Utils** (Producers, Transformers)
4. **Persistence** (Resources.java, EntityManager producer)
5. **Models** (8 entity/model classes - simple javax → jakarta)
6. **Services** (10 service classes - EJB to CDI conversion)
7. **REST** (4 REST endpoint classes)
8. **Complex Patterns** (MDBs, JNDI, lifecycle listeners)
9. **Cleanup** (delete WebLogic files, remove obsolete configs)
10. **Tests** (update test dependencies)

---

## Steps

### Step 1: Update Maven POM to Quarkus 3
- **File:** pom.xml
- **Action:** MODIFY
- **What to do:**
  1. Change packaging from `war` to `jar`
  2. Add Quarkus BOM to dependencyManagement:
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
  3. Remove Java EE dependencies:
     - `javax:javaee-web-api`
     - `javax:javaee-api`
     - `org.jboss.spec.javax.jms:jboss-jms-api_2.0_spec`
     - `org.jboss.spec.javax.rmi:jboss-rmi-api_1.0_spec`
  4. Add Quarkus extensions:
     - `io.quarkus:quarkus-arc` (CDI)
     - `io.quarkus:quarkus-rest` (JAX-RS)
     - `io.quarkus:quarkus-rest-jackson` (JSON)
     - `io.quarkus:quarkus-hibernate-orm` (JPA)
     - `io.quarkus:quarkus-jdbc-postgresql` (PostgreSQL)
     - `io.quarkus:quarkus-jdbc-h2` (for tests)
     - `io.quarkus:quarkus-narayana-jta` (transactions)
     - `io.quarkus:quarkus-flyway` (database migration)
     - `io.quarkus:quarkus-smallrye-reactive-messaging` (messaging core)
     - `io.quarkus:quarkus-messaging-kafka` OR `io.quarkus:quarkus-artemis-jms` (based on message broker choice)
     - `io.quarkus:quarkus-oidc` (for Keycloak integration)
  5. Update compiler plugin to Java 11+ (Quarkus 3 minimum):
     ```xml
     <source>11</source>
     <target>11</target>
     ```
  6. Remove maven-war-plugin
  7. Add quarkus-maven-plugin:
     ```xml
     <plugin>
       <groupId>io.quarkus</groupId>
       <artifactId>quarkus-maven-plugin</artifactId>
       <version>3.8.4</version>
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
  8. Update Flyway dependency from `org.flywaydb:flyway-core` to just use `io.quarkus:quarkus-flyway`
  9. Keep system-scoped audit-logging-library as-is initially (verify compatibility later)
  10. Update test dependencies:
      - Keep junit, mockito, h2
      - Remove `org.hibernate:hibernate-entitymanager`
      - Add `io.quarkus:quarkus-junit5` and `io.rest-assured:rest-assured`
- **Why:** Quarkus uses a BOM for dependency management and provides extensions instead of raw Java EE APIs
- **Depends on:** none
- **Verify:** `mvn dependency:tree` should show Quarkus dependencies, no javax.* namespace conflicts

### Step 2: Create application.properties
- **File:** src/main/resources/application.properties
- **Action:** CREATE
- **What to do:**
  Create with the following content:
  ```properties
  # Application name
  quarkus.application.name=coolstore-monolith
  
  # Datasource configuration (from persistence.xml)
  quarkus.datasource.db-kind=postgresql
  quarkus.datasource.username=postgresUser
  quarkus.datasource.password=postgresPW
  quarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5432/postgresDB
  
  # Hibernate configuration
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=false
  quarkus.hibernate-orm.log.format-sql=true
  
  # Flyway migration
  quarkus.flyway.migrate-at-start=true
  quarkus.flyway.baseline-on-migrate=true
  
  # Messaging configuration (for topic/orders)
  mp.messaging.outgoing.orders-out.connector=smallrye-kafka
  mp.messaging.outgoing.orders-out.topic=orders
  mp.messaging.incoming.orders-in.connector=smallrye-kafka
  mp.messaging.incoming.orders-in.topic=orders
  
  # OIDC/Keycloak configuration (from keycloak.json)
  quarkus.oidc.auth-server-url=http://127.0.0.1:8081/realms/eap
  quarkus.oidc.client-id=eap-app
  quarkus.oidc.credentials.secret=<from keycloak.json>
  quarkus.oidc.application-type=web-app
  
  # HTTP configuration
  quarkus.http.port=8080
  
  # Dev mode configuration
  %dev.quarkus.datasource.db-kind=h2
  %dev.quarkus.datasource.jdbc.url=jdbc:h2:mem:testdb
  %dev.quarkus.hibernate-orm.database.generation=drop-and-create
  %dev.quarkus.hibernate-orm.log.sql=true
  
  # Test configuration
  %test.quarkus.datasource.db-kind=h2
  %test.quarkus.datasource.jdbc.url=jdbc:h2:mem:testdb
  %test.quarkus.hibernate-orm.database.generation=drop-and-create
  ```
- **Why:** Quarkus uses application.properties instead of XML configuration files
- **Depends on:** Step 1
- **Verify:** File exists and follows Quarkus property naming conventions

### Step 3: Migrate imports in model/CatalogItemEntity.java
- **File:** src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- **Action:** MODIFY
- **What to do:** Replace all `javax.persistence.*` → `jakarta.persistence.*`
- **Why:** Quarkus 3 uses Jakarta EE namespace
- **Depends on:** Step 1
- **Verify:** `grep -r "import javax.persistence" src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java` returns nothing

### Step 4: Migrate imports in model/InventoryEntity.java
- **File:** src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- **Action:** MODIFY
- **What to do:** Replace all `javax.persistence.*` → `jakarta.persistence.*`
- **Why:** Quarkus 3 uses Jakarta EE namespace
- **Depends on:** Step 1
- **Verify:** No javax.persistence imports remain

### Step 5: Migrate imports in model/Order.java
- **File:** src/main/java/com/redhat/coolstore/model/Order.java
- **Action:** MODIFY
- **What to do:** Replace all `javax.persistence.*` → `jakarta.persistence.*`
- **Why:** Quarkus 3 uses Jakarta EE namespace
- **Depends on:** Step 1
- **Verify:** No javax.persistence imports remain

### Step 6: Migrate imports in model/OrderItem.java
- **File:** src/main/java/com/redhat/coolstore/model/OrderItem.java
- **Action:** MODIFY
- **What to do:** Replace all `javax.persistence.*` → `jakarta.persistence.*`
- **Why:** Quarkus 3 uses Jakarta EE namespace
- **Depends on:** Step 1
- **Verify:** No javax.persistence imports remain

### Step 7: Migrate imports in model/Product.java
- **File:** src/main/java/com/redhat/coolstore/model/Product.java
- **Action:** MODIFY
- **What to do:** Replace all `javax.*` → `jakarta.*` (if any JPA annotations present)
- **Why:** Quarkus 3 uses Jakarta EE namespace
- **Depends on:** Step 1
- **Verify:** No javax imports remain

### Step 8: Migrate imports in model/Promotion.java
- **File:** src/main/java/com/redhat/coolstore/model/Promotion.java
- **Action:** MODIFY
- **What to do:** Replace all `javax.*` → `jakarta.*` (if any)
- **Why:** Quarkus 3 uses Jakarta EE namespace
- **Depends on:** Step 1
- **Verify:** No javax imports remain

### Step 9: Migrate imports in model/ShoppingCart.java
- **File:** src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- **Action:** MODIFY
- **What to do:** Replace all `javax.*` → `jakarta.*` (if any)
- **Why:** Quarkus 3 uses Jakarta EE namespace
- **Depends on:** Step 1
- **Verify:** No javax imports remain

### Step 10: Migrate imports in model/ShoppingCartItem.java
- **File:** src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- **Action:** MODIFY
- **What to do:** Replace all `javax.*` → `jakarta.*` (if any)
- **Why:** Quarkus 3 uses Jakarta EE namespace
- **Depends on:** Step 1
- **Verify:** No javax imports remain

### Step 11: Update utils/Producers.java
- **File:** src/main/java/com/redhat/coolstore/utils/Producers.java
- **Action:** MODIFY
- **What to do:**
  1. Replace `javax.enterprise.inject.*` → `jakarta.enterprise.inject.*`
  2. Logger producer remains the same (standard Java logging)
- **Why:** CDI namespace change
- **Depends on:** Step 1
- **Verify:** `grep -c "import javax" src/main/java/com/redhat/coolstore/utils/Producers.java` returns 0

### Step 12: Update utils/Transformers.java
- **File:** src/main/java/com/redhat/coolstore/utils/Transformers.java
- **Action:** MODIFY
- **What to do:** Replace any `javax.*` → `jakarta.*` (likely none, but verify JSON-P imports)
- **Why:** Jakarta namespace
- **Depends on:** Step 1
- **Verify:** No javax imports remain

### Step 13: COMPLEX - Convert persistence/Resources.java
- **File:** src/main/java/com/redhat/coolstore/persistence/Resources.java
- **Action:** MODIFY
- **What to do:**
  - BEFORE:
    ```java
    @Dependent
    public class Resources {
        @PersistenceContext
        private EntityManager em;
        
        @Produces
        public EntityManager getEntityManager() {
            return em;
        }
    }
    ```
  - AFTER:
    ```java
    @ApplicationScoped
    public class Resources {
        @Inject
        EntityManager em;
        
        @Produces
        public EntityManager getEntityManager() {
            return em;
        }
    }
    ```
  - Specific changes:
    1. Replace `javax.enterprise.context.Dependent` → `jakarta.enterprise.context.ApplicationScoped`
    2. Replace `javax.persistence.*` → `jakarta.persistence.*`
    3. Replace `@PersistenceContext` → `@Inject` (Quarkus manages EntityManager lifecycle)
    4. Change scope from `@Dependent` to `@ApplicationScoped`
- **Why:** Quarkus injects EntityManager directly, no need for @PersistenceContext
- **Depends on:** Step 1
- **Verify:** File compiles without errors; EntityManager injection works

### Step 14: Convert service/PromoService.java (EJB to CDI)
- **File:** src/main/java/com/redhat/coolstore/service/PromoService.java
- **Action:** MODIFY
- **What to do:**
  1. Replace `javax.enterprise.context.ApplicationScoped` → `jakarta.enterprise.context.ApplicationScoped`
  2. Already uses @ApplicationScoped, so only namespace change needed
  3. No EJB annotations present
- **Why:** Jakarta namespace
- **Depends on:** Step 1
- **Verify:** No javax imports remain

### Step 15: Convert service/ProductService.java (EJB to CDI)
- **File:** src/main/java/com/redhat/coolstore/service/ProductService.java
- **Action:** MODIFY
- **What to do:**
  1. Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  2. Replace `javax.persistence.*` → `jakarta.persistence.*`
  3. Add `@ApplicationScoped` if not already present
  4. No EJB-specific code expected
- **Why:** Jakarta namespace
- **Depends on:** Step 1, Step 13
- **Verify:** No javax imports remain

### Step 16: Convert service/CatalogService.java (EJB to CDI)
- **File:** src/main/java/com/redhat/coolstore/service/CatalogService.java
- **Action:** MODIFY
- **What to do:**
  1. Replace `@Stateless` → `@ApplicationScoped`
  2. Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  3. Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  4. Replace `javax.persistence.*` → `jakarta.persistence.*`
- **Why:** Quarkus uses CDI instead of EJB
- **Depends on:** Step 1, Step 13
- **Verify:** Compiles without errors; no @Stateless annotation remains

### Step 17: Convert service/OrderService.java (EJB to CDI)
- **File:** src/main/java/com/redhat/coolstore/service/OrderService.java
- **Action:** MODIFY
- **What to do:**
  1. Replace `@Stateless` → `@ApplicationScoped`
  2. Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  3. Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  4. Replace `javax.persistence.*` → `jakarta.persistence.*`
  5. Replace `javax.annotation.PostConstruct` → `jakarta.annotation.PostConstruct`
  6. Replace `javax.annotation.PreDestroy` → `jakarta.annotation.PreDestroy`
  7. **NOTE:** External audit-logging-library may need verification for Jakarta compatibility
- **Why:** Quarkus uses CDI instead of EJB
- **Depends on:** Step 1, Step 13
- **Verify:** Compiles; verify audit library compatibility at runtime

### Step 18: COMPLEX - Convert service/ShippingService.java (Remove @Remote)
- **File:** src/main/java/com/redhat/coolstore/service/ShippingService.java
- **Action:** MODIFY
- **What to do:**
  - BEFORE:
    ```java
    @Stateless
    @Remote
    public class ShippingService implements ShippingServiceRemote { ... }
    ```
  - AFTER:
    ```java
    @ApplicationScoped
    public class ShippingService implements ShippingServiceRemote { ... }
    ```
  - Specific changes:
    1. Replace `@Stateless` → `@ApplicationScoped`
    2. Remove `@Remote` annotation (no remote EJB in Quarkus)
    3. Remove `javax.ejb.*` imports
    4. Add `jakarta.enterprise.context.ApplicationScoped`
    5. Keep ShippingServiceRemote interface (just as marker interface)
- **Why:** Quarkus doesn't support remote EJB; local CDI injection only
- **Depends on:** Step 1
- **Verify:** Compiles without errors

### Step 19: Update service/ShippingServiceRemote.java
- **File:** src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- **Action:** MODIFY
- **What to do:** 
  1. Keep as plain Java interface (no @Remote)
  2. Remove any EJB-specific imports
  3. This becomes just a marker interface
- **Why:** No remote EJB in Quarkus
- **Depends on:** Step 18
- **Verify:** Interface compiles; used only locally

### Step 20: COMPLEX - Convert service/ShoppingCartService.java (Remove JNDI lookup)
- **File:** src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- **Action:** MODIFY
- **What to do:**
  - BEFORE:
    ```java
    @Stateful
    public class ShoppingCartService {
        ...
        private static ShippingServiceRemote lookupShippingServiceRemote() {
            final Hashtable<String, String> jndiProperties = new Hashtable<>();
            jndiProperties.put(Context.INITIAL_CONTEXT_FACTORY, "org.wildfly.naming.client.WildFlyInitialContextFactory");
            final Context context = new InitialContext(jndiProperties);
            return (ShippingServiceRemote) context.lookup("ejb:/ROOT/ShippingService!" + ShippingServiceRemote.class.getName());
        }
    }
    ```
  - AFTER:
    ```java
    @ApplicationScoped
    public class ShoppingCartService {
        @Inject
        ShippingService shippingService;
        
        // Remove lookupShippingServiceRemote() method
        // Replace all lookupShippingServiceRemote() calls with shippingService
    }
    ```
  - Specific changes:
    1. Replace `@Stateful` → `@ApplicationScoped`
    2. Replace `javax.ejb.Stateful` → `jakarta.enterprise.context.ApplicationScoped`
    3. Replace `javax.inject.Inject` → `jakarta.inject.Inject`
    4. Add `@Inject ShippingService shippingService;`
    5. Delete entire `lookupShippingServiceRemote()` method
    6. Replace `lookupShippingServiceRemote().calculateShipping(sc)` → `shippingService.calculateShipping(sc)`
    7. Replace `lookupShippingServiceRemote().calculateShippingInsurance(sc)` → `shippingService.calculateShippingInsurance(sc)`
    8. Remove javax.naming.* imports
    9. Remove Hashtable imports
    10. **Session state consideration:** @ApplicationScoped is shared; if per-user state needed, consider @RequestScoped
- **Why:** Quarkus uses CDI injection instead of JNDI lookups; no remote EJB
- **Depends on:** Step 1, Step 18
- **Verify:** Compiles; test checkout flow works correctly

### Step 21: Convert service/ShoppingCartOrderProcessor.java
- **File:** src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- **Action:** MODIFY
- **What to do:**
  1. Replace `@Stateless` → `@ApplicationScoped`
  2. Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  3. Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  4. Replace `javax.annotation.Resource` with reactive messaging pattern:
     - Remove `@Resource(lookup = "java:/topic/orders") private Topic ordersTopic;`
     - Remove `@Inject private transient JMSContext context;`
     - Add `@Inject @Channel("orders-out") Emitter<String> ordersEmitter;`
  5. Replace JMS send logic:
     - BEFORE: `context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));`
     - AFTER: `ordersEmitter.send(Transformers.shoppingCartToJson(cart));`
  6. Add imports:
     - `import io.smallrye.reactive.messaging.annotations.Channel;`
     - `import org.eclipse.microprofile.reactive.messaging.Emitter;`
  7. Remove javax.jms.* imports
- **Why:** Quarkus uses reactive messaging instead of JMS API
- **Depends on:** Step 1, Step 2
- **Verify:** Compiles; order messages sent to Kafka/AMQP topic

### Step 22: COMPLEX - Convert service/OrderServiceMDB.java (MDB to Reactive)
- **File:** src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- **Action:** MODIFY
- **What to do:**
  - BEFORE:
    ```java
    @MessageDriven(name = "OrderServiceMDB", activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "topic/orders"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")})
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
        
        @Incoming("orders-in")
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
  - Specific changes:
    1. Remove `@MessageDriven` and all activation config properties
    2. Remove `implements MessageListener`
    3. Add `@ApplicationScoped`
    4. Add `@Incoming("orders-in")` to onMessage method
    5. Add `@Transactional` for database operations
    6. Change method signature: `onMessage(Message rcvMessage)` → `onMessage(String orderStr)`
    7. Remove JMS Message handling code - direct String parameter
    8. Remove try-catch for JMSException
    9. Update imports:
       - Remove: `javax.ejb.*`, `javax.jms.*`
       - Add: `jakarta.enterprise.context.ApplicationScoped`
       - Add: `org.eclipse.microprofile.reactive.messaging.Incoming`
       - Add: `jakarta.transaction.Transactional`
- **Why:** Quarkus uses reactive messaging instead of JMS MDB
- **Depends on:** Step 1, Step 2, Step 16, Step 17
- **Verify:** Compiles; messages from orders topic processed correctly

### Step 23: COMPLEX - Convert service/InventoryNotificationMDB.java (Remove WebLogic JNDI)
- **File:** src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- **Action:** MODIFY
- **What to do:**
  - BEFORE: WebLogic-specific JNDI initialization with manual topic subscription
  - AFTER: Reactive messaging consumer
  - Specific changes:
    1. Remove all WebLogic JNDI constants and code:
       - `JNDI_FACTORY`, `JMS_FACTORY`, `TOPIC`, connection/session fields
       - `init()`, `close()`, `getInitialContext()` methods
    2. Add `@ApplicationScoped` class annotation
    3. Add `@Incoming("orders-in")` to onMessage method
    4. Change method signature: `onMessage(Message rcvMessage)` → `onMessage(String orderStr)`
    5. Remove JMS message handling, use direct String
    6. Keep inventory threshold logic unchanged
    7. Update imports:
       - Remove: `javax.jms.*`, `javax.naming.*`, `javax.rmi.*`, `java.util.Hashtable`
       - Add: `jakarta.enterprise.context.ApplicationScoped`
       - Add: `org.eclipse.microprofile.reactive.messaging.Incoming`
    8. Note: This creates a second consumer for the same topic - may need different consumer group config
  - AFTER:
    ```java
    @ApplicationScoped
    public class InventoryNotificationMDB {
        private static final int LOW_THRESHOLD = 50;
        
        @Inject
        private CatalogService catalogService;
        
        @Incoming("orders-in")
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
- **Why:** Remove WebLogic-specific JNDI code; use Quarkus reactive messaging
- **Depends on:** Step 1, Step 2, Step 16
- **Verify:** Compiles; inventory notifications work; configure separate consumer group if needed

### Step 24: COMPLEX - Convert utils/DataBaseMigrationStartup.java
- **File:** src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- **Action:** MODIFY
- **What to do:**
  - BEFORE:
    ```java
    @Singleton
    @Startup
    @TransactionManagement(TransactionManagementType.BEAN)
    public class DataBaseMigrationStartup {
        @Inject Logger logger;
        @Resource(mappedName = "java:jboss/datasources/CoolstoreDS")
        DataSource dataSource;
        
        @PostConstruct
        private void startup() {
            // Flyway migration code
        }
    }
    ```
  - AFTER:
    ```java
    @ApplicationScoped
    public class DataBaseMigrationStartup {
        @Inject Logger logger;
        
        void onStart(@Observes StartupEvent ev) {
            logger.info("Database migration will be handled by Quarkus Flyway extension");
            // Migration happens automatically via quarkus.flyway.migrate-at-start=true
        }
    }
    ```
  - Specific changes:
    1. Remove `@Singleton`, `@Startup`, `@TransactionManagement` annotations
    2. Add `@ApplicationScoped`
    3. Remove `@Resource DataSource` injection (not needed)
    4. Remove `@PostConstruct` method
    5. Add CDI startup observer: `void onStart(@Observes StartupEvent ev)`
    6. Remove manual Flyway code - Quarkus handles this via configuration
    7. Update imports:
       - Remove: `javax.ejb.*`, `javax.annotation.Resource`, `javax.sql.DataSource`, `org.flywaydb.core.*`
       - Add: `jakarta.enterprise.context.ApplicationScoped`
       - Add: `jakarta.enterprise.event.Observes`
       - Add: `io.quarkus.runtime.StartupEvent`
- **Why:** Quarkus Flyway extension handles migration automatically; no manual setup needed
- **Depends on:** Step 1, Step 2
- **Verify:** Application starts; Flyway migrations execute automatically; check logs

### Step 25: COMPLEX - Convert utils/StartupListener.java (Remove WebLogic lifecycle)
- **File:** src/main/java/com/redhat/coolstore/utils/StartupListener.java
- **Action:** MODIFY
- **What to do:**
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
  - Specific changes:
    1. Add `@ApplicationScoped` annotation
    2. Remove `extends ApplicationLifecycleListener`
    3. Replace `postStart(ApplicationLifecycleEvent)` → `onStart(@Observes StartupEvent)`
    4. Replace `preStop(ApplicationLifecycleEvent)` → `onStop(@Observes ShutdownEvent)`
    5. Update imports:
       - Remove: `weblogic.application.*`
       - Add: `jakarta.enterprise.context.ApplicationScoped`
       - Add: `jakarta.enterprise.event.Observes`
       - Add: `io.quarkus.runtime.StartupEvent`
       - Add: `io.quarkus.runtime.ShutdownEvent`
- **Why:** Quarkus uses CDI events instead of WebLogic lifecycle listeners
- **Depends on:** Step 1
- **Verify:** Startup/shutdown log messages appear correctly

### Step 26: Update rest/RestApplication.java
- **File:** src/main/java/com/redhat/coolstore/rest/RestApplication.java
- **Action:** MODIFY
- **What to do:**
  1. Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
  2. Keep `@ApplicationPath("/services")`
  3. Class remains mostly unchanged
- **Why:** Jakarta namespace change
- **Depends on:** Step 1
- **Verify:** No javax imports remain

### Step 27: Update rest/CartEndpoint.java
- **File:** src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- **Action:** MODIFY
- **What to do:**
  1. Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
  2. Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  3. Replace `@SessionScoped` → `@ApplicationScoped` or `@RequestScoped`
     - **Important:** Quarkus REST is stateless by default
     - If session state is needed, consider using a distributed cache or database-backed sessions
     - For now, change to `@ApplicationScoped` and accept that cart is not session-bound
  4. Replace `javax.enterprise.context.SessionScoped` → `jakarta.enterprise.context.ApplicationScoped`
- **Why:** Jakarta namespace; session management works differently in Quarkus
- **Depends on:** Step 1, Step 20
- **Verify:** Compiles; cart operations work (note: not session-scoped anymore)

### Step 28: Update rest/OrderEndpoint.java
- **File:** src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- **Action:** MODIFY
- **What to do:**
  1. Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
  2. Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  3. No EJB annotations expected
- **Why:** Jakarta namespace
- **Depends on:** Step 1, Step 17
- **Verify:** No javax imports remain

### Step 29: Update rest/ProductEndpoint.java
- **File:** src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- **Action:** MODIFY
- **What to do:**
  1. Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
  2. Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  3. No EJB annotations expected
- **Why:** Jakarta namespace
- **Depends on:** Step 1, Step 15
- **Verify:** No javax imports remain

### Step 30: Update persistence.xml
- **File:** src/main/resources/META-INF/persistence.xml
- **Action:** MODIFY
- **What to do:**
  1. Change namespace from `http://xmlns.jcp.org/xml/ns/persistence` to `https://jakarta.ee/xml/ns/persistence`
  2. Change version from `2.1` to `3.0`
  3. Remove `<jta-data-source>` (configured in application.properties)
  4. Remove all `<property>` elements (moved to application.properties)
  5. Minimal persistence.xml:
     ```xml
     <?xml version="1.0" encoding="UTF-8"?>
     <persistence version="3.0"
                  xmlns="https://jakarta.ee/xml/ns/persistence"
                  xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence 
                                      https://jakarta.ee/xml/ns/persistence/persistence_3_0.xsd">
         <persistence-unit name="primary" transaction-type="JTA">
         </persistence-unit>
     </persistence>
     ```
- **Why:** Quarkus manages datasource via application.properties; minimal persistence.xml needed
- **Depends on:** Step 2
- **Verify:** Application starts; EntityManager injection works

### Step 31: Update beans.xml
- **File:** src/main/webapp/WEB-INF/beans.xml
- **Action:** MODIFY
- **What to do:**
  1. Change namespace from `http://xmlns.jcp.org/xml/ns/javaee` to `https://jakarta.ee/xml/ns/jakartaee`
  2. Change schema location accordingly
  3. Keep `bean-discovery-mode="all"`
- **Why:** Jakarta namespace
- **Depends on:** Step 1
- **Verify:** File is valid XML with Jakarta namespace

### Step 32: Update web.xml (or remove)
- **File:** src/main/webapp/WEB-INF/web.xml
- **Action:** MODIFY or DELETE
- **What to do:**
  - Option 1: Delete the file entirely (Quarkus doesn't need web.xml)
  - Option 2: Update namespace if keeping:
    1. Change `http://java.sun.com/xml/ns/javaee` → `https://jakarta.ee/xml/ns/jakartaee`
    2. Change version to `5.0`
  - Recommendation: DELETE (only contains `<distributable />` which is not relevant to Quarkus)
- **Why:** Quarkus doesn't use web.xml for configuration
- **Depends on:** Step 1
- **Verify:** Application runs without web.xml

### Step 33: Configure messaging connector in application.properties
- **File:** src/main/resources/application.properties
- **Action:** MODIFY
- **What to do:**
  1. Add detailed messaging configuration based on chosen broker (Kafka or Artemis):
     
     **For Kafka:**
     ```properties
     # Outgoing channel (producer)
     mp.messaging.outgoing.orders-out.connector=smallrye-kafka
     mp.messaging.outgoing.orders-out.topic=orders
     mp.messaging.outgoing.orders-out.value.serializer=org.apache.kafka.common.serialization.StringSerializer
     
     # Incoming channel (consumers - both MDBs)
     mp.messaging.incoming.orders-in.connector=smallrye-kafka
     mp.messaging.incoming.orders-in.topic=orders
     mp.messaging.incoming.orders-in.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
     mp.messaging.incoming.orders-in.auto.offset.reset=earliest
     
     # Kafka connection
     kafka.bootstrap.servers=localhost:9092
     ```
     
     **OR for ActiveMQ Artemis (closer to original JMS):**
     ```properties
     # Outgoing channel
     mp.messaging.outgoing.orders-out.connector=smallrye-amqp
     mp.messaging.outgoing.orders-out.address=orders
     
     # Incoming channel
     mp.messaging.incoming.orders-in.connector=smallrye-amqp
     mp.messaging.incoming.orders-in.address=orders
     
     # AMQP connection
     amqp-host=localhost
     amqp-port=5672
     amqp-username=admin
     amqp-password=admin
     ```
  2. Note: Two consumers (OrderServiceMDB, InventoryNotificationMDB) on same topic will both receive messages
     - For Kafka: they share consumer group, so only one receives each message
     - For AMQP: configure separate queues or use durable=false for second consumer
- **Why:** Configure reactive messaging broker connection
- **Depends on:** Step 2, Step 21, Step 22, Step 23
- **Verify:** Messages flow correctly; both MDBs receive messages as expected

### Step 34: DELETE WebLogic ApplicationLifecycleEvent.java
- **File:** src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- **Action:** DELETE
- **What to do:** Remove the entire file
- **Why:** WebLogic-specific class no longer needed
- **Depends on:** Step 25
- **Verify:** File deleted; no references remain in codebase

### Step 35: DELETE WebLogic ApplicationLifecycleListener.java
- **File:** src/main/java/weblogic/application/ApplicationLifecycleListener.java
- **Action:** DELETE
- **What to do:** Remove the entire file
- **Why:** WebLogic-specific class no longer needed
- **Depends on:** Step 25
- **Verify:** File deleted; no references remain in codebase

### Step 36: DELETE WebLogic NonCatalogLogger.java
- **File:** src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- **Action:** DELETE
- **What to do:** Remove the entire file
- **Why:** WebLogic-specific logging class no longer needed
- **Depends on:** Step 1
- **Verify:** File deleted; no references remain in codebase; check if used anywhere

### Step 37: Verify external audit library compatibility
- **File:** lib/audit-logging-library-1.0.0.jar
- **Action:** VERIFY
- **What to do:**
  1. Test if the audit library works with Jakarta namespace
  2. Check if library has Jakarta-compatible version (audit-logging-library-2.0.0.jar exists in lib/)
  3. If incompatible:
     - Update pom.xml to use version 2.0.0 instead
     - Change systemPath to `${project.basedir}/lib/audit-logging-library-2.0.0.jar`
  4. If still incompatible, consider:
     - Replacing with Quarkus logging/audit capabilities
     - Wrapping library calls in compatibility layer
     - Requesting Jakarta-compatible version from vendor
- **Why:** External libraries may not be Jakarta-compatible
- **Depends on:** Step 17
- **Verify:** OrderService runs without ClassNotFoundException or NoClassDefFoundError

### Step 38: Update test dependencies in pom.xml
- **File:** pom.xml
- **Action:** MODIFY
- **What to do:**
  1. Add Quarkus test dependencies:
     ```xml
     <dependency>
       <groupId>io.quarkus</groupId>
       <artifactId>quarkus-junit5</artifactId>
       <scope>test</scope>
     </dependency>
     <dependency>
       <groupId>io.rest-assured</groupId>
       <artifactId>rest-assured</artifactId>
       <scope>test</scope>
     </dependency>
     ```
  2. Update JUnit from 4 to 5:
     - Remove `junit:junit:4.13.2`
     - JUnit 5 comes with quarkus-junit5
  3. Keep mockito but update to latest compatible version
  4. Keep H2 for testing
  5. Remove `org.hibernate:hibernate-entitymanager`
  6. Remove `org.glassfish:javax.json` (use Quarkus JSON-B or Jackson)
- **Why:** Quarkus uses JUnit 5 and REST-assured for testing
- **Depends on:** Step 1
- **Verify:** `mvn test` runs (tests may need updates)

### Step 39: Update test classes (if any exist)
- **File:** src/test/java/**/*.java
- **Action:** MODIFY
- **What to do:**
  1. Update test annotations from JUnit 4 to JUnit 5:
     - `@Test` remains the same but import from `org.junit.jupiter.api.Test`
     - `@Before` → `@BeforeEach`
     - `@After` → `@AfterEach`
     - `@BeforeClass` → `@BeforeAll`
     - `@AfterClass` → `@AfterAll`
  2. Add `@QuarkusTest` annotation to integration tests
  3. Update EntityManager injection in tests
  4. Replace javax.* → jakarta.*
- **Why:** Quarkus uses JUnit 5 and provides @QuarkusTest for integration tests
- **Depends on:** Step 38
- **Verify:** Tests compile and run

### Step 40: Create Dockerfile.jvm (optional)
- **File:** src/main/docker/Dockerfile.jvm
- **Action:** CREATE
- **What to do:**
  ```dockerfile
  FROM registry.access.redhat.com/ubi8/openjdk-11:1.14
  
  ENV LANGUAGE='en_US:en'
  
  COPY --chown=185 target/quarkus-app/lib/ /deployments/lib/
  COPY --chown=185 target/quarkus-app/*.jar /deployments/
  COPY --chown=185 target/quarkus-app/app/ /deployments/app/
  COPY --chown=185 target/quarkus-app/quarkus/ /deployments/quarkus/
  
  EXPOSE 8080
  USER 185
  
  ENTRYPOINT [ "java", "-jar", "/deployments/quarkus-run.jar" ]
  ```
- **Why:** Provide Docker image for deployment
- **Depends on:** Step 1
- **Verify:** `docker build -f src/main/docker/Dockerfile.jvm -t coolstore-monolith:latest .` succeeds

### Step 41: Create OpenShift deployment resources (optional)
- **File:** src/main/kubernetes/openshift.yml
- **Action:** CREATE
- **What to do:**
  Create Kubernetes/OpenShift deployment manifest with:
  - Deployment
  - Service
  - Route
  - ConfigMap for application.properties overrides
  - PostgreSQL database reference
  - Kafka/Artemis broker reference
- **Why:** Enable OpenShift deployment
- **Depends on:** Step 1, Step 2
- **Verify:** Resources apply successfully to OpenShift cluster

### Step 42: Create .dockerignore
- **File:** .dockerignore
- **Action:** CREATE
- **What to do:**
  ```
  *
  !target/quarkus-app/**
  ```
- **Why:** Optimize Docker build context
- **Depends on:** Step 40
- **Verify:** Docker builds faster

### Step 43: Add Quarkus dev services configuration (optional)
- **File:** src/main/resources/application.properties
- **Action:** MODIFY
- **What to do:**
  Add dev services configuration:
  ```properties
  # Dev Services - Quarkus starts PostgreSQL and Kafka automatically in dev mode
  %dev.quarkus.datasource.devservices.enabled=true
  %dev.quarkus.kafka.devservices.enabled=true
  
  # Test Services
  %test.quarkus.datasource.devservices.enabled=true
  %test.quarkus.kafka.devservices.enabled=true
  ```
- **Why:** Auto-start dependencies in dev mode
- **Depends on:** Step 2
- **Verify:** `mvn quarkus:dev` starts without manual PostgreSQL/Kafka setup

### Step 44: Build verification
- **File:** N/A
- **Action:** VERIFY
- **What to do:**
  1. Run `mvn clean package`
  2. Verify build succeeds
  3. Check target/ for quarkus-app/ directory (not WAR file)
  4. Verify no compilation errors
  5. Check for any warnings about deprecated APIs
- **Why:** Ensure migration is complete and builds successfully
- **Depends on:** All previous steps
- **Verify:** Build succeeds; `target/quarkus-app/quarkus-run.jar` exists

### Step 45: Runtime verification
- **File:** N/A
- **Action:** VERIFY
- **What to do:**
  1. Start dependencies (PostgreSQL, Kafka/Artemis)
  2. Run `mvn quarkus:dev`
  3. Verify application starts without errors
  4. Check startup logs for:
     - Flyway migration success
     - EntityManager creation
     - REST endpoints registered (/services/cart, /services/products, /services/orders)
     - Messaging channels connected
     - Keycloak OIDC configuration loaded
  5. Test basic operations:
     - GET products
     - Add to cart
     - Checkout (verify message sent)
     - Verify MDBs receive and process messages
  6. Check logs for inventory threshold notifications
- **Why:** Verify application works correctly after migration
- **Depends on:** Step 44
- **Verify:** All features work; no runtime errors

### Step 46: Integration testing
- **File:** N/A
- **Action:** VERIFY
- **What to do:**
  1. Run full checkout flow
  2. Verify order persisted to database
  3. Verify order message sent to topic
  4. Verify both MDBs processed the message:
     - OrderServiceMDB saved order
     - InventoryNotificationMDB checked threshold
  5. Verify inventory updated correctly
  6. Test with authenticated user (Keycloak)
  7. Test shipping calculation
- **Why:** End-to-end verification
- **Depends on:** Step 45
- **Verify:** Complete checkout flow works end-to-end

### Step 47: Performance baseline
- **File:** N/A
- **Action:** VERIFY
- **What to do:**
  1. Measure startup time (Quarkus should start in <5 seconds)
  2. Measure memory usage (RSS, heap)
  3. Test response times for REST endpoints
  4. Compare with original JBoss deployment (if available)
  5. Document metrics for future reference
- **Why:** Quarkus should significantly improve startup time and memory usage
- **Depends on:** Step 45
- **Verify:** Document baseline metrics

### Step 48: Update README.md
- **File:** README.md
- **Action:** MODIFY
- **What to do:**
  1. Update title: "CoolStore Monolith - Quarkus 3"
  2. Remove JBoss 7.4 setup instructions
  3. Add Quarkus dev mode instructions:
     ```
     # Start in dev mode (auto-starts PostgreSQL and Kafka via Dev Services)
     mvn quarkus:dev
     ```
  4. Add production build instructions:
     ```
     # Build
     mvn package
     
     # Run
     java -jar target/quarkus-app/quarkus-run.jar
     ```
  5. Update prerequisites:
     - Remove JBoss EAP requirement
     - Add: Java 11+, Maven 3.8+
     - Note: PostgreSQL and Kafka optional in dev mode (Dev Services)
  6. Update Keycloak setup (remains mostly same)
  7. Add Docker build/run instructions
  8. Add OpenShift deployment instructions (if applicable)
- **Why:** Update documentation to reflect Quarkus migration
- **Depends on:** All previous steps
- **Verify:** README accurately reflects new setup

### Step 49: Create migration notes document
- **File:** MIGRATION_NOTES.md
- **Action:** CREATE
- **What to do:**
  Create document with:
  1. **Breaking Changes:**
     - Session-scoped CartEndpoint now application-scoped (session state lost)
     - Remote EJB removed - ShippingService now local only
     - JMS replaced with reactive messaging (protocol change)
  2. **Configuration Changes:**
     - JNDI lookups replaced with application.properties config
     - Datasource config moved from server XML to application.properties
     - Messaging config in application.properties instead of activation config
  3. **Deployment Changes:**
     - WAR → JAR packaging
     - No application server needed
     - Standalone executable
  4. **Dependencies Updated:**
     - javax.* → jakarta.*
     - EJB → CDI
     - JMS → Reactive Messaging
     - Flyway managed by Quarkus
  5. **Known Issues:**
     - Audit library compatibility (if any)
     - Two MDB consumers on same topic (may need consumer group config)
     - Session state handling in CartEndpoint needs rework for multi-user
  6. **Testing Notes:**
     - What was tested
     - What needs additional testing
  7. **Rollback Plan:**
     - Git tag before migration
     - How to revert if needed
- **Why:** Document migration for team awareness
- **Depends on:** All previous steps
- **Verify:** Document created and comprehensive

### Step 50: Git commit migration
- **File:** N/A
- **Action:** COMMIT
- **What to do:**
  ```bash
  git add .
  git commit -m "Migrate from Java EE 7/JBoss EAP 7.4 to Quarkus 3

  - Updated pom.xml: javax → jakarta, EJB → CDI extensions
  - Converted all model classes: javax.persistence → jakarta.persistence
  - Converted service layer: @Stateless/@Stateful → @ApplicationScoped
  - Removed Remote EJB, replaced JNDI lookups with CDI injection
  - Converted MDBs to reactive messaging (@MessageDriven → @Incoming)
  - Replaced JMS API with SmallRye Reactive Messaging
  - Migrated configuration: XML → application.properties
  - Removed WebLogic-specific code (lifecycle listeners, JNDI)
  - Updated Flyway to Quarkus-managed migration
  - Converted lifecycle hooks to CDI observers
  - Updated REST endpoints: javax.ws.rs → jakarta.ws.rs
  - Updated tests to JUnit 5 and Quarkus testing
  - Added Docker and OpenShift deployment resources
  - Updated documentation

  Breaking changes documented in MIGRATION_NOTES.md
  "
  ```
- **Why:** Commit completed migration
- **Depends on:** All previous steps
- **Verify:** Git shows all changes committed

---

## Verification

After completing all steps, verify the migration with these commands:

```bash
# 1. Clean build
mvn clean package

# 2. Run tests
mvn test

# 3. Start in dev mode
mvn quarkus:dev

# 4. Verify endpoints (in another terminal)
curl http://localhost:8080/services/products
curl http://localhost:8080/services/cart/testcart

# 5. Check Quarkus info
curl http://localhost:8080/q/health
curl http://localhost:8080/q/metrics

# 6. Build native image (optional, requires GraalVM)
mvn package -Pnative

# 7. Docker build
docker build -f src/main/docker/Dockerfile.jvm -t coolstore-monolith:latest .
docker run -p 8080:8080 coolstore-monolith:latest
```

**Success criteria:**
- ✅ `mvn clean package` succeeds with no errors
- ✅ Application starts in <5 seconds
- ✅ All REST endpoints respond correctly
- ✅ Order checkout flow works end-to-end
- ✅ Messages flow through reactive messaging channels
- ✅ Both MDBs process messages correctly
- ✅ Database operations work (JPA, Flyway)
- ✅ Keycloak authentication works
- ✅ No javax.* imports remain in source code
- ✅ Health and metrics endpoints available

---

## Notes

### Critical Decisions Made

1. **Messaging Broker Choice:** Plan includes configuration for both Kafka and Artemis - choose based on infrastructure
2. **Session State:** CartEndpoint changed from @SessionScoped to @ApplicationScoped - requires session management rework for production
3. **MDB Consumers:** Two consumers on same topic may need separate consumer groups or queue configuration
4. **Audit Library:** External system-scoped dependency needs compatibility verification

### Gotchas

1. **@SessionScoped removed:** Quarkus REST doesn't support HTTP session scope out-of-box - use Redis or database for session state
2. **Remote EJB removed:** All EJB calls must be local - ShippingService no longer supports remote lookup
3. **JMS → Reactive Messaging:** Protocol changes from JMS to Kafka/AMQP - message format should remain compatible (String/JSON)
4. **Flyway auto-migration:** Quarkus runs Flyway at startup automatically - no manual trigger needed
5. **JNDI removed:** All JNDI lookups replaced with CDI injection or configuration properties
6. **WebLogic code deleted:** Entire weblogic.* package removed - custom lifecycle listeners converted to CDI observers

### Testing Priorities

1. **High Priority:**
   - Order checkout flow (REST → JMS → MDB → database)
   - Inventory updates and threshold notifications
   - Keycloak authentication
   - Shipping calculation (former remote EJB)

2. **Medium Priority:**
   - Shopping cart operations (note session state limitation)
   - Database migration (Flyway)
   - Error handling in MDBs

3. **Low Priority:**
   - Startup/shutdown listeners
   - Audit logging (if used)

### Migration Complexity by Component

**Simple (mechanical changes only):**
- Model classes (8 files) - just import changes
- PromoService - already CDI, just imports
- REST endpoints - mostly imports
- Configuration files - namespace updates

**Medium (pattern changes):**
- Service classes - @Stateless/@Stateful → @ApplicationScoped
- ShoppingCartOrderProcessor - JMS → Emitter
- DataBaseMigrationStartup - delegate to Quarkus
- Producers, Transformers - CDI updates

**Complex (structural/architectural changes):**
- OrderServiceMDB - MDB → @Incoming with transaction
- InventoryNotificationMDB - WebLogic JNDI removal + MDB → @Incoming
- ShoppingCartService - Remove JNDI lookup, handle state
- StartupListener - WebLogic lifecycle → CDI observers
- CartEndpoint - Session scope implications

### Future Enhancements

1. Consider Quarkus Panache for simplified JPA (optional)
2. Implement distributed session cache for CartEndpoint (Redis, Infinispan)
3. Add MicroProfile Health checks
4. Add MicroProfile Metrics for business metrics
5. Consider native compilation for faster startup
6. Implement circuit breaker patterns (SmallRye Fault Tolerance)
7. Add OpenAPI/Swagger documentation
8. Implement distributed tracing (OpenTelemetry)

### Rollback Plan

If migration fails:
1. Git revert to pre-migration commit
2. Redeploy original WAR to JBoss EAP 7.4
3. Review migration notes for issues
4. Re-attempt migration with fixes

### Dependencies on External Systems

1. **PostgreSQL:** Required for production, Dev Services provides in dev mode
2. **Kafka or Artemis:** Choose one for messaging, Dev Services can provide Kafka in dev mode
3. **Keycloak:** Required for authentication, same setup as before
4. **Audit Library:** Verify Jakarta compatibility, may need update

### Performance Expectations

**Quarkus vs JBoss EAP:**
- Startup time: ~2-4 seconds vs ~30-60 seconds (10-30x faster)
- Memory usage: ~50-100MB RSS vs ~500MB+ RSS (5-10x less)
- Build time: Similar or slightly faster
- Hot reload: Much faster in dev mode

### OpenShift Deployment Notes

If deploying to OpenShift:
1. Use OpenShift S2I build or binary build
2. Configure PostgreSQL and Kafka as OpenShift services
3. Use ConfigMaps for environment-specific configuration
4. Use Secrets for database passwords and Keycloak credentials
5. Configure Route for external access
6. Set resource limits appropriately (suggest: 256Mi-512Mi memory, 500m-1 CPU)
