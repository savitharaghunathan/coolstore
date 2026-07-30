# PLAN.md

## Goal
Migrate Java EE 7 monolith application from WebLogic/WildFly to Quarkus 3, converting EJBs to CDI, MDBs to SmallRye Reactive Messaging, removing JNDI lookups, and replacing server-specific lifecycle listeners with Quarkus events.

- Reference used: javaee-to-quarkus skill (6 phases: Build Config, App Config, EJB to CDI, Messaging, Lifecycle, Cleanup)

## Project Summary
- Type: Maven WAR (Java EE 7) → Maven JAR (Quarkus 3)
- Java version: 8 → 17
- Files affected: ~30 Java source files + configuration files
- Estimated complexity: **High**
- Hardest steps:
  1. Converting InventoryNotificationMDB with manual WebLogic JNDI lookups to SmallRye Reactive Messaging
  2. Removing JNDI-based EJB remote lookup in ShoppingCartService
  3. Migrating WebLogic ApplicationLifecycleListener to Quarkus lifecycle events

## Steps

### Phase 1: Build Configuration

#### Step 1: Update Maven compiler configuration
- File: pom.xml
- Action: MODIFY
- What to do:
  - Change `<source>1.8</source>` and `<target>1.8</target>` to `<source>17</source>` and `<target>17</target>`
  - Update maven-compiler-plugin version from 3.0 to 3.11.0
- Why: Quarkus 3 requires Java 17 minimum
- Depends on: none
- Verify: `grep -A 10 "maven-compiler-plugin" pom.xml | grep "<source>17"`

#### Step 2: Change packaging from WAR to JAR
- File: pom.xml
- Action: MODIFY
- What to do:
  - Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
  - Remove maven-war-plugin section
- Why: Quarkus applications are standalone JARs, not WARs deployed to app servers
- Depends on: Step 1
- Verify: `grep "<packaging>" pom.xml` shows "jar"

#### Step 3: Add Quarkus BOM
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add after `<properties>` section:
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
- Why: Quarkus BOM manages all Quarkus extension versions
- Depends on: Step 2
- Verify: `grep -A 5 "quarkus-bom" pom.xml`

#### Step 4: Replace Java EE dependencies with Quarkus extensions
- File: pom.xml
- Action: MODIFY
- What to do:
  - Remove: javaee-web-api, javaee-api dependencies
  - Remove: jboss-jms-api_2.0_spec, jboss-rmi-api_1.0_spec
  - Add Quarkus extensions:
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
        <artifactId>quarkus-smallrye-reactive-messaging-amqp</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-arc</artifactId>
    </dependency>
    ```
  - Keep: flyway-core, audit-logging-library (system scoped)
- Why: Quarkus uses extensions instead of Java EE APIs
- Depends on: Step 3
- Verify: `grep "quarkus-" pom.xml | wc -l` shows at least 5 extensions

#### Step 5: Add Quarkus Maven plugin
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add after maven-compiler-plugin:
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
  - Remove: maven-surefire-plugin (Quarkus BOM provides it)
- Why: Required for Quarkus builds and dev mode
- Depends on: Step 4
- Verify: `mvn clean compile` succeeds

### Phase 2: Application Configuration

#### Step 6: Create application.properties
- File: src/main/resources/application.properties
- Action: CREATE
- What to do:
  - Create new file with datasource and JPA configuration:
    ```properties
    # Datasource configuration
    quarkus.datasource.db-kind=postgresql
    quarkus.datasource.username=coolstore
    quarkus.datasource.password=coolstore
    quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore
    
    # Hibernate configuration
    quarkus.hibernate-orm.database.generation=none
    quarkus.hibernate-orm.log.sql=false
    quarkus.hibernate-orm.log.format-sql=true
    
    # Messaging configuration
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
    ```
- Why: Quarkus uses application.properties instead of persistence.xml for configuration
- Depends on: Step 5
- Verify: File exists and contains datasource config

#### Step 7: Delete persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Remove file completely
- Why: Configuration moved to application.properties
- Depends on: Step 6
- Verify: `test ! -f src/main/resources/META-INF/persistence.xml`

#### Step 8: Delete beans.xml
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Remove file completely
- Why: Quarkus Arc (CDI) doesn't require beans.xml
- Depends on: Step 6
- Verify: `test ! -f src/main/webapp/WEB-INF/beans.xml`

#### Step 9: Delete web.xml
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Remove file completely
- Why: JAR packaging doesn't use web.xml
- Depends on: Step 6
- Verify: `test ! -f src/main/webapp/WEB-INF/web.xml`

### Phase 3: EJB to CDI Migration

#### Step 10: Convert CatalogService from EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` with `@ApplicationScoped`
  - Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
  - Replace all `javax.inject.*` imports with `jakarta.inject.*`
