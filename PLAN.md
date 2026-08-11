# PLAN.md

## Goal
Migrate Java EE 7 monolith application from WebLogic/JBoss to Quarkus 3, converting WAR packaging to standalone JAR, replacing EJB with CDI, migrating JMS/MDB to SmallRye Reactive Messaging, and removing all application server dependencies.

- Reference used: javaee-to-quarkus (Java EE 7/8 to Quarkus 3 migration)

## Project Summary
- Type: Maven WAR project (Java EE 7)
- Files affected: ~30 Java files + build/config files
- Estimated complexity: **High**
- Hardest steps:
  1. Converting MDB classes to SmallRye Reactive Messaging (@MessageDriven → @Incoming)
  2. Replacing JNDI lookups and WebLogic-specific InitialContext with CDI injection
  3. Migrating WebLogic ApplicationLifecycleListener to Quarkus lifecycle events
  4. Converting JMS producer (@Resource Topic lookup → Emitter injection)

## Architecture Overview

**Current layers identified:**
- **Build**: pom.xml (WAR packaging, Java EE 7 dependencies)
- **Config**: persistence.xml, web.xml, beans.xml
- **Models**: 8 entity classes (javax.persistence annotations)
- **Services**: 9 service classes (6 @Stateless EJBs, 2 @MessageDriven MDBs, 1 lifecycle listener)
- **REST API**: 4 JAX-RS endpoints
- **Utils**: Helper classes, transformers, lifecycle listeners

**Key migration patterns detected:**
- 6 @Stateless EJBs → @ApplicationScoped CDI beans
- 2 @MessageDriven MDBs → SmallRye @Incoming consumers
- 1 JMS producer with @Resource Topic lookup → Emitter injection
- 1 WebLogic ApplicationLifecycleListener → Quarkus startup/shutdown events
- 1 JNDI-based message consumer → SmallRye Reactive Messaging
- javax.persistence imports in 8+ entity files
- JNDI InitialContext with WebLogic-specific factory

## Steps

### Step 1: Update pom.xml - Change packaging to JAR
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus applications are packaged as standalone JARs, not WARs
- Depends on: none
- Verify: `grep '<packaging>jar</packaging>' pom.xml`

### Step 2: Update pom.xml - Add Quarkus BOM
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add to `<properties>`:
    ```xml
    <quarkus.platform.version>3.2.0.Final</quarkus.platform.version>
    <compiler-plugin.version>3.11.0</compiler-plugin.version>
    <maven.compiler.release>17</maven.compiler.release>
    ```
  - Add to `<dependencyManagement>` section (create if not exists):
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
- Why: Quarkus BOM manages all extension versions consistently
- Depends on: Step 1
- Verify: `grep 'quarkus-bom' pom.xml`

