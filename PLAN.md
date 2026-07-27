# PLAN.md

## Goal
Migrate Java EE 7 application (WAR packaging, WebLogic/WildFly) to Quarkus 3 with JAR packaging, replacing EJB with CDI, JMS/MDB with SmallRye Reactive Messaging, and JNDI lookups with direct injection.

- Reference used: javaee-to-quarkus migration skill
- Migration phases: Build Config → App Config → EJB to CDI → Messaging → Lifecycle → Cleanup

## Project Summary
- Type: Maven (Java EE 7 WAR → Quarkus 3 JAR)
- Files affected: ~30 Java files + 4 config files + pom.xml
- Estimated complexity: **High**
- Hardest steps:
  1. Converting 2 MDB files to SmallRye Reactive Messaging (one uses manual JMS setup with JNDI)
  2. Replacing JNDI remote EJB lookup with CDI injection
  3. Converting WebLogic ApplicationLifecycleListener to Quarkus lifecycle events
  4. Migrating system-scoped audit library dependency

## Architecture Overview
Based on graph analysis and selective reading:
- **Layer 1 - Build**: pom.xml, lib/ (audit JAR)
- **Layer 2 - Models**: 8 entity/model classes (@Entity, POJOs)
- **Layer 3 - Persistence**: Resources.java, persistence.xml
- **Layer 4 - Services**: 10 service classes (@Stateless, @Stateful, @MessageDriven)
- **Layer 5 - REST**: 4 endpoint classes (JAX-RS)
- **Layer 6 - Utils**: Transformers, Producers, lifecycle listeners
- **Layer 7 - Legacy**: WebLogic stubs (to delete)

## Steps

### Step 1: Convert WAR to JAR packaging in pom.xml
- File: pom.xml
- Action: MODIFY
- What to do:
  - Change `<packaging>war</packaging>` → `<packaging>jar</packaging>`
  - Remove `<finalName>ROOT</finalName>` from build section
  - Remove maven-war-plugin
- Why: Quarkus applications are standalone JARs, not WARs deployed to app servers
- Depends on: none
- Verify: `grep -E "<packaging>" pom.xml` shows jar

### Step 2: Add Quarkus BOM and plugin to pom.xml
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add to properties section:
    ```xml
    <quarkus.platform.version>3.2.0.Final</quarkus.platform.version>
    <compiler-plugin.version>3.11.0</compiler-plugin.version>
    ```
  - Add to dependencyManagement section (create if missing):
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
  - Add to plugins section:
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
- Why: Quarkus BOM manages extension versions, plugin handles build lifecycle
- Depends on: Step 1
- Verify: `grep -A 5 "quarkus-bom" pom.xml` shows the BOM declaration