- Why: Quarkus uses CDI managed beans instead of EJBs
- Depends on: Step 9
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/CatalogService.java`

#### Step 11: Convert OrderService from EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` with `@ApplicationScoped`
  - Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
  - Replace all `javax.inject.*` imports with `jakarta.inject.*`
- Why: Quarkus uses CDI managed beans instead of EJBs
- Depends on: Step 9
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/OrderService.java`

#### Step 12: Convert ProductService from EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` with `@ApplicationScoped`
  - Replace all `javax.persistence.*` imports with `jakarta.persistence.*`
  - Replace all `javax.inject.*` imports with `jakarta.inject.*`
- Why: Quarkus uses CDI managed beans instead of EJBs
- Depends on: Step 9
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/ProductService.java`

#### Step 13: COMPLEX - Convert ShippingService from Remote EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateless @Remote` with ShippingServiceRemote interface
  - AFTER: `@ApplicationScoped` with direct injection
  - Specific changes:
    1. Remove: `import javax.ejb.Remote;`, `import javax.ejb.Stateless;`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`
    3. Remove: `@Stateless` and `@Remote` annotations
    4. Add: `@ApplicationScoped`
    5. Keep: `implements ShippingServiceRemote` (interface becomes regular interface)
- Why: Quarkus doesn't support EJB @Remote; use direct CDI injection instead
- Depends on: Step 9
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/ShippingService.java && ! grep -q "@Remote" src/main/java/com/redhat/coolstore/service/ShippingService.java`

#### Step 14: Convert ShoppingCartOrderProcessor from EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` with `@ApplicationScoped`
  - Replace all `javax.*` imports with `jakarta.*` equivalents
- Why: Quarkus uses CDI managed beans instead of EJBs
- Depends on: Step 9
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

#### Step 15: COMPLEX - Convert ShoppingCartService from Stateful EJB to SessionScoped CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE: `@Stateful` EJB with JNDI lookup of ShippingServiceRemote
  - AFTER: `@SessionScoped` CDI bean with direct injection
  - Specific changes:
    1. Remove: `import javax.ejb.Stateful;`
    2. Remove: `import javax.naming.*` imports
    3. Remove: `import java.util.Hashtable;`
    4. Add: `import jakarta.enterprise.context.SessionScoped;`
    5. Add: `import jakarta.inject.Inject;`
    6. Replace: `@Stateful` with `@SessionScoped`
    7. Add field: `@Inject ShippingServiceRemote shippingService;`
    8. Replace method `lookupShippingServiceRemote()` calls with `shippingService`
    9. Delete: entire `lookupShippingServiceRemote()` method
    10. Replace all `javax.inject.*` imports with `jakarta.inject.*`
- Why: @Stateful EJB maps to @SessionScoped in CDI; JNDI lookups replaced with direct injection
- Depends on: Step 13 (ShippingService must be converted first)
- Verify: `grep -q "@SessionScoped" src/main/java/com/redhat/coolstore/service/ShoppingCartService.java && ! grep -q "InitialContext" src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

#### Step 16: Convert DataBaseMigrationStartup from Singleton to ApplicationScoped
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Singleton;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `import javax.ejb.Startup;` with `import io.quarkus.runtime.Startup;`
  - Replace `@Singleton` with `@ApplicationScoped`
  - Keep `@Startup` (Quarkus provides equivalent)
  - Replace all `javax.*` imports with `jakarta.*` equivalents
- Why: Quarkus uses @ApplicationScoped with @Startup instead of @Singleton
- Depends on: Step 9
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`

### Phase 4: Messaging Migration

