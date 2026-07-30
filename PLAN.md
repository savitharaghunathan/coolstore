# PLAN.md

## Goal
Migrate Java EE 7 application (WebLogic/WildFly) to Quarkus 3 - replacing WAR packaging with JAR, EJBs with CDI, JMS/MDB with SmallRye Reactive Messaging, JNDI lookups with direct injection, and WebLogic lifecycle hooks with Quarkus events.

- Reference used: javaee-to-quarkus (phases: build-config, app-config, ejb-to-cdi, messaging, lifecycle, cleanup)

## Project Summary
- Type: Maven WAR application (Java EE 7)
- Java files: 51 (30 main, 21 weblogic stubs/test)
- Files affected: ~40 files (source + config)
- Estimated complexity: **High**
- Hardest steps:
  1. Convert 2 MDB classes to SmallRye Reactive Messaging (structural change)
  2. Remove JNDI lookups and replace with direct CDI injection
  3. Replace WebLogic lifecycle listener with Quarkus lifecycle events

## Architecture Summary
Based on code analysis:
- **Layer 1 - Build**: pom.xml (WAR → JAR, Java EE → Quarkus)
- **Layer 2 - Config**: persistence.xml → application.properties, remove web.xml/beans.xml
- **Layer 3 - Models**: 8 entity/model classes (simple javax.persistence imports)
- **Layer 4 - Persistence**: Resources.java (EntityManager producer)
- **Layer 5 - Services**: 10 service classes (4 with EJB annotations, 2 MDBs, 1 with JNDI)
- **Layer 6 - REST**: 4 REST endpoint classes
- **Layer 7 - Utilities**: 4 utility classes (1 WebLogic lifecycle listener)
- **Layer 8 - Cleanup**: Delete 3 WebLogic stub files

## Steps

### Step 1: Replace pom.xml packaging from WAR to JAR
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus produces standalone JARs, not WAR files for app servers
- Depends on: none
- Verify: `grep '<packaging>jar</packaging>' pom.xml`

### Step 2: Update Java version to 17 in pom.xml
- File: pom.xml
- Action: MODIFY
- What to do: 
  - Change `<source>1.8</source>` to `<source>17</source>`
  - Change `<target>1.8</target>` to `<target>17</target>`
- Why: Quarkus 3 requires Java 17 minimum
- Depends on: none
- Verify: `grep '<source>17</source>' pom.xml && grep '<target>17</target>' pom.xml`

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
        <version>3.2.0.Final</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  ```
- Why: Quarkus BOM manages all extension versions
- Depends on: Step 1, Step 2
- Verify: `grep 'quarkus-bom' pom.xml`

### Step 4: Replace Java EE dependencies with Quarkus extensions in pom.xml
- File: pom.xml
- Action: MODIFY
- What to do: Remove these dependencies:
  ```xml
  <!-- REMOVE -->
  <dependency>
    <groupId>javax</groupId>
    <artifactId>javaee-web-api</artifactId>
  </dependency>
  <dependency>
    <groupId>javax</groupId>
    <artifactId>javaee-api</artifactId>
  </dependency>
  <dependency>
    <groupId>org.jboss.spec.javax.jms</groupId>
    <artifactId>jboss-jms-api_2.0_spec</artifactId>
  </dependency>
  <dependency>
    <groupId>org.jboss.spec.javax.rmi</groupId>
    <artifactId>jboss-rmi-api_1.0_spec</artifactId>
  </dependency>
  ```
  Add these Quarkus extensions:
  ```xml
  <!-- ADD -->
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-hibernate-orm-panache</artifactId>
  </dependency>
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-postgresql</artifactId>
  </dependency>
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-resteasy-jackson</artifactId>
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
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-flyway</artifactId>
  </dependency>
  ```
- Why: Replace Java EE APIs with equivalent Quarkus extensions
- Depends on: Step 3
- Verify: `grep 'quarkus-hibernate-orm-panache' pom.xml && ! grep 'javaee-api' pom.xml`

### Step 5: Replace maven-war-plugin with quarkus-maven-plugin in pom.xml
- File: pom.xml
- Action: MODIFY
- What to do: Remove:
  ```xml
  <plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-war-plugin</artifactId>
    <version>3.2.0</version>
  </plugin>
  ```
  Add:
  ```xml
  <plugin>
    <groupId>io.quarkus.platform</groupId>
    <artifactId>quarkus-maven-plugin</artifactId>
    <version>3.2.0.Final</version>
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
- Why: Quarkus uses its own plugin for building and dev mode
- Depends on: Step 1, Step 4
- Verify: `grep 'quarkus-maven-plugin' pom.xml && ! grep 'maven-war-plugin' pom.xml`

