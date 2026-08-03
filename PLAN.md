# PLAN.md

## Goal
Migrate Java EE 7 monolith application (WebLogic/WildFly) to Quarkus 3 runtime
- Reference used: javaee-to-quarkus (Java EE 7/8 to Quarkus 3 migration)

## Project Summary
- Type: Maven WAR project (Java EE 7)
- Files affected: 58 Java files, 1 build file, 3 config files, 2 legacy lib files
- Estimated complexity: High
- Hardest steps:
  1. MDB conversion (2 files) - InventoryNotificationMDB has custom JNDI setup + manual JMS
  2. JNDI lookup refactoring (ShoppingCartService) - EJB remote lookup needs complete redesign
  3. WebLogic lifecycle listener replacement (StartupListener)

## Steps

### Step 1: Update build configuration - Change packaging to JAR
- File: pom.xml
- Action: MODIFY
- What to do:
  - Change `<packaging>war</packaging>` → `<packaging>jar</packaging>`
  - Update `<maven.compiler.source>` and `<maven.compiler.target>` from 1.8 to 17
- Why: Quarkus uses JAR packaging and requires Java 17 minimum
- Depends on: none
- Verify: `grep -E '(packaging>jar|source>17|target>17)' pom.xml`

### Step 2: Update build configuration - Replace Java EE BOM with Quarkus BOM
- File: pom.xml
- Action: MODIFY
- What to do:
  - Remove `<dependencyManagement>` section (if exists)
  - Add Quarkus BOM before `<dependencies>`:
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
- Depends on: Step 1
- Verify: `grep -A5 'quarkus-bom' pom.xml`

### Step 3: Update build configuration - Replace Java EE dependencies
- File: pom.xml
- Action: MODIFY
- What to do:
  - Remove dependencies:
    - `javaee-web-api`
    - `javaee-api`
    - `jboss-jms-api_2.0_spec`
    - `jboss-rmi-api_1.0_spec`
  - Add Quarkus extensions:
    - `quarkus-hibernate-orm-panache` (for JPA)
    - `quarkus-jdbc-postgresql` (database)
    - `quarkus-resteasy-reactive-jackson` (JAX-RS)
    - `quarkus-smallrye-reactive-messaging-kafka` (messaging)
    - `quarkus-arc` (CDI)
    - `quarkus-flyway` (keep existing flyway-core)
- Why: Replace Java EE APIs with Quarkus extensions
- Depends on: Step 2
- Verify: `grep -E '(quarkus-hibernate|quarkus-jdbc|quarkus-resteasy|quarkus-smallrye|quarkus-arc)' pom.xml`

### Step 4: Update build configuration - Add Quarkus Maven plugin
- File: pom.xml
- Action: MODIFY
- What to do:
  - Remove `maven-war-plugin`
  - Replace `maven-compiler-plugin` configuration with:
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
  - Keep `maven-surefire-plugin` and `maven-compiler-plugin` with updated config
- Why: Quarkus Maven plugin handles JAR packaging and dev mode
- Depends on: Step 3
- Verify: `mvn clean compile`

### Step 5: Update build configuration - Handle system dependency
- File: pom.xml
- Action: MODIFY
- What to do:
  - Note: `audit-logging-library` system dependency needs manual review
  - Change `<scope>system</scope>` to `<scope>compile</scope>`
  - Remove `<systemPath>` element
  - Install JAR to local Maven repo: `mvn install:install-file -Dfile=lib/audit-logging-library-1.0.0.jar -DgroupId=com.enterprise -DartifactId=audit-logging-library -Dversion=1.0.0 -Dpackaging=jar`
- Why: Quarkus doesn't support system scope dependencies well; use local Maven repo
- Depends on: Step 4
- Verify: `mvn dependency:tree | grep audit-logging`