### Step 3: Replace Java EE dependencies with Quarkus extensions in pom.xml
- File: pom.xml
- Action: MODIFY
- What to do:
  - REMOVE dependencies:
    ```xml
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
  - ADD Quarkus extensions:
    ```xml
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
      <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
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
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-flyway</artifactId>
    </dependency>
    ```
  - KEEP (modify scope to compile):
    ```xml
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
      <version>4.1.2</version>
    </dependency>
    ```
  - UPDATE audit library dependency (change from system to compile scope, or install to local Maven repo)
- Why: Replace Java EE APIs with Quarkus extensions that provide Jakarta EE + reactive capabilities
- Depends on: Step 2
- Verify: `mvn dependency:tree | grep quarkus` shows Quarkus dependencies

### Step 4: Update Java compiler version in pom.xml
- File: pom.xml
- Action: MODIFY
- What to do:
  - Change maven-compiler-plugin configuration:
    ```xml
    <source>17</source>
    <target>17</target>
    ```
- Why: Quarkus 3 requires Java 17 minimum
- Depends on: Step 3
- Verify: `grep -A 2 "maven-compiler-plugin" pom.xml` shows Java 17

### Step 5: Create application.properties for Quarkus configuration
- File: src/main/resources/application.properties
- Action: CREATE
- What to do:
  - Create file with datasource and messaging configuration:
    ```properties
    # Datasource configuration (replaces JNDI lookup)
    quarkus.datasource.db-kind=postgresql
    quarkus.datasource.username=coolstore
    quarkus.datasource.password=coolstore
    quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore
    
    # Hibernate configuration
    quarkus.hibernate-orm.database.generation=none
    quarkus.hibernate-orm.log.sql=false
    quarkus.hibernate-orm.log.format-sql=true
    
    # Flyway configuration
    quarkus.flyway.migrate-at-start=true
    
    # AMQP/Reactive Messaging (replaces JMS)
    mp.messaging.incoming.orders.connector=smallrye-amqp
    mp.messaging.incoming.orders.address=orders
    mp.messaging.incoming.orders.durable=true
    
    mp.messaging.outgoing.orders-out.connector=smallrye-amqp
    mp.messaging.outgoing.orders-out.address=orders
    
    # AMQP broker connection
    amqp-host=localhost
    amqp-port=5672
    amqp-username=admin
    amqp-password=admin
    
    # Logging
    quarkus.log.level=INFO
    quarkus.log.category."com.redhat.coolstore".level=DEBUG
    ```
- Why: Quarkus uses application.properties instead of XML config files
- Depends on: Step 3
- Verify: File exists and contains datasource configuration

### Step 6: Delete persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Remove file (configuration moved to application.properties)
- Why: Quarkus configures Hibernate via application.properties, not persistence.xml
- Depends on: Step 5
- Verify: `test ! -f src/main/resources/META-INF/persistence.xml`

### Step 7: Delete web.xml
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Remove file (JAX-RS auto-discovery in Quarkus)
- Why: Quarkus doesn't use web.xml; REST endpoints are auto-discovered
- Depends on: Step 1
- Verify: `test ! -f src/main/webapp/WEB-INF/web.xml`

### Step 8: Delete beans.xml
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Remove file (CDI enabled by default in Quarkus)
- Why: Quarkus enables CDI by default without beans.xml
- Depends on: Step 1
- Verify: `test ! -f src/main/webapp/WEB-INF/beans.xml`

### Step 9: Migrate CatalogItemEntity to Jakarta namespace
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do:
  - Replace imports:
    - `javax.persistence.*` → `jakarta.persistence.*`
    - `javax.xml.bind.annotation.*` → `jakarta.xml.bind.annotation.*` (if present)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 5
- Verify: `grep -q "jakarta.persistence" src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`

### Step 10: Migrate InventoryEntity to Jakarta namespace
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do:
  - Replace imports: `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 5
- Verify: `grep -q "jakarta.persistence" src/main/java/com/redhat/coolstore/model/InventoryEntity.java`

### Step 11: Migrate Order entity to Jakarta namespace
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do:
  - Replace imports: `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 5
- Verify: `grep -q "jakarta.persistence" src/main/java/com/redhat/coolstore/model/Order.java`

### Step 12: Migrate OrderItem entity to Jakarta namespace
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do:
  - Replace imports: `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 5
- Verify: `grep -q "jakarta.persistence" src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 13: Migrate Resources.java to Jakarta namespace
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Replace imports:
    - `javax.enterprise.inject.Produces` → `jakarta.enterprise.inject.Produces`
    - `javax.persistence.EntityManager` → `jakarta.persistence.EntityManager`
    - `javax.persistence.PersistenceContext` → `jakarta.persistence.PersistenceContext`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 5
- Verify: `grep -q "jakarta.enterprise" src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 14: Convert CatalogService from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace imports:
    - `javax.ejb.Stateless` → REMOVE
    - `javax.inject.Inject` → `jakarta.inject.Inject`
    - `javax.persistence.*` → `jakarta.persistence.*`
  - Replace annotations:
    - `@Stateless` → `@jakarta.enterprise.context.ApplicationScoped`
