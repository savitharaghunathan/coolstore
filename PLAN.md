# PLAN.md

## Goal
Migrate this Java EE 7 monolith application (WebLogic/JBoss) to Quarkus 3, converting from WAR packaging to standalone JAR with CDI beans, replacing JMS/MDB with SmallRye Reactive Messaging, and eliminating JNDI lookups and application server dependencies.

- Reference used: javaee-to-quarkus migration skill (Java EE 7/8 → Quarkus 3)

## Project Summary
- Type: Maven WAR project (Java EE 7)
- Files affected: 51 Java files + pom.xml + config files
- Estimated complexity: **High**
- Hardest steps:
  1. Converting OrderServiceMDB and InventoryNotificationMDB from JMS MessageListener to SmallRye Reactive Messaging
  2. Replacing JNDI-based JMS producer (InventoryNotificationMDB manual connection setup) with Quarkus injection
  3. Converting WebLogic ApplicationLifecycleListener to Quarkus lifecycle events

## Architecture Analysis
The application follows a layered architecture:
- **Model layer** (8 entities): CatalogItemEntity, InventoryEntity, Order, OrderItem, Product, Promotion, ShoppingCart, ShoppingCartItem
- **Persistence layer**: Resources.java (EntityManager producer)
- **Service layer** (7 services): CatalogService, OrderService, ProductService, PromoService, ShippingService, ShoppingCartService, ShoppingCartOrderProcessor
- **Messaging layer** (2 MDBs): OrderServiceMDB, InventoryNotificationMDB
- **REST layer** (4 endpoints): CartEndpoint, OrderEndpoint, ProductEndpoint, RestApplication
- **Lifecycle**: StartupListener (WebLogic-specific)

## Steps

### Step 1: Update pom.xml - Change packaging to JAR
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus applications are packaged as standalone JARs, not WARs
- Depends on: none
- Verify: `grep -q '<packaging>jar</packaging>' pom.xml`

### Step 2: Update pom.xml - Add Quarkus BOM
- File: pom.xml
- Action: MODIFY
- What to do: 
  - Add `<dependencyManagement>` section after `<properties>`
  - Add Quarkus BOM:
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
- Verify: `grep -q 'quarkus-bom' pom.xml`

### Step 3: Update pom.xml - Replace Java EE dependencies with Quarkus extensions
- File: pom.xml
- Action: MODIFY
- What to do:
  - Remove: `javaee-web-api`, `javaee-api`, `jboss-jms-api_2.0_spec`, `jboss-rmi-api_1.0_spec`
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
        <artifactId>quarkus-jdbc-postgresql</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-smallrye-reactive-messaging-kafka</artifactId>
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
- Why: Replace Java EE APIs with Quarkus equivalents
- Depends on: Step 2
- Verify: `grep -q 'quarkus-resteasy-reactive-jackson' pom.xml && ! grep -q 'javaee-web-api' pom.xml`

### Step 4: Update pom.xml - Add Quarkus Maven plugin
- File: pom.xml
- Action: MODIFY
- What to do:
  - Replace `maven-war-plugin` with `quarkus-maven-plugin`
  - Update compiler plugin source/target to 17
  - Add:
    ```xml
    <plugin>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-maven-plugin</artifactId>
        <version>3.8.0</version>
        <executions>
            <execution>
                <goals>
                    <goal>build</goal>
                </goals>
            </execution>
        </executions>
    </plugin>
    ```
- Why: Quarkus plugin required for building Quarkus applications
- Depends on: Step 1
- Verify: `grep -q 'quarkus-maven-plugin' pom.xml && grep -q '<source>17</source>' pom.xml`

### Step 5: Update pom.xml - Handle system-scoped audit library dependency
- File: pom.xml
- Action: MODIFY
- What to do:
  - Change audit-logging-library from `<scope>system</scope>` to manual installation in local Maven repo OR remove systemPath and install to local repo:
    ```bash
    mvn install:install-file -Dfile=lib/audit-logging-library-1.0.0.jar -DgroupId=com.enterprise -DartifactId=audit-logging-library -Dversion=1.0.0 -Dpackaging=jar
    ```
  - Update dependency to use `<scope>compile</scope>` instead of system scope
- Why: Quarkus build process doesn't handle system-scoped dependencies well
- Depends on: Step 3
- Verify: `grep -q 'audit-logging-library' pom.xml && ! grep -q 'systemPath' pom.xml`