### Step 6: Create Quarkus configuration file
- File: src/main/resources/application.properties
- Action: CREATE
- What to do:
  - Create new file with content:
    ```properties
    # Datasource configuration
    quarkus.datasource.db-kind=postgresql
    quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore
    quarkus.datasource.username=coolstore
    quarkus.datasource.password=coolstore
    
    # Hibernate configuration
    quarkus.hibernate-orm.database.generation=none
    quarkus.hibernate-orm.log.sql=false
    quarkus.hibernate-orm.sql-load-script=no-file
    
    # Flyway migration
    quarkus.flyway.migrate-at-start=true
    quarkus.flyway.locations=classpath:db/migration
    
    # Kafka messaging (replaces JMS)
    mp.messaging.outgoing.orders.connector=smallrye-kafka
    mp.messaging.outgoing.orders.topic=orders
    mp.messaging.incoming.orders.connector=smallrye-kafka
    mp.messaging.incoming.orders.topic=orders
    
    # Dev services
    %dev.quarkus.datasource.devservices.enabled=true
    %dev.quarkus.kafka.devservices.enabled=true
    ```
- Why: Replaces persistence.xml and externalizes configuration
- Depends on: Step 5
- Verify: `cat src/main/resources/application.properties`

### Step 7: Update JPA entities - Change imports (CatalogItemEntity)
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 6
- Verify: `grep -c 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`

### Step 8: Update JPA entities - Change imports (InventoryEntity)
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 6
- Verify: `grep -c 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/InventoryEntity.java`

### Step 9: Update JPA entities - Change imports (Order)
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 6
- Verify: `grep -c 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/Order.java`

### Step 10: Update JPA entities - Change imports (OrderItem)
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 6
- Verify: `grep -c 'jakarta.persistence' src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 11: Update EntityManager producer - Change to Quarkus pattern
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Replace `@PersistenceContext` with direct `@Inject`
  - Remove `@Produces` method
  - Change to:
    ```java
    import jakarta.enterprise.context.ApplicationScoped;
    import jakarta.inject.Inject;
    import jakarta.persistence.EntityManager;
    
    @ApplicationScoped
    public class Resources {
        @Inject
        EntityManager em;
        
        public EntityManager getEntityManager() {
            return em;
        }
    }
    ```
- Why: Quarkus injects EntityManager directly, no need for @PersistenceContext producer
- Depends on: Step 10
- Verify: `grep -E '@Inject.*EntityManager' src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 12: Convert EJB to CDI - CatalogService
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Remove `import javax.ejb.Stateless;`
  - Remove `@Stateless` annotation
  - Add `import jakarta.enterprise.context.ApplicationScoped;`
  - Add `@ApplicationScoped` annotation
  - Update all `javax.inject.*` → `jakarta.inject.*`
  - Update all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 11
- Verify: `grep -E '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 13: Convert EJB to CDI - ProductService
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Remove `@Stateless`
  - Add `@ApplicationScoped`
  - Update imports: `javax.ejb.*` → remove, add `jakarta.enterprise.context.ApplicationScoped`
  - Update `javax.inject.*` → `jakarta.inject.*`
  - Update `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 12
- Verify: `grep -E '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 14: Convert EJB to CDI - PromoService
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - Remove `@Stateless`
  - Add `@ApplicationScoped`
  - Update all javax imports to jakarta
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 13
- Verify: `grep -E '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/PromoService.java`

### Step 15: Convert EJB to CDI - ShippingService
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Remove `@Stateless` and `@Remote` annotations
  - Remove `ShippingServiceRemote` interface implementation (if present)
  - Add `@ApplicationScoped`
  - Update all javax imports to jakarta
