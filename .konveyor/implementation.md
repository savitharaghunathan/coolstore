# Implementation Plan

## Goal
Migrate Java EE 7 monolith application from WebLogic/JBoss to Quarkus 3, transforming the programming model from EJB to CDI, MDB to SmallRye Reactive Messaging, and removing all application server dependencies.

- Domain skill: javaee-to-quarkus

## Project Summary
- Type: Maven / Java EE 7 WAR
- Files affected: ~35 files (27 Java + 8 config/build)
- Estimated complexity: Medium-High
- Hardest steps:
  1. Step 8: Convert InventoryNotificationMDB (WebLogic JNDI + manual topic subscription)
  2. Step 7: Convert OrderServiceMDB to SmallRye Reactive Messaging
  3. Step 18: Remove JNDI lookup in ShoppingCartService

## Steps

### Step 1: Update pom.xml packaging and Java version
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do:
  - Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
  - Update `<source>1.8</source>` to `<source>17</source>`
  - Update `<target>1.8</target>` to `<target>17</target>`
- Why: Quarkus 3 requires Java 17 minimum and produces JAR artifacts, not WAR
- Depends on: none
- Verify: Packaging is jar, Java version is 17

### Step 2: Add Quarkus BOM to pom.xml
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add to `<properties>`:
    ```xml
    <quarkus.platform.version>3.2.9.Final</quarkus.platform.version>
    <compiler-plugin.version>3.11.0</compiler-plugin.version>
    ```
  - Add to `<dependencyManagement>`:
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
- Why: Quarkus BOM manages all Quarkus extension versions
- Depends on: Step 1
- Verify: BOM is present in dependencyManagement section

### Step 3: Replace Java EE dependencies with Quarkus extensions in pom.xml
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do:
  - Remove dependencies:
    - `javax:javaee-web-api`
    - `javax:javaee-api`
    - `org.jboss.spec.javax.jms:jboss-jms-api_2.0_spec`
    - `org.jboss.spec.javax.rmi:jboss-rmi-api_1.0_spec`
  - Add Quarkus extensions:
    ```xml
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-arc</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-hibernate-orm</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-jdbc-h2</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-smallrye-reactive-messaging-amqp</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-flyway</artifactId>
    </dependency>
    ```
  - Keep `org.flywaydb:flyway-core` but change to `<scope>provided</scope>`
- Why: Quarkus provides its own implementations of CDI, REST, JPA, and messaging
- Depends on: Step 2
- Verify: No javax.* API dependencies remain (except in test scope)

### Step 4: Update Maven plugins in pom.xml
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do:
  - Remove `maven-war-plugin`
  - Update `maven-compiler-plugin` version to `${compiler-plugin.version}`
  - Add Quarkus plugin:
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
- Why: Quarkus uses its own build plugin instead of WAR plugin
- Depends on: Step 3
- Verify: quarkus-maven-plugin is present, maven-war-plugin is removed

### Step 5: Handle system-scoped dependency in pom.xml
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do:
  - Change audit-logging-library dependency from system scope:
    ```xml
    <dependency>
      <groupId>com.enterprise</groupId>
      <artifactId>audit-logging-library</artifactId>
      <version>1.0.0</version>
    </dependency>
    ```
  - Add note in comments to install to local Maven repo:
    ```
    <!-- Install to local Maven repo with: mvn install:install-file -Dfile=lib/audit-logging-library-1.0.0.jar -DgroupId=com.enterprise -DartifactId=audit-logging-library -Dversion=1.0.0 -Dpackaging=jar -->
    ```
- Why: Quarkus packaging doesn't handle system-scoped dependencies well; better to use local Maven repo
- Depends on: Step 4
- Verify: System scope removed, comment with install instructions added

### Step 6: Create application.properties
- Phase: App Config
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create file with content:
  ```properties
  # Datasource configuration (from persistence.xml)
  quarkus.datasource.db-kind=h2
  quarkus.datasource.jdbc.url=jdbc:h2:mem:coolstore;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1
  quarkus.datasource.username=sa
  quarkus.datasource.password=
  
  # Hibernate configuration
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=false
  quarkus.hibernate-orm.log.format-sql=true
  quarkus.hibernate-orm.sql-load-script=no-file
  
  # Flyway configuration
  quarkus.flyway.migrate-at-start=true
  quarkus.flyway.baseline-on-migrate=true
  quarkus.flyway.locations=classpath:db/migration
  
  # SmallRye Reactive Messaging - AMQP (orders topic)
  mp.messaging.incoming.orders.connector=smallrye-amqp
  mp.messaging.incoming.orders.address=orders
  mp.messaging.incoming.orders.durable=true
  mp.messaging.incoming.orders.broadcast=true
  
  mp.messaging.outgoing.orders.connector=smallrye-amqp
  mp.messaging.outgoing.orders.address=orders
  mp.messaging.outgoing.orders.durable=true
  
  # AMQP connection (configure for your broker)
  amqp-host=localhost
  amqp-port=5672
  amqp-username=quarkus
  amqp-password=quarkus
  ```
