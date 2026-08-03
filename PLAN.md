# PLAN.md

## Goal
Migrate a Java EE 7 monolith application from WebLogic/JBoss to Quarkus 3.x standalone runtime.
- Reference used: javaee-to-quarkus (Java EE 7/8 to Quarkus 3 migration skill)

## Project Summary
- Type: Maven WAR (Java EE 7) → Maven JAR (Quarkus 3)
- Java files affected: 29
- Estimated complexity: **High**
- Hardest steps:
  1. Converting 2 Message Driven Beans to SmallRye Reactive Messaging
  2. Replacing WebLogic-specific JNDI lookups with CDI injection
  3. Migrating WebLogic ApplicationLifecycleListener to Quarkus lifecycle events

## Steps

### Step 1: Upgrade Java version to 17
- File: pom.xml
- Action: MODIFY
- What to do: Change `<source>1.8</source>` and `<target>1.8</target>` to `<source>17</source>` and `<target>17</target>`
- Why: Quarkus 3 requires Java 17 minimum
- Depends on: none
- Verify: `mvn clean compile` succeeds

### Step 2: Change packaging from WAR to JAR
- File: pom.xml
- Action: MODIFY
- What to do: Replace `<packaging>war</packaging>` with `<packaging>jar</packaging>`
- Why: Quarkus produces standalone JAR files, not WAR files
- Depends on: Step 1
- Verify: Grep confirms `<packaging>jar</packaging>` in pom.xml

### Step 3: Add Quarkus BOM (Bill of Materials)
- File: pom.xml
- Action: MODIFY
- What to do: Add dependencyManagement section after `<properties>`:
  ```xml
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.quarkus.platform</groupId>
        <artifactId>quarkus-bom</artifactId>
        <version>3.8.1</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  ```
- Why: Quarkus BOM manages all Quarkus extension versions
- Depends on: Step 2
- Verify: Grep confirms `quarkus-bom` in pom.xml

### Step 4: Replace Java EE dependencies with Quarkus extensions
- File: pom.xml
- Action: MODIFY
- What to do:
  - REMOVE:
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
  - ADD (without version tags - managed by BOM):
    ```xml
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
      <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
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
- Why: Replace Java EE APIs with Quarkus extensions for CDI, JPA, REST, and messaging
- Depends on: Step 3
- Verify: `mvn dependency:tree | grep quarkus` shows Quarkus dependencies

### Step 5: Add Quarkus Maven plugin
- File: pom.xml
- Action: MODIFY
- What to do: Replace `maven-war-plugin` with Quarkus plugin in `<plugins>`:
  ```xml
  <plugin>
    <groupId>io.quarkus.platform</groupId>
    <artifactId>quarkus-maven-plugin</artifactId>
    <version>3.8.1</version>
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
- Why: Quarkus Maven plugin handles JAR packaging and code generation
- Depends on: Step 4
- Verify: Grep confirms `quarkus-maven-plugin` in pom.xml

### Step 6: Update compiler plugin configuration
- File: pom.xml
- Action: MODIFY
- What to do: Update maven-compiler-plugin configuration to add parameters flag:
  ```xml
  <plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
      <encoding>${project.encoding}</encoding>
      <release>17</release>
      <parameters>true</parameters>
    </configuration>
  </plugin>
  ```
- Why: Quarkus requires parameter names for CDI injection
- Depends on: Step 5
- Verify: `mvn clean compile` succeeds

### Step 7: Create Quarkus application.properties
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create new file with datasource and messaging configuration:
  ```properties
  # Datasource configuration
  quarkus.datasource.db-kind=h2
  quarkus.datasource.username=sa
  quarkus.datasource.password=
  quarkus.datasource.jdbc.url=jdbc:h2:mem:coolstore;DB_CLOSE_DELAY=-1
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=false
  quarkus.hibernate-orm.sql-load-script=no-file
  
  # Flyway configuration
  quarkus.flyway.migrate-at-start=true
  quarkus.flyway.locations=classpath:db/migration
  
  # AMQP Messaging configuration (for dev/test)
  mp.messaging.incoming.orders.connector=smallrye-in-memory
  mp.messaging.outgoing.orders.connector=smallrye-in-memory
  
  # HTTP configuration
  quarkus.http.port=8080
  ```
- Why: Quarkus uses application.properties instead of persistence.xml for configuration
- Depends on: Step 6
- Verify: File exists at src/main/resources/application.properties

### Step 8: Migrate persistence.xml to application.properties (configuration only)
- File: src/main/resources/META-INF/persistence.xml
- Action: MODIFY (will delete in cleanup phase)
- What to do: Note that configuration has been moved to application.properties (Step 7)
  - `jta-data-source` → `quarkus.datasource.*` properties
  - `hibernate.show_sql` → `quarkus.hibernate-orm.log.sql`
  - Schema generation handled by Flyway
