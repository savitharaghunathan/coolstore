# Implementation Plan

## Goal
Migrate Java EE 7 monolithic application from WebLogic to Quarkus 3, transforming from WAR-packaged application server deployment to standalone fast-jar Quarkus application.

- **Domain skill:** javaee-to-quarkus

## Project Summary
- **Type:** Maven / Java EE 7 on WebLogic
- **Files affected:** 30 Java files + 4 config files + pom.xml
- **Estimated complexity:** High
- **Hardest steps:**
  1. Step 24-25: COMPLEX — Convert JMS message-driven beans to SmallRye Reactive Messaging (different patterns: annotation-based vs manual JNDI)
  2. Step 14: COMPLEX — Convert @Stateful ShoppingCartService with JNDI lookup to @SessionScoped CDI bean
  3. Step 18: COMPLEX — Create new REST endpoint for remote EJB replacement

## Steps

### Step 1: Install audit-logging-library to local Maven repository
- **Phase:** Build Config
- **File:** lib/audit-logging-library-1.0.0.jar
- **Action:** MODIFY (external action)
- **What to do:** Run command to install system-scoped JAR to local Maven repository
  ```bash
  mvn install:install-file \
    -Dfile=lib/audit-logging-library-1.0.0.jar \
    -DgroupId=com.enterprise \
    -DartifactId=audit-logging-library \
    -Dversion=1.0.0 \
    -Dpackaging=jar
  ```
- **Why:** System-scoped dependencies don't work with Quarkus; proper Maven dependency management required
- **Depends on:** none
- **Verify:** Check local Maven repository (~/.m2/repository/com/enterprise/audit-logging-library/1.0.0/)

