# PLAN.md

## Goal
Migrate a Java EE 7 monolith application (WebLogic/JBoss) to Quarkus 3, replacing EJB with CDI, JMS/MDB with SmallRye Reactive Messaging, WAR packaging with JAR, and removing all application server dependencies.

- Reference used: javaee-to-quarkus (pattern-map.md, dependency-map.md, annotation-map.md, config-map.md)

## Project Summary
- Type: Maven (Java EE 7 WAR)
- Files affected: 27 Java files + pom.xml + configs
- Estimated complexity: **High**
- Hardest steps:
  1. Converting InventoryNotificationMDB (JNDI lookups + WebLogic-specific TopicConnectionFactory)
  2. Converting OrderServiceMDB (JMS Topic → Reactive Messaging)
  3. Replacing WebLogic ApplicationLifecycleListener with Quarkus events
  4. Migrating system-scoped audit-logging-library dependency

## Steps

### Step 1: Update pom.xml - Change packaging to JAR
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus produces standalone JARs, not WARs
- Depends on: none
- Verify: `grep '<packaging>jar</packaging>' pom.xml`

### Step 2: Add Quarkus BOM to pom.xml
- File: pom.xml
- Action: MODIFY
- What to do:
  - Add `<dependencyManagement>` section with Quarkus BOM 3.8.4
  - Add after `<properties>` section, before `<dependencies>`
  ```xml
  <dependencyManagement>
      <dependencies>
          <dependency>
              <groupId>io.quarkus.platform</groupId>
              <artifactId>quarkus-bom</artifactId>
              <version>3.8.4</version>
              <type>pom</type>
              <scope>import</scope>
          </dependency>
      </dependencies>
  </dependencyManagement>
  ```
- Why: Quarkus BOM manages all extension versions
- Depends on: Step 1
- Verify: `grep 'quarkus-bom' pom.xml`

### Step 3: Replace Java EE dependencies with Quarkus extensions in pom.xml
- File: pom.xml
- Action: MODIFY
- What to do:
  - REMOVE: `javax:javaee-web-api`, `javax:javaee-api`, `org.jboss.spec.javax.jms:jboss-jms-api_2.0_spec`, `org.jboss.spec.javax.rmi:jboss-rmi-api_1.0_spec`
  - ADD (without version numbers - managed by BOM):
    ```xml
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-arc</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-rest</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-rest-jackson</artifactId>
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
        <artifactId>quarkus-flyway</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-smallrye-reactive-messaging-amqp</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-narayana-jta</artifactId>
    </dependency>
    ```
  - UPDATE Flyway: Remove `<version>4.1.2</version>` from `org.flywaydb:flyway-core` (managed by quarkus-flyway)
  - KEEP: Test dependencies (junit, mockito, h2, hibernate-entitymanager for tests), audit-logging-library
- Why: Quarkus uses individual extensions instead of monolithic Java EE API
- Depends on: Step 2
- Verify: `mvn dependency:tree | grep quarkus`