- Why: Quarkus uses centralized application.properties
- Depends on: Step 7
- Verify: Application.properties contains datasource config

### Step 9: Replace @Stateless in CatalogService
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - REMOVE: `import javax.ejb.Stateless;` and `@Stateless` annotation
  - ADD: `import jakarta.enterprise.context.ApplicationScoped;` and `@ApplicationScoped` annotation
- Why: Quarkus uses CDI @ApplicationScoped instead of EJB @Stateless
- Depends on: Step 8
- Verify: `grep -l "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 10: Replace @Stateless in OrderService
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - REMOVE: `import javax.ejb.Stateless;` and `@Stateless` annotation
  - ADD: `import jakarta.enterprise.context.ApplicationScoped;` and `@ApplicationScoped` annotation
- Why: Quarkus uses CDI @ApplicationScoped instead of EJB @Stateless
- Depends on: Step 8
- Verify: `grep -l "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 11: Replace @Stateless in ProductService
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - REMOVE: `import javax.ejb.Stateless;` and `@Stateless` annotation
  - ADD: `import jakarta.enterprise.context.ApplicationScoped;` and `@ApplicationScoped` annotation
- Why: Quarkus uses CDI @ApplicationScoped instead of EJB @Stateless
- Depends on: Step 8
- Verify: `grep -l "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 12: Replace @Stateless in PromoService
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - REMOVE: `import javax.ejb.Stateless;` and `@Stateless` annotation
  - ADD: `import jakarta.enterprise.context.ApplicationScoped;` and `@ApplicationScoped` annotation
- Why: Quarkus uses CDI @ApplicationScoped instead of EJB @Stateless
- Depends on: Step 8
- Verify: `grep -l "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/PromoService.java`

### Step 13: COMPLEX — Migrate @Stateless @Remote ShippingService to CDI
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless @Remote` EJB with remote interface
  - AFTER: `@ApplicationScoped` CDI bean (remove @Remote, keep interface implementation)
  - REMOVE: `import javax.ejb.Stateless;`, `import javax.ejb.Remote;`, `@Stateless`, `@Remote`
  - ADD: `import jakarta.enterprise.context.ApplicationScoped;`, `@ApplicationScoped`
  - NOTE: Remote interface becomes local - JNDI lookup callers must be updated
- Why: Quarkus doesn't support @Remote EJBs; use CDI with direct injection instead
- Depends on: Step 8
- Verify: `grep -l "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 14: Replace @Stateful in ShoppingCartService
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - REMOVE: `import javax.ejb.Stateful;` and `@Stateful` annotation
  - ADD: `import jakarta.enterprise.context.SessionScoped;` and `@SessionScoped` annotation
  - ADD: `import java.io.Serializable;` and make class implement `Serializable`
- Why: Quarkus uses CDI @SessionScoped instead of EJB @Stateful
- Depends on: Step 8
- Verify: `grep -l "@SessionScoped" src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 15: COMPLEX — Remove JNDI lookup in ShoppingCartService
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: Static `lookupShippingServiceRemote()` method using InitialContext and JNDI
  - AFTER: Direct CDI injection
  - REMOVE: Entire `lookupShippingServiceRemote()` method and all JNDI imports:
    ```java
    import java.util.Hashtable;
    import javax.naming.Context;
    import javax.naming.InitialContext;
    import javax.naming.NamingException;
    ```
  - ADD: Inject ShippingService as field:
    ```java
    @Inject
    ShippingService shippingService;
    ```
  - REPLACE: All calls to `lookupShippingServiceRemote()` with `shippingService`
    - Example: `lookupShippingServiceRemote().calculateShipping(sc)` → `shippingService.calculateShipping(sc)`
- Why: Quarkus uses CDI injection instead of JNDI lookups
- Depends on: Step 13, Step 14
- Verify: `grep -v "InitialContext\|lookupShipping" src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 16: Replace @Stateless in ShoppingCartOrderProcessor
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - REMOVE: `import javax.ejb.Stateless;` and `@Stateless` annotation
  - ADD: `import jakarta.enterprise.context.ApplicationScoped;` and `@ApplicationScoped` annotation
