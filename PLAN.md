# PLAN.md

## Goal
Migrate Java EE 7 coolstore-monolith application from WebLogic/JBoss to Quarkus 3, replacing EJB with CDI, JMS/MDB with SmallRye Reactive Messaging, and converting from WAR to standalone JAR packaging.

- Reference used: javaee-to-quarkus (modules: build-config, app-config, ejb-to-cdi, messaging, lifecycle, cleanup)

## Project Summary
- Type: Maven WAR project (Java EE 7)
- Files affected: 30+ Java files, 3 config files, 1 pom.xml
- Estimated complexity: **High**
- Hardest steps:
  1. Converting InventoryNotificationMDB (manual JMS listener with WebLogic JNDI)
  2. Replacing JNDI lookup in ShoppingCartService with direct injection
  3. Converting @MessageDriven MDB to SmallRye Reactive Messaging

## Steps

### Step 1: Convert packaging from WAR to JAR
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus applications use JAR packaging, not WAR
- Depends on: none
- Verify: `grep '<packaging>jar</packaging>' pom.xml`

### Step 2: Add Quarkus BOM and plugin
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add to `<properties>`:
    ```xml
    <quarkus.platform.version>3.2.0.Final</quarkus.platform.version>
    <compiler-plugin.version>3.11.0</compiler-plugin.version>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    ```
  - Add to `<dependencyManagement>` section (create if missing):
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
  - Add Quarkus Maven plugin to `<build><plugins>`:
    ```xml
    <plugin>
      <groupId>io.quarkus.platform</groupId>
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
- Why: Quarkus requires its BOM for dependency management and plugin for builds
- Depends on: Step 1
- Verify: `grep 'quarkus-bom' pom.xml && grep 'quarkus-maven-plugin' pom.xml`

### Step 3: Replace Java EE dependencies with Quarkus extensions
- File: pom.xml
- Action: MODIFY
- What to do:
  - Remove:
    ```xml
    <dependency>
      <groupId>javax</groupId>
      <artifactId>javaee-web-api</artifactId>
      <version>7.0</version>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>javax</groupId>
      <artifactId>javaee-api</artifactId>
      <version>7.0</version>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>org.jboss.spec.javax.jms</groupId>
      <artifactId>jboss-jms-api_2.0_spec</artifactId>
      <version>2.0.0.Final</version>
    </dependency>
    <dependency>
      <groupId>org.jboss.spec.javax.rmi</groupId>
      <artifactId>jboss-rmi-api_1.0_spec</artifactId>
      <version>1.0.2.Final</version>
    </dependency>
    ```
  - Add Quarkus extensions:
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
      <artifactId>quarkus-smallrye-reactive-messaging-amqp</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-arc</artifactId>
    </dependency>
    ```
- Why: Quarkus provides its own extensions instead of Java EE APIs
- Depends on: Step 2
- Verify: `mvn dependency:tree | grep quarkus`

### Step 4: Update Flyway dependency
- File: pom.xml
- Action: MODIFY
- What to do:
  - Replace:
    ```xml
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
      <version>4.1.2</version>
    </dependency>
    ```
  - With:
    ```xml
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-flyway</artifactId>
    </dependency>
    ```
- Why: Quarkus has native Flyway integration
- Depends on: Step 3
- Verify: `grep 'quarkus-flyway' pom.xml`

### Step 5: Handle system-scoped local JAR dependency
- File: pom.xml
- Action: MODIFY
- What to do:
  - Note: The audit-logging-library dependency uses system scope, which works in Quarkus
  - Update version from 1.0.0 to 2.0.0 (lib/audit-logging-library-2.0.0.jar exists):
    ```xml
    <dependency>
      <groupId>com.enterprise</groupId>
      <artifactId>audit-logging-library</artifactId>
      <version>2.0.0</version>
      <scope>system</scope>
      <systemPath>${project.basedir}/lib/audit-logging-library-2.0.0.jar</systemPath>
    </dependency>
    ```