### Step 3: Update pom.xml - Replace Java EE dependencies with Quarkus extensions
- File: pom.xml
- Action: MODIFY
- What to do:
  - **REMOVE** these dependencies:
    ```xml
    javax:javaee-web-api:7.0
    javax:javaee-api:7.0
    org.jboss.spec.javax.jms:jboss-jms-api_2.0_spec
    org.jboss.spec.javax.rmi:jboss-rmi-api_1.0_spec
    ```
  - **ADD** these Quarkus extensions:
    ```xml
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-hibernate-orm-panache</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-jdbc-postgresql</artifactId>
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
  - **KEEP** these dependencies (update scope if needed):
    ```xml
    org.flywaydb:flyway-core (change to io.quarkus:quarkus-flyway)
    com.enterprise:audit-logging-library (keep as system dependency)
    ```
  - **UPDATE** test dependencies:
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
- Why: Quarkus provides these capabilities through extensions instead of Java EE APIs
- Depends on: Step 2
- Verify: `mvn dependency:tree | grep quarkus`

### Step 4: Update pom.xml - Replace maven-war-plugin with quarkus-maven-plugin
- File: pom.xml
- Action: MODIFY
- What to do:
  - **UPDATE** maven-compiler-plugin configuration:
    ```xml
    <plugin>
      <artifactId>maven-compiler-plugin</artifactId>
      <version>${compiler-plugin.version}</version>
      <configuration>
        <release>${maven.compiler.release}</release>
      </configuration>
    </plugin>
    ```
  - **REMOVE**: `maven-war-plugin`
  - **ADD** after maven-compiler-plugin:
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
- Why: Quarkus uses its own Maven plugin for building and running applications
- Depends on: Step 3
- Verify: `mvn clean compile` (should succeed)

### Step 5: Create application.properties with datasource configuration
- File: src/main/resources/application.properties
- Action: CREATE
- What to do:
  ```properties
  # Datasource configuration (replaces JNDI lookup)
  quarkus.datasource.db-kind=postgresql
  quarkus.datasource.username=coolstore
  quarkus.datasource.password=coolstore
  quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore
  
  # Hibernate configuration
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=false
  quarkus.hibernate-orm.sql-load-script=no-file
  
  # Flyway configuration
  quarkus.flyway.migrate-at-start=true
  
  # AMQP/Messaging configuration
  mp.messaging.incoming.orders.connector=smallrye-amqp
  mp.messaging.incoming.orders.address=orders
  mp.messaging.incoming.orders.durable=true
  
  mp.messaging.outgoing.orders-out.connector=smallrye-amqp
  mp.messaging.outgoing.orders-out.address=orders
  mp.messaging.outgoing.orders-out.durable=true
  
  # AMQP broker configuration
  amqp-host=localhost
  amqp-port=5672
  amqp-username=admin
  amqp-password=admin
  ```
- Why: Quarkus uses application.properties instead of persistence.xml and for all configuration
- Depends on: Step 4
- Verify: File exists and contains datasource config

### Step 6: Delete persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Remove file completely
- Why: Replaced by application.properties in Step 5
- Depends on: Step 5
- Verify: `! test -f src/main/resources/META-INF/persistence.xml`

### Step 7: Delete web.xml
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Remove file completely
- Why: Quarkus doesn't use web.xml; JAX-RS configuration is automatic
- Depends on: Step 5
- Verify: `! test -f src/main/webapp/WEB-INF/web.xml`

### Step 8: Delete beans.xml
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Remove file completely
- Why: Quarkus CDI is enabled by default; beans.xml not needed
- Depends on: Step 5
- Verify: `! test -f src/main/webapp/WEB-INF/beans.xml`

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

### Step 13: Migrate imports in Product.java
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `! grep 'javax.persistence' src/main/java/com/redhat/coolstore/model/Product.java`

### Step 14: Migrate imports in Promotion.java
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `! grep 'javax.persistence' src/main/java/com/redhat/coolstore/model/Promotion.java`

### Step 15: Migrate imports in ShoppingCart.java
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `! grep 'javax.persistence' src/main/java/com/redhat/coolstore/model/ShoppingCart.java`

### Step 16: Migrate imports in ShoppingCartItem.java
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 4
- Verify: `! grep 'javax.persistence' src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java`

### Step 17: Migrate CatalogService.java from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` → `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
  - Replace `import javax.persistence.*;` → `import jakarta.persistence.*;`
  - Replace `@Stateless` → `@ApplicationScoped`
- Why: EJB annotations replaced by CDI; Jakarta namespace required
- Depends on: Steps 9-16
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 18: Migrate OrderService.java from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` → `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
  - Replace `import javax.persistence.*;` → `import jakarta.persistence.*;`
  - Replace `@Stateless` → `@ApplicationScoped`
- Why: EJB annotations replaced by CDI; Jakarta namespace required
- Depends on: Steps 9-16
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 19: Migrate ProductService.java from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` → `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
  - Replace `@Stateless` → `@ApplicationScoped`
- Why: EJB annotations replaced by CDI; Jakarta namespace required
- Depends on: Steps 9-16
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 20: Migrate ShippingService.java from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` → `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` → `@ApplicationScoped`
- Why: EJB annotations replaced by CDI; Jakarta namespace required
- Depends on: Steps 9-16
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 21: Migrate ShoppingCartService.java from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` → `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
  - Replace `import javax.persistence.*;` → `import jakarta.persistence.*;`
  - Replace `@Stateless` → `@ApplicationScoped`
- Why: EJB annotations replaced by CDI; Jakarta namespace required
- Depends on: Steps 9-16
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 22: COMPLEX - Convert ShoppingCartOrderProcessor from JMS producer to Emitter
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - **BEFORE**: JMS with @Resource Topic lookup and JMSContext
  - **AFTER**: SmallRye Reactive Messaging with @Channel Emitter
  - **Specific changes**:
    1. Remove imports:
       ```java
       import javax.ejb.Stateless;
       import javax.annotation.Resource;
       import javax.jms.JMSContext;
       import javax.jms.Topic;
       ```
    2. Add imports:
       ```java
       import jakarta.enterprise.context.ApplicationScoped;
       import jakarta.inject.Inject;
       import org.eclipse.microprofile.reactive.messaging.Channel;
       import org.eclipse.microprofile.reactive.messaging.Emitter;
       ```
    3. Replace annotation: `@Stateless` → `@ApplicationScoped`
    4. Replace field declarations:
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
    5. Replace method body in `process()`:
       ```java
       // OLD:
       context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));
       
       // NEW:
       ordersEmitter.send(Transformers.shoppingCartToJson(cart));
       ```