### Step 6: Create application.properties
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create Quarkus configuration replacing persistence.xml settings:
  ```properties
  # Database configuration
  quarkus.datasource.db-kind=postgresql
  quarkus.datasource.username=coolstore
  quarkus.datasource.password=coolstore
  quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore
  
  # Hibernate configuration
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=false
  quarkus.hibernate-orm.sql-load-script=no-file
  
  # Flyway migration
  quarkus.flyway.migrate-at-start=true
  quarkus.flyway.locations=classpath:db/migration
  
  # Kafka/Messaging configuration
  mp.messaging.outgoing.orders.connector=smallrye-kafka
  mp.messaging.outgoing.orders.topic=orders
  mp.messaging.outgoing.orders.value.serializer=org.apache.kafka.common.serialization.StringSerializer
  
  mp.messaging.incoming.orders.connector=smallrye-kafka
  mp.messaging.incoming.orders.topic=orders
  mp.messaging.incoming.orders.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
  
  # Application configuration
  quarkus.http.port=8080
  quarkus.http.cors=true
  ```
- Why: Quarkus uses application.properties instead of XML configuration files
- Depends on: none
- Verify: `test -f src/main/resources/application.properties && grep -q 'quarkus.datasource' src/main/resources/application.properties`

### Step 7: Migrate CatalogItemEntity imports
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: Step 3
- Verify: `grep -q 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java && ! grep -q 'javax.persistence' src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`

### Step 8: Migrate InventoryEntity imports
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: Step 3
- Verify: `grep -q 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/InventoryEntity.java && ! grep -q 'javax.persistence' src/main/java/com/redhat/coolstore/model/InventoryEntity.java`

### Step 9: Migrate Order imports
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: Step 3
- Verify: `grep -q 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/Order.java && ! grep -q 'javax.persistence' src/main/java/com/redhat/coolstore/model/Order.java`

### Step 10: Migrate OrderItem imports
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: Step 3
- Verify: `grep -q 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/OrderItem.java && ! grep -q 'javax.persistence' src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 11: Migrate Resources.java persistence producer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Replace `javax.persistence.*` with `jakarta.persistence.*`
  - Replace `javax.enterprise.*` with `jakarta.enterprise.*`
  - Change `@PersistenceContext` to direct injection with `@Inject`
- Why: Quarkus manages EntityManager through CDI injection
- Depends on: Step 3
- Verify: `grep -q 'jakarta.persistence' src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 12: Migrate CatalogService from EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Remove: `import javax.ejb.Stateless;` and `@Stateless`
  - Add: `import jakarta.enterprise.context.ApplicationScoped;` and `@ApplicationScoped`
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
  - Replace `javax.persistence.*` with `jakarta.persistence.*`
- Why: Quarkus uses CDI @ApplicationScoped instead of EJB @Stateless
- Depends on: Step 3
- Verify: `grep -q '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/CatalogService.java && ! grep -q '@Stateless' src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 13: Migrate OrderService from EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Remove: `import javax.ejb.Stateless;` and `@Stateless`
  - Add: `import jakarta.enterprise.context.ApplicationScoped;` and `@ApplicationScoped`
  - Replace all `javax.*` imports with `jakarta.*` equivalents
- Why: Quarkus uses CDI @ApplicationScoped instead of EJB @Stateless
- Depends on: Step 3
- Verify: `grep -q '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/OrderService.java && ! grep -q '@Stateless' src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 14: Migrate ProductService from EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Remove: `import javax.ejb.Stateless;` and `@Stateless`
  - Add: `import jakarta.enterprise.context.ApplicationScoped;` and `@ApplicationScoped`
  - Replace all `javax.*` imports with `jakarta.*` equivalents