### Step 6: Update maven-compiler-plugin version in pom.xml
- File: pom.xml
- Action: MODIFY
- What to do: Change version from `3.0` to `3.11.0`:
  ```xml
  <plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
      <encoding>${project.encoding}</encoding>
      <source>17</source>
      <target>17</target>
    </configuration>
  </plugin>
  ```
- Why: Newer compiler plugin version compatible with Java 17
- Depends on: Step 2
- Verify: `grep -A 8 'maven-compiler-plugin' pom.xml | grep '3.11.0'`

### Step 7: Update Flyway dependency version in pom.xml
- File: pom.xml
- Action: MODIFY
- What to do: Change Flyway version from `4.1.2` to `9.16.0`:
  ```xml
  <dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>9.16.0</version>
  </dependency>
  ```
- Why: Quarkus 3 requires newer Flyway version
- Depends on: Step 3
- Verify: `grep -A 3 'flyway-core' pom.xml | grep '9.16.0'`

### Step 8: Create application.properties configuration file
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create with datasource and Hibernate configuration:
  ```properties
  # Datasource configuration (migrated from persistence.xml)
  quarkus.datasource.db-kind=postgresql
  quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore
  quarkus.datasource.username=coolstore
  quarkus.datasource.password=coolstore
  quarkus.datasource.jdbc.max-size=16
  
  # Hibernate configuration
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=false
  quarkus.hibernate-orm.log.format-sql=true
  quarkus.hibernate-orm.sql-load-script=no-file
  
  # Flyway configuration
  quarkus.flyway.migrate-at-start=true
  quarkus.flyway.locations=classpath:db/migration
  
  # AMQP/Messaging configuration (for SmallRye Reactive Messaging)
  amqp-host=localhost
  amqp-port=5672
  amqp-username=admin
  amqp-password=admin
  
  # Reactive messaging channels
  mp.messaging.outgoing.orders.connector=smallrye-amqp
  mp.messaging.outgoing.orders.address=orders
  mp.messaging.outgoing.orders.durable=true
  
  mp.messaging.incoming.orders-in.connector=smallrye-amqp
  mp.messaging.incoming.orders-in.address=orders
  mp.messaging.incoming.orders-in.durable=true
  
  # Application configuration
  quarkus.http.port=8080
  ```
- Why: Quarkus uses application.properties instead of persistence.xml and other XML configs
- Depends on: Step 4
- Verify: `test -f src/main/resources/application.properties`

### Step 9: Migrate imports in CatalogItemEntity.java
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `! grep 'javax.persistence' src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`

### Step 10: Migrate imports in InventoryEntity.java
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `! grep 'javax.persistence' src/main/java/com/redhat/coolstore/model/InventoryEntity.java`

### Step 11: Migrate imports in Order.java
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `! grep 'javax.persistence' src/main/java/com/redhat/coolstore/model/Order.java`