- Why: Quarkus uses CDI; remote interfaces not needed (use REST instead)
- Depends on: Step 14
- Verify: `grep -E '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 16: Convert EJB to CDI - OrderService with lifecycle
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Remove `@Stateless`
  - Add `@ApplicationScoped`
  - Keep `@PostConstruct` and `@PreDestroy` (CDI supports these)
  - Update imports:
    - `javax.ejb.Stateless` → remove
    - `javax.annotation.*` → `jakarta.annotation.*`
    - `javax.inject.*` → `jakarta.inject.*`
    - `javax.persistence.*` → `jakarta.persistence.*`
- Why: Quarkus CDI supports lifecycle annotations
- Depends on: Step 15
- Verify: `grep -E '@ApplicationScoped|@PostConstruct|@PreDestroy' src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 17: Convert EJB to CDI - ShoppingCartOrderProcessor
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Remove `@Stateless`
  - Add `@ApplicationScoped`
  - Update all javax imports to jakarta
- Why: Quarkus uses CDI instead of EJB
- Depends on: Step 16
- Verify: `grep -E '@ApplicationScoped' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 18: COMPLEX - Convert Stateful EJB with JNDI lookup (ShoppingCartService)
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateful` EJB with JNDI remote lookup to ShippingService
  - AFTER: `@ApplicationScoped` CDI bean with direct `@Inject ShippingService`
  - Specific changes:
    1. Remove: `@Stateful`, entire `lookupShippingServiceRemote()` method
    2. Remove imports: `javax.ejb.*`, `javax.naming.*`, `java.util.Hashtable`
    3. Add: `@ApplicationScoped`, `@Inject ShippingService shippingService`
    4. Replace: All calls to `lookupShippingServiceRemote().method()` with `shippingService.method()`
    5. Update: All `javax.*` imports → `jakarta.*`
  - Note: Stateful → ApplicationScoped means shared state; if per-user state needed, use `@RequestScoped` instead
- Why: Quarkus doesn't support EJB remote interfaces; use direct injection
- Depends on: Step 15 (ShippingService must be converted first)
- Verify: `! grep -E 'lookupShippingServiceRemote|@Stateful|javax.naming' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java && grep '@Inject.*ShippingService' src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 19: COMPLEX - Convert MDB to Reactive Messaging (OrderServiceMDB)
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: `@MessageDriven` MDB with JMS MessageListener
  - AFTER: CDI bean with `@Incoming` reactive method
  - Specific changes:
    1. Remove: `@MessageDriven`, `activationConfig` properties, `implements MessageListener`
    2. Remove imports: `javax.ejb.*`, `javax.jms.*`
    3. Add imports: `jakarta.enterprise.context.ApplicationScoped`, `org.eclipse.microprofile.reactive.messaging.Incoming`
    4. Add: `@ApplicationScoped` to class
    5. Replace `onMessage(Message rcvMessage)` with:
       ```java
       @Incoming("orders")
       public void processOrder(String orderStr) {
           System.out.println("Received order: " + orderStr);
           Order order = Transformers.jsonToOrder(orderStr);
           System.out.println("Order object is " + order);
           orderService.save(order);
           order.getItemList().forEach(orderItem -> {
               catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
           });
       }
       ```
    6. Update: `javax.inject.*` → `jakarta.inject.*`
  - Configuration: Uses `mp.messaging.incoming.orders` from application.properties
- Why: Quarkus uses SmallRye Reactive Messaging instead of JMS MDB
- Depends on: Step 6 (application.properties), Step 12 (CatalogService), Step 16 (OrderService)
- Verify: `grep -E '@Incoming.*orders' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java && ! grep '@MessageDriven' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 20: COMPLEX - Convert manual JMS listener with JNDI (InventoryNotificationMDB)
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Manual JMS TopicSubscriber with WebLogic JNDI lookup + init/close methods
  - AFTER: CDI bean with `@Incoming` reactive method
  - Specific changes:
    1. Remove: All JNDI constants, TopicConnection/Session/Subscriber fields, `init()`, `close()`, `getInitialContext()` methods
    2. Remove imports: `javax.jms.*`, `javax.naming.*`, `javax.rmi.*`, `java.util.Hashtable`
    3. Add imports: `jakarta.enterprise.context.ApplicationScoped`, `org.eclipse.microprofile.reactive.messaging.Incoming`
    4. Add: `@ApplicationScoped` to class
    5. Replace `onMessage(Message rcvMessage)` with:
       ```java
       @Incoming("orders")
       public void processInventory(String orderStr) {
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
    6. Keep: `@Inject CatalogService`, `LOW_THRESHOLD` constant
    7. Update: `javax.inject.*` → `jakarta.inject.*`
  - Note: Both MDBs listen to same "orders" topic; Kafka allows multiple consumers
- Why: Replace manual JMS + JNDI with declarative reactive messaging
- Depends on: Step 6 (application.properties), Step 12 (CatalogService)
- Verify: `grep -E '@Incoming.*orders' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java && ! grep -E 'TopicConnection|InitialContext|JNDI' src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 21: Add message publisher to ShoppingCartOrderProcessor
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Add import: `org.eclipse.microprofile.reactive.messaging.Channel`, `org.eclipse.microprofile.reactive.messaging.Emitter`
  - Add field: `@Inject @Channel("orders") Emitter<String> ordersEmitter;`
  - In `process()` method, replace JMS send with: `ordersEmitter.send(Transformers.orderToJson(order));`
  - Update all javax imports to jakarta