### Step 4: Add Quarkus Maven plugin to pom.xml
- File: pom.xml
- Action: MODIFY
- What to do:
  - REMOVE: `maven-war-plugin`
  - ADD in `<build><plugins>` section:
    ```xml
    <plugin>
        <groupId>io.quarkus.platform</groupId>
        <artifactId>quarkus-maven-plugin</artifactId>
        <version>3.8.4</version>
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
  - UPDATE maven-compiler-plugin: Change source/target from `1.8` to `17`
- Why: Quarkus requires Java 17+ and uses its own build plugin
- Depends on: Step 3
- Verify: `grep 'quarkus-maven-plugin' pom.xml`

### Step 5: Build verification - Phase 1 checkpoint
- File: pom.xml
- Action: MODIFY
- What to do: Run `mvn clean compile`
- Why: Verify dependencies resolve before modifying source code
- Depends on: Step 4
- Verify: Build succeeds with no errors

### Step 6: Create application.properties with datasource config
- File: src/main/resources/application.properties
- Action: CREATE
- What to do:
  ```properties
  # Datasource configuration
  quarkus.datasource.db-kind=h2
  quarkus.datasource.jdbc.url=jdbc:h2:mem:coolstore;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1
  quarkus.datasource.username=sa
  quarkus.datasource.password=sa

  # Hibernate configuration
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=false
  quarkus.hibernate-orm.log.format-sql=true

  # Flyway configuration
  quarkus.flyway.migrate-at-start=true
  quarkus.flyway.locations=classpath:db/migration

  # Messaging configuration (AMQP)
  # Order topic
  mp.messaging.incoming.order-queue.connector=smallrye-amqp
  mp.messaging.incoming.order-queue.address=orders
  mp.messaging.incoming.order-queue.durable=true

  # HTTP configuration
  quarkus.http.port=8080

  # Dev mode configuration
  %dev.quarkus.datasource.jdbc.url=jdbc:h2:mem:devdb;DB_CLOSE_ON_EXIT=FALSE
  %dev.quarkus.hibernate-orm.database.generation=drop-and-create
  %dev.quarkus.hibernate-orm.log.sql=true

  # Test configuration
  %test.quarkus.datasource.jdbc.url=jdbc:h2:mem:testdb
  %test.quarkus.hibernate-orm.database.generation=drop-and-create
  ```
- Why: Quarkus uses application.properties instead of XML configs
- Depends on: Step 5
- Verify: File exists and contains datasource properties

### Step 7: Delete persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Remove file - configuration moved to application.properties
- Why: Quarkus manages JPA configuration via application.properties
- Depends on: Step 6
- Verify: File no longer exists

### Step 8: Delete web.xml
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Remove file - not needed for Quarkus
- Why: Quarkus uses JAX-RS annotations, not web.xml
- Depends on: Step 6
- Verify: File no longer exists

### Step 9: Delete beans.xml
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Remove file - CDI enabled by default in Quarkus
- Why: Quarkus Arc (CDI) is always enabled
- Depends on: Step 6
- Verify: File no longer exists

### Step 10: Migrate imports in Resources.java
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Replace `javax.enterprise.inject.*` → `jakarta.enterprise.inject.*`
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
- Why: Jakarta EE namespace change
- Depends on: Step 5
- Verify: No `javax.` imports remain in file

### Step 11: Migrate imports in Producers.java
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Replace `javax.enterprise.inject.*` → `jakarta.enterprise.inject.*`
  - Replace `javax.enterprise.context.*` → `jakarta.enterprise.context.*`
- Why: Jakarta EE namespace change
- Depends on: Step 5
- Verify: No `javax.` imports remain in file

### Step 12: Migrate imports and annotations in DataBaseMigrationStartup.java
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - Replace `javax.annotation.*` → `jakarta.annotation.*`
  - Replace `javax.ejb.Singleton` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.ejb.Startup` → `io.quarkus.runtime.Startup`
  - Update imports accordingly
- Why: EJB Singleton → CDI ApplicationScoped, Quarkus Startup annotation
- Depends on: Step 5
- Verify: No `javax.ejb` imports remain

### Step 13: COMPLEX - Replace WebLogic lifecycle listener with Quarkus events
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - BEFORE: Extends `ApplicationLifecycleListener` with `postStart/preStop` methods
  - AFTER:
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
  - Remove: All WebLogic imports and inheritance
  - Add: Quarkus event observers
- Why: WebLogic ApplicationLifecycleListener not available in Quarkus
- Depends on: Step 5
- Verify: `grep '@Observes StartupEvent' src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Step 14: Migrate imports in all model entities
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Jakarta EE namespace change
- Depends on: Step 5
- Verify: No `javax.persistence` imports remain

### Step 15: Migrate imports in InventoryEntity.java
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Jakarta EE namespace change
- Depends on: Step 5
- Verify: No `javax.persistence` imports remain

### Step 16: Migrate imports in Order.java
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Jakarta EE namespace change
- Depends on: Step 5
- Verify: No `javax.persistence` imports remain

### Step 17: Migrate imports in OrderItem.java
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Jakarta EE namespace change
- Depends on: Step 5
- Verify: No `javax.persistence` imports remain

### Step 18: Migrate imports in Product.java
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Jakarta EE namespace change
- Depends on: Step 5
- Verify: No `javax.persistence` imports remain

### Step 19: Migrate imports in Promotion.java
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Jakarta EE namespace change
- Depends on: Step 5
- Verify: No `javax.persistence` imports remain

### Step 20: Migrate imports in ShoppingCart.java
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Jakarta EE namespace change
- Depends on: Step 5
- Verify: No `javax.persistence` imports remain

### Step 21: Migrate imports in ShoppingCartItem.java
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Replace all `javax.persistence.*` → `jakarta.persistence.*`
- Why: Jakarta EE namespace change
- Depends on: Step 5
- Verify: No `javax.persistence` imports remain

### Step 22: Migrate EJB to CDI in CatalogService.java
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.*` → `jakarta.inject.*`
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
- Why: EJB Stateless → CDI ApplicationScoped
- Depends on: Steps 14-21
- Verify: No `javax.ejb` imports remain