- Why: Quarkus uses CDI @ApplicationScoped instead of EJB @Stateless
- Depends on: Step 8
- Verify: `grep -l "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 17: COMPLEX — Convert JMS producer to SmallRye Emitter in ShoppingCartOrderProcessor
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - BEFORE: JMS API with `@Inject JMSContext` and `@Resource Topic`
  - AFTER: SmallRye Reactive Messaging with `@Channel Emitter`
  - REMOVE:
    ```java
    import javax.annotation.Resource;
    import javax.jms.JMSContext;
    import javax.jms.Topic;
    
    @Inject
    private transient JMSContext context;
    
    @Resource(lookup = "java:/topic/orders")
    private Topic ordersTopic;
    
    context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));
    ```
  - ADD:
    ```java
    import org.eclipse.microprofile.reactive.messaging.Channel;
    import org.eclipse.microprofile.reactive.messaging.Emitter;
    
    @Inject
    @Channel("orders")
    Emitter<String> ordersEmitter;
    
    ordersEmitter.send(Transformers.shoppingCartToJson(cart));
    ```
- Why: Quarkus uses SmallRye Reactive Messaging instead of JMS API
- Depends on: Step 16
- Verify: `grep -l "Emitter" src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 18: COMPLEX — Convert OrderServiceMDB to SmallRye @Incoming
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: `@MessageDriven` MDB implementing `MessageListener`
  - AFTER: CDI bean with `@Incoming` method
  - REMOVE:
    ```java
    import javax.ejb.ActivationConfigProperty;
    import javax.ejb.MessageDriven;
    import javax.jms.JMSException;
    import javax.jms.Message;
    import javax.jms.MessageListener;
    import javax.jms.TextMessage;
    
    @MessageDriven(name = "OrderServiceMDB", activationConfig = {...})
    public class OrderServiceMDB implements MessageListener {
    
        @Override
        public void onMessage(Message rcvMessage) {
            TextMessage msg = null;
            try {
                if (rcvMessage instanceof TextMessage) {
                    msg = (TextMessage) rcvMessage;
                    String orderStr = msg.getBody(String.class);
                    // ... processing logic
                }
            } catch (JMSException e) {
                throw new RuntimeException(e);
            }
        }
    }
    ```
  - ADD:
    ```java
    import jakarta.enterprise.context.ApplicationScoped;
    import org.eclipse.microprofile.reactive.messaging.Incoming;
    
    @ApplicationScoped
    public class OrderServiceMDB {
    
        @Inject
        OrderService orderService;
    
        @Inject
        CatalogService catalogService;
    
        @Incoming("orders")
        public void processOrder(String orderStr) {
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
- Why: Quarkus uses SmallRye Reactive Messaging @Incoming instead of @MessageDriven
- Depends on: Step 17
- Verify: `grep -l "@Incoming" src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 19: COMPLEX — Convert InventoryNotificationMDB to SmallRye @Incoming
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Manual MessageListener with WebLogic JNDI setup
  - AFTER: CDI bean with `@Incoming` method
  - REMOVE: All JNDI, JMS API code, init(), close() methods
  - REPLACE entire file content with:
    ```java
    package com.redhat.coolstore.service;
    
    import com.redhat.coolstore.model.Order;
    import com.redhat.coolstore.utils.Transformers;
    import jakarta.enterprise.context.ApplicationScoped;
    import jakarta.inject.Inject;
    import org.eclipse.microprofile.reactive.messaging.Incoming;
    
    @ApplicationScoped
    public class InventoryNotificationMDB {
    
        private static final int LOW_THRESHOLD = 50;
    
        @Inject
        private CatalogService catalogService;
    
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
- Why: Eliminate WebLogic-specific JNDI and use SmallRye Reactive Messaging
- Depends on: Step 18
- Verify: `grep -l "@Incoming" src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 20: COMPLEX — Replace WebLogic lifecycle listener with Quarkus events
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - BEFORE: Extends `weblogic.application.ApplicationLifecycleListener`
  - AFTER: CDI bean with Quarkus lifecycle event observers
  - REPLACE entire file content with:
    ```java
    package com.redhat.coolstore.utils;
    
    import io.quarkus.runtime.StartupEvent;
    import io.quarkus.runtime.ShutdownEvent;
    import jakarta.enterprise.context.ApplicationScoped;
    import jakarta.enterprise.event.Observes;
    import jakarta.inject.Inject;
    import java.util.logging.Logger;
    
    @ApplicationScoped
    public class StartupListener {
    
        @Inject
        Logger log;
    
        void onStart(@Observes StartupEvent ev) {
            log.info("AppListener(postStart)");
        }
    
        void onStop(@Observes ShutdownEvent ev) {
            log.info("AppListener(preStop)");
        }
    }
    ```