- Why: Quarkus uses CDI @ApplicationScoped instead of EJB @Stateless
- Depends on: Step 3
- Verify: `grep -q '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ProductService.java && ! grep -q '@Stateless' src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 15: Migrate ShippingService from EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Remove: `import javax.ejb.Stateless;` and `@Stateless`
  - Add: `import jakarta.enterprise.context.ApplicationScoped;` and `@ApplicationScoped`
  - Replace all `javax.*` imports with `jakarta.*` equivalents
- Why: Quarkus uses CDI @ApplicationScoped instead of EJB @Stateless
- Depends on: Step 3
- Verify: `grep -q '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShippingService.java && ! grep -q '@Stateless' src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 16: COMPLEX - Convert ShoppingCartOrderProcessor from JMS producer to reactive emitter
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: EJB @Stateless with JMS Topic injection via @Resource and JMSContext
  - AFTER: CDI @ApplicationScoped with SmallRye Reactive Messaging @Channel Emitter
  - Specific changes:
    1. Remove: `import javax.ejb.Stateless;`, `import javax.annotation.Resource;`, `import javax.jms.*;`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import org.eclipse.microprofile.reactive.messaging.Channel;`, `import org.eclipse.microprofile.reactive.messaging.Emitter;`
    3. Replace:
       ```java
       @Stateless
       public class ShoppingCartOrderProcessor {
           @Inject private transient JMSContext context;
           @Resource(lookup = "java:/topic/orders") private Topic ordersTopic;
           public void process(ShoppingCart cart) {
               context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));
           }
       }
       ```
       WITH:
       ```java
       @ApplicationScoped
       public class ShoppingCartOrderProcessor {
           @Inject
           @Channel("orders")
           Emitter<String> ordersEmitter;
           
           public void process(ShoppingCart cart) {
               ordersEmitter.send(Transformers.shoppingCartToJson(cart));
           }
       }
       ```
    4. Replace `javax.inject.Inject` with `jakarta.inject.Inject`
- Why: Quarkus uses SmallRye Reactive Messaging instead of JMS API
- Depends on: Step 3, Step 6 (messaging configuration)
- Verify: `grep -q '@Channel("orders")' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java && ! grep -q 'JMSContext' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 17: COMPLEX - Convert OrderServiceMDB from MessageDriven to reactive consumer
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: @MessageDriven MDB implementing MessageListener with JMS API
  - AFTER: CDI @ApplicationScoped bean with @Incoming reactive method
  - Specific changes:
    1. Remove: `import javax.ejb.*;`, `import javax.jms.*;`, `implements MessageListener`
    2. Remove: `@MessageDriven` annotation and all `@ActivationConfigProperty`
    3. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    4. Replace:
       ```java
       @MessageDriven(name = "OrderServiceMDB", activationConfig = {...})
       public class OrderServiceMDB implements MessageListener {
           @Override
           public void onMessage(Message rcvMessage) {
               TextMessage msg = (TextMessage) rcvMessage;
               String orderStr = msg.getBody(String.class);
               // ... process order
           }
       }
       ```
       WITH:
       ```java
       @ApplicationScoped
       public class OrderServiceMDB {
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
       }
       ```
    5. Replace `javax.inject.Inject` with `jakarta.inject.Inject`
    6. Remove try-catch for JMSException (no longer needed)
- Why: Quarkus uses SmallRye Reactive Messaging @Incoming instead of MDB
- Depends on: Step 3, Step 6 (messaging configuration)
- Verify: `grep -q '@Incoming("orders")' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java && ! grep -q '@MessageDriven' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 18: COMPLEX - Convert InventoryNotificationMDB from manual JNDI/JMS to reactive consumer
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Manual JNDI lookup and JMS subscription with WebLogic-specific InitialContext
  - AFTER: Simple CDI bean with @Incoming reactive method
  - Specific changes:
    1. Remove: All JNDI/JMS imports (`javax.jms.*`, `javax.naming.*`, `javax.rmi.PortableRemoteObject`, `java.util.Hashtable`)
    2. Remove: All manual connection management (init(), close(), getInitialContext() methods)
    3. Remove: JNDI_FACTORY, JMS_FACTORY, TOPIC constants and connection fields
    4. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    5. Replace:
       ```java
       public class InventoryNotificationMDB implements MessageListener {
           public void onMessage(Message rcvMessage) {
               TextMessage msg = (TextMessage) rcvMessage;
               String orderStr = msg.getBody(String.class);
               // ... process
           }
           public void init() throws NamingException, JMSException { /* JNDI setup */ }
           public void close() throws JMSException { /* cleanup */ }
           private static InitialContext getInitialContext() { /* WebLogic JNDI */ }
       }
       ```
       WITH:
       ```java
       @ApplicationScoped
       public class InventoryNotificationMDB {
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
       }
       ```
    6. Replace `javax.inject.Inject` with `jakarta.inject.Inject`
    7. Remove try-catch blocks for JMSException
- Why: Quarkus provides automatic message consumption via SmallRye Reactive Messaging, eliminating manual JNDI/JMS setup
- Depends on: Step 3, Step 6 (messaging configuration)
- Verify: `grep -q '@Incoming("orders")' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java && ! grep -q 'InitialContext' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 19: Migrate ShoppingCartService namespace imports
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do: Replace all `javax.*` imports with `jakarta.*` equivalents
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: Step 3
- Verify: `! grep -q 'import javax\.' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java || grep -q 'import javax.xml' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 20: Migrate PromoService namespace imports
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do: Replace all `javax.*` imports with `jakarta.*` equivalents (if any EE imports exist)
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: Step 3
- Verify: `! grep -q 'import javax\.enterprise\|import javax\.inject\|import javax\.persistence' src/main/java/com/redhat/coolstore/service/PromoService.java`

### Step 21: Migrate CartEndpoint REST endpoint
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.*` with `jakarta.ws.rs.*`
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
  - Replace `javax.enterprise.context.RequestScoped` with `jakarta.enterprise.context.RequestScoped`