- Why: Quarkus uses CDI beans instead of EJBs
- Depends on: Step 13
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 15: Convert ProductService from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace imports:
    - `javax.ejb.Stateless` → REMOVE
    - `javax.inject.Inject` → `jakarta.inject.Inject`
  - Replace annotations:
    - `@Stateless` → `@jakarta.enterprise.context.ApplicationScoped`
- Why: Quarkus uses CDI beans instead of EJBs
- Depends on: Step 14
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 16: Convert OrderService from @Stateless to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace imports:
    - `javax.ejb.Stateless` → REMOVE
    - `javax.inject.Inject` → `jakarta.inject.Inject`
    - `javax.persistence.*` → `jakarta.persistence.*`
    - `javax.annotation.PostConstruct` → `jakarta.annotation.PostConstruct`
    - `javax.annotation.PreDestroy` → `jakarta.annotation.PreDestroy`
  - Replace annotations:
    - `@Stateless` → `@jakarta.enterprise.context.ApplicationScoped`
- Why: Quarkus uses CDI beans instead of EJBs
- Depends on: Step 14
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 17: Convert PromoService to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - Replace imports: `javax.*` → `jakarta.*` for inject, enterprise
  - Add `@jakarta.enterprise.context.ApplicationScoped` if not already present
- Why: Quarkus uses CDI beans
- Depends on: Step 14
- Verify: `grep -q "jakarta" src/main/java/com/redhat/coolstore/service/PromoService.java`

### Step 18: Convert ShippingServiceRemote interface to plain interface
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: MODIFY
- What to do:
  - Remove `@Remote` import and annotation
  - Keep interface as-is (becomes regular Java interface)
- Why: Quarkus doesn't use EJB remote interfaces; local injection only
- Depends on: Step 14
- Verify: `! grep -q "@Remote" src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java`

### Step 19: COMPLEX — Convert ShippingService from @Stateless @Remote to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless @Remote` EJB bean
  - AFTER: `@ApplicationScoped` CDI bean
  - Replace imports:
    - `javax.ejb.Stateless` → REMOVE
    - `javax.ejb.Remote` → REMOVE
  - Add import: `jakarta.enterprise.context.ApplicationScoped`
  - Replace annotations:
    - Remove `@Stateless` and `@Remote`
    - Add `@ApplicationScoped`
  - Keep `implements ShippingServiceRemote` (interface still needed for typing)
- Why: Quarkus doesn't support remote EJBs; uses local CDI injection
- Depends on: Step 18
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/ShippingService.java && ! grep -q "@Remote" src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 20: COMPLEX — Convert ShoppingCartService from @Stateful to @RequestScoped
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateful` EJB with JNDI lookup for remote EJB
  - AFTER: `@RequestScoped` CDI bean with direct injection
  - Replace imports:
    - `javax.ejb.Stateful` → REMOVE
    - `javax.inject.Inject` → `jakarta.inject.Inject`
    - `javax.naming.*` → REMOVE (no more JNDI)
  - Add import: `jakarta.enterprise.context.RequestScoped`
  - Replace annotations:
    - `@Stateful` → `@RequestScoped`
  - Add injection for ShippingService:
    ```java
    @Inject
    ShippingServiceRemote shippingService;
    ```
  - Replace `lookupShippingServiceRemote()` method with direct field usage:
    - Change all calls from `lookupShippingServiceRemote().calculateShipping(sc)` 
    - To: `shippingService.calculateShipping(sc)`
  - DELETE the entire `lookupShippingServiceRemote()` method
- Why: Quarkus doesn't support JNDI lookups for beans; uses CDI injection. @Stateful → @RequestScoped for per-request state
- Depends on: Step 19
- Verify: `grep -q "@RequestScoped" src/main/java/com/redhat/coolstore/service/ShoppingCartService.java && ! grep -q "lookupShippingServiceRemote" src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 21: Convert ShoppingCartOrderProcessor to @ApplicationScoped
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Replace imports: `javax.inject.Inject` → `jakarta.inject.Inject`
  - Add `@jakarta.enterprise.context.ApplicationScoped` if EJB annotation present