#### Step 17: COMPLEX - Convert OrderServiceMDB to SmallRye Reactive Messaging
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: `@MessageDriven` with JMS MessageListener and onMessage()
  - AFTER: `@ApplicationScoped` with `@Incoming` reactive method
  - Specific changes:
    1. Remove: All `javax.ejb.*` imports
    2. Remove: All `javax.jms.*` imports
    3. Add: `import jakarta.enterprise.context.ApplicationScoped;`
    4. Add: `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    5. Remove: `@MessageDriven` annotation and all `@ActivationConfigProperty`
    6. Remove: `implements MessageListener`
    7. Add: `@ApplicationScoped`
    8. Replace method signature:
       - OLD: `public void onMessage(Message rcvMessage)`
       - NEW: `@Incoming("orders") public void processOrder(String orderStr)`
    9. Remove: JMS message handling code (TextMessage casting, msg.getBody())
    10. Simplify: Method body now receives String directly
    11. Replace all `javax.inject.*` imports with `jakarta.inject.*`
  - Final method:
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
- Why: Quarkus uses SmallRye Reactive Messaging instead of JMS/MDB
- Depends on: Step 10, Step 11 (services must be converted first)
- Verify: `grep -q "@Incoming" src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java && ! grep -q "@MessageDriven" src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

#### Step 18: COMPLEX - Convert InventoryNotificationMDB to SmallRye Reactive Messaging
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Manual JNDI lookup with WebLogic-specific TopicConnection, implements MessageListener
  - AFTER: `@ApplicationScoped` with `@Incoming` reactive method
  - Specific changes:
    1. Remove: All `javax.jms.*` imports
    2. Remove: All `javax.naming.*` imports
    3. Remove: All `javax.rmi.*` imports
    4. Remove: `import java.util.Hashtable;`
    5. Add: `import jakarta.enterprise.context.ApplicationScoped;`
    6. Add: `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    7. Remove: `implements MessageListener`
    8. Add: `@ApplicationScoped` class annotation
    9. Remove: All static JNDI constants (JNDI_FACTORY, JMS_FACTORY, TOPIC)
    10. Remove: All JMS connection fields (tcon, tsession, tsubscriber)
    11. Replace method signature:
        - OLD: `public void onMessage(Message rcvMessage)`
        - NEW: `@Incoming("orders") public void checkInventory(String orderStr)`
    12. Remove: JMS message handling code
    13. Remove: `init()` and `close()` methods (no longer needed)
    14. Remove: `getInitialContext()` method
    15. Replace all `javax.inject.*` imports with `jakarta.inject.*`
  - Final method:
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
- Why: Eliminate WebLogic-specific JNDI and use Quarkus messaging
- Depends on: Step 10 (CatalogService must be converted first)
- Verify: `grep -q "@Incoming" src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java && ! grep -q "InitialContext" src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

#### Step 19: Update ShoppingCartOrderProcessor to use Reactive Messaging Emitter
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Add: `import org.eclipse.microprofile.reactive.messaging.Channel;`
  - Add: `import org.eclipse.microprofile.reactive.messaging.Emitter;`
  - Add field: `@Inject @Channel("orders-out") Emitter<String> orderEmitter;`
  - Replace JMS send code with: `orderEmitter.send(orderJson);`
  - Replace all `javax.*` imports with `jakarta.*` equivalents
- Why: Use SmallRye Reactive Messaging Emitter instead of JMS producers
- Depends on: Step 14
- Verify: `grep -q "Emitter" src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Phase 5: Lifecycle Migration

#### Step 20: COMPLEX - Replace WebLogic lifecycle with Quarkus events
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - BEFORE: Extends WebLogic ApplicationLifecycleListener
  - AFTER: CDI bean with Quarkus lifecycle observers
  - Specific changes:
    1. Remove: `import weblogic.application.*;`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`
    3. Add: `import io.quarkus.runtime.StartupEvent;`
    4. Add: `import io.quarkus.runtime.ShutdownEvent;`
    5. Add: `import jakarta.enterprise.event.Observes;`
    6. Remove: `extends ApplicationLifecycleListener`
    7. Add: `@ApplicationScoped` class annotation
    8. Replace method signature:
       - OLD: `public void postStart(ApplicationLifecycleEvent evt)`
       - NEW: `void onStart(@Observes StartupEvent event)`
    9. Replace method signature:
       - OLD: `public void preStop(ApplicationLifecycleEvent evt)`
       - NEW: `void onStop(@Observes ShutdownEvent event)`
    10. Update log messages to remove "AppListener" prefix
    11. Replace all `javax.inject.*` imports with `jakarta.inject.*`