### Step 2: Transform pom.xml to Quarkus build configuration
- **Phase:** Build Config
- **File:** pom.xml
- **Action:** MODIFY
- **What to do:** Major restructuring of pom.xml
  1. Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
  2. Update `<maven.compiler.source>` and `<maven.compiler.target>` to `17`
  3. Add Quarkus BOM to `<dependencyManagement>`:
     ```xml
     <dependency>
       <groupId>io.quarkus.platform</groupId>
       <artifactId>quarkus-bom</artifactId>
       <version>3.2.0.Final</version>
       <type>pom</type>
       <scope>import</scope>
     </dependency>
     ```
  4. Add quarkus-maven-plugin to `<build><plugins>`:
     ```xml
     <plugin>
       <groupId>io.quarkus</groupId>
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
  5. Replace Java EE dependencies with Quarkus extensions:
     - Remove: `javaee-web-api`, `javax.jms-api`, `weblogic-*` dependencies
     - Add: `quarkus-hibernate-orm`, `quarkus-jdbc-postgresql`, `quarkus-flyway`, `quarkus-resteasy-reactive-jackson`, `quarkus-smallrye-reactive-messaging-amqp`, `quarkus-arc`, `quarkus-undertow`
  6. Update audit-logging-library dependency from system scope to regular dependency:
     ```xml
     <dependency>
       <groupId>com.enterprise</groupId>
       <artifactId>audit-logging-library</artifactId>
       <version>1.0.0</version>
     </dependency>
     ```
  7. Update Flyway dependency to use Quarkus extension version
- **Why:** Quarkus uses JAR packaging, requires BOM for version management, and uses extensions instead of Java EE APIs
- **Depends on:** Step 1
- **Verify:** `mvn clean compile` succeeds (may have compilation errors in source files - that's expected)

### Step 3: Create Quarkus application.properties
- **Phase:** App Config
- **File:** src/main/resources/application.properties
- **Action:** CREATE
- **What to do:** Create comprehensive Quarkus configuration file with all settings:
  ```properties
  # Datasource configuration (replaces JNDI java:jboss/datasources/CoolstoreDS)
  quarkus.datasource.db-kind=postgresql
  quarkus.datasource.username=coolstore
  quarkus.datasource.password=coolstore
  quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore
  
  # Hibernate ORM configuration (from persistence.xml)
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=true
  quarkus.hibernate-orm.sql-load-script=no-file
  
  # Flyway configuration
  quarkus.flyway.migrate-at-start=true
  quarkus.flyway.baseline-on-migrate=true
  quarkus.flyway.locations=classpath:db/migration
  
  # AMQP messaging configuration (replaces JMS topic/orders)
  mp.messaging.incoming.orders.connector=smallrye-amqp
  mp.messaging.incoming.orders.address=orders
  mp.messaging.incoming.orders.durable=true
  
  mp.messaging.outgoing.orders.connector=smallrye-amqp
  mp.messaging.outgoing.orders.address=orders
  mp.messaging.outgoing.orders.durable=true
  
  # AMQP broker connection
  amqp-host=localhost
  amqp-port=5672
  amqp-username=admin
  amqp-password=admin
  
  # REST configuration
  quarkus.resteasy-reactive.path=/services
  
  # Logging
  quarkus.log.level=INFO
  quarkus.log.category."com.redhat.coolstore".level=DEBUG
  ```
- **Why:** Quarkus uses application.properties for all configuration instead of XML files
- **Depends on:** Step 2
- **Verify:** File created with valid properties syntax

### Step 4: Delete persistence.xml
- **Phase:** App Config
- **File:** src/main/resources/META-INF/persistence.xml
- **Action:** DELETE
- **What to do:** Delete this file — Hibernate configuration now in application.properties
- **Why:** Quarkus doesn't use persistence.xml; all JPA config moves to application.properties
- **Depends on:** Step 3
- **Verify:** File no longer exists

### Step 5: Delete web.xml
- **Phase:** App Config
- **File:** src/main/webapp/WEB-INF/web.xml
- **Action:** DELETE
- **What to do:** Delete this file — JAX-RS configuration is automatic in Quarkus
- **Why:** Quarkus doesn't use deployment descriptors; REST endpoints auto-discovered
- **Depends on:** Step 3
- **Verify:** File no longer exists

### Step 6: Delete beans.xml
- **Phase:** App Config
- **File:** src/main/webapp/WEB-INF/beans.xml
- **Action:** DELETE
- **What to do:** Delete this file — CDI is enabled by default in Quarkus
- **Why:** Quarkus Arc enables CDI automatically without beans.xml
- **Depends on:** Step 3
- **Verify:** File no longer exists

### Step 7: Convert CatalogService from @Stateless to @ApplicationScoped
- **Phase:** EJB to CDI
- **File:** src/main/java/com/redhat/coolstore/service/CatalogService.java
- **Action:** MODIFY
- **What to do:**
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.persistence.EntityManager;` with `import jakarta.persistence.EntityManager;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `@Stateless` annotation with `@ApplicationScoped`
- **Why:** Quarkus uses CDI beans instead of EJBs; @ApplicationScoped provides similar singleton semantics as @Stateless
- **Depends on:** Step 2
- **Verify:** No @Stateless annotation, has @ApplicationScoped, uses jakarta.* imports

### Step 8: Convert OrderService from @Stateless to @ApplicationScoped
- **Phase:** EJB to CDI
- **File:** src/main/java/com/redhat/coolstore/service/OrderService.java
- **Action:** MODIFY
- **What to do:**
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.persistence.EntityManager;` with `import jakarta.persistence.EntityManager;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `@Stateless` annotation with `@ApplicationScoped`
- **Why:** Quarkus uses CDI beans instead of EJBs
- **Depends on:** Step 2
- **Verify:** No @Stateless annotation, has @ApplicationScoped, uses jakarta.* imports

### Step 9: Convert ProductService from @Stateless to @ApplicationScoped
- **Phase:** EJB to CDI
- **File:** src/main/java/com/redhat/coolstore/service/ProductService.java
- **Action:** MODIFY
- **What to do:**
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `@Stateless` annotation with `@ApplicationScoped`
- **Why:** Quarkus uses CDI beans instead of EJBs
- **Depends on:** Step 2
- **Verify:** No @Stateless annotation, has @ApplicationScoped, uses jakarta.* imports

### Step 10: Convert PromoService from @Stateless to @ApplicationScoped
- **Phase:** EJB to CDI
- **File:** src/main/java/com/redhat/coolstore/service/PromoService.java
- **Action:** MODIFY
- **What to do:**
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `@Stateless` annotation with `@ApplicationScoped`
- **Why:** Quarkus uses CDI beans instead of EJBs
- **Depends on:** Step 2
- **Verify:** No @Stateless annotation, has @ApplicationScoped, uses jakarta.* imports

### Step 11: Convert ShippingService from @Stateless @Remote to @ApplicationScoped
- **Phase:** EJB to CDI
- **File:** src/main/java/com/redhat/coolstore/service/ShippingService.java
- **Action:** MODIFY
- **What to do:**
  - Remove `import javax.ejb.Remote;`
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Remove `@Stateless` and `@Remote` annotations
  - Add `@ApplicationScoped` annotation
  - Keep `implements ShippingServiceRemote` (will be used as local interface)
- **Why:** Quarkus uses CDI beans; remote access will be provided via REST endpoint (Step 18)
- **Depends on:** Step 2
- **Verify:** No @Stateless or @Remote annotations, has @ApplicationScoped

### Step 12: Convert ShoppingCartOrderProcessor from @Stateless to @ApplicationScoped
- **Phase:** EJB to CDI
- **File:** src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- **Action:** MODIFY
- **What to do:**
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `@Stateless` annotation with `@ApplicationScoped`
  - Will be updated in Step 26 to add message publishing
- **Why:** Quarkus uses CDI beans instead of EJBs
- **Depends on:** Step 2
- **Verify:** No @Stateless annotation, has @ApplicationScoped, uses jakarta.* imports

### Step 13: Update Resources.java to use jakarta.* imports
- **Phase:** EJB to CDI
- **File:** src/main/java/com/redhat/coolstore/persistence/Resources.java
- **Action:** MODIFY
- **What to do:**
  - Replace `import javax.enterprise.context.Dependent;` with `import jakarta.enterprise.context.Dependent;`
  - Replace `import javax.enterprise.inject.Produces;` with `import jakarta.enterprise.inject.Produces;`
  - Replace `import javax.persistence.EntityManager;` with `import jakarta.persistence.EntityManager;`
  - Replace `import javax.persistence.PersistenceContext;` with `import jakarta.persistence.PersistenceContext;`
  - Replace `@PersistenceContext` with `@Inject` (Quarkus injects EntityManager directly)
- **Why:** Quarkus 3 uses jakarta.* namespace and simplified EntityManager injection
- **Depends on:** Step 2
- **Verify:** All imports use jakarta.*, EntityManager uses @Inject

### Step 14: COMPLEX — Convert ShoppingCartService from @Stateful to @SessionScoped
- **Phase:** EJB to CDI
- **File:** src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- **Action:** MODIFY
- **What to do:**
  - BEFORE: @Stateful EJB with JNDI lookup for remote ShippingService
  - AFTER: @SessionScoped CDI bean with direct injection
  - Specific changes:
    1. Remove imports:
       - `import javax.ejb.Stateful;`
       - `import javax.naming.Context;`
       - `import javax.naming.InitialContext;`
       - `import javax.naming.NamingException;`
       - `import java.util.Hashtable;`
    2. Add imports:
       - `import jakarta.enterprise.context.SessionScoped;`
       - `import jakarta.inject.Inject;` (update from javax)
       - `import java.io.Serializable;`
    3. Replace `@Stateful` with `@SessionScoped`
    4. Add `implements Serializable` to class declaration
    5. Add field: `@Inject ShippingService shippingService;`
    6. Replace all calls to `lookupShippingServiceRemote().calculateShipping(sc)` with `shippingService.calculateShipping(sc)`
    7. Replace all calls to `lookupShippingServiceRemote().calculateShippingInsurance(sc)` with `shippingService.calculateShippingInsurance(sc)`
    8. Delete entire `lookupShippingServiceRemote()` method
- **Why:** @Stateful EJB maps to @SessionScoped CDI bean for session state; JNDI lookup replaced with direct CDI injection
- **Depends on:** Step 11
- **Verify:** No @Stateful, has @SessionScoped, implements Serializable, no JNDI lookups, direct injection used

### Step 15: COMPLEX — Convert DataBaseMigrationStartup from @Singleton @Startup to Quarkus lifecycle
- **Phase:** EJB to CDI
- **File:** src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- **Action:** MODIFY
- **What to do:**
  - BEFORE: @Singleton @Startup EJB with @PostConstruct and @Resource datasource injection
  - AFTER: @ApplicationScoped CDI bean with @Observes StartupEvent (but Flyway will run automatically)
  - Specific changes:
    1. Remove imports:
       - `import javax.annotation.PostConstruct;`
       - `import javax.annotation.Resource;`
       - `import javax.ejb.Singleton;`
       - `import javax.ejb.Startup;`
       - `import javax.ejb.TransactionManagement;`
       - `import javax.ejb.TransactionManagementType;`
       - `import org.flywaydb.core.Flyway;`
       - `import org.flywaydb.core.api.FlywayException;`
       - `import javax.sql.DataSource;`
    2. Add imports:
       - `import jakarta.enterprise.context.ApplicationScoped;`
       - `import jakarta.inject.Inject;`
       - `import io.quarkus.runtime.StartupEvent;`
       - `import jakarta.enterprise.event.Observes;`
    3. Replace `@Singleton @Startup @TransactionManagement(TransactionManagementType.BEAN)` with `@ApplicationScoped`
    4. Remove `@Resource(mappedName = "java:jboss/datasources/CoolstoreDS") DataSource dataSource;` field
    5. Replace `@PostConstruct private void startup()` with `void onStart(@Observes StartupEvent event)`
    6. Simplify method body to just log message (Quarkus Flyway extension handles migration automatically):
       ```java
       void onStart(@Observes StartupEvent event) {
           logger.info("Application started - Flyway migrations handled by Quarkus extension");
       }
       ```
- **Why:** Quarkus Flyway extension runs migrations automatically at startup; manual Flyway code not needed
- **Depends on:** Step 2, Step 3
- **Verify:** No @Singleton/@Startup, has @ApplicationScoped, uses @Observes StartupEvent, no manual Flyway code

### Step 16: Update all JPA entity imports to jakarta.*
- **Phase:** EJB to CDI
- **File:** src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- **Action:** MODIFY
- **What to do:**
  - Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
  - Replace all `javax.xml.bind.annotation.*` imports with `jakarta.xml.bind.annotation.*`
- **Why:** Quarkus 3 uses jakarta.* namespace
- **Depends on:** Step 2
- **Verify:** All imports use jakarta.*

### Step 17: Update all remaining JPA entities to jakarta.*
- **Phase:** EJB to CDI
- **File:** Multiple files (7 entities total)
- **Action:** MODIFY
- **What to do:** For each of these files, replace javax.* with jakarta.* imports:
  - src/main/java/com/redhat/coolstore/model/InventoryEntity.java
  - src/main/java/com/redhat/coolstore/model/Order.java
  - src/main/java/com/redhat/coolstore/model/OrderItem.java
  - src/main/java/com/redhat/coolstore/model/Product.java
  - src/main/java/com/redhat/coolstore/model/Promotion.java
  - src/main/java/com/redhat/coolstore/model/ShoppingCart.java
  - src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
  - Replace `javax.persistence.*` with `jakarta.persistence.*`
  - Replace `javax.xml.bind.annotation.*` with `jakarta.xml.bind.annotation.*`
- **Why:** Quarkus 3 uses jakarta.* namespace for all JPA entities
- **Depends on:** Step 2
- **Verify:** All entity files use jakarta.* imports, no javax.* remains

### Step 18: COMPLEX — Create ShippingEndpoint for remote EJB replacement
- **Phase:** EJB to CDI
- **File:** src/main/java/com/redhat/coolstore/rest/ShippingEndpoint.java
- **Action:** CREATE
- **What to do:** Create new REST endpoint to replace @Remote EJB access
  ```java
  package com.redhat.coolstore.rest;
  
  import jakarta.inject.Inject;
  import jakarta.ws.rs.Consumes;
  import jakarta.ws.rs.POST;
  import jakarta.ws.rs.Path;
  import jakarta.ws.rs.Produces;
  import jakarta.ws.rs.core.MediaType;
  
  import com.redhat.coolstore.model.ShoppingCart;
  import com.redhat.coolstore.service.ShippingService;
  
  @Path("/shipping")
  public class ShippingEndpoint {
  
      @Inject
      ShippingService shippingService;
  
      @POST
      @Path("/calculate")
      @Consumes(MediaType.APPLICATION_JSON)
      @Produces(MediaType.APPLICATION_JSON)
      public double calculateShipping(ShoppingCart sc) {
          return shippingService.calculateShipping(sc);
      }
  
      @POST
      @Path("/insurance")
      @Consumes(MediaType.APPLICATION_JSON)
      @Produces(MediaType.APPLICATION_JSON)
      public double calculateShippingInsurance(ShoppingCart sc) {
          return shippingService.calculateShippingInsurance(sc);
      }
  }
  ```
- **Why:** Replaces @Remote EJB access with standard REST API for remote clients
- **Depends on:** Step 11
- **Verify:** File created, REST endpoints accessible at /services/shipping/calculate and /services/shipping/insurance

### Step 19: Update CartEndpoint to use jakarta.* and @SessionScoped
- **Phase:** EJB to CDI
- **File:** src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- **Action:** MODIFY
- **What to do:**
  - Replace `import javax.enterprise.context.SessionScoped;` with `import jakarta.enterprise.context.SessionScoped;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace all `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
- **Why:** Quarkus 3 uses jakarta.* namespace; @SessionScoped works in Quarkus with session support
- **Depends on:** Step 2
- **Verify:** All imports use jakarta.*, @SessionScoped annotation present

### Step 20: Update OrderEndpoint to use jakarta.*
- **Phase:** EJB to CDI
- **File:** src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- **Action:** MODIFY
- **What to do:**
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace all `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
  - Replace `import javax.persistence.EntityManager;` with `import jakarta.persistence.EntityManager;`