- Why: Ensure CDI bean discovery
- Depends on: Step 14
- Verify: `grep -q "jakarta" src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 22: COMPLEX — Convert OrderServiceMDB to SmallRye Reactive Messaging
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: `@MessageDriven` JMS MessageListener
  - AFTER: SmallRye Reactive Messaging `@Incoming` method
  - Replace imports:
    - REMOVE all `javax.jms.*` imports
    - REMOVE `javax.ejb.*` imports
    - `javax.inject.Inject` → `jakarta.inject.Inject`
    - ADD: `org.eclipse.microprofile.reactive.messaging.Incoming`
    - ADD: `io.smallrye.reactive.messaging.annotations.Blocking`
  - ADD class annotation: `@jakarta.enterprise.context.ApplicationScoped`
  - Replace class structure:
    ```java
    // REMOVE: @MessageDriven annotation and all activationConfig
    // REMOVE: implements MessageListener
    // ADD: @ApplicationScoped class annotation
    
    // CHANGE method signature from:
    public void onMessage(Message rcvMessage)
    
    // TO:
    @Incoming("orders")
    @Blocking
    public void processOrder(String orderStr)
    ```
  - Replace method body:
    ```java
    // REMOVE: All JMS type casting (TextMessage, Message)
    // REMOVE: try-catch for JMSException
    
    // KEEP: Business logic
    System.out.println("\nMessage recd !");
    System.out.println("Received order: " + orderStr);
    Order order = Transformers.jsonToOrder(orderStr);
    System.out.println("Order object is " + order);
    orderService.save(order);
    order.getItemList().forEach(orderItem -> {
        catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
    });
    ```
- Why: Quarkus uses SmallRye Reactive Messaging instead of JMS/MDB
- Depends on: Step 16, Step 5 (application.properties with messaging config)
- Verify: `grep -q "@Incoming" src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java && ! grep -q "@MessageDriven" src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 23: COMPLEX — Convert InventoryNotificationMDB to SmallRye Reactive Messaging
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Manual JMS MessageListener with JNDI setup
  - AFTER: SmallRye Reactive Messaging `@Incoming` method
  - Replace imports:
    - REMOVE all `javax.jms.*`, `javax.naming.*`, `javax.rmi.*` imports
    - `javax.inject.Inject` → `jakarta.inject.Inject`
    - ADD: `org.eclipse.microprofile.reactive.messaging.Incoming`
    - ADD: `io.smallrye.reactive.messaging.annotations.Blocking`
  - ADD class annotation: `@jakarta.enterprise.context.ApplicationScoped`
  - DELETE: All JNDI constants (JNDI_FACTORY, JMS_FACTORY, TOPIC)
  - DELETE: All connection fields (tcon, tsession, tsubscriber)
  - DELETE: `init()` method (JNDI setup no longer needed)
  - DELETE: `close()` method
  - DELETE: `getInitialContext()` method
  - Replace `onMessage` method:
    ```java
    // CHANGE signature from:
    public void onMessage(Message rcvMessage)
    
    // TO:
    @Incoming("orders")
    @Blocking
    public void checkInventory(String orderStr)
    ```
  - Simplify method body:
    ```java
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
- Why: Replace manual JMS setup with declarative reactive messaging
- Depends on: Step 14, Step 5
- Verify: `grep -q "@Incoming" src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java && ! grep -q "InitialContext" src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 24: Migrate Producers utility to Jakarta namespace
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Replace imports:
    - `javax.enterprise.inject.Produces` → `jakarta.enterprise.inject.Produces`
    - `javax.enterprise.inject.spi.InjectionPoint` → `jakarta.enterprise.inject.spi.InjectionPoint`