- Why: Quarkus uses application.properties instead of XML descriptors
- Depends on: Step 5
- Verify: File exists with all required configuration properties

### Step 7: COMPLEX — Convert OrderServiceMDB to SmallRye Reactive Messaging
- Phase: Messaging
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: @MessageDriven MDB with JMS Message/TextMessage
  - AFTER: @ApplicationScoped CDI bean with @Incoming reactive messaging
  - Specific changes:
    1. Remove imports:
       - `javax.ejb.ActivationConfigProperty`
       - `javax.ejb.MessageDriven`
       - `javax.jms.*`
    2. Add imports:
       - `jakarta.enterprise.context.ApplicationScoped`
       - `org.eclipse.microprofile.reactive.messaging.Incoming`
    3. Replace class annotation:
       ```java
       @ApplicationScoped
       public class OrderServiceMDB {
       ```
    4. Replace onMessage method:
       ```java
       @Incoming("orders")
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
       ```
    5. Remove MessageListener interface
    6. Remove try-catch for JMSException (no longer needed)
- Why: Quarkus uses SmallRye Reactive Messaging instead of JMS MDB
- Depends on: Step 6
- Verify: No JMS imports, uses @Incoming annotation, compiles successfully

### Step 8: COMPLEX — Convert InventoryNotificationMDB to SmallRye Reactive Messaging
- Phase: Messaging
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: MessageListener with manual WebLogic JNDI setup, init(), and close()
  - AFTER: @ApplicationScoped CDI bean with @Incoming reactive messaging
  - Specific changes:
    1. Remove imports:
       - `javax.jms.*`
       - `javax.naming.*`
       - `javax.rmi.PortableRemoteObject`
       - `java.util.Hashtable`
    2. Add imports:
       - `jakarta.enterprise.context.ApplicationScoped`
       - `org.eclipse.microprofile.reactive.messaging.Incoming`
    3. Add class annotation:
       ```java
       @ApplicationScoped
       public class InventoryNotificationMDB {
       ```
    4. Remove all JNDI-related fields:
       - `JNDI_FACTORY`
       - `JMS_FACTORY`
       - `TOPIC`
       - `tcon`, `tsession`, `tsubscriber`
    5. Replace onMessage method:
       ```java
       @Incoming("orders")
       public void onMessage(String orderStr) {
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
       }
       ```
    6. Delete init() method entirely
    7. Delete close() method entirely
    8. Delete getInitialContext() method entirely
    9. Remove MessageListener interface
- Why: Quarkus SmallRye Reactive Messaging handles subscription automatically; no manual JNDI setup needed
- Depends on: Step 6
- Verify: No JNDI/JMS imports, no init/close methods, uses @Incoming annotation

### Step 9: Convert ShoppingCartOrderProcessor JMS producer to Reactive Messaging
- Phase: Messaging
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Remove imports:
    - `javax.annotation.Resource`
    - `javax.jms.JMSContext`
    - `javax.jms.Topic`
  - Add imports:
    - `jakarta.enterprise.context.ApplicationScoped`
    - `org.eclipse.microprofile.reactive.messaging.Channel`
    - `org.eclipse.microprofile.reactive.messaging.Emitter`
  - Replace `@Stateless` with `@ApplicationScoped`
  - Replace fields:
    ```java
    @Inject
    @Channel("orders")
    Emitter<String> ordersEmitter;
    ```
  - Remove:
    - `@Inject JMSContext context`
    - `@Resource(lookup = "java:/topic/orders") Topic ordersTopic`
  - Replace process() method body:
    ```java
    public void process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersEmitter.send(Transformers.shoppingCartToJson(cart));
    }
    ```
- Why: Quarkus uses Emitter instead of JMS producer
- Depends on: Step 6
- Verify: Uses @Channel Emitter, no JMS/Topic references