- **Why:** Quarkus 3 uses jakarta.* namespace
- **Depends on:** Step 2
- **Verify:** All imports use jakarta.*

### Step 21: Update ProductEndpoint to use jakarta.*
- **Phase:** EJB to CDI
- **File:** src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- **Action:** MODIFY
- **What to do:**
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace all `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
- **Why:** Quarkus 3 uses jakarta.* namespace
- **Depends on:** Step 2
- **Verify:** All imports use jakarta.*

### Step 22: Update RestApplication to use jakarta.*
- **Phase:** EJB to CDI
- **File:** src/main/java/com/redhat/coolstore/rest/RestApplication.java
- **Action:** MODIFY
- **What to do:**
  - Replace `import javax.ws.rs.ApplicationPath;` with `import jakarta.ws.rs.ApplicationPath;`
  - Replace `import javax.ws.rs.core.Application;` with `import jakarta.ws.rs.core.Application;`
- **Why:** Quarkus 3 uses jakarta.* namespace
- **Depends on:** Step 2
- **Verify:** All imports use jakarta.*

### Step 23: Update Producers.java and Transformers.java to use jakarta.*
- **Phase:** EJB to CDI
- **File:** src/main/java/com/redhat/coolstore/utils/Producers.java
- **Action:** MODIFY
- **What to do:**
  - Replace `import javax.enterprise.inject.Produces;` with `import jakarta.enterprise.inject.Produces;`
  - Replace any other javax.* imports with jakarta.*
  - Do the same for Transformers.java (update any javax.* imports to jakarta.*)
- **Why:** Quarkus 3 uses jakarta.* namespace
- **Depends on:** Step 2
- **Verify:** All imports use jakarta.* in both files

### Step 24: COMPLEX — Convert OrderServiceMDB to SmallRye Reactive Messaging
- **Phase:** Messaging
- **File:** src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- **Action:** MODIFY
- **What to do:**
  - BEFORE: @MessageDriven bean with JMS MessageListener
  - AFTER: @ApplicationScoped bean with @Incoming Reactive Messaging
  - Specific changes:
    1. Remove imports:
       - `import javax.ejb.ActivationConfigProperty;`
       - `import javax.ejb.MessageDriven;`
       - `import javax.jms.JMSException;`
       - `import javax.jms.Message;`
       - `import javax.jms.MessageListener;`
       - `import javax.jms.TextMessage;`
    2. Add imports:
       - `import jakarta.enterprise.context.ApplicationScoped;`
       - `import jakarta.inject.Inject;` (update from javax)
       - `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    3. Remove `@MessageDriven(name = "OrderServiceMDB", activationConfig = {...})` annotation
    4. Add `@ApplicationScoped` annotation to class
    5. Remove `implements MessageListener`
    6. Replace `onMessage(Message rcvMessage)` method with:
       ```java
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
- **Why:** Quarkus uses SmallRye Reactive Messaging instead of JMS; @Incoming annotation receives from AMQP channel
- **Depends on:** Step 3, Step 7, Step 8
- **Verify:** No @MessageDriven, has @ApplicationScoped, has @Incoming("orders"), no JMS imports

### Step 25: COMPLEX — Convert InventoryNotificationMDB to SmallRye Reactive Messaging
- **Phase:** Messaging
- **File:** src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- **Action:** MODIFY
- **What to do:**
  - BEFORE: Manual JMS MessageListener with WebLogic JNDI setup
  - AFTER: @ApplicationScoped bean with @Incoming Reactive Messaging
  - Specific changes:
    1. Remove imports:
       - All `javax.jms.*` imports
       - All `javax.naming.*` imports
       - `import javax.rmi.PortableRemoteObject;`
       - `import java.util.Hashtable;`
    2. Add imports:
       - `import jakarta.enterprise.context.ApplicationScoped;`
       - `import jakarta.inject.Inject;` (update from javax)
       - `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    3. Add `@ApplicationScoped` annotation to class
    4. Remove `implements MessageListener`
    5. Remove all fields: `JNDI_FACTORY`, `JMS_FACTORY`, `TOPIC`, `tcon`, `tsession`, `tsubscriber`
    6. Replace `onMessage(Message rcvMessage)` method with:
       ```java
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
    7. Delete `init()` method
    8. Delete `close()` method
    9. Delete `getInitialContext()` method
- **Why:** Quarkus uses SmallRye Reactive Messaging; manual JNDI setup replaced with declarative @Incoming
- **Depends on:** Step 3, Step 7
- **Verify:** No MessageListener interface, has @ApplicationScoped, has @Incoming("orders"), no JNDI/JMS code

### Step 26: Add message publishing to ShoppingCartOrderProcessor
- **Phase:** Messaging
- **File:** src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- **Action:** MODIFY
- **What to do:**
  - Add imports:
    - `import org.eclipse.microprofile.reactive.messaging.Channel;`
    - `import org.eclipse.microprofile.reactive.messaging.Emitter;`
    - `import jakarta.inject.Inject;` (if not already present)
  - Add field:
    ```java
    @Inject
    @Channel("orders")
    Emitter<String> orderEmitter;
    ```
  - Update `process(ShoppingCart sc)` method to publish order to AMQP:
    ```java
    public void process(ShoppingCart sc) {
        Order order = new Order();
        // ... existing order creation logic ...
        String orderJson = Transformers.orderToJson(order);
        orderEmitter.send(orderJson);
    }
    ```
- **Why:** Replace JMS producer with SmallRye Reactive Messaging Emitter for publishing to AMQP
- **Depends on:** Step 3, Step 12
- **Verify:** Has @Channel Emitter field, uses orderEmitter.send() to publish messages

### Step 27: COMPLEX — Replace WebLogic lifecycle listener with Quarkus events
- **Phase:** Lifecycle
- **File:** src/main/java/com/redhat/coolstore/utils/StartupListener.java
- **Action:** MODIFY
- **What to do:**
  - BEFORE: WebLogic ApplicationLifecycleListener
  - AFTER: @ApplicationScoped CDI bean with Quarkus lifecycle events
  - Specific changes:
    1. Remove imports:
       - `import weblogic.application.ApplicationLifecycleEvent;`
       - `import weblogic.application.ApplicationLifecycleListener;`
    2. Add imports:
       - `import jakarta.enterprise.context.ApplicationScoped;`
       - `import jakarta.enterprise.event.Observes;`
       - `import jakarta.inject.Inject;` (update from javax)
       - `import io.quarkus.runtime.StartupEvent;`
       - `import io.quarkus.runtime.ShutdownEvent;`
    3. Add `@ApplicationScoped` annotation to class
    4. Remove `extends ApplicationLifecycleListener`
    5. Replace `postStart(ApplicationLifecycleEvent evt)` with:
       ```java
       void onStart(@Observes StartupEvent event) {
           log.info("AppListener(postStart)");
       }
       ```
    6. Replace `preStop(ApplicationLifecycleEvent evt)` with:
       ```java
       void onStop(@Observes ShutdownEvent event) {
           log.info("AppListener(preStop)");
       }
       ```
- **Why:** Quarkus uses lifecycle events instead of application server listeners
- **Depends on:** Step 2
- **Verify:** No weblogic imports, has @ApplicationScoped, uses @Observes StartupEvent/ShutdownEvent

### Step 28: Delete WebLogic ApplicationLifecycleEvent stub
- **Phase:** Cleanup
- **File:** src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- **Action:** DELETE
- **What to do:** Delete this WebLogic stub class — no longer needed
- **Why:** WebLogic-specific code removed, using Quarkus lifecycle events
- **Depends on:** Step 27
- **Verify:** File no longer exists

### Step 29: Delete WebLogic ApplicationLifecycleListener stub
- **Phase:** Cleanup
- **File:** src/main/java/weblogic/application/ApplicationLifecycleListener.java
- **Action:** DELETE
- **What to do:** Delete this WebLogic stub class — no longer needed
- **Why:** WebLogic-specific code removed, using Quarkus lifecycle events
- **Depends on:** Step 27
- **Verify:** File no longer exists

### Step 30: Delete WebLogic NonCatalogLogger stub
- **Phase:** Cleanup
- **File:** src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- **Action:** DELETE
- **What to do:** Delete this WebLogic stub class — no longer needed
- **Why:** WebLogic-specific logging removed, using standard Java logging
- **Depends on:** Step 27
- **Verify:** File no longer exists

### Step 31: Delete weblogic package directory
- **Phase:** Cleanup
- **File:** src/main/java/weblogic/
- **Action:** DELETE
- **What to do:** Delete entire weblogic package directory
- **Why:** All WebLogic-specific code removed
- **Depends on:** Step 28, Step 29, Step 30
- **Verify:** Directory no longer exists

### Step 32: Delete src/main/webapp/WEB-INF directory
- **Phase:** Cleanup
- **File:** src/main/webapp/WEB-INF/
- **Action:** DELETE
- **What to do:** Delete entire WEB-INF directory (already deleted web.xml and beans.xml in steps 5-6, now remove directory)
- **Why:** JAR packaging doesn't use webapp structure
- **Depends on:** Step 5, Step 6
- **Verify:** Directory no longer exists

### Step 33: Delete ShippingServiceRemote interface
- **Phase:** Cleanup
- **File:** src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- **Action:** DELETE
- **What to do:** Delete this @Remote interface — no longer needed, replaced by REST endpoint
- **Why:** Remote EJB interface replaced with REST API, local interface not needed
- **Depends on:** Step 11, Step 18
- **Verify:** File no longer exists; ShippingService.java updated to not implement interface

### Step 34: Update ShippingService to remove interface implementation
- **Phase:** Cleanup
- **File:** src/main/java/com/redhat/coolstore/service/ShippingService.java
- **Action:** MODIFY
- **What to do:** Remove `implements ShippingServiceRemote` from class declaration (was kept in Step 11, now removing)
- **Why:** Interface deleted, no longer needed
- **Depends on:** Step 33
- **Verify:** Class declaration is just `public class ShippingService {` without interface

### Step 35: Final verification — check for remaining javax.* imports
- **Phase:** Cleanup
- **File:** All Java files in src/main/java
- **Action:** MODIFY (if needed)
- **What to do:** Run verification command to find any remaining javax.* EE imports:
  ```bash
  grep -r "import javax\\.ejb\\." src/main/java/ || echo "✓ No EJB imports"
  grep -r "import javax\\.jms\\." src/main/java/ || echo "✓ No JMS imports"
  grep -r "import javax\\.persistence\\." src/main/java/ || echo "✓ No javax persistence"
  grep -r "import javax\\.enterprise\\." src/main/java/ || echo "✓ No javax enterprise"
  grep -r "import javax\\.inject\\." src/main/java/ || echo "✓ No javax inject"
  grep -r "import javax\\.ws\\.rs\\." src/main/java/ || echo "✓ No javax JAX-RS"
  grep -r "import weblogic\\." src/main/java/ || echo "✓ No WebLogic imports"
  ```
  If any found, replace with jakarta.* equivalents
- **Why:** Ensure complete migration to jakarta.* namespace
- **Depends on:** All previous steps
- **Verify:** No javax.ejb, javax.jms, javax.persistence, javax.enterprise, javax.inject, javax.ws.rs, or weblogic imports remain

## Verification

### Build Command
```bash
mvn clean package
```

### Success Criteria
1. Build completes without errors
2. Application starts successfully: `java -jar target/quarkus-app/quarkus-run.jar`
3. REST endpoints respond: `curl http://localhost:8080/services/cart/test`
4. Database migrations execute on startup (check logs)
5. No javax.* EE imports remain
6. No weblogic.* imports remain
7. Application starts in under 5 seconds

### Testing Strategy
1. Run `mvn clean verify` to execute all tests
2. Test REST endpoints with curl/Postman
3. Verify AMQP messaging (requires ActiveMQ Artemis broker running)
4. Check database schema created by Flyway
5. Test session behavior across multiple requests

## Notes

### Prerequisites
- Java 17 JDK installed and configured
- Maven 3.8+ installed
- ActiveMQ Artemis broker running (for messaging tests)
- PostgreSQL database running (or update datasource config)
- audit-logging-library installed to local Maven repository (Step 1)

### Gotchas
1. **Session state**: CartEndpoint and ShoppingCartService use @SessionScoped, requires sticky sessions in production
2. **Message consumption**: Both OrderServiceMDB and InventoryNotificationMDB listen to the same "orders" channel — they both receive all messages (pub/sub pattern preserved)
3. **Flyway migrations**: Existing SQL files reused without changes; Quarkus extension runs them automatically
4. **Remote EJB clients**: External clients using ShippingService @Remote interface must migrate to REST API at /services/shipping/*
5. **AMQP configuration**: Broker connection details in application.properties must match your ActiveMQ Artemis setup
6. **EntityManager injection**: Changed from @PersistenceContext to @Inject in Quarkus

### Special Cases
- **Transformers.java**: Ensure it has methods `jsonToOrder(String)` and `orderToJson(Order)` for message serialization
- **Logging**: Standard java.util.logging works in Quarkus; WebLogic NonCatalogLogger removed
- **Transaction management**: Quarkus handles transactions automatically for @Transactional methods; removed manual EJB transaction management