- Why: Quarkus 3 uses Jakarta namespace
- Depends on: Step 13
- Verify: `grep -q "jakarta.enterprise" src/main/java/com/redhat/coolstore/utils/Producers.java`

### Step 25: Migrate Transformers utility to Jakarta namespace
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do:
  - Replace imports if any `javax.*` imports exist → `jakarta.*`
  - This is likely a utility class with JSON parsing only, may not need changes
- Why: Ensure consistency with Jakarta namespace
- Depends on: Step 24
- Verify: `! grep -q "javax\\." src/main/java/com/redhat/coolstore/utils/Transformers.java || grep -q "jakarta" src/main/java/com/redhat/coolstore/utils/Transformers.java`

### Step 26: COMPLEX — Replace DataBaseMigrationStartup with Quarkus lifecycle
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Singleton @Startup` EJB lifecycle bean
  - AFTER: Quarkus `@Observes StartupEvent` CDI observer
  - Replace imports:
    - REMOVE: `javax.ejb.Singleton`, `javax.ejb.Startup`, `javax.annotation.PostConstruct`
    - `javax.inject.Inject` → `jakarta.inject.Inject`
    - ADD: `io.quarkus.runtime.StartupEvent`
    - ADD: `jakarta.enterprise.event.Observes`
    - ADD: `jakarta.enterprise.context.ApplicationScoped`
  - Replace class structure:
    ```java
    // BEFORE:
    @Singleton
    @Startup
    public class DataBaseMigrationStartup {
        @PostConstruct
        public void init() { ... }
    }
    
    // AFTER:
    @ApplicationScoped
    public class DataBaseMigrationStartup {
        void onStart(@Observes StartupEvent event) { ... }
    }
    ```
  - Move `@PostConstruct init()` logic into `onStart()` method
- Why: Quarkus uses CDI lifecycle events instead of EJB @Startup
- Depends on: Step 5
- Verify: `grep -q "StartupEvent" src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java && ! grep -q "@Startup" src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`

### Step 27: COMPLEX — Replace StartupListener with Quarkus lifecycle events
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - BEFORE: WebLogic `ApplicationLifecycleListener`
  - AFTER: Quarkus `StartupEvent` and `ShutdownEvent` observers
  - Replace imports:
    - REMOVE: all `weblogic.application.*` imports
    - `javax.inject.Inject` → `jakarta.inject.Inject`
    - ADD: `io.quarkus.runtime.StartupEvent`
    - ADD: `io.quarkus.runtime.ShutdownEvent`
    - ADD: `jakarta.enterprise.event.Observes`
    - ADD: `jakarta.enterprise.context.ApplicationScoped`
  - Replace class structure:
    ```java
    // BEFORE:
    public class StartupListener extends ApplicationLifecycleListener {
        public void postStart(ApplicationLifecycleEvent evt) { ... }
        public void preStop(ApplicationLifecycleEvent evt) { ... }
    }
    
    // AFTER:
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
- Why: Quarkus uses portable CDI events instead of vendor-specific lifecycle listeners
- Depends on: Step 26
- Verify: `grep -q "StartupEvent" src/main/java/com/redhat/coolstore/utils/StartupListener.java && ! grep -q "ApplicationLifecycleListener" src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Step 28: Migrate CartEndpoint to Jakarta namespace
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace imports:
    - `javax.inject.Inject` → `jakarta.inject.Inject`
    - `javax.ws.rs.*` → `jakarta.ws.rs.*`
- Why: Quarkus 3 uses Jakarta namespace for JAX-RS
- Depends on: Step 20
- Verify: `grep -q "jakarta.ws.rs" src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 29: Migrate OrderEndpoint to Jakarta namespace
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Replace imports:
    - `javax.inject.Inject` → `jakarta.inject.Inject`
    - `javax.ws.rs.*` → `jakarta.ws.rs.*`