- Why: Quarkus uses CDI events instead of application server lifecycle listeners
- Depends on: Step 9
- Verify: `grep -q "@Observes StartupEvent" src/main/java/com/redhat/coolstore/utils/StartupListener.java && ! grep -q "weblogic" src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Phase 6: JPA Entity Migration

#### Step 21: Migrate JPA imports in all entity classes
- File: Multiple entity files in src/main/java/com/redhat/coolstore/model/
- Action: MODIFY
- What to do:
  - For each entity (CatalogItemEntity.java, InventoryEntity.java, Order.java, OrderItem.java, ShoppingCart.java):
    - Replace all `import javax.persistence.*;` with `import jakarta.persistence.*;`
  - No other changes needed (JPA annotations remain the same)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 9
- Verify: `! grep -r "import javax.persistence" src/main/java/com/redhat/coolstore/model/`

#### Step 22: Migrate JPA imports in Resources.java
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Replace `import javax.enterprise.inject.Produces;` with `import jakarta.enterprise.inject.Produces;`
  - Replace `import javax.enterprise.inject.Default;` with `import jakarta.enterprise.inject.Default;`
  - Replace `import javax.persistence.EntityManager;` with `import jakarta.persistence.EntityManager;`
  - Replace `import javax.persistence.PersistenceContext;` with `import jakarta.persistence.PersistenceContext;`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 9
- Verify: `! grep -q "import javax\\." src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Phase 7: JAX-RS Migration

#### Step 23: Migrate JAX-RS imports in CartEndpoint
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
  - Replace `javax.inject.*` imports with `jakarta.inject.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 9
- Verify: `! grep -q "import javax\\.ws\\.rs" src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

#### Step 24: Migrate JAX-RS imports in OrderEndpoint
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
  - Replace `javax.inject.*` imports with `jakarta.inject.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 9
- Verify: `! grep -q "import javax\\.ws\\.rs" src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

#### Step 25: Migrate JAX-RS imports in ProductEndpoint
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `javax.ws.rs.*` imports with `jakarta.ws.rs.*`
  - Replace `javax.inject.*` imports with `jakarta.inject.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 9
- Verify: `! grep -q "import javax\\.ws\\.rs" src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

#### Step 26: Migrate RestApplication
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ws.rs.ApplicationPath;` with `import jakarta.ws.rs.ApplicationPath;`
  - Replace `import javax.ws.rs.core.Application;` with `import jakarta.ws.rs.core.Application;`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 9
- Verify: `! grep -q "import javax\\.ws\\.rs" src/main/java/com/redhat/coolstore/rest/RestApplication.java`

### Phase 8: Utilities Migration

#### Step 27: Migrate Producers utility
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Replace all `javax.enterprise.*` imports with `jakarta.enterprise.*`
  - Replace all `javax.inject.*` imports with `jakarta.inject.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 9
- Verify: `! grep -q "import javax\\." src/main/java/com/redhat/coolstore/utils/Producers.java`

#### Step 28: Migrate Transformers utility
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do:
  - Replace all `javax.*` imports with `jakarta.*` equivalents (if any)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 9
- Verify: `! grep -q "import javax\\." src/main/java/com/redhat/coolstore/utils/Transformers.java`

### Phase 9: Cleanup

#### Step 29: Delete WebLogic stub classes
- File: src/main/java/weblogic/ (entire directory)
- Action: DELETE
- What to do:
  - Delete: src/main/java/weblogic/application/ApplicationLifecycleListener.java
  - Delete: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
  - Delete: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
  - Delete entire weblogic package directory
- Why: WebLogic-specific code no longer needed in Quarkus
- Depends on: Step 20 (StartupListener must be migrated first)
- Verify: `test ! -d src/main/java/weblogic`