- Why: Quarkus doesn't support JMS @Resource lookups; uses Reactive Messaging
- Depends on: Step 5 (application.properties with mp.messaging.outgoing.orders-out)
- Verify: `grep 'Emitter<String>' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 23: COMPLEX - Convert OrderServiceMDB from @MessageDriven to @Incoming
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - **BEFORE**: @MessageDriven MDB with MessageListener
  - **AFTER**: CDI bean with @Incoming reactive method
  - **Specific changes**:
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
       ```
    3. Remove class annotation and implements clause:
       ```java
       // OLD:
       @MessageDriven(name = "OrderServiceMDB", activationConfig = {...})
       public class OrderServiceMDB implements MessageListener {
       
       // NEW:
       @ApplicationScoped
       public class OrderServiceMDB {
       ```
    4. Replace `onMessage()` method:
       ```java
       // OLD:
       @Override
       public void onMessage(Message rcvMessage) {
           TextMessage msg = null;
           try {
               if (rcvMessage instanceof TextMessage) {
                   msg = (TextMessage) rcvMessage;
                   String orderStr = msg.getBody(String.class);
                   // ... processing
               }
           } catch (JMSException e) {
               throw new RuntimeException(e);
           }
       }
       
       // NEW:
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
       ```
    5. Update `@Inject` imports: `javax.inject.Inject` → `jakarta.inject.Inject`
- Why: Quarkus uses SmallRye Reactive Messaging instead of JMS MDBs
- Depends on: Step 5 (application.properties with mp.messaging.incoming.orders)
- Verify: `grep '@Incoming("orders")' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 24: COMPLEX - Convert InventoryNotificationMDB from JNDI/JMS to @Incoming
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - **BEFORE**: Manual JMS subscription with JNDI lookup and WebLogic InitialContext
  - **AFTER**: CDI bean with @Incoming reactive method
  - **Specific changes**:
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
       ```
    3. Add class annotation:
       ```java
       @ApplicationScoped
       ```
    4. Remove all JNDI-related fields:
       ```java
       // REMOVE:
       private final static String JNDI_FACTORY = "weblogic.jndi.WLInitialContextFactory";
       private final static String JMS_FACTORY = "TCF";
       private final static String TOPIC = "topic/orders";
       private TopicConnection tcon;
       private TopicSession tsession;
       private TopicSubscriber tsubscriber;
       ```
    5. Replace `onMessage()` method:
       ```java
       // OLD:
       public void onMessage(Message rcvMessage) {
           TextMessage msg;
           try {
               if (rcvMessage instanceof TextMessage) {
                   msg = (TextMessage) rcvMessage;
                   String orderStr = msg.getBody(String.class);
                   // ... processing
               }
           } catch (JMSException jmse) { ... }
       }
       
       // NEW:
       @Incoming("orders")
       public void checkInventory(String orderStr) {
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
    6. **DELETE** methods: `init()`, `close()`, `getInitialContext()`
    7. Update `@Inject` import: `javax.inject.Inject` → `jakarta.inject.Inject`
- Why: JNDI and manual JMS subscription replaced by declarative Reactive Messaging
- Depends on: Step 5 (application.properties with mp.messaging.incoming.orders)
- Verify: `! grep 'InitialContext\|JNDI_FACTORY' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 25: Migrate REST endpoints - CartEndpoint.java
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
  - Replace `import javax.ws.rs.*;` → `import jakarta.ws.rs.*;`
  - Replace `import javax.ws.rs.core.*;` → `import jakarta.ws.rs.core.*;`