### Step 12: Migrate imports in OrderItem.java
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `! grep 'javax.persistence' src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 13: Migrate imports in Resources.java
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do: 
  - Replace `javax.enterprise.context.Dependent` → `jakarta.enterprise.context.Dependent`
  - Replace `javax.enterprise.inject.Produces` → `jakarta.enterprise.inject.Produces`
  - Replace `javax.persistence.EntityManager` → `jakarta.persistence.EntityManager`
  - Replace `javax.persistence.PersistenceContext` → `jakarta.persistence.PersistenceContext`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `! grep 'javax\.' src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 14: Migrate EJB annotations in CatalogService.java
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  - Replace `javax.persistence.EntityManager` → `jakarta.persistence.EntityManager`
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 4
- Verify: `! grep '@Stateless' src/main/java/com/redhat/coolstore/service/CatalogService.java && grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 15: Migrate EJB annotations in OrderService.java
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  - Replace `javax.persistence.EntityManager` → `jakarta.persistence.EntityManager`
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 4
- Verify: `! grep '@Stateless' src/main/java/com/redhat/coolstore/service/OrderService.java && grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 16: Migrate EJB annotations in ProductService.java
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 4
- Verify: `! grep '@Stateless' src/main/java/com/redhat/coolstore/service/ProductService.java && grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 17: Migrate EJB annotations in PromoService.java
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 4
- Verify: `! grep '@Stateless' src/main/java/com/redhat/coolstore/service/PromoService.java && grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/PromoService.java`

### Step 18: COMPLEX - Remove EJB Remote interface and JNDI lookup in ShippingService.java
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless @Remote` EJB with ShippingServiceRemote interface
  - AFTER: `@ApplicationScoped` CDI bean without Remote interface
  - Specific changes:
    1. Remove: `import javax.ejb.Remote;`, `import javax.ejb.Stateless;`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`
    3. Remove: `@Stateless`, `@Remote` annotations
    4. Add: `@ApplicationScoped` annotation
    5. Keep: `implements ShippingServiceRemote` (interface still needed for contract)
- Why: Quarkus uses CDI instead of EJB; no need for @Remote in same JVM
- Depends on: Step 4
- Verify: `! grep '@Stateless' src/main/java/com/redhat/coolstore/service/ShippingService.java && grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 19: COMPLEX - Remove JNDI lookup and use direct injection in ShoppingCartService.java
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateful` EJB with JNDI lookup for ShippingService
  - AFTER: `@ApplicationScoped` CDI bean with direct injection
  - Specific changes:
    1. Remove: `import javax.ejb.Stateful;`, `import javax.naming.*;`, `import java.util.Hashtable;`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import jakarta.inject.Inject;`
    3. Replace: `@Stateful` → `@ApplicationScoped`
    4. Add field: `@Inject ShippingServiceRemote shippingService;`
    5. Remove method: `lookupShippingServiceRemote()` (entire method)
    6. Replace calls: `lookupShippingServiceRemote().calculateShipping(sc)` → `shippingService.calculateShipping(sc)`
    7. Replace calls: `lookupShippingServiceRemote().calculateShippingInsurance(sc)` → `shippingService.calculateShippingInsurance(sc)`
    8. Update all imports: `javax.inject.Inject` → `jakarta.inject.Inject`
  - Affected files: None (all changes in this file)