- Why: Quarkus uses CDI event observers instead of WebLogic ApplicationLifecycleListener
- Depends on: Step 8
- Verify: `grep -l "StartupEvent" src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Step 21: Update all javax.persistence imports to jakarta.persistence (7 model files)
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace all `import javax.persistence.*` with `import jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep "jakarta.persistence" src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`

### Step 22: Update all javax.persistence imports to jakarta.persistence (InventoryEntity)
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace all `import javax.persistence.*` with `import jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep "jakarta.persistence" src/main/java/com/redhat/coolstore/model/InventoryEntity.java`

### Step 23: Update all javax.persistence imports to jakarta.persistence (Order)
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Replace all `import javax.persistence.*` with `import jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep "jakarta.persistence" src/main/java/com/redhat/coolstore/model/Order.java`

### Step 24: Update all javax.persistence imports to jakarta.persistence (OrderItem)
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace all `import javax.persistence.*` with `import jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep "jakarta.persistence" src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 25: Update all javax imports to jakarta (Product)
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Replace all `import javax.*` with `import jakarta.*` (for validation and persistence)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep "jakarta\." src/main/java/com/redhat/coolstore/model/Product.java`

### Step 26: Update all javax imports to jakarta (Promotion)
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Replace all `import javax.*` with `import jakarta.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep "jakarta\." src/main/java/com/redhat/coolstore/model/Promotion.java`

### Step 27: Update all javax imports to jakarta (ShoppingCart)
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Replace all `import javax.*` with `import jakarta.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep "jakarta\." src/main/java/com/redhat/coolstore/model/ShoppingCart.java`

### Step 28: Update all javax imports to jakarta (ShoppingCartItem)
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Replace all `import javax.*` with `import jakarta.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep "jakarta\." src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java`

### Step 29: Update javax imports to jakarta in Resources
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do: Replace all `import javax.*` with `import jakarta.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep "jakarta\." src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 30: Update javax imports to jakarta in REST endpoints (CartEndpoint)
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do: Replace all `import javax.ws.rs.*` with `import jakarta.ws.rs.*` and `import javax.inject.*` with `import jakarta.inject.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep "jakarta\." src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 31: Update javax imports to jakarta in REST endpoints (OrderEndpoint)
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do: Replace all `import javax.ws.rs.*` with `import jakarta.ws.rs.*` and `import javax.inject.*` with `import jakarta.inject.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep "jakarta\." src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 32: Update javax imports to jakarta in REST endpoints (ProductEndpoint)
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do: Replace all `import javax.ws.rs.*` with `import jakarta.ws.rs.*` and `import javax.inject.*` with `import jakarta.inject.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep "jakarta\." src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 33: Update javax imports to jakarta in RestApplication
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do: Replace `import javax.ws.rs.ApplicationPath;` with `import jakarta.ws.rs.ApplicationPath;` and `import javax.ws.rs.core.Application;` with `import jakarta.ws.rs.core.Application;`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep "jakarta.ws.rs" src/main/java/com/redhat/coolstore/rest/RestApplication.java`

### Step 34: Update javax imports to jakarta in DataBaseMigrationStartup
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do: Replace all `import javax.*` with `import jakarta.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep "jakarta\." src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`

### Step 35: Update javax imports to jakarta in Producers
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do: Replace all `import javax.*` with `import jakarta.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep "jakarta\." src/main/java/com/redhat/coolstore/utils/Producers.java`