- Why: Quarkus 3 uses Jakarta namespace for JAX-RS
- Depends on: Step 28
- Verify: `grep -q "jakarta.ws.rs" src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 30: Migrate ProductEndpoint to Jakarta namespace
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Replace imports:
    - `javax.inject.Inject` → `jakarta.inject.Inject`
    - `javax.ws.rs.*` → `jakarta.ws.rs.*`
- Why: Quarkus 3 uses Jakarta namespace for JAX-RS
- Depends on: Step 28
- Verify: `grep -q "jakarta.ws.rs" src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 31: Migrate RestApplication to Jakarta namespace
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Replace imports:
    - `javax.ws.rs.ApplicationPath` → `jakarta.ws.rs.ApplicationPath`
    - `javax.ws.rs.core.Application` → `jakarta.ws.rs.core.Application`
- Why: Quarkus 3 uses Jakarta namespace for JAX-RS
- Depends on: Step 28
- Verify: `grep -q "jakarta.ws.rs" src/main/java/com/redhat/coolstore/rest/RestApplication.java`

### Step 32: Delete WebLogic ApplicationLifecycleEvent stub
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Remove entire file
- Why: WebLogic-specific stub no longer needed after lifecycle migration
- Depends on: Step 27
- Verify: `test ! -f src/main/java/weblogic/application/ApplicationLifecycleEvent.java`

### Step 33: Delete WebLogic ApplicationLifecycleListener stub
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Remove entire file
- Why: WebLogic-specific stub no longer needed after lifecycle migration
- Depends on: Step 27
- Verify: `test ! -f src/main/java/weblogic/application/ApplicationLifecycleListener.java`

### Step 34: Delete WebLogic NonCatalogLogger stub
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Remove entire file
- Why: WebLogic-specific stub no longer needed
- Depends on: Step 27
- Verify: `test ! -f src/main/java/weblogic/i18n/logging/NonCatalogLogger.java`

### Step 35: Delete entire weblogic package directory
- File: src/main/java/weblogic/
- Action: DELETE
- What to do: Remove entire directory tree
- Why: No WebLogic-specific code needed in Quarkus
- Depends on: Step 32, Step 33, Step 34
- Verify: `test ! -d src/main/java/weblogic`

### Step 36: Verify no javax.* EE imports remain
- File: src/main/java (entire directory)
- Action: VERIFY
- What to do:
  - Run: `grep -r "import javax\\.ejb" src/main/java || echo "✓ No EJB imports"`
  - Run: `grep -r "import javax\\.jms" src/main/java || echo "✓ No JMS imports"`
  - Run: `grep -r "import javax\\.inject\\.Inject" src/main/java || echo "✓ No javax.inject imports"`
  - Run: `grep -r "import javax\\.persistence" src/main/java || echo "✓ No javax.persistence imports"`
  - Run: `grep -r "import javax\\.ws\\.rs" src/main/java || echo "✓ No javax.ws.rs imports"`
  - All should return "✓" message (no matches)
- Why: Ensure complete migration to Jakarta namespace
- Depends on: All previous steps
- Verify: No javax.* EE imports found

## Verification

After completing all steps, run these commands in order:

1. **Compile check**:
   ```bash
   mvn clean compile
   ```
   - Expected: BUILD SUCCESS
   - If failed: Check for remaining javax.* imports or missing Jakarta dependencies

2. **Package check**:
   ```bash
   mvn clean package
   ```
   - Expected: BUILD SUCCESS with JAR artifact in target/
   - If failed: Check pom.xml packaging and plugin configuration

3. **Quarkus dev mode**:
   ```bash
   mvn quarkus:dev
   ```
   - Expected: Application starts on port 8080
   - Check logs for: "Quarkus X.X.X started in XXXms"
   - If failed: Check application.properties for datasource/messaging config

4. **Database connection check**:
   ```bash
   # Ensure PostgreSQL is running
   docker run -d --name postgres -e POSTGRES_DB=coolstore -e POSTGRES_USER=coolstore -e POSTGRES_PASSWORD=coolstore -p 5432:5432 postgres:13
   mvn quarkus:dev
   ```
   - Expected: Flyway migrations execute successfully
   - Check logs for: "Successfully applied X migrations"