### Step 10: Replace @Stateless with @ApplicationScoped in CatalogService
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Remove import: `javax.ejb.Stateless`
  - Add import: `jakarta.enterprise.context.ApplicationScoped`
  - Replace `@Stateless` with `@ApplicationScoped`
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 5
- Verify: Uses @ApplicationScoped, no EJB imports

### Step 11: Replace @Stateless with @ApplicationScoped in OrderService
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Remove import: `javax.ejb.Stateless`
  - Add import: `jakarta.enterprise.context.ApplicationScoped`
  - Replace `@Stateless` with `@ApplicationScoped`
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 5
- Verify: Uses @ApplicationScoped, no EJB imports

### Step 12: Replace @Stateless with @ApplicationScoped in ProductService
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Remove import: `javax.ejb.Stateless`
  - Add import: `jakarta.enterprise.context.ApplicationScoped`
  - Replace `@Stateless` with `@ApplicationScoped`
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 5
- Verify: Uses @ApplicationScoped, no EJB imports

### Step 13: Replace @Stateless with @ApplicationScoped in PromoService
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - Remove import: `javax.ejb.Stateless`
  - Add import: `jakarta.enterprise.context.ApplicationScoped`
  - Replace `@Stateless` with `@ApplicationScoped`
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 5
- Verify: Uses @ApplicationScoped, no EJB imports

### Step 14: COMPLEX — Remove @Remote and convert ShippingService to CDI bean
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - BEFORE: @Stateless @Remote EJB with interface
  - AFTER: @ApplicationScoped CDI bean without Remote interface
  - Specific changes:
    1. Remove imports:
       - `javax.ejb.Remote`
       - `javax.ejb.Stateless`
    2. Add import:
       - `jakarta.enterprise.context.ApplicationScoped`
    3. Replace annotations:
       ```java
       @ApplicationScoped
       public class ShippingService implements ShippingServiceRemote {
       ```
    4. Keep ShippingServiceRemote interface implementation for now (will be removed in cleanup phase)
- Why: Quarkus uses CDI instead of EJB; @Remote is not needed for local injection
- Depends on: Step 5
- Verify: Uses @ApplicationScoped, no @Remote or EJB imports

### Step 15: Replace @Stateful with @SessionScoped in ShoppingCartService
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - Remove import: `javax.ejb.Stateful`
  - Add import: `jakarta.enterprise.context.SessionScoped`
  - Replace `@Stateful` with `@SessionScoped`
  - Add `implements Serializable` to class declaration
  - Add import: `java.io.Serializable`
- Why: @Stateful EJB maps to @SessionScoped CDI bean; SessionScoped beans must be Serializable
- Depends on: Step 5
- Verify: Uses @SessionScoped, implements Serializable

### Step 16: COMPLEX — Convert DataBaseMigrationStartup lifecycle
- Phase: Lifecycle
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - BEFORE: @Singleton @Startup EJB with @PostConstruct
  - AFTER: CDI @Singleton with @Observes StartupEvent
  - Specific changes:
    1. Remove imports:
       - `javax.annotation.PostConstruct`
       - `javax.annotation.Resource`
       - `javax.ejb.Singleton`
       - `javax.ejb.Startup`
       - `javax.ejb.TransactionManagement`
       - `javax.ejb.TransactionManagementType`
       - `javax.sql.DataSource`
    2. Add imports:
       - `jakarta.inject.Singleton`
       - `io.quarkus.runtime.StartupEvent`
       - `jakarta.enterprise.event.Observes`
       - `io.agroal.api.AgroalDataSource`
    3. Replace class annotations:
       ```java
       @Singleton
       public class DataBaseMigrationStartup {
       ```
    4. Replace DataSource injection:
       ```java
       @Inject
       AgroalDataSource dataSource;
       ```
    5. Replace startup() method signature:
       ```java
       void onStart(@Observes StartupEvent ev) {
       ```
    6. Remove `@PostConstruct` annotation
    7. Keep method body the same
- Why: Quarkus uses StartupEvent instead of EJB @Startup/@PostConstruct
- Depends on: Step 6
- Verify: Uses CDI @Singleton and @Observes StartupEvent, no EJB imports