- Why: Jakarta namespace required for Quarkus 3
- Depends on: Steps 17-21
- Verify: `! grep 'import javax' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 26: Migrate REST endpoints - OrderEndpoint.java
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
  - Replace `import javax.ws.rs.*;` → `import jakarta.ws.rs.*;`
  - Replace `import javax.ws.rs.core.*;` → `import jakarta.ws.rs.core.*;`
- Why: Jakarta namespace required for Quarkus 3
- Depends on: Steps 17-21
- Verify: `! grep 'import javax' src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 27: Migrate REST endpoints - ProductEndpoint.java
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Replace `import javax.inject.Inject;` → `import jakarta.inject.Inject;`
  - Replace `import javax.ws.rs.*;` → `import jakarta.ws.rs.*;`
  - Replace `import javax.ws.rs.core.*;` → `import jakarta.ws.rs.core.*;`
- Why: Jakarta namespace required for Quarkus 3
- Depends on: Steps 17-21
- Verify: `! grep 'import javax' src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 28: Migrate REST endpoints - RestApplication.java
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ws.rs.ApplicationPath;` → `import jakarta.ws.rs.ApplicationPath;`
  - Replace `import javax.ws.rs.core.Application;` → `import jakarta.ws.rs.core.Application;`
- Why: Jakarta namespace required for Quarkus 3
- Depends on: Steps 17-21
- Verify: `! grep 'import javax' src/main/java/com/redhat/coolstore/rest/RestApplication.java`

### Step 29: Migrate Resources.java producer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Replace `import javax.enterprise.inject.Produces;` → `import jakarta.enterprise.inject.Produces;`
  - Replace `import javax.persistence.*;` → `import jakarta.persistence.*;`
- Why: Jakarta namespace required for Quarkus 3
- Depends on: Steps 17-21
- Verify: `! grep 'import javax' src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 30: Migrate DataBaseMigrationStartup.java
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - Replace `import javax.annotation.PostConstruct;` → `import jakarta.annotation.PostConstruct;`
  - Replace `import javax.ejb.Singleton;` → `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.ejb.Startup;` → `import io.quarkus.runtime.Startup;`
  - Replace `@Singleton` → `@ApplicationScoped`
  - Keep `@Startup` (Quarkus supports this annotation)
- Why: EJB Singleton replaced by CDI ApplicationScoped; Jakarta namespace required
- Depends on: Steps 17-21
- Verify: `grep '@ApplicationScoped' src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`

### Step 31: COMPLEX - Replace StartupListener with Quarkus lifecycle events
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - **BEFORE**: WebLogic ApplicationLifecycleListener
  - **AFTER**: Quarkus lifecycle event observers
  - **Specific changes**:
    1. Remove imports:
       ```java
       import weblogic.application.ApplicationLifecycleEvent;
       import weblogic.application.ApplicationLifecycleListener;
       ```
    2. Add imports:
       ```java
       import io.quarkus.runtime.StartupEvent;
       import io.quarkus.runtime.ShutdownEvent;
       import jakarta.enterprise.context.ApplicationScoped;
       import jakarta.enterprise.event.Observes;
       import jakarta.inject.Inject;
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
- Why: WebLogic-specific lifecycle replaced by standard Quarkus events
- Depends on: Steps 17-21
- Verify: `grep '@Observes StartupEvent' src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Step 32: Delete WebLogic stub classes
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Remove entire file
- Why: No longer needed after Step 31
- Depends on: Step 31
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleEvent.java`