- Why: Quarkus 3 uses Jakarta EE 9+ namespace for REST APIs
- Depends on: Step 3
- Verify: `grep -q 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java && ! grep -q 'javax.ws.rs' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 22: Migrate OrderEndpoint REST endpoint
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.*` with `jakarta.ws.rs.*`
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
- Why: Quarkus 3 uses Jakarta EE 9+ namespace for REST APIs
- Depends on: Step 3
- Verify: `grep -q 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java && ! grep -q 'javax.ws.rs' src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 23: Migrate ProductEndpoint REST endpoint
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.*` with `jakarta.ws.rs.*`
  - Replace `javax.inject.Inject` with `jakarta.inject.Inject`
- Why: Quarkus 3 uses Jakarta EE 9+ namespace for REST APIs
- Depends on: Step 3
- Verify: `grep -q 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java && ! grep -q 'javax.ws.rs' src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 24: Migrate RestApplication
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.ApplicationPath` with `jakarta.ws.rs.ApplicationPath`
  - Replace `javax.ws.rs.core.Application` with `jakarta.ws.rs.core.Application`
- Why: Quarkus 3 uses Jakarta EE 9+ namespace for REST APIs
- Depends on: Step 3
- Verify: `grep -q 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/RestApplication.java && ! grep -q 'javax.ws.rs' src/main/java/com/redhat/coolstore/rest/RestApplication.java`

### Step 25: COMPLEX - Convert StartupListener from WebLogic lifecycle to Quarkus events
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - BEFORE: WebLogic ApplicationLifecycleListener with postStart/preStop methods
  - AFTER: CDI bean with Quarkus @Observes lifecycle events
  - Specific changes:
    1. Remove: `import weblogic.application.*;`, `extends ApplicationLifecycleListener`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`, `import jakarta.enterprise.event.Observes;`, `import io.quarkus.runtime.StartupEvent;`, `import io.quarkus.runtime.ShutdownEvent;`
    3. Replace:
       ```java
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
       WITH:
       ```java
       @ApplicationScoped
       public class StartupListener {
           void onStart(@Observes StartupEvent evt) {
               log.info("AppListener(postStart)");
           }
           void onStop(@Observes ShutdownEvent evt) {
               log.info("AppListener(preStop)");
           }
       }
       ```
    4. Replace `javax.inject.Inject` with `jakarta.inject.Inject`
- Why: Quarkus uses CDI event observers instead of application server-specific lifecycle listeners
- Depends on: Step 3
- Verify: `grep -q '@Observes StartupEvent' src/main/java/com/redhat/coolstore/utils/StartupListener.java && ! grep -q 'ApplicationLifecycleListener' src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Step 26: Migrate Transformers utility class namespace imports
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: Replace any `javax.*` imports with `jakarta.*` equivalents (especially javax.json if present)
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: Step 3
- Verify: `! grep -q 'import javax\.json\|import javax\.enterprise' src/main/java/com/redhat/coolstore/utils/Transformers.java || echo "Check manually"`

### Step 27: Update all test files namespace imports
- File: src/test/java/**/*.java (all test files)
- Action: MODIFY
- What to do: Replace all `javax.*` imports with `jakarta.*` equivalents in all test files
- Why: Quarkus 3 uses Jakarta EE 9+ namespace
- Depends on: Step 3
- Verify: `! find src/test/java -name "*.java" -exec grep -l 'import javax\.(persistence\|inject\|enterprise)' {} \; | grep -q .`

### Step 28: Delete persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file (configuration moved to application.properties)
- Why: Quarkus uses application.properties for persistence configuration
- Depends on: Step 6
- Verify: `! test -f src/main/resources/META-INF/persistence.xml`

### Step 29: Delete web.xml
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file (not needed for JAR packaging)
- Why: Quarkus applications don't use web.xml
- Depends on: Step 1
- Verify: `! test -f src/main/webapp/WEB-INF/web.xml`

### Step 30: Delete beans.xml (optional)
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file OR move to src/main/resources/META-INF/beans.xml if CDI discovery customization is needed
- Why: Quarkus has CDI enabled by default; beans.xml only needed for custom discovery modes
- Depends on: Step 1
- Verify: `! test -f src/main/webapp/WEB-INF/beans.xml || test -f src/main/resources/META-INF/beans.xml`

### Step 31: Delete WebLogic ApplicationLifecycleListener stub
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this WebLogic stub file (no longer needed)
- Why: Removed dependency on WebLogic-specific APIs
- Depends on: Step 25
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleListener.java`