### Step 17: COMPLEX — Convert StartupListener to Quarkus lifecycle events
- Phase: Lifecycle
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - BEFORE: WebLogic ApplicationLifecycleListener
  - AFTER: CDI bean with @Observes StartupEvent and ShutdownEvent
  - Specific changes:
    1. Remove imports:
       - `weblogic.application.ApplicationLifecycleEvent`
       - `weblogic.application.ApplicationLifecycleListener`
    2. Add imports:
       - `jakarta.enterprise.context.ApplicationScoped`
       - `jakarta.enterprise.event.Observes`
       - `io.quarkus.runtime.StartupEvent`
       - `io.quarkus.runtime.ShutdownEvent`
    3. Replace class declaration:
       ```java
       @ApplicationScoped
       public class StartupListener {
       ```
    4. Replace postStart method:
       ```java
       void onStart(@Observes StartupEvent ev) {
           log.info("AppListener(postStart)");
       }
       ```
    5. Replace preStop method:
       ```java
       void onStop(@Observes ShutdownEvent ev) {
           log.info("AppListener(preStop)");
       }
       ```
- Why: Quarkus uses CDI event observers instead of application server lifecycle listeners
- Depends on: Step 6
- Verify: Uses @Observes with StartupEvent/ShutdownEvent, no WebLogic imports

### Step 18: COMPLEX — Remove JNDI lookup in ShoppingCartService
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: lookupShippingServiceRemote() JNDI method
  - AFTER: Direct CDI injection
  - Specific changes:
    1. Remove imports:
       - `java.util.Hashtable`
       - `javax.naming.Context`
       - `javax.naming.InitialContext`
       - `javax.naming.NamingException`
    2. Add field:
       ```java
       @Inject
       ShippingService shippingService;
       ```
    3. Replace all calls to `lookupShippingServiceRemote()` with `shippingService`:
       - Line with `calculateShipping`: `shippingService.calculateShipping(sc)`
       - Line with `calculateShippingInsurance`: `shippingService.calculateShippingInsurance(sc)`
    4. Delete the entire `lookupShippingServiceRemote()` method
- Why: Quarkus uses CDI injection instead of JNDI lookups
- Depends on: Step 15
- Verify: No JNDI/naming imports, direct injection of ShippingService

### Step 19: Delete persistence.xml
- Phase: App Config
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file — configuration moved to application.properties
- Why: Quarkus uses application.properties for datasource and Hibernate configuration
- Depends on: Step 6
- Verify: File no longer exists

### Step 20: Delete test persistence.xml
- Phase: App Config
- File: src/test/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file — test configuration will use application.properties
- Why: Quarkus uses application.properties for test configuration
- Depends on: Step 6
- Verify: File no longer exists

### Step 21: Delete web.xml
- Phase: App Config
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file — no longer needed
- Why: Quarkus doesn't use web.xml deployment descriptors
- Depends on: Step 6
- Verify: File no longer exists

### Step 22: Delete beans.xml
- Phase: App Config
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file — CDI is auto-enabled in Quarkus
- Why: Quarkus enables CDI by default without beans.xml
- Depends on: Step 6
- Verify: File no longer exists

### Step 23: Delete WebLogic ApplicationLifecycleEvent stub
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this file — no longer needed after StartupListener conversion
- Why: WebLogic-specific classes are not used in Quarkus
- Depends on: Step 17
- Verify: File no longer exists

### Step 24: Delete WebLogic ApplicationLifecycleListener stub
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this file — no longer needed after StartupListener conversion
- Why: WebLogic-specific classes are not used in Quarkus
- Depends on: Step 17
- Verify: File no longer exists

### Step 25: Delete WebLogic NonCatalogLogger stub
- Phase: Cleanup
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Delete this file — WebLogic-specific logging stub no longer needed
- Why: Quarkus uses standard Java logging or other logging frameworks
- Depends on: Step 17
- Verify: File no longer exists

### Step 26: Delete ShippingServiceRemote interface
- Phase: Cleanup
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Delete this file — Remote interface not needed without EJB
- Why: CDI beans don't require Remote interfaces for injection
- Depends on: Step 14, Step 18
- Verify: File no longer exists

### Step 27: Remove ShippingServiceRemote from ShippingService
- Phase: Cleanup
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Remove `implements ShippingServiceRemote` from class declaration
  - Remove `@Override` annotations from methods (if present)
- Why: Interface has been deleted
- Depends on: Step 26
- Verify: No reference to ShippingServiceRemote

### Step 28: Update Resources.java for CDI instead of EJB
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Review if this file contains any `@Produces` methods for EntityManager or other resources
  - If using `@PersistenceContext`, keep it (supported in Quarkus)
  - Replace any EJB-specific annotations with CDI equivalents
  - If produces Logger, change from EJB pattern to CDI:
    ```java
    @Produces
    public Logger produceLogger(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }
    ```