#### Step 30: Delete webapp directory
- File: src/main/webapp/ (entire directory)
- Action: DELETE
- What to do:
  - Move static resources (if any needed) to src/main/resources/META-INF/resources/
  - Delete src/main/webapp directory
- Why: JAR packaging uses META-INF/resources instead of webapp
- Depends on: Step 9
- Verify: `test ! -d src/main/webapp`

#### Step 31: Rename OrderServiceMDB class
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - Rename class from `OrderServiceMDB` to `OrderMessageConsumer`
  - Rename file to `OrderMessageConsumer.java`
- Why: "MDB" suffix is Java EE-specific; better naming for Quarkus
- Depends on: Step 17
- Verify: `test -f src/main/java/com/redhat/coolstore/service/OrderMessageConsumer.java`

#### Step 32: Rename InventoryNotificationMDB class
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - Rename class from `InventoryNotificationMDB` to `InventoryMessageConsumer`
  - Rename file to `InventoryMessageConsumer.java`
- Why: "MDB" suffix is Java EE-specific; better naming for Quarkus
- Depends on: Step 18
- Verify: `test -f src/main/java/com/redhat/coolstore/service/InventoryMessageConsumer.java`

#### Step 33: Final verification - no javax.* Java EE imports remain
- File: All Java files
- Action: VERIFY
- What to do:
  - Run: `grep -r "import javax\\.ejb\\|import javax\\.jms\\|import javax\\.persistence\\|import javax\\.ws\\.rs\\|import javax\\.enterprise\\|import javax\\.inject" src/main/java/com/redhat/coolstore/`
  - Should return NO matches
- Why: All Java EE javax imports should be replaced with jakarta
- Depends on: All previous steps
- Verify: Command returns empty (exit code 1)

## Verification

After all steps are complete, verify the migration:

```bash
# Build the Quarkus application
mvn clean package

# Run tests
mvn test

# Start dev mode
mvn quarkus:dev

# Check application starts successfully
curl http://localhost:8080/api/products

# Verify no javax.* Java EE imports remain
! grep -r "import javax\\.ejb\\|import javax\\.jms\\|import javax\\.persistence\\|import javax\\.ws\\.rs\\|import javax\\.enterprise\\|import javax\\.inject" src/main/java/com/redhat/coolstore/
```

## Notes

### Critical Dependencies
- Steps 17-18 (MDB conversion) depend on services being converted first (Steps 10-11)
- Step 15 (ShoppingCartService JNDI removal) depends on Step 13 (ShippingService conversion)
- Step 20 (lifecycle) depends on WebLogic stubs still existing during migration
- Step 29 (delete stubs) must come after Step 20

### Complex Transformations
1. **InventoryNotificationMDB** - Most complex due to manual WebLogic JNDI lookups and connection management
2. **ShoppingCartService** - JNDI lookup of remote EJB must be replaced with direct injection
3. **Messaging** - Two different MDB patterns (annotation-based and manual) require different approaches

### Configuration Notes
- **Datasource**: persistence.xml used JTA datasource `java:jboss/datasources/CoolstoreDS`
  - Migrated to Quarkus datasource config in application.properties
  - Verify actual database type (PostgreSQL assumed)
- **Messaging**: Assumed AMQP/ActiveMQ Artemis for messaging
  - Original used JMS Topic "topic/orders"
  - Migrated to SmallRye Reactive Messaging with AMQP connector
  - May need adjustment based on actual message broker

### Testing Strategy
- Test after each phase completion
- Phase 1-2: `mvn compile` should succeed
- Phase 3: Services should compile with CDI
- Phase 4: Messaging consumers should compile
- Phase 5-9: Full build and tests should pass

### Rollback Points
- After Step 5: Build config complete, can verify compilation
- After Step 9: Config migration complete
- After Step 16: EJB migration complete
- After Step 19: Messaging migration complete
- After Step 28: All code migration complete

### Known Issues
- **Audit logging library**: System-scoped dependency may need adjustment for Quarkus
- **Static resources**: webapp/ content needs manual review before deletion
- **Session management**: @SessionScoped may require HTTP session configuration in Quarkus
- **Message broker**: AMQP configuration assumes ActiveMQ Artemis; adjust for actual broker