5. **Messaging check**:
   ```bash
   # Ensure AMQP broker is running (ActiveMQ Artemis or RabbitMQ)
   docker run -d --name artemis -p 5672:5672 -p 8161:8161 -e ARTEMIS_USERNAME=admin -e ARTEMIS_PASSWORD=admin vromero/activemq-artemis
   mvn quarkus:dev
   ```
   - Expected: AMQP connector establishes connection
   - Check logs for: "AMQP connection established"

6. **REST endpoint check**:
   ```bash
   curl http://localhost:8080/api/products
   ```
   - Expected: JSON response with product list
   - If empty: Database may need seeding

7. **Final verification checklist**:
   - [ ] Application builds successfully
   - [ ] No javax.* EE imports remain
   - [ ] All services are @ApplicationScoped or @RequestScoped
   - [ ] No @Stateless, @Stateful, @Singleton EJB annotations remain
   - [ ] No @MessageDriven annotations remain
   - [ ] No JNDI lookups remain (InitialContext, lookup())
   - [ ] No persistence.xml, web.xml, or beans.xml files remain
   - [ ] application.properties contains all configuration
   - [ ] JAR packaging in pom.xml
   - [ ] weblogic/ directory deleted

## Notes

### Complex Migration Points

1. **Two MDB patterns**: OrderServiceMDB uses `@MessageDriven` annotation (standard), while InventoryNotificationMDB uses manual JNDI/JMS setup. Both need different conversion approaches to SmallRye Reactive Messaging.

2. **JNDI Remote EJB lookup**: ShoppingCartService uses `lookupShippingServiceRemote()` to obtain a remote EJB reference. In Quarkus, this becomes simple CDI `@Inject` with no remote capability (all beans are local).

3. **@Stateful to @RequestScoped**: ShoppingCartService maintains per-user session state. Converted to `@RequestScoped` for per-request instances, but session management may need review if multi-request sessions are required.

4. **Audit library**: The system-scoped dependency `audit-logging-library-1.0.0.jar` should be installed to local Maven repository or deployed to a Maven repository manager for proper dependency resolution.

5. **WebLogic lifecycle**: Custom WebLogic `ApplicationLifecycleListener` replaced with portable Quarkus `StartupEvent`/`ShutdownEvent` observers.

6. **Messaging broker**: Application originally used JMS (likely WebLogic JMS or JBoss HornetQ). Quarkus requires AMQP broker (ActiveMQ Artemis, RabbitMQ, or Azure Service Bus). Configuration in application.properties assumes AMQP - adjust broker details as needed.

### Migration Strategy Decisions

- **Packaging**: WAR → JAR (Quarkus uber-jar deployment)
- **Datasource**: JNDI lookup → application.properties configuration
- **Messaging**: JMS Topics → AMQP with SmallRye Reactive Messaging
- **Session state**: @Stateful EJB → @RequestScoped CDI (review if multi-request sessions needed)
- **Remote EJBs**: Eliminated (all beans local via CDI injection)
- **Java version**: 1.8 → 17 (Quarkus 3 requirement)

### Post-Migration Considerations

1. **Testing**: No test files identified - create integration tests for REST endpoints and messaging
2. **Session management**: If shopping cart needs multi-request sessions, consider HTTP session or external cache (Redis)
3. **Messaging topology**: Verify AMQP topic/queue naming matches JMS setup
4. **Database migrations**: Flyway configuration preserved - review migration scripts for compatibility
5. **Frontend**: webapp/ directory contains frontend assets - ensure static resources are properly served from src/main/resources/META-INF/resources/
6. **Monitoring**: Consider adding quarkus-smallrye-health and quarkus-micrometer-registry-prometheus extensions
7. **Security**: No security configuration identified - may need to add Quarkus OIDC/JWT extensions if authentication required