- Why: Use Reactive Messaging Emitter instead of JMS producer
- Depends on: Step 6 (application.properties), Step 17
- Verify: `grep -E '@Channel.*orders.*Emitter' src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 22: Update JAX-RS endpoints - CartEndpoint
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - Replace `javax.inject.*` → `jakarta.inject.*`
- Why: Quarkus 3 uses Jakarta namespace for JAX-RS
- Depends on: Step 18 (ShoppingCartService)
- Verify: `grep -c 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 23: Update JAX-RS endpoints - OrderEndpoint
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do: Replace all `javax.ws.rs.*` → `jakarta.ws.rs.*` and `javax.inject.*` → `jakarta.inject.*`
- Why: Quarkus 3 uses Jakarta namespace
- Depends on: Step 16 (OrderService)
- Verify: `grep -c 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 24: Update JAX-RS endpoints - ProductEndpoint
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do: Replace all `javax.ws.rs.*` → `jakarta.ws.rs.*` and `javax.inject.*` → `jakarta.inject.*`
- Why: Quarkus 3 uses Jakarta namespace
- Depends on: Step 11 (Resources/EntityManager)
- Verify: `grep -c 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 25: Update JAX-RS application class
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Replace `javax.ws.rs.core.Application` → `jakarta.ws.rs.core.Application`
  - Replace `javax.ws.rs.ApplicationPath` → `jakarta.ws.rs.ApplicationPath`
- Why: Quarkus 3 uses Jakarta namespace
- Depends on: Step 24
- Verify: `grep -c 'jakarta.ws.rs' src/main/java/com/redhat/coolstore/rest/RestApplication.java`

### Step 26: Update utility classes - Producers
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Replace all `javax.enterprise.*` → `jakarta.enterprise.*`
  - Replace all `javax.inject.*` → `jakarta.inject.*`
- Why: Quarkus 3 uses Jakarta namespace
- Depends on: Step 25
- Verify: `grep -c 'jakarta.enterprise' src/main/java/com/redhat/coolstore/utils/Producers.java`

### Step 27: Update utility classes - DataBaseMigrationStartup
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - Replace all `javax.*` imports → `jakarta.*`
  - Keep Flyway integration as-is (still supported)
- Why: Quarkus 3 uses Jakarta namespace
- Depends on: Step 26
- Verify: `! grep 'import javax\\.' src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`