### Step 23: Migrate EJB to CDI in ProductService.java
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.*` → `jakarta.inject.*`
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
- Why: EJB Stateless → CDI ApplicationScoped
- Depends on: Steps 14-21
- Verify: No `javax.ejb` imports remain

### Step 24: Migrate EJB to CDI in PromoService.java
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.*` → `jakarta.inject.*`
- Why: EJB Stateless → CDI ApplicationScoped
- Depends on: Step 5
- Verify: No `javax.ejb` imports remain

### Step 25: Migrate EJB to CDI in ShoppingCartOrderProcessor.java
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.*` → `jakarta.inject.*`
- Why: EJB Stateless → CDI ApplicationScoped
- Depends on: Step 5
- Verify: No `javax.ejb` imports remain

### Step 26: Migrate EJB to CDI and remove JNDI in ShoppingCartService.java
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateful` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.*` → `jakarta.inject.*`
  - Remove all JNDI-related code: `InitialContext`, `Context`, `NamingException`, `Hashtable` imports
  - Replace any JNDI lookups with direct `@Inject`
- Why: EJB Stateful → CDI ApplicationScoped, no JNDI in Quarkus
- Depends on: Steps 22-25
- Verify: No `javax.ejb` or `javax.naming` imports remain

### Step 27: Migrate EJB to CDI in OrderService.java
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Replace `javax.inject.*` → `jakarta.inject.*`
  - Replace `javax.persistence.*` → `jakarta.persistence.*`
  - Replace `javax.annotation.*` → `jakarta.annotation.*`
  - Add `jakarta.transaction.Transactional` import and annotation to save() method
- Why: EJB Stateless → CDI ApplicationScoped, explicit transaction management
- Depends on: Steps 14-21
- Verify: No `javax.ejb` imports remain, `@Transactional` on save()

### Step 28: COMPLEX - Remove Remote EJB interface and convert ShippingService
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Remove `@Remote` annotation
  - Replace `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped`
  - Remove `implements ShippingServiceRemote` (keep methods)
  - Add `jakarta.enterprise.context.ApplicationScoped` import
- Why: No remote EJBs in Quarkus, use CDI beans directly
- Depends on: Step 5
- Verify: No `javax.ejb` imports, no `@Remote` annotation

### Step 29: Delete ShippingServiceRemote.java
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Remove file - remote interfaces not needed
- Why: Quarkus uses local CDI beans only
- Depends on: Step 28
- Verify: File no longer exists

### Step 30: COMPLEX - Convert OrderServiceMDB to Reactive Messaging
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: `@MessageDriven` with JMS Topic listener
  - AFTER:
    ```java
    package com.redhat.coolstore.service;

    import jakarta.enterprise.context.ApplicationScoped;
    import jakarta.inject.Inject;
    import org.eclipse.microprofile.reactive.messaging.Incoming;
    import com.redhat.coolstore.model.Order;
    import com.redhat.coolstore.utils.Transformers;

    @ApplicationScoped
    public class OrderServiceMDB {

        @Inject
        OrderService orderService;

        @Inject
        CatalogService catalogService;

        @Incoming("order-queue")
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
  - Remove: All JMS imports (`javax.jms.*`, `javax.ejb.MessageDriven`, etc.)
  - Remove: `implements MessageListener`, `onMessage(Message)` signature
  - Add: `@Incoming("order-queue")` for SmallRye Reactive Messaging
  - Change: Method parameter from `Message` to `String` (automatic deserialization)
  - Remove: JMS exception handling - SmallRye handles it
- Why: JMS MDB → SmallRye Reactive Messaging consumer
- Depends on: Steps 22, 27
- Verify: `grep '@Incoming' src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 31: COMPLEX - Convert InventoryNotificationMDB to Reactive Messaging and remove JNDI
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Manual JNDI lookup with WebLogic TopicConnectionFactory
  - AFTER:
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

        @Incoming("order-queue")
        public void onMessage(String orderStr) {
            System.out.println("received message inventory");
            Order order = Transformers.jsonToOrder(orderStr);
            order.getItemList().forEach(orderItem -> {
                int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId())
                    .getInventory().getQuantity();
                int new_quantity = old_quantity - orderItem.getQuantity();
                if (new_quantity < LOW_THRESHOLD) {
                    System.out.println("Inventory for item " + orderItem.getProductId() 
                        + " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
                } else {
                    orderItem.setQuantity(new_quantity);
                }
            });
        }
    }
    ```
  - Remove: All JNDI code (`InitialContext`, `Context`, `NamingException`, `Hashtable`)
  - Remove: All JMS manual connection code (`TopicConnectionFactory`, `TopicConnection`, `TopicSession`, `TopicSubscriber`)
  - Remove: `init()`, `close()`, `getInitialContext()` methods
  - Remove: WebLogic-specific imports (`weblogic.jndi.*`, `javax.rmi.PortableRemoteObject`)
  - Add: `@ApplicationScoped` and `@Incoming("order-queue")` annotations
  - Change: `onMessage(Message)` → `onMessage(String)`
- Why: Replace WebLogic JNDI/JMS with Quarkus Reactive Messaging
- Depends on: Step 22
- Verify: No JNDI or JMS client imports remain

### Step 32: Migrate JAX-RS imports in RestApplication.java
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do: Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
- Why: Jakarta EE namespace change
- Depends on: Step 5
- Verify: No `javax.ws.rs` imports remain

### Step 33: Migrate JAX-RS imports in CartEndpoint.java
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - Replace `javax.inject.*` → `jakarta.inject.*`
- Why: Jakarta EE namespace change
- Depends on: Step 5
- Verify: No `javax.` imports remain

### Step 34: Migrate JAX-RS imports in OrderEndpoint.java
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - Replace `javax.inject.*` → `jakarta.inject.*`
- Why: Jakarta EE namespace change
- Depends on: Step 5
- Verify: No `javax.` imports remain

### Step 35: Migrate JAX-RS imports in ProductEndpoint.java
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - Replace `javax.inject.*` → `jakarta.inject.*`
- Why: Jakarta EE namespace change
- Depends on: Step 5
- Verify: No `javax.` imports remain

### Step 36: Migrate imports in Transformers.java
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do:
  - Replace `javax.json.*` → `jakarta.json.*` (if present)
  - Update any other `javax.*` EE imports to `jakarta.*`
- Why: Jakarta EE namespace change
- Depends on: Step 5
- Verify: No `javax.` EE imports remain

### Step 37: Delete WebLogic stub classes
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Remove file - no longer needed after Step 13
- Why: WebLogic-specific stub not needed in Quarkus
- Depends on: Step 13
- Verify: File no longer exists

### Step 38: Delete WebLogic stub classes
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Remove file - no longer needed after Step 13
- Why: WebLogic-specific stub not needed in Quarkus
- Depends on: Step 13
- Verify: File no longer exists

### Step 39: Delete WebLogic NonCatalogLogger stub
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Remove file if present
- Why: WebLogic-specific logging not needed
- Depends on: Step 13
- Verify: File no longer exists (or no weblogic/* directories remain)

### Step 40: Build verification - Final checkpoint
- File: pom.xml
- Action: MODIFY
- What to do: Run `mvn clean compile`
- Why: Verify all migrations compile successfully
- Depends on: Steps 1-39
- Verify: Build succeeds with no compilation errors

### Step 41: Update TODO comment in pom.xml
- File: pom.xml
- Action: MODIFY
- What to do: Remove or update `<!-- TODO: Add OpenShift profile here -->` comment
- Why: Clean up migration artifacts
- Depends on: Step 40
- Verify: TODO comment addressed

## Verification

After all steps complete, run these commands to verify the migration:

```bash
# 1. Clean build
mvn clean compile

# 2. Run tests
mvn test

# 3. Package the application
mvn package

# 4. Verify JAR was created (not WAR)
ls -la target/*.jar

# 5. Run in dev mode
mvn quarkus:dev

# 6. Verify no javax.* EE imports remain in source
grep -r "import javax\\.ejb" src/main/java/ && echo "ERROR: EJB imports found" || echo "OK"
grep -r "import javax\\.jms" src/main/java/ && echo "ERROR: JMS imports found" || echo "OK"
grep -r "import javax\\.ws\\.rs" src/main/java/ && echo "ERROR: javax JAX-RS imports found" || echo "OK"
grep -r "import javax\\.persistence" src/main/java/ && echo "ERROR: javax JPA imports found" || echo "OK"

# 7. Verify Quarkus is running
curl http://localhost:8080/q/health

# 8. Check for WebLogic artifacts
find src -path "*/weblogic/*" && echo "ERROR: WebLogic packages remain" || echo "OK"
```

## Notes

### Complex Migration Areas
1. **InventoryNotificationMDB**: Required complete rewrite from WebLogic JNDI-based JMS to Quarkus Reactive Messaging. Original used manual TopicConnectionFactory lookup and manual subscription setup.

2. **OrderServiceMDB**: Converted from JMS Topic MDB to SmallRye `@Incoming` consumer. Message handling simplified - SmallRye handles message deserialization automatically.

3. **Messaging Configuration**: Both MDBs subscribe to the same `order-queue` channel. In production, configure AMQP broker details in application.properties. For development, may need embedded broker or mock.

4. **Remote EJB**: ShippingService was `@Remote` - removed interface since Quarkus uses local CDI injection only. If truly remote access needed, expose as REST endpoint instead.

5. **System-scoped dependency**: The audit-logging-library (system-scoped JAR) remains unchanged. Verify it's compatible with Jakarta namespace. If not, consider updating to version 2.0.0 or finding alternative.

6. **WebLogic Lifecycle**: Converted to Quarkus `@Observes StartupEvent/ShutdownEvent` pattern which is more flexible and CDI-native.

### Migration Order Rationale
- Build config first (Steps 1-5) - establishes Quarkus foundation
- App config second (Steps 6-9) - removes XML configs
- Models third (Steps 14-21) - no dependencies, just import changes
- Services fourth (Steps 22-31) - depends on models and configs
- REST endpoints fifth (Steps 32-35) - depends on services
- Cleanup last (Steps 37-39) - safe to delete after all code migrated

### Dependencies on Graph Analysis
- Community 13: Model entities (CatalogItemEntity, InventoryEntity, etc.)
- Service layer: CatalogService, OrderService, ProductService, PromoService, ShippingService
- MDB layer: OrderServiceMDB, InventoryNotificationMDB (high complexity)
- REST layer: CartEndpoint, OrderEndpoint, ProductEndpoint
- Utils layer: StartupListener (WebLogic → Quarkus events)

### Potential Issues
1. **AMQP Broker**: The reactive-messaging-amqp extension expects an AMQP broker (e.g., ActiveMQ Artemis, RabbitMQ). Configure broker connection in application.properties or switch to in-memory connector for dev/test.

2. **Audit Library**: System-scoped dependency may not work well in Quarkus. Consider migrating to local repo or Maven Central if available.

3. **Flyway**: Migration scripts in `src/main/resources/db/migration` should work as-is, but verify path matches Quarkus expectations.

4. **H2 Database**: Using in-memory H2. For production, configure PostgreSQL or other production database in application.properties.

5. **WebApp Directory**: The `src/main/webapp` directory with static content (index.jsp, health.jsp, Angular app) is NOT addressed in this plan. Quarkus can serve static resources from `src/main/resources/META-INF/resources/` - migrate JSP to HTML or use Qute templates if needed.