- Why: Using newer version of audit library available in lib/
- Depends on: Step 3
- Verify: `ls lib/audit-logging-library-2.0.0.jar`

### Step 6: Update compiler plugin configuration
- File: pom.xml
- Action: MODIFY
- What to do:
  - Update maven-compiler-plugin configuration to use Java 17:
    ```xml
    <plugin>
      <artifactId>maven-compiler-plugin</artifactId>
      <version>${compiler-plugin.version}</version>
      <configuration>
        <encoding>${project.encoding}</encoding>
        <source>${maven.compiler.source}</source>
        <target>${maven.compiler.target}</target>
        <parameters>true</parameters>
      </configuration>
    </plugin>
    ```
  - Remove maven-war-plugin (no longer needed for JAR packaging)
- Why: Quarkus 3 requires Java 17 minimum, parameters flag enables better CDI
- Depends on: Step 2
- Verify: `mvn clean compile`

### Step 7: Create application.properties
- File: src/main/resources/application.properties
- Action: CREATE
- What to do:
  - Create new file with Quarkus configuration:
    ```properties
    # Datasource configuration (replaces persistence.xml JNDI datasource)
    quarkus.datasource.db-kind=h2
    quarkus.datasource.jdbc.url=jdbc:h2:mem:coolstore;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    quarkus.datasource.username=sa
    quarkus.datasource.password=
    
    # Hibernate ORM configuration
    quarkus.hibernate-orm.database.generation=none
    quarkus.hibernate-orm.log.sql=false
    quarkus.hibernate-orm.sql-load-script=no-file
    
    # Flyway configuration
    quarkus.flyway.migrate-at-start=true
    quarkus.flyway.locations=classpath:db/migration
    
    # Messaging - AMQP broker configuration
    mp.messaging.connector.smallrye-amqp.host=localhost
    mp.messaging.connector.smallrye-amqp.port=5672
    mp.messaging.connector.smallrye-amqp.username=guest
    mp.messaging.connector.smallrye-amqp.password=guest
    
    # Incoming channel - orders topic subscriber
    mp.messaging.incoming.orders.connector=smallrye-amqp
    mp.messaging.incoming.orders.address=orders
    mp.messaging.incoming.orders.durable=true
    
    # Outgoing channel - orders topic publisher
    mp.messaging.outgoing.orders-out.connector=smallrye-amqp
    mp.messaging.outgoing.orders-out.address=orders
    mp.messaging.outgoing.orders-out.durable=true
    
    # HTTP configuration
    quarkus.http.port=8080
    ```
- Why: Quarkus uses application.properties instead of XML configs for datasource, JMS, etc.
- Depends on: Step 3
- Verify: `cat src/main/resources/application.properties | grep quarkus`

### Step 8: Migrate imports in CatalogItemEntity.java
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 7
- Verify: `grep -L 'javax.persistence' src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`

### Step 9: Migrate imports in InventoryEntity.java
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 7
- Verify: `grep -L 'javax.persistence' src/main/java/com/redhat/coolstore/model/InventoryEntity.java`

### Step 10: Migrate imports in Order.java
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 7
- Verify: `grep -L 'javax.persistence' src/main/java/com/redhat/coolstore/model/Order.java`

### Step 11: Migrate imports in OrderItem.java
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 7
- Verify: `grep -L 'javax.persistence' src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 12: Update Resources.java CDI producer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Replace `javax.enterprise.inject.Produces` → `jakarta.enterprise.inject.Produces`
  - Replace `javax.persistence.EntityManager` → `jakarta.persistence.EntityManager`
  - Replace `javax.persistence.PersistenceContext` → `jakarta.persistence.PersistenceContext`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 7
- Verify: `grep 'jakarta.persistence.EntityManager' src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 13: Convert ProductEndpoint to Quarkus REST
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  - Replace `javax.ejb.Stateless` → remove (not needed in Quarkus)
  - Add `@jakarta.enterprise.context.ApplicationScoped` if not present