### Step 28: COMPLEX - Replace WebLogic lifecycle listener (StartupListener)
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - BEFORE: WebLogic `ApplicationLifecycleListener` with `postStart()` and `preStop()`
  - AFTER: Quarkus startup/shutdown events
  - Specific changes:
    1. Remove: `extends ApplicationLifecycleListener`, `postStart()`, `preStop()` methods
    2. Remove imports: `weblogic.application.*`
    3. Add imports: `io.quarkus.runtime.StartupEvent`, `io.quarkus.runtime.ShutdownEvent`, `jakarta.enterprise.event.Observes`
    4. Add `@ApplicationScoped` annotation
    5. Replace with:
       ```java
       void onStart(@Observes StartupEvent event) {
           log.info("AppListener(postStart)");
       }
       
       void onStop(@Observes ShutdownEvent event) {
           log.info("AppListener(preStop)");
       }
       ```
    6. Update: `javax.inject.*` → `jakarta.inject.*`
- Why: Quarkus uses CDI events for lifecycle management, not app server listeners
- Depends on: Step 27
- Verify: `grep -E '@Observes.*(StartupEvent|ShutdownEvent)' src/main/java/com/redhat/coolstore/utils/StartupListener.java && ! grep 'weblogic' src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Step 29: Update utility classes - Transformers
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: Replace all `javax.*` imports → `jakarta.*` (if any)
- Why: Quarkus 3 uses Jakarta namespace
- Depends on: Step 28
- Verify: `! grep 'import javax\\.' src/main/java/com/redhat/coolstore/utils/Transformers.java || echo "No javax imports"`

### Step 30: Update all test files - Change imports
- File: All files in src/test/java/**/*.java (22 files)
- Action: MODIFY
- What to do:
  - Replace all `javax.persistence.*` → `jakarta.persistence.*`
  - Replace all `javax.inject.*` → `jakarta.inject.*`
  - Replace all `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - Replace all `javax.enterprise.*` → `jakarta.enterprise.*`
  - Add `@QuarkusTest` annotation to test classes
  - May need to update test setup for Quarkus test framework
- Why: Tests need Jakarta namespace and Quarkus test support
- Depends on: Step 29
- Verify: `! grep -r 'import javax\\.' src/test/java/ | grep -v '.class'`

### Step 31: Delete persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Remove file entirely
- Why: Configuration moved to application.properties
- Depends on: Step 6 (application.properties created)
- Verify: `! test -f src/main/resources/META-INF/persistence.xml`

### Step 32: Delete beans.xml
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Remove file entirely
- Why: Quarkus auto-discovers CDI beans; beans.xml not needed
- Depends on: Step 30
- Verify: `! test -f src/main/webapp/WEB-INF/beans.xml`

### Step 33: Delete or update web.xml
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Remove file entirely (JAX-RS configuration in RestApplication is sufficient)
- Why: Quarkus JAR packaging doesn't use web.xml
- Depends on: Step 32
- Verify: `! test -f src/main/webapp/WEB-INF/web.xml`