- Why: Quarkus doesn't support JNDI lookups; use CDI injection instead
- Depends on: Step 18 (ShippingService must be migrated first)
- Verify: `! grep 'lookupShippingServiceRemote' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java && grep '@Inject.*ShippingServiceRemote' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 20: Migrate EJB annotations in ShoppingCartOrderProcessor.java
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.annotation.Resource` → `jakarta.annotation.Resource` (but see Step 21 - will be replaced)
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  - Replace `javax.jms.*` → (will be replaced in messaging migration)
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 4
- Verify: `! grep '@Stateless' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java && grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 21: COMPLEX - Convert JMS producer to SmallRye Reactive Messaging in ShoppingCartOrderProcessor.java
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: JMS with `@Resource` topic lookup and JMSContext
  - AFTER: SmallRye Reactive Messaging with `@Channel` and `Emitter`
  - Specific changes:
    1. Remove imports:
       ```java
       import javax.annotation.Resource;
       import javax.jms.JMSContext;
       import javax.jms.Topic;
       ```
    2. Add imports:
       ```java
       import org.eclipse.microprofile.reactive.messaging.Channel;
       import org.eclipse.microprofile.reactive.messaging.Emitter;
       import io.smallrye.reactive.messaging.annotations.Broadcast;
       ```
    3. Remove fields:
       ```java
       @Inject
       private transient JMSContext context;
       
       @Resource(lookup = "java:/topic/orders")
       private Topic ordersTopic;
       ```
    4. Add field:
       ```java
       @Channel("orders")
       @Broadcast
       Emitter<String> ordersEmitter;
       ```
    5. Replace method body in `process()`:
       ```java
       // OLD:
       context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));
       
       // NEW:
       ordersEmitter.send(Transformers.shoppingCartToJson(cart));
       ```
  - Affected files: src/main/resources/application.properties (already configured in Step 8)
- Why: Quarkus uses SmallRye Reactive Messaging instead of JMS API
- Depends on: Step 4, Step 8, Step 20
- Verify: `grep '@Channel.*orders' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java && ! grep 'JMSContext' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 22: COMPLEX - Convert @MessageDriven to @Incoming in OrderServiceMDB.java
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: `@MessageDriven` MDB implementing MessageListener
  - AFTER: `@ApplicationScoped` CDI bean with `@Incoming` method
  - Specific changes:
    1. Remove imports:
       ```java
       import javax.ejb.ActivationConfigProperty;
       import javax.ejb.MessageDriven;
       import javax.jms.JMSException;
       import javax.jms.Message;
       import javax.jms.MessageListener;
       import javax.jms.TextMessage;
       ```
    2. Add imports:
       ```java
       import jakarta.enterprise.context.ApplicationScoped;
       import jakarta.inject.Inject;
       import org.eclipse.microprofile.reactive.messaging.Incoming;
       import io.smallrye.reactive.messaging.annotations.Blocking;
       ```
    3. Remove class annotation:
       ```java
       @MessageDriven(name = "OrderServiceMDB", activationConfig = {
         @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "topic/orders"),
         @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
         @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")})
       ```
    4. Add class annotation: `@ApplicationScoped`
    5. Remove: `implements MessageListener`
    6. Replace method signature:
       ```java
       // OLD:
       @Override
       public void onMessage(Message rcvMessage) {
       
       // NEW:
       @Incoming("orders-in")
       @Blocking
       public void onMessage(String orderStr) {
       ```
    7. Replace method body:
       ```java
       // OLD:
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
       
       // NEW:
       System.out.println("\nMessage recd !");
       System.out.println("Received order: " + orderStr);
       Order order = Transformers.jsonToOrder(orderStr);
       System.out.println("Order object is " + order);
       orderService.save(order);
       order.getItemList().forEach(orderItem -> {
           catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
       });
       ```
  - Affected files: src/main/resources/application.properties (already configured in Step 8)