### Step 32: Delete ApplicationLifecycleEvent stub (if exists)
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this WebLogic stub file if it exists
- Why: Removed dependency on WebLogic-specific APIs
- Depends on: Step 25
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleEvent.java`

### Step 33: Move static web resources
- File: src/main/webapp/* (excluding WEB-INF)
- Action: MODIFY
- What to do: Move static resources from src/main/webapp to src/main/resources/META-INF/resources
  ```bash
  mkdir -p src/main/resources/META-INF/resources
  cp -r src/main/webapp/app src/main/resources/META-INF/resources/
  cp -r src/main/webapp/bower_components src/main/resources/META-INF/resources/
  cp -r src/main/webapp/partials src/main/resources/META-INF/resources/
  cp src/main/webapp/*.jsp src/main/resources/META-INF/resources/
  cp src/main/webapp/*.json src/main/resources/META-INF/resources/
  ```
- Why: Quarkus JAR packaging requires static resources in META-INF/resources
- Depends on: Step 1
- Verify: `test -d src/main/resources/META-INF/resources && test -f src/main/resources/META-INF/resources/index.jsp`

### Step 34: Delete webapp directory
- File: src/main/webapp (entire directory)
- Action: DELETE
- What to do: Remove the webapp directory after resources are moved
- Why: No longer needed for JAR packaging
- Depends on: Step 33
- Verify: `! test -d src/main/webapp`

### Step 35: Update database migration scripts location (if needed)
- File: src/main/resources/db/* (migration scripts)
- Action: MODIFY
- What to do: Ensure Flyway migration scripts are in src/main/resources/db/migration directory
- Why: Quarkus Flyway expects migrations in db/migration by default
- Depends on: Step 6
- Verify: `test -d src/main/resources/db/migration || echo "Verify migration location"`

## Verification

After completing all steps, verify the migration:

```bash
# Build the application
mvn clean package

# Check for any remaining javax.* imports (should only see javax.xml, javax.crypto, etc. - JDK packages)
find src/main/java -name "*.java" -exec grep -H 'import javax\.' {} \; | grep -v 'javax.xml\|javax.crypto'

# Run the application
mvn quarkus:dev

# Test REST endpoints
curl http://localhost:8080/api/products
curl http://localhost:8080/api/cart/123

# Check Kafka consumer is registered
# (Look for "Configured incoming channel" in startup logs)

# Run tests
mvn test
```

## Notes

### Migration Complexity Drivers
1. **Two MDB classes** require complete restructuring from JMS MessageListener to SmallRye Reactive Messaging @Incoming methods
2. **JNDI manual lookup** in InventoryNotificationMDB requires complete removal of WebLogic-specific InitialContext setup
3. **WebLogic lifecycle listener** requires conversion to Quarkus CDI event observers
4. **WAR to JAR packaging** requires moving static resources to META-INF/resources

### Key Decisions Made
- Using **Kafka** as the messaging backend for SmallRye Reactive Messaging (alternative: AMQP/RabbitMQ)
- Using **PostgreSQL** as the database (can be changed in application.properties)
- Using **Hibernate ORM** with Panache (simpler than vanilla Hibernate ORM)
- Keeping entity classes as-is (not converting to Panache entities) to minimize changes

### Post-Migration Tasks
1. Set up Kafka broker (required for messaging)
2. Set up PostgreSQL database (replace H2 or existing DB)
3. Update deployment configuration for Kubernetes/OpenShift
4. Configure environment-specific properties (dev, test, prod)
5. Update CI/CD pipelines for Quarkus build process
6. Test messaging flows end-to-end
7. Performance test and optimize (Quarkus native image if needed)

### Gotchas
- **Audit library**: System-scoped dependency needs to be installed in local Maven repo
- **Kafka required**: Application won't start without Kafka broker unless messaging is disabled
- **Database schema**: Ensure Flyway migrations run successfully on first startup
- **Static resources**: JSP files may need special handling or conversion to static HTML if JSP features are used
- **CORS**: Enabled in application.properties - adjust for production
- **Java 17**: Minimum JDK version for Quarkus 3

### Additional Files That May Need Migration
(Not found in initial scan, but check if they exist):
- Security configuration files
- Additional JMS queue/topic consumers
- EJB Timer services (convert to Quarkus Scheduler)
- Application server-specific deployment descriptors