### Step 34: Move webapp resources to META-INF/resources
- File: src/main/webapp/* (all files/folders except WEB-INF)
- Action: MODIFY
- What to do:
  - Create `src/main/resources/META-INF/resources/` directory
  - Move all content from `src/main/webapp/` (except WEB-INF) to `src/main/resources/META-INF/resources/`
  - Includes: app/, bower_components/, partials/, *.jsp, *.json, *.png files
- Why: Quarkus serves static resources from META-INF/resources
- Depends on: Step 33
- Verify: `test -d src/main/resources/META-INF/resources && ls src/main/resources/META-INF/resources/`

### Step 35: Delete ShippingServiceRemote interface
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Remove file entirely
- Why: Remote EJB interfaces not needed in Quarkus; replaced with direct injection
- Depends on: Step 18 (ShoppingCartService no longer uses it)
- Verify: `! test -f src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java`

### Step 36: Delete WebLogic stub classes
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Remove file entirely
- Why: WebLogic-specific class no longer needed
- Depends on: Step 28 (StartupListener converted)
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleEvent.java`

### Step 37: Delete WebLogic stub classes
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Remove file entirely
- Why: WebLogic-specific class no longer needed
- Depends on: Step 28 (StartupListener converted)
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleListener.java`

### Step 38: Delete WebLogic stub classes
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Remove file entirely (check for usages first)
- Why: WebLogic-specific logging class; use standard Java logging or Quarkus logging
- Depends on: Step 37
- Verify: `! test -f src/main/java/weblogic/i18n/logging/NonCatalogLogger.java`

### Step 39: Update .gitignore for Quarkus
- File: .gitignore (create if doesn't exist)
- Action: MODIFY or CREATE
- What to do:
  - Add Quarkus-specific entries:
    ```
    target/
    .quarkus/
    quarkus.log
    ```
- Why: Ignore Quarkus build artifacts and dev mode files
- Depends on: Step 38
- Verify: `grep '.quarkus' .gitignore`

### Step 40: Final build verification
- File: N/A (build verification)
- Action: N/A
- What to do: Run `mvn clean package` to verify all changes compile successfully
- Why: Ensure migration is complete and builds
- Depends on: Step 39
- Verify: `mvn clean package && ls -la target/*.jar`

## Verification
Run these commands after all steps are complete:

```bash
# Verify build succeeds
mvn clean package

# Verify no javax.* Java EE imports remain (some javax.* are OK, like javax.crypto)
! grep -r "import javax\\.ejb" src/main/java/
! grep -r "import javax\\.jms" src/main/java/
! grep -r "import javax\\.persistence" src/main/java/
! grep -r "import javax\\.ws\\.rs" src/main/java/

# Verify Quarkus extensions are present
grep -E "quarkus-hibernate|quarkus-resteasy|quarkus-smallrye" pom.xml

# Verify JAR packaging
grep "<packaging>jar</packaging>" pom.xml

# Verify configuration file exists
test -f src/main/resources/application.properties

# Run Quarkus dev mode (requires Kafka and PostgreSQL)
mvn quarkus:dev
```

## Notes

### Gotchas
1. **State management**: The original `ShoppingCartService` was `@Stateful`, which maintained per-client state. Converting to `@ApplicationScoped` makes it singleton. If per-user shopping carts are needed, consider:
   - Using `@RequestScoped` with session storage
   - Storing cart state in database
   - Using client-side state management

2. **Messaging change**: JMS Topic → Kafka Topic. Both MDBs listen to same "orders" topic, which works in Kafka (consumer groups), but verify the ordering semantics meet requirements.

3. **Remote EJB**: The `ShippingServiceRemote` JNDI lookup pattern was for distributed calls. If ShippingService needs to run as separate microservice, expose it as REST endpoint instead.

4. **System dependency**: The `audit-logging-library-1.0.0.jar` needs to be installed to local Maven repo before build will work. Run the install command from Step 5.

5. **Database**: Application expects PostgreSQL. For local dev, Quarkus Dev Services will auto-start a container. For production, update application.properties with real database URL.

6. **Java version**: Quarkus 3 requires Java 17 minimum. Update development environment accordingly.

### Testing Strategy
1. Test each layer independently:
   - Entities (Step 7-10)
   - Services (Step 12-21)
   - REST endpoints (Step 22-25)
2. Complex conversions (MDB, JNDI) should be tested immediately after conversion
3. Full integration test after Step 40

### Migration Order Rationale
The steps follow the layered dependency order:
1. **Build config** (Steps 1-5) - Foundation for everything
2. **App config** (Step 6) - Required by services
3. **Entities** (Steps 7-10) - No dependencies, used by services
4. **Persistence** (Step 11) - Used by services
5. **Services** (Steps 12-21) - Business logic layer, ordered by dependencies
6. **REST** (Steps 22-25) - Depends on services
7. **Utils** (Steps 26-29) - Cross-cutting concerns
8. **Tests** (Step 30) - Depends on all application code
9. **Cleanup** (Steps 31-39) - Delete after migration complete
10. **Verify** (Step 40) - Final validation