### Step 36: Update javax imports to jakarta in Transformers
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: Replace `import javax.json.*` with `import jakarta.json.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 8
- Verify: `grep "jakarta.json" src/main/java/com/redhat/coolstore/utils/Transformers.java`

### Step 37: Delete WebLogic stub classes
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete the entire file
- Why: WebLogic-specific classes not needed in Quarkus
- Depends on: Step 20
- Verify: File does not exist

### Step 38: Delete WebLogic stub classes
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete the entire file
- Why: WebLogic-specific classes not needed in Quarkus
- Depends on: Step 20
- Verify: File does not exist

### Step 39: Delete WebLogic stub classes
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Delete the entire file
- Why: WebLogic-specific classes not needed in Quarkus
- Depends on: Step 20
- Verify: File does not exist

### Step 40: Delete weblogic directory
- File: src/main/java/weblogic
- Action: DELETE
- What to do: Delete the entire directory
- Why: No WebLogic dependencies in Quarkus
- Depends on: Steps 37, 38, 39
- Verify: Directory does not exist

### Step 41: Delete persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete the file (configuration moved to application.properties)
- Why: Quarkus uses application.properties for datasource config
- Depends on: Step 7
- Verify: File does not exist

### Step 42: Delete web.xml
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete the file
- Why: Not needed in Quarkus JAR packaging
- Depends on: Step 2
- Verify: File does not exist

### Step 43: Delete beans.xml
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete the file
- Why: Quarkus has CDI enabled by default
- Depends on: Step 2
- Verify: File does not exist

### Step 44: Move static resources from webapp to resources
- File: src/main/resources/META-INF/resources/
- Action: CREATE
- What to do: Create directory structure and move webapp content:
  - `mkdir -p src/main/resources/META-INF/resources`
  - `mv src/main/webapp/app src/main/resources/META-INF/resources/`
  - `mv src/main/webapp/bower_components src/main/resources/META-INF/resources/`
  - `mv src/main/webapp/partials src/main/resources/META-INF/resources/`
  - `mv src/main/webapp/*.jsp src/main/resources/META-INF/resources/`
  - `mv src/main/webapp/*.json src/main/resources/META-INF/resources/`
- Why: Quarkus JAR packaging serves static content from META-INF/resources
- Depends on: Step 2
- Verify: `ls src/main/resources/META-INF/resources/app`

### Step 45: Delete webapp directory
- File: src/main/webapp
- Action: DELETE
- What to do: Delete the entire directory (after moving content in Step 44)
- Why: Not used in JAR packaging
- Depends on: Step 44
- Verify: Directory does not exist

### Step 46: Update test persistence.xml to application.properties
- File: src/test/resources/application.properties
- Action: CREATE
- What to do: Create test configuration:
  ```properties
  # Test datasource configuration
  quarkus.datasource.db-kind=h2
  quarkus.datasource.username=sa
  quarkus.datasource.password=
  quarkus.datasource.jdbc.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
  quarkus.hibernate-orm.database.generation=drop-and-create
  quarkus.hibernate-orm.sql-load-script=no-file
  ```
- Why: Test configuration in Quarkus
- Depends on: Step 7
- Verify: File exists at src/test/resources/application.properties

### Step 47: Delete test persistence.xml
- File: src/test/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete the file
- Why: Replaced by application.properties
- Depends on: Step 46
- Verify: File does not exist

## Verification
After completing all steps, run these commands to verify the migration:

1. **Build verification:**
   ```bash
   mvn clean package
   ```
   Should produce `target/monolith-1.0.0-SNAPSHOT-runner.jar`

2. **Quarkus dev mode:**
   ```bash
   mvn quarkus:dev
   ```
   Should start application on http://localhost:8080

3. **Check for javax imports:**
   ```bash
   grep -r "import javax\." src/main/java --include="*.java"
   ```
   Should return no results (all should be jakarta.*)

4. **Check for EJB annotations:**
   ```bash
   grep -r "@Stateless\|@Stateful\|@MessageDriven\|@Remote" src/main/java --include="*.java"
   ```
   Should return no results

5. **Verify messaging configuration:**
   ```bash
   grep "mp.messaging" src/main/resources/application.properties
   ```
   Should show SmallRye messaging config

## Notes

### Complex Migration Areas
1. **Message Driven Beans (MDBs):** Two MDB files required complete structural rewrite from JMS API to SmallRye Reactive Messaging. InventoryNotificationMDB had additional complexity with WebLogic-specific JNDI lookups.

2. **JNDI to CDI:** ShoppingCartService used programmatic JNDI lookups to obtain remote EJB reference. This required converting the remote EJB to a local CDI bean and replacing JNDI lookup with @Inject.

3. **Lifecycle Events:** WebLogic ApplicationLifecycleListener replaced with Quarkus CDI event observers (@Observes StartupEvent/ShutdownEvent).

4. **JMS Producer:** ShoppingCartOrderProcessor used JMS API (@Resource Topic, JMSContext) which was replaced with SmallRye Reactive Messaging (@Channel Emitter).

### Decisions Made
- **Messaging Implementation:** Using SmallRye in-memory connector for dev/test. Production should configure AMQP or Kafka connector in application.properties.
- **Datasource:** Using H2 in-memory database. Production should configure PostgreSQL or other production database.
- **Session Scope:** @Stateful ShoppingCartService converted to @SessionScoped CDI bean with Serializable interface for HTTP session support.
- **Static Resources:** Moved webapp content to META-INF/resources to work with Quarkus JAR packaging.
- **Flyway:** Kept Flyway for database migrations (compatible with Quarkus).

### Post-Migration Tasks
1. Configure production datasource (replace H2 with PostgreSQL/MySQL)
2. Configure production messaging (replace in-memory with AMQP/Kafka)
3. Add Quarkus health checks and metrics
4. Configure Quarkus container image building
5. Update deployment descriptors for OpenShift/Kubernetes
6. Review and optimize for Quarkus native compilation if needed