- Why: Quarkus uses SmallRye Reactive Messaging @Incoming instead of @MessageDriven
- Depends on: Step 4, Step 8
- Verify: `grep '@Incoming.*orders-in' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java && ! grep '@MessageDriven' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 23: COMPLEX - Convert manual JMS listener to @Incoming in InventoryNotificationMDB.java
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Manual JMS TopicSubscriber with WebLogic JNDI configuration and lifecycle methods
  - AFTER: `@ApplicationScoped` CDI bean with `@Incoming` method
  - Specific changes:
    1. Remove imports:
       ```java
       import javax.jms.*;
       import javax.naming.Context;
       import javax.naming.InitialContext;
       import javax.naming.NamingException;
       import javax.rmi.PortableRemoteObject;
       import java.util.Hashtable;
       ```
    2. Add imports:
       ```java
       import jakarta.enterprise.context.ApplicationScoped;
       import jakarta.inject.Inject;
       import org.eclipse.microprofile.reactive.messaging.Incoming;
       import io.smallrye.reactive.messaging.annotations.Blocking;
       ```
    3. Add class annotation: `@ApplicationScoped`
    4. Remove fields:
       ```java
       private final static String JNDI_FACTORY = "weblogic.jndi.WLInitialContextFactory";
       private final static String JMS_FACTORY = "TCF";
       private final static String TOPIC = "topic/orders";
       private TopicConnection tcon;
       private TopicSession tsession;
       private TopicSubscriber tsubscriber;
       ```
    5. Remove: `implements MessageListener`
    6. Replace method signature:
       ```java
       // OLD:
       public void onMessage(Message rcvMessage) {
       
       // NEW:
       @Incoming("orders-in")
       @Blocking
       public void onMessage(String orderStr) {
       ```
    7. Replace method body:
       ```java
       // OLD:
       TextMessage msg;
       {
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
       ```
    8. Remove methods: `init()`, `close()`, `getInitialContext()` (entire methods - no longer needed)
  - Affected files: src/main/resources/application.properties (already configured in Step 8)
- Why: Quarkus uses SmallRye Reactive Messaging; eliminates manual JMS setup and WebLogic JNDI
- Depends on: Step 4, Step 8
- Verify: `grep '@Incoming.*orders-in' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java && ! grep 'TopicSubscriber' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java && ! grep 'JNDI_FACTORY' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 24: Migrate REST imports in CartEndpoint.java
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  - Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `! grep 'javax\.' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 25: Migrate REST imports in OrderEndpoint.java
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  - Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `! grep 'javax\.' src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 26: Migrate REST imports in ProductEndpoint.java
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.inject.Inject` → `jakarta.inject.Inject`
  - Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `! grep 'javax\.' src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 27: Migrate REST imports in RestApplication.java
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.ApplicationPath` → `jakarta.ws.rs.ApplicationPath`
  - Replace `javax.ws.rs.core.Application` → `jakarta.ws.rs.core.Application`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `! grep 'javax\.' src/main/java/com/redhat/coolstore/rest/RestApplication.java`

### Step 28: Migrate imports in DataBaseMigrationStartup.java
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - Replace `javax.annotation.PostConstruct` → `jakarta.annotation.PostConstruct`
  - Replace `javax.ejb.*` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace any other `javax.*` → `jakarta.*` imports
- Why: Quarkus 3 uses Jakarta EE namespace and CDI instead of EJB
- Depends on: Step 4
- Verify: `! grep 'javax\.' src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`

### Step 29: Migrate imports in Producers.java
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Replace `javax.enterprise.inject.Produces` → `jakarta.enterprise.inject.Produces`
  - Replace any other `javax.*` → `jakarta.*` imports
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `! grep 'javax\.' src/main/java/com/redhat/coolstore/utils/Producers.java`

### Step 30: COMPLEX - Replace WebLogic lifecycle listener with Quarkus lifecycle events in StartupListener.java
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - BEFORE: WebLogic ApplicationLifecycleListener with postStart/preStop
  - AFTER: Quarkus CDI bean with @Observes lifecycle events
  - Specific changes:
    1. Remove imports:
       ```java
       import weblogic.application.ApplicationLifecycleEvent;
       import weblogic.application.ApplicationLifecycleListener;
       ```
    2. Add imports:
       ```java
       import jakarta.enterprise.context.ApplicationScoped;
       import jakarta.enterprise.event.Observes;
       import io.quarkus.runtime.StartupEvent;
       import io.quarkus.runtime.ShutdownEvent;
       import jakarta.inject.Inject;
       import java.util.logging.Logger;
       ```
    3. Replace class declaration:
       ```java
       // OLD:
       public class StartupListener extends ApplicationLifecycleListener {
       
       // NEW:
       @ApplicationScoped
       public class StartupListener {
       ```
    4. Replace methods:
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
  - Affected files: None (WebLogic stubs will be deleted in cleanup)
- Why: Quarkus uses CDI observer pattern for lifecycle events, not WebLogic-specific listeners
- Depends on: Step 4
- Verify: `grep '@Observes StartupEvent' src/main/java/com/redhat/coolstore/utils/StartupListener.java && ! grep 'ApplicationLifecycleListener' src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Step 31: Delete persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Remove file completely
- Why: Quarkus uses application.properties for datasource configuration
- Depends on: Step 8
- Verify: `! test -f src/main/resources/META-INF/persistence.xml`

### Step 32: Delete web.xml
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Remove file completely
- Why: Quarkus doesn't use web.xml; auto-configures servlets
- Depends on: Step 1
- Verify: `! test -f src/main/webapp/WEB-INF/web.xml`

### Step 33: Delete beans.xml
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Remove file completely
- Why: Quarkus auto-enables CDI; beans.xml not required
- Depends on: Step 1
- Verify: `! test -f src/main/webapp/WEB-INF/beans.xml`

### Step 34: Delete WebLogic ApplicationLifecycleEvent stub
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Remove file completely
- Why: No longer needed after migrating to Quarkus lifecycle events
- Depends on: Step 30
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleEvent.java`

### Step 35: Delete WebLogic ApplicationLifecycleListener stub
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Remove file completely
- Why: No longer needed after migrating to Quarkus lifecycle events
- Depends on: Step 30
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleListener.java`

### Step 36: Delete WebLogic NonCatalogLogger stub
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Remove file completely
- Why: WebLogic-specific logging not needed in Quarkus
- Depends on: Step 30
- Verify: `! test -f src/main/java/weblogic/i18n/logging/NonCatalogLogger.java`

### Step 37: Update ShippingServiceRemote interface imports
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: MODIFY
- What to do: Remove `import javax.ejb.Remote;` and `@Remote` annotation if present; keep as plain Java interface
- Why: No longer an EJB Remote interface, just a contract interface
- Depends on: Step 18
- Verify: `! grep '@Remote' src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java`

## Verification

After completing all steps, run these commands in order:

1. **Clean build**: `mvn clean`
2. **Compile**: `mvn compile` (must succeed)
3. **Run tests**: `mvn test` (should pass or show only test-specific failures)
4. **Package**: `mvn package` (produces a JAR, not WAR)
5. **Verify JAR packaging**: `ls -lh target/*.jar` (should see a Quarkus runner JAR)
6. **Dev mode test**: `mvn quarkus:dev` (start Quarkus in dev mode, verify no errors)
7. **Verify no javax imports remain**: `grep -r "import javax\." src/main/java --include="*.java" || echo "Clean!"`
8. **Verify no EJB annotations remain**: `grep -r "@Stateless\|@Stateful\|@MessageDriven" src/main/java --include="*.java" || echo "Clean!"`
9. **Verify no JNDI lookups remain**: `grep -r "InitialContext\|JNDI" src/main/java --include="*.java" || echo "Clean!"`
10. **Verify SmallRye Messaging**: `grep -r "@Incoming\|@Channel" src/main/java --include="*.java" | wc -l` (should be >= 3)

## Notes

### Gotchas
- **Two different MDB patterns**: OrderServiceMDB uses `@MessageDriven`, InventoryNotificationMDB uses manual JMS setup - both convert to `@Incoming` but different starting points
- **JNDI removal**: ShoppingCartService uses JNDI to look up ShippingService - must replace with direct injection after ShippingService is converted
- **WebLogic stubs**: Three stub files exist just to compile the app outside WebLogic - safe to delete after lifecycle migration
- **Stateful EJB**: ShoppingCartService is `@Stateful` - converting to `@ApplicationScoped` changes session semantics (may need `@SessionScoped` if session state is critical)
- **System libraries**: audit-logging-library in lib/ folder uses `<systemPath>` - may need to be installed to local Maven repo or converted to proper dependency

### Design Decisions
- **Messaging**: Using AMQP (via SmallRye) instead of JMS because Quarkus favors reactive messaging
- **Datasource**: Assuming PostgreSQL based on common practice; original persistence.xml used generic JNDI `java:jboss/datasources/CoolstoreDS`
- **Session scope**: Converting `@Stateful` to `@ApplicationScoped` - may need to revisit if shopping cart state management requires session scope
- **Port**: Using default Quarkus port 8080 (was likely same in Java EE app)

### Migration Order Rationale
1. Build config first (foundation)
2. App config second (establishes new configuration approach)
3. Simple imports third (models, persistence) - lowest risk
4. EJB to CDI fourth (services) - medium risk, no JNDI yet
5. Complex JNDI/Remote last - highest risk, depends on services being CDI
6. Messaging conversion - complex, isolated from other changes
7. Lifecycle events - isolated change
8. Cleanup last - only after everything else works

### File Count Breakdown
- Models: 8 files (simple import changes)
- Persistence: 1 file (simple import changes)
- Services: 10 files (4 simple EJB→CDI, 1 complex JNDI, 2 complex MDB, 3 message-related)
- REST: 4 files (simple import changes)
- Utils: 4 files (1 simple import, 1 complex lifecycle)
- Config: 3 deletions, 1 creation
- WebLogic stubs: 3 deletions
- Build: 1 file (multiple complex changes)

**Total**: ~40 files modified/created/deleted