### Step 33: Delete WebLogic stub classes
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Remove entire file
- Why: No longer needed after Step 31
- Depends on: Step 31
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleListener.java`

### Step 34: Delete entire webapp directory
- File: src/main/webapp/
- Action: DELETE
- What to do: Remove entire directory and contents
- Why: Quarkus JAR packaging doesn't use webapp directory
- Depends on: Steps 7, 8
- Verify: `! test -d src/main/webapp`

### Step 35: Move static resources if needed
- File: src/main/resources/META-INF/resources/
- Action: CREATE (if src/main/webapp had static content other than WEB-INF)
- What to do:
  - Check if src/main/webapp contains any static files (HTML, CSS, JS, images)
  - If yes, move them to src/main/resources/META-INF/resources/
  - If no static files exist, skip this step
- Why: Quarkus serves static content from META-INF/resources in JAR packaging
- Depends on: Step 34
- Verify: Static resources accessible at same URLs

### Step 36: Update PromoService.java imports
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.enterprise.context.ApplicationScoped;` → `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace any other `javax.*` imports with `jakarta.*` equivalents
- Why: Jakarta namespace required for Quarkus 3
- Depends on: Steps 17-21
- Verify: `! grep 'import javax' src/main/java/com/redhat/coolstore/service/PromoService.java`

### Step 37: Update Transformers.java imports
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: Replace any `javax.*` imports with `jakarta.*` equivalents (check for JSON-B, JSON-P, etc.)
- Why: Jakarta namespace required for Quarkus 3
- Depends on: Steps 17-21
- Verify: `! grep 'import javax' src/main/java/com/redhat/coolstore/utils/Transformers.java`

### Step 38: Final verification - Build the project
- File: N/A (build command)
- Action: VERIFY
- What to do: Run `mvn clean package`
- Why: Ensure all changes compile successfully
- Depends on: Steps 1-37
- Verify: Build completes without errors, produces quarkus-app/ directory in target/

### Step 39: Final verification - Check for remaining javax imports
- File: N/A (verification command)
- Action: VERIFY
- What to do: Run `grep -r "import javax\." src/main/java --include="*.java"` (should return no Java EE imports)
- Why: Ensure complete migration to Jakarta namespace
- Depends on: Step 38
- Verify: No javax.ejb, javax.persistence, javax.ws.rs, javax.jms, javax.enterprise, javax.inject imports remain

### Step 40: Final verification - Test application startup
- File: N/A (runtime test)
- Action: VERIFY
- What to do:
  - Ensure PostgreSQL is running on localhost:5432
  - Ensure AMQP broker is running on localhost:5672
  - Run `mvn quarkus:dev`
  - Verify application starts without errors
  - Check that lifecycle events fire (check logs for "AppListener(postStart)")
- Why: Confirm runtime behavior matches expected Quarkus application
- Depends on: Step 38
- Verify: Application starts on port 8080, REST endpoints accessible

## Verification

Final verification commands after all steps complete:

```bash
# 1. Build succeeds
mvn clean package

# 2. No javax.* Java EE imports remain
! grep -r "import javax\.\(ejb\|persistence\|jms\|ws\.rs\|enterprise\|inject\|annotation\)" src/main/java --include="*.java"

# 3. Application runs in dev mode
mvn quarkus:dev

# 4. Test REST endpoints (in another terminal)
curl http://localhost:8080/api/products
curl http://localhost:8080/api/cart/123

# 5. Verify JAR packaging
ls -lh target/quarkus-app/quarkus-run.jar

# 6. Verify no WAR artifact
! ls target/*.war
```

## Notes

### Critical Dependencies

1. **Java 17+**: Quarkus 3 requires Java 17 minimum. Update `JAVA_HOME` if needed.

2. **Database**: The application expects PostgreSQL instead of the original datasource. Update connection details in `application.properties` for your environment.

3. **Message Broker**: Migration assumes AMQP (e.g., Apache Artemis, RabbitMQ). The original JMS Topic setup is replaced with Reactive Messaging channels. Adjust broker configuration as needed.

4. **Audit Library**: The system-scoped dependency `audit-logging-library-1.0.0.jar` may need verification for Jakarta EE compatibility. If it uses `javax.*` packages internally, it may need updating.

### Architecture Changes

- **No Remote EJB**: All @Stateless EJBs become local CDI beans. If remote access was used, REST endpoints must replace it.

- **Reactive Messaging**: Both MDB consumers now share the same "orders" channel. This creates two subscribers to the same topic. Verify this matches intended behavior; if separate topics are needed, update `application.properties`.

- **Transaction Management**: EJB container-managed transactions are replaced by Quarkus CDI transactions. Add `@Transactional` annotations to service methods that need transaction boundaries.

- **JNDI Removal**: Complete removal of JNDI lookups. All resources configured declaratively in `application.properties` and injected via CDI.

### Migration Strategy

The steps follow this layer order:
1. Build config (Steps 1-4)
2. App config (Steps 5-8)
3. Models/Entities (Steps 9-16)
4. Services/EJB (Steps 17-24)
5. REST API (Steps 25-28)
6. Utils/Lifecycle (Steps 29-37)
7. Cleanup (Steps 32-35)
8. Verification (Steps 38-40)

This ensures dependencies are available when needed and reduces compilation errors during migration.

### Testing Recommendations

After migration:
1. Update unit tests to use `@QuarkusTest` instead of Java EE testing frameworks
2. Test all REST endpoints thoroughly
3. Verify message flow: send to orders-out channel → receive on orders channel
4. Test database operations and transactions
5. Verify startup/shutdown lifecycle events fire correctly
6. Load test to compare performance vs. original Java EE deployment