- Why: Quarkus uses Jakarta namespace and CDI instead of EJB
- Depends on: Step 7
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 14: Convert OrderEndpoint to Quarkus REST
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  - Replace `javax.ejb.Stateless` → remove
  - Add `@jakarta.enterprise.context.ApplicationScoped`
- Why: Quarkus uses Jakarta namespace and CDI instead of EJB
- Depends on: Step 7
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 15: COMPLEX - Convert CartEndpoint session scope
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.enterprise.context.SessionScoped` → `jakarta.enterprise.context.SessionScoped`
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  - Replace all `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - Note: @SessionScoped requires quarkus-undertow extension OR convert to @ApplicationScoped with proper session handling
  - For now, keep @SessionScoped (will add extension if needed)
- Why: Quarkus uses Jakarta namespace; session scope requires servlet extension
- Depends on: Step 7
- Verify: `grep 'jakarta.enterprise.context.SessionScoped' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 16: Update RestApplication JAX-RS config
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.ApplicationPath` → `jakarta.ws.rs.ApplicationPath`
  - Replace `javax.ws.rs.core.Application` → `jakarta.ws.rs.core.Application`
- Why: Quarkus uses Jakarta namespace
- Depends on: Step 7
- Verify: `grep 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/RestApplication.java`

### Step 17: Convert CatalogService from EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  - Replace `javax.persistence.EntityManager` → `jakarta.persistence.EntityManager`
- Why: Quarkus uses CDI beans instead of EJBs
- Depends on: Step 12
- Verify: `grep 'ApplicationScoped' src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 18: Convert OrderService from EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  - Replace `javax.persistence.EntityManager` → `jakarta.persistence.EntityManager`
- Why: Quarkus uses CDI beans instead of EJBs
- Depends on: Step 12
- Verify: `grep 'ApplicationScoped' src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 19: Convert ProductService from EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
- Why: Quarkus uses CDI beans instead of EJBs
- Depends on: Step 7
- Verify: `grep 'ApplicationScoped' src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 20: Convert PromoService from EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
- Why: Quarkus uses CDI beans instead of EJBs
- Depends on: Step 7
- Verify: `grep 'ApplicationScoped' src/main/java/com/redhat/coolstore/service/PromoService.java`

### Step 21: COMPLEX - Convert ShippingService EJB and remove Remote interface
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Remove `@javax.ejb.Stateless` annotation
  - Remove `@javax.ejb.Remote` annotation
  - Add `@jakarta.enterprise.context.ApplicationScoped`
  - Remove `implements ShippingServiceRemote` (keep the methods)
  - Make class non-remote: direct injection replaces JNDI lookup
- Why: Quarkus doesn't support EJB Remote; use direct CDI injection instead
- Depends on: Step 7
- Verify: `grep 'ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShippingService.java && grep -L '@Remote' src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 22: COMPLEX - Replace JNDI lookup with injection in ShoppingCartService
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateful` with JNDI lookup method `lookupShippingServiceRemote()`
  - AFTER: `@ApplicationScoped` with direct injection
  - Changes:
    1. Replace `javax.ejb.Stateful` → `jakarta.enterprise.context.ApplicationScoped`
    2. Replace all `javax.inject.Inject` → `jakarta.inject.Inject`
    3. Remove imports: `javax.naming.*`, `java.util.Hashtable`
    4. Delete the `lookupShippingServiceRemote()` method entirely
    5. Add field injection:
       ```java
       @Inject
       ShippingService shippingService;
       ```
    6. Replace all calls to `lookupShippingServiceRemote()` with direct `shippingService` usage:
       - `lookupShippingServiceRemote().calculateShipping(sc)` → `shippingService.calculateShipping(sc)`
       - `lookupShippingServiceRemote().calculateShippingInsurance(sc)` → `shippingService.calculateShippingInsurance(sc)`
  - Note: @Stateful becomes @ApplicationScoped because shopping cart state is already managed in the cart field
- Why: Quarkus doesn't support JNDI lookups; use CDI injection directly
- Depends on: Step 21 (ShippingService must be converted first)
- Verify: `grep -L 'lookupShippingServiceRemote' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java && grep 'shippingService' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 23: COMPLEX - Convert ShoppingCartOrderProcessor JMS to Reactive Messaging
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless` with `@Resource` JMS Topic and `JMSContext`
  - AFTER: `@ApplicationScoped` with SmallRye Reactive Messaging `@Channel` and `Emitter`
  - Changes:
    1. Remove `@javax.ejb.Stateless`
    2. Add `@jakarta.enterprise.context.ApplicationScoped`
    3. Replace imports:
       - Remove: `javax.annotation.Resource`, `javax.jms.JMSContext`, `javax.jms.Topic`
       - Add: `org.eclipse.microprofile.reactive.messaging.Channel`, `org.eclipse.microprofile.reactive.messaging.Emitter`
    4. Replace `javax.inject.Inject` → `jakarta.inject.Inject`
    5. Replace fields:
       ```java
       // OLD:
       @Inject
       private transient JMSContext context;
       
       @Resource(lookup = "java:/topic/orders")
       private Topic ordersTopic;
       
       // NEW:
       @Inject
       @Channel("orders-out")
       Emitter<String> ordersEmitter;
       ```
    6. Replace `process` method body:
       ```java
       // OLD:
       context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));
       
       // NEW:
       ordersEmitter.send(Transformers.shoppingCartToJson(cart));
       ```
- Why: Quarkus uses SmallRye Reactive Messaging instead of JMS API
- Depends on: Step 7 (application.properties with orders-out channel)
- Verify: `grep 'Emitter<String>' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java && grep '@Channel' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 24: COMPLEX - Convert OrderServiceMDB to Reactive Messaging consumer
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: `@MessageDriven` MDB implementing `MessageListener`
  - AFTER: `@ApplicationScoped` bean with `@Incoming` method
  - Changes:
    1. Remove all imports: `javax.ejb.*`, `javax.jms.*`
    2. Add imports:
       - `jakarta.enterprise.context.ApplicationScoped`
       - `org.eclipse.microprofile.reactive.messaging.Incoming`
    3. Replace `javax.inject.Inject` → `jakarta.inject.Inject`
    4. Remove `@MessageDriven` annotation and activationConfig
    5. Add `@ApplicationScoped` annotation
    6. Remove `implements MessageListener`
    7. Replace `onMessage` method:
       ```java
       // OLD:
       @Override
       public void onMessage(Message rcvMessage) {
           System.out.println("\nMessage recd !");
           TextMessage msg = null;
           try {
               if (rcvMessage instanceof TextMessage) {
                   msg = (TextMessage) rcvMessage;
                   String orderStr = msg.getBody(String.class);
                   System.out.println("Received order: " + orderStr);
                   Order order = Transformers.jsonToOrder(orderStr);
                   System.out.println("Order object is " + order);
                   orderService.save(order);
                   order.getItemList().forEach(orderItem -> {
                       catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
                   });
               }
           } catch (JMSException e) {
               throw new RuntimeException(e);
           }
       }
       
       // NEW:
       @Incoming("orders")
       public void processOrder(String orderStr) {
           System.out.println("\nMessage received!");
           System.out.println("Received order: " + orderStr);
           Order order = Transformers.jsonToOrder(orderStr);
           System.out.println("Order object is " + order);
           orderService.save(order);
           order.getItemList().forEach(orderItem -> {
               catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
           });
       }
       ```