- Why: Producer methods should use CDI patterns
- Depends on: Step 5
- Verify: No EJB-specific annotations, uses CDI patterns

### Step 29: Update REST endpoints for Quarkus
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
  - If using `@Stateless`, replace with `@ApplicationScoped`
  - Verify all JAX-RS annotations are present
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 5
- Verify: Uses jakarta.ws.rs imports, compiles successfully

### Step 30: Update OrderEndpoint for Quarkus
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
  - If using `@Stateless`, replace with `@ApplicationScoped`
  - Verify all JAX-RS annotations are present
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 5
- Verify: Uses jakarta.ws.rs imports, compiles successfully

### Step 31: Update ProductEndpoint for Quarkus
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
  - If using `@Stateless`, replace with `@ApplicationScoped`
  - Verify all JAX-RS annotations are present
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 5
- Verify: Uses jakarta.ws.rs imports, compiles successfully

### Step 32: Update RestApplication for Quarkus
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
  - Keep `@ApplicationPath` annotation
  - This class may not be needed in Quarkus (auto-registers REST endpoints), but keep for compatibility
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 5
- Verify: Uses jakarta.ws.rs imports

### Step 33: Update all entity classes for Jakarta namespace
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do:
  - Replace `javax.persistence.*` imports with `jakarta.persistence.*`
  - Keep all JPA annotations (@Entity, @Id, @Column, etc.)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 5
- Verify: Uses jakarta.persistence imports, compiles successfully

### Step 34: Update InventoryEntity for Jakarta namespace
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do:
  - Replace `javax.persistence.*` imports with `jakarta.persistence.*`
  - Keep all JPA annotations
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 5
- Verify: Uses jakarta.persistence imports, compiles successfully

### Step 35: Update all other model classes for Jakarta namespace
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do:
  - Replace any `javax.*` imports with `jakarta.*` equivalents
  - Common replacements: `javax.persistence.*` → `jakarta.persistence.*`, `javax.validation.*` → `jakarta.validation.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 5
- Verify: No javax.* imports remain (except in test scope or legacy libraries)

## Verification

After completing all steps, run the following verification commands in order:

1. **Compile**: `mvn clean compile`
   - Must succeed without errors
   - Warnings about deprecated APIs are acceptable

2. **Test**: `mvn test`
   - Tests may need updates for Quarkus test framework
   - Use `@QuarkusTest` annotation for integration tests

3. **Run in dev mode**: `mvn quarkus:dev`
   - Application should start successfully
   - Check console for any startup errors
   - Verify datasource initialization via Flyway

4. **Test REST endpoints**: Use curl or browser to test:
   - `http://localhost:8080/services/products` - should return product list
   - `http://localhost:8080/services/cart/{cartId}` - should return cart
   - Other endpoints as defined in REST classes

5. **Test messaging**: 
   - Verify AMQP broker connection in logs
   - Test order submission through checkout
   - Verify both MDB consumers receive messages

## Notes

### Gotchas and Special Cases

1. **System-scoped dependency**: The audit-logging-library needs to be installed to local Maven repo. Run the install command from Step 5 before building.

2. **Messaging broker**: The application requires an AMQP broker (e.g., Apache Artemis, RabbitMQ). Update `application.properties` with correct broker connection details.

3. **Database**: The application uses H2 in-memory database by default. For production, update to PostgreSQL or another database and change `quarkus.datasource.db-kind` accordingly.

4. **Session scope**: ShoppingCartService uses `@SessionScoped`, which requires HTTP session support. If building a stateless API, consider using a different strategy (e.g., passing cartId in requests).

5. **Frontend**: The application includes an AngularJS frontend in `src/main/webapp`. This will NOT be packaged in the JAR by default. Options:
   - Serve static files from `src/main/resources/META-INF/resources`
   - Use separate frontend build process
   - Keep WAR packaging for frontend (not recommended)

6. **Namespace changes**: All `javax.*` must be changed to `jakarta.*` for Jakarta EE 9+ (Quarkus 3). Be thorough with find-replace.

7. **Reactive Messaging**: The conversion from JMS MDB to SmallRye Reactive Messaging changes the programming model. Both MDBs consume from the same topic - ensure this is intended behavior.

8. **Build time**: First Quarkus build will download many dependencies and may take several minutes. Subsequent builds are much faster.

9. **Dev mode**: Quarkus dev mode (`mvn quarkus:dev`) supports hot reload - code changes are reflected immediately without restart.

10. **Transaction management**: Quarkus handles transactions differently than application servers. Review any complex transactional code carefully.