- Why: Quarkus uses SmallRye Reactive Messaging `@Incoming` instead of MDB
- Depends on: Step 7 (application.properties with orders channel)
- Verify: `grep '@Incoming("orders")' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java && grep -L '@MessageDriven' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 25: COMPLEX - Convert InventoryNotificationMDB manual listener to Reactive Messaging
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Manual `MessageListener` with WebLogic JNDI and JMS API
  - AFTER: `@ApplicationScoped` bean with `@Incoming` method
  - Changes:
    1. Remove all imports: `javax.jms.*`, `javax.naming.*`, `javax.rmi.PortableRemoteObject`, `java.util.Hashtable`
    2. Add imports:
       - `jakarta.enterprise.context.ApplicationScoped`
       - `org.eclipse.microprofile.reactive.messaging.Incoming`
    3. Replace `javax.inject.Inject` → `jakarta.inject.Inject`
    4. Remove `implements MessageListener`
    5. Add `@ApplicationScoped` annotation
    6. Remove all fields: `JNDI_FACTORY`, `JMS_FACTORY`, `TOPIC`, `tcon`, `tsession`, `tsubscriber`
    7. Delete methods: `init()`, `close()`, `getInitialContext()`
    8. Replace `onMessage` method:
       ```java
       // OLD:
       public void onMessage(Message rcvMessage) {
           TextMessage msg;
           try {
               System.out.println("received message inventory");
               if (rcvMessage instanceof TextMessage) {
                   msg = (TextMessage) rcvMessage;
                   String orderStr = msg.getBody(String.class);
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
           } catch (JMSException jmse) {
               System.err.println("An exception occurred: " + jmse.getMessage());
           }
       }
       
       // NEW:
       @Incoming("orders")
       public void processInventoryNotification(String orderStr) {
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
  - Note: Both OrderServiceMDB and InventoryNotificationMDB listen to same "orders" channel (topic semantics preserved)
- Why: Remove all WebLogic/JBoss JNDI dependencies; use Reactive Messaging
- Depends on: Step 7 (application.properties with orders channel)
- Verify: `grep '@Incoming("orders")' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java && grep -L 'InitialContext' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 26: Convert DataBaseMigrationStartup to Quarkus lifecycle
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - Replace `javax.annotation.PostConstruct` → `jakarta.annotation.PostConstruct`
  - Replace `javax.ejb.Singleton` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.ejb.Startup` → `io.quarkus.runtime.Startup`
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
- Why: Quarkus uses different startup annotations
- Depends on: Step 7
- Verify: `grep 'io.quarkus.runtime.Startup' src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`

### Step 27: Update Producers logger factory
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Replace `javax.enterprise.inject.Produces` → `jakarta.enterprise.inject.Produces`
  - Replace `javax.enterprise.inject.spi.InjectionPoint` → `jakarta.enterprise.inject.spi.InjectionPoint`
  - Add `@jakarta.enterprise.context.ApplicationScoped` to class
- Why: Quarkus uses Jakarta namespace
- Depends on: Step 7
- Verify: `grep 'jakarta.enterprise' src/main/java/com/redhat/coolstore/utils/Producers.java`

### Step 28: COMPLEX - Convert StartupListener from WebLogic to Quarkus lifecycle
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - BEFORE: Extends `weblogic.application.ApplicationLifecycleListener`
  - AFTER: Uses Quarkus `@Observes` for lifecycle events
  - Changes:
    1. Remove import: `weblogic.application.*`
    2. Add imports:
       - `jakarta.enterprise.context.ApplicationScoped`
       - `jakarta.enterprise.event.Observes`
       - `io.quarkus.runtime.StartupEvent`
       - `io.quarkus.runtime.ShutdownEvent`
    3. Replace `javax.inject.Inject` → `jakarta.inject.Inject`
    4. Add `@ApplicationScoped` annotation
    5. Remove `extends ApplicationLifecycleListener`
    6. Replace methods:
       ```java
       // OLD:
       @Override
       public void postStart(ApplicationLifecycleEvent evt) {
           log.info("AppListener(postStart)");
       }
       
       @Override
       public void preStop(ApplicationLifecycleEvent evt) {
           log.info("AppListener(preStop)");
       }
       
       // NEW:
       void onStart(@Observes StartupEvent ev) {
           log.info("AppListener(postStart)");
       }
       
       void onStop(@Observes ShutdownEvent ev) {
           log.info("AppListener(preStop)");
       }
       ```
- Why: Quarkus doesn't use WebLogic lifecycle; uses CDI observers
- Depends on: Step 7
- Verify: `grep '@Observes StartupEvent' src/main/java/com/redhat/coolstore/utils/StartupListener.java && grep -L 'weblogic' src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Step 29: Update Transformers utility class
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do:
  - Replace `javax.json.*` → `jakarta.json.*`
  - Add `@jakarta.enterprise.context.ApplicationScoped` if not already present
- Why: Quarkus uses Jakarta namespace
- Depends on: Step 7
- Verify: `grep 'jakarta.json' src/main/java/com/redhat/coolstore/utils/Transformers.java`

### Step 30: Delete persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Remove file completely
- Why: Quarkus uses application.properties for datasource configuration
- Depends on: Step 7 (application.properties created with datasource config)
- Verify: `test ! -f src/main/resources/META-INF/persistence.xml`

### Step 31: Delete or update beans.xml
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Remove file (Quarkus auto-discovers CDI beans)
- Why: Quarkus uses zero-config CDI discovery by default
- Depends on: Step 1 (JAR packaging doesn't use WEB-INF)
- Verify: `test ! -f src/main/webapp/WEB-INF/beans.xml`

### Step 32: Delete web.xml
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Remove file
- Why: Quarkus JAR packaging doesn't use WEB-INF or web.xml
- Depends on: Step 1 (JAR packaging)
- Verify: `test ! -f src/main/webapp/WEB-INF/web.xml`

### Step 33: Delete WebLogic stub classes
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Remove entire file
- Why: No longer needed after StartupListener conversion
- Depends on: Step 28
- Verify: `test ! -f src/main/java/weblogic/application/ApplicationLifecycleListener.java`

### Step 34: Delete WebLogic ApplicationLifecycleEvent stub
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Remove entire file
- Why: No longer needed after StartupListener conversion
- Depends on: Step 28
- Verify: `test ! -f src/main/java/weblogic/application/ApplicationLifecycleEvent.java`

### Step 35: Delete WebLogic NonCatalogLogger stub
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Remove entire file
- Why: WebLogic-specific; not used in Quarkus
- Depends on: Step 7
- Verify: `test ! -f src/main/java/weblogic/i18n/logging/NonCatalogLogger.java`

### Step 36: Delete ShippingServiceRemote interface
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Remove entire file
- Why: Remote interfaces not used in Quarkus (direct injection replaces JNDI)
- Depends on: Step 21, Step 22 (ShippingService converted and JNDI lookup removed)
- Verify: `test ! -f src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java`

### Step 37: Verify no javax.* EE imports remain
- File: All Java files in src/main/java/com/redhat/coolstore
- Action: VERIFY
- What to do: Scan all source files to ensure javax.ejb, javax.jms, javax.persistence replaced with jakarta.*
- Why: Ensure complete migration to Jakarta namespace
- Depends on: All previous steps
- Verify: `! grep -r 'import javax\.\(ejb\|jms\|persistence\|enterprise\|inject\|ws\.rs\|annotation\)' src/main/java/com/redhat/coolstore/`

### Step 38: Move static web resources from webapp to resources
- File: Multiple files in src/main/webapp/
- Action: MODIFY
- What to do:
  - Create `src/main/resources/META-INF/resources/` directory
  - Move non-WEB-INF content from `src/main/webapp/` to `src/main/resources/META-INF/resources/`:
    - `app/` directory → `src/main/resources/META-INF/resources/app/`
    - `bower_components/` → `src/main/resources/META-INF/resources/bower_components/`
    - `partials/` → `src/main/resources/META-INF/resources/partials/`
    - `*.jsp`, `*.json` files → `src/main/resources/META-INF/resources/`
  - Note: WEB-INF directory will be deleted separately
- Why: Quarkus JAR packaging serves static files from META-INF/resources
- Depends on: Step 1 (JAR packaging)
- Verify: `ls src/main/resources/META-INF/resources/ | grep -E '(app|index.jsp)'`

### Step 39: Build verification
- File: N/A (build process)
- Action: VERIFY
- What to do: Run `mvn clean compile` to verify all changes compile successfully
- Why: Ensure all imports, annotations, and code changes are correct
- Depends on: Steps 1-38
- Verify: `mvn clean compile`

### Step 40: Integration test build
- File: N/A (build process)
- Action: VERIFY
- What to do: Run `mvn clean package` to build complete Quarkus application
- Why: Ensure the application packages correctly as an executable JAR
- Depends on: Step 39
- Verify: `mvn clean package && ls target/*-runner.jar`

## Verification

After completing all steps, run these commands to verify the migration:

```bash
# Verify build succeeds
mvn clean package

# Verify JAR packaging
ls target/*.jar | grep -v 'original'

# Verify no javax.* EE imports in source
! grep -r 'import javax\.\(ejb\|jms\|persistence\|enterprise\|inject\|ws\.rs\)' src/main/java/com/redhat/coolstore/

# Verify Quarkus extensions loaded
mvn dependency:tree | grep -E 'quarkus-(resteasy|hibernate|arc|smallrye)'

# Verify application.properties exists
test -f src/main/resources/application.properties

# Verify old config files deleted
test ! -f src/main/resources/META-INF/persistence.xml
test ! -f src/main/webapp/WEB-INF/web.xml
test ! -f src/main/webapp/WEB-INF/beans.xml

# Verify WebLogic stubs deleted
test ! -d src/main/java/weblogic/

# Start application (requires AMQP broker running)
mvn quarkus:dev
```

## Notes

### Critical Decisions Made

1. **Session Scope Handling**: CartEndpoint uses `@SessionScoped` which requires servlet support in Quarkus. If this causes issues during runtime, we may need to add `quarkus-undertow` extension or refactor to `@ApplicationScoped` with manual session management.

2. **ShoppingCartService State**: Original `@Stateful` EJB converted to `@ApplicationScoped`. The cart state is maintained in the instance field, but this may need adjustment based on actual usage patterns (consider using `@RequestScoped` with session storage if multiple users share the service).

3. **Messaging Broker**: Migration assumes AMQP broker (e.g., ActiveMQ Artemis, RabbitMQ). The original JMS Topic setup is replaced with SmallRye Reactive Messaging channels. Both OrderServiceMDB and InventoryNotificationMDB subscribe to the same "orders" channel, preserving pub/sub topic semantics.

4. **Audit Library**: Using system-scoped dependency (lib/audit-logging-library-2.0.0.jar). This works in Quarkus but consider migrating to Maven Central or local repository for better dependency management.

5. **Database**: Configured for H2 in-memory database. Production deployment should update application.properties for PostgreSQL/MySQL/etc.

6. **Static Resources**: JSP files moved to META-INF/resources, but JSP is not natively supported in Quarkus. Consider migrating to Qute templates or serving as static HTML if dynamic rendering is needed.

### Complexity Hotspots

- **InventoryNotificationMDB**: Most complex transformation - manual JMS listener with WebLogic-specific JNDI lookups converted to Reactive Messaging
- **ShoppingCartService**: JNDI lookup removal and stateful-to-stateless conversion
- **ShoppingCartOrderProcessor**: JMS producer to Reactive Messaging Emitter conversion
- **StartupListener**: WebLogic lifecycle to Quarkus CDI observers

### Post-Migration Tasks (Not in Scope)

- Configure production datasource
- Set up AMQP broker connection parameters for production
- Add Quarkus extensions as needed (e.g., OpenAPI, Metrics, Health checks)
- Review and optimize CDI bean scopes based on actual usage
- Consider replacing JSP with Qute or modern frontend framework
- Add integration tests for messaging flows
- Configure logging (Quarkus uses JBoss Logging by default)
