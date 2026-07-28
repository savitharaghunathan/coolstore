# Implementation Plan

## Goal
Migrate Java EE 7 application from JBoss/WildFly to Quarkus 3 cloud-native runtime.
- Domain skill: javaee-to-quarkus

## Project Summary
- Type: Maven / Java EE 7 WAR
- Files affected: 35 (30 Java source files + 1 pom.xml + 4 config files)
- Estimated complexity: High
- Hardest steps:
  1. Step 31: Convert OrderServiceMDB message-driven bean to reactive messaging
  2. Step 32: Convert InventoryNotificationMDB with WebLogic JNDI to reactive messaging
  3. Step 24: Migrate stateful ShoppingCartService to CDI with session scope

---

## Steps

### Step 1: Update pom.xml - Change packaging
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus produces standalone JAR files, not WARs for deployment to application servers
- Depends on: none
- Verify: `grep -q "<packaging>jar</packaging>" pom.xml`

### Step 2: Update pom.xml - Set Java version
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Update `<maven.compiler.source>1.8</maven.compiler.source>` and `<maven.compiler.target>1.8</maven.compiler.target>` to `17`
- Why: Quarkus 3 requires Java 17 minimum
- Depends on: Step 1
- Verify: `grep -q "<maven.compiler.source>17</maven.compiler.source>" pom.xml`

### Step 3: Update pom.xml - Add Quarkus BOM
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Add Quarkus BOM to `<dependencyManagement>` section before existing dependencies
  ```xml
  <dependency>
    <groupId>io.quarkus.platform</groupId>
    <artifactId>quarkus-bom</artifactId>
    <version>3.2.9.Final</version>
    <type>pom</type>
    <scope>import</scope>
  </dependency>
  ```
- Why: Quarkus BOM manages all Quarkus extension versions
- Depends on: Step 2
- Verify: `grep -q "quarkus-bom" pom.xml`

### Step 4: Update pom.xml - Add quarkus-maven-plugin
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Add quarkus-maven-plugin to `<build><plugins>` section
  ```xml
  <plugin>
    <groupId>io.quarkus.platform</groupId>
    <artifactId>quarkus-maven-plugin</artifactId>
    <version>3.2.9.Final</version>
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
- Why: Required to build Quarkus applications
- Depends on: Step 3
- Verify: `grep -q "quarkus-maven-plugin" pom.xml`

### Step 5: Update pom.xml - Remove javaee-api dependency
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Remove the dependency with `<artifactId>javaee-api</artifactId>` (or `javaee-web-api`)
- Why: Java EE APIs are replaced by Quarkus extensions
- Depends on: Step 4
- Verify: `! grep -q "javaee-api" pom.xml`

### Step 6: Update pom.xml - Add quarkus-hibernate-orm-panache
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Add dependency in `<dependencies>` section
  ```xml
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-hibernate-orm-panache</artifactId>
  </dependency>
  ```
- Why: Replaces JPA with Quarkus Panache for simplified persistence
- Depends on: Step 5
- Verify: `grep -q "quarkus-hibernate-orm-panache" pom.xml`

### Step 7: Update pom.xml - Add quarkus-jdbc-postgresql
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Add dependency
  ```xml
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-postgresql</artifactId>
  </dependency>
  ```
- Why: Provides PostgreSQL JDBC driver for Quarkus
- Depends on: Step 6
- Verify: `grep -q "quarkus-jdbc-postgresql" pom.xml`

### Step 8: Update pom.xml - Add quarkus-smallrye-reactive-messaging-kafka
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Add dependency
  ```xml
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-reactive-messaging-kafka</artifactId>
  </dependency>
  ```
- Why: Replaces JMS with Kafka reactive messaging
- Depends on: Step 7
- Verify: `grep -q "quarkus-smallrye-reactive-messaging-kafka" pom.xml`

### Step 9: Update pom.xml - Add quarkus-resteasy-reactive-jackson
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Add dependency
  ```xml
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
  </dependency>
  ```
- Why: Replaces JAX-RS with RESTEasy Reactive
- Depends on: Step 8
- Verify: `grep -q "quarkus-resteasy-reactive-jackson" pom.xml`

### Step 10: Update pom.xml - Add quarkus-flyway
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Replace `<artifactId>flyway-core</artifactId>` dependency with
  ```xml
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-flyway</artifactId>
  </dependency>
  ```
- Why: Quarkus extension provides better integration with Flyway
- Depends on: Step 9
- Verify: `grep -q "quarkus-flyway" pom.xml && ! grep -q "flyway-core" pom.xml`

### Step 11: Update pom.xml - Remove JBoss/WildFly dependencies
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Remove dependencies with groupId `org.jboss.spec` (jboss-jms-api_2.0_spec, jboss-rmi-api_1.0_spec)
- Why: JBoss-specific APIs not needed in Quarkus
- Depends on: Step 10
- Verify: `! grep -q "org.jboss.spec" pom.xml`

### Step 12: Update pom.xml - Add quarkus-arc (CDI)
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Add dependency
  ```xml
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-arc</artifactId>
  </dependency>
  ```
- Why: Provides CDI support to replace EJB
- Depends on: Step 11
- Verify: `grep -q "quarkus-arc" pom.xml`

### Step 13: Create application.properties
- Phase: App Config
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create file with Quarkus configuration
  ```properties
  # Datasource configuration
  quarkus.datasource.db-kind=postgresql
  quarkus.datasource.username=coolstore
  quarkus.datasource.password=coolstore
  quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore
  
  # Hibernate configuration
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=false
  
  # Flyway configuration
  quarkus.flyway.migrate-at-start=true
  quarkus.flyway.locations=classpath:db/migration
  
  # Kafka messaging configuration
  mp.messaging.incoming.orders.connector=smallrye-kafka
  mp.messaging.incoming.orders.topic=orders
  mp.messaging.incoming.orders.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
  
  mp.messaging.incoming.inventory.connector=smallrye-kafka
  mp.messaging.incoming.inventory.topic=inventory-notifications
  mp.messaging.incoming.inventory.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
  
  # Development Kafka broker
  kafka.bootstrap.servers=localhost:9092
  
  # HTTP configuration
  quarkus.http.port=8080
  
  # Logging
  quarkus.log.level=INFO
  quarkus.log.console.enable=true
  ```
- Why: Quarkus uses application.properties instead of XML configuration files
- Depends on: Step 12
- Verify: `test -f src/main/resources/application.properties`

### Step 14: Migrate CatalogService
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` annotation with `@ApplicationScoped`
  - Replace `import javax.persistence.EntityManager;` with `import jakarta.persistence.EntityManager;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `import javax.persistence.Query;` with `import jakarta.persistence.Query;`
- Why: EJB @Stateless beans become CDI @ApplicationScoped beans in Quarkus
- Depends on: Step 13
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 15: Migrate OrderService
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` annotation with `@ApplicationScoped`
  - Replace `import javax.persistence.EntityManager;` with `import jakarta.persistence.EntityManager;`
  - Replace `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace `import javax.transaction.Transactional;` with `import jakarta.transaction.Transactional;`
- Why: EJB @Stateless beans become CDI @ApplicationScoped beans in Quarkus
- Depends on: Step 14
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 16: Migrate ProductService
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` annotation with `@ApplicationScoped`
  - Replace all `javax.inject.*` imports with `jakarta.inject.*`
- Why: EJB @Stateless beans become CDI @ApplicationScoped beans in Quarkus
- Depends on: Step 15
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 17: Migrate PromoService
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - Replace all `javax.inject.*` imports with `jakarta.inject.*`
  - Add `import jakarta.enterprise.context.ApplicationScoped;`
  - Add `@ApplicationScoped` annotation to the class
- Why: Services need explicit CDI scope in Quarkus
- Depends on: Step 16
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/PromoService.java`

### Step 18: Migrate ShoppingCartOrderProcessor
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateless;` with `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` annotation with `@ApplicationScoped`
  - Replace all `javax.inject.*` imports with `jakarta.inject.*`
- Why: EJB @Stateless beans become CDI @ApplicationScoped beans in Quarkus
- Depends on: Step 17
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 19: COMPLEX - Migrate ShippingService and remove @Remote
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Remove `import javax.ejb.Remote;` and `@Remote` annotation
  - Remove `import javax.ejb.Stateless;`
  - Add `import jakarta.enterprise.context.ApplicationScoped;`
  - Replace `@Stateless` annotation with `@ApplicationScoped`
  - Replace all `javax.inject.*` imports with `jakarta.inject.*`
  - Remove `implements ShippingServiceRemote` (keep the interface methods)
- Why: Quarkus doesn't use EJB Remote interfaces; use direct injection instead
- Depends on: Step 18
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/ShippingService.java && ! grep -q "@Remote" src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 20: Delete ShippingServiceRemote interface
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Delete this file - no longer needed
- Why: Quarkus uses direct CDI injection, not remote EJB interfaces
- Depends on: Step 19
- Verify: `! test -f src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java`

### Step 21: COMPLEX - Replace JNDI lookup in ShoppingCartService
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - Remove JNDI lookup code (`lookupShippingServiceRemote()` method)
  - Add direct injection: `@Inject ShippingService shippingService;`
  - Replace all calls to `lookupShippingServiceRemote()` with direct `shippingService` usage
  - Remove `import javax.naming.*` statements
  - Remove `import java.util.Hashtable;`
- Why: Quarkus uses CDI injection, not JNDI lookups
- Depends on: Step 20
- Verify: `grep -q "@Inject ShippingService" src/main/java/com/redhat/coolstore/service/ShoppingCartService.java && ! grep -q "lookupShippingServiceRemote" src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 22: COMPLEX - Update ShoppingCartService imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - Replace `import javax.ejb.Stateful;` with `import jakarta.enterprise.context.SessionScoped;`
  - Replace `@Stateful` annotation with `@SessionScoped` and add `import java.io.Serializable;`
  - Make class implement `Serializable`
  - Replace all `javax.inject.*` imports with `jakarta.inject.*`
- Why: @Stateful EJBs map to @SessionScoped CDI beans for user-specific state
- Depends on: Step 21
- Verify: `grep -q "@SessionScoped" src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 23: Update JPA entities - CatalogItemEntity
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do:
  - Replace `import javax.persistence.*;` with `import jakarta.persistence.*;`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 22
- Verify: `grep -q "jakarta.persistence" src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`

### Step 24: Update JPA entities - InventoryEntity
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do:
  - Replace `import javax.persistence.*;` with `import jakarta.persistence.*;`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 23
- Verify: `grep -q "jakarta.persistence" src/main/java/com/redhat/coolstore/model/InventoryEntity.java`

### Step 25: Update JPA entities - Order
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do:
  - Replace `import javax.persistence.*;` with `import jakarta.persistence.*;`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 24
- Verify: `grep -q "jakarta.persistence" src/main/java/com/redhat/coolstore/model/Order.java`

### Step 26: Update JPA entities - OrderItem
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do:
  - Replace `import javax.persistence.*;` with `import jakarta.persistence.*;`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 25
- Verify: `grep -q "jakarta.persistence" src/main/java/com/redhat/coolstore/model/OrderItem.java`

### Step 27: Update persistence Resources.java
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Replace `import javax.enterprise.inject.Produces;` with `import jakarta.enterprise.inject.Produces;`
  - Replace `import javax.persistence.EntityManager;` with `import jakarta.persistence.EntityManager;`
  - Replace `import javax.persistence.PersistenceContext;` with `import jakarta.persistence.PersistenceContext;`
  - Replace all other `javax.*` imports with `jakarta.*`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 26
- Verify: `grep -q "jakarta.persistence" src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 28: Update JAX-RS - CartEndpoint
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `import javax.ws.rs.*;` with `import jakarta.ws.rs.*;`
  - Replace all `import javax.inject.*;` with `import jakarta.inject.*;`
- Why: Quarkus 3 uses Jakarta EE namespace for JAX-RS
- Depends on: Step 27
- Verify: `grep -q "jakarta.ws.rs" src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`

### Step 29: Update JAX-RS - OrderEndpoint
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `import javax.ws.rs.*;` with `import jakarta.ws.rs.*;`
  - Replace all `import javax.inject.*;` with `import jakarta.inject.*;`
- Why: Quarkus 3 uses Jakarta EE namespace for JAX-RS
- Depends on: Step 28
- Verify: `grep -q "jakarta.ws.rs" src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`

### Step 30: Update JAX-RS - ProductEndpoint and RestApplication
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Replace all `import javax.ws.rs.*;` with `import jakarta.ws.rs.*;`
  - Replace all `import javax.inject.*;` with `import jakarta.inject.*;`
  - Also update src/main/java/com/redhat/coolstore/rest/RestApplication.java similarly
- Why: Quarkus 3 uses Jakarta EE namespace for JAX-RS
- Depends on: Step 29
- Verify: `grep -q "jakarta.ws.rs" src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

### Step 31: COMPLEX - Convert OrderServiceMDB to reactive messaging
- Phase: Messaging
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - Remove `@MessageDriven` annotation and all `@ActivationConfigProperty` imports
  - Remove `implements MessageListener`
  - Add `import jakarta.enterprise.context.ApplicationScoped;`
  - Add `import org.eclipse.microprofile.reactive.messaging.Incoming;`
  - Add `@ApplicationScoped` to class
  - Replace `onMessage(Message rcvMessage)` method with:
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
  - Remove all JMS imports (`javax.jms.*`)
  - Keep existing `@Inject` fields
- Why: Quarkus uses SmallRye Reactive Messaging instead of JMS MDBs
- Depends on: Step 30
- Verify: `grep -q "@Incoming" src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java && ! grep -q "@MessageDriven" src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 32: COMPLEX - Convert InventoryNotificationMDB to reactive messaging
- Phase: Messaging
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - Remove all WebLogic-specific imports (`weblogic.jndi.*`, `weblogic.i18n.logging.*`)
  - Remove manual JMS connection code and JNDI lookups
  - Add `import jakarta.enterprise.context.ApplicationScoped;`
  - Add `import org.eclipse.microprofile.reactive.messaging.Incoming;`
  - Add `@ApplicationScoped` to class
  - Replace manual listener setup with:
    ```java
    @Incoming("inventory")
    public void processInventoryNotification(String notification) {
        // Process inventory notification
        System.out.println("Inventory notification: " + notification);
    }
    ```
  - Remove `MessageListener` implementation
- Why: Remove WebLogic-specific code and replace with Quarkus reactive messaging
- Depends on: Step 31
- Verify: `grep -q "@Incoming" src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java && ! grep -q "weblogic" src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 33: COMPLEX - Replace WebLogic lifecycle in StartupListener
- Phase: Lifecycle
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - Remove `extends ApplicationLifecycleListener`
  - Remove all `weblogic.application.*` imports
  - Add `import io.quarkus.runtime.StartupEvent;`
  - Add `import jakarta.enterprise.event.Observes;`
  - Add `import jakarta.enterprise.context.ApplicationScoped;`
  - Add `@ApplicationScoped` to class
  - Replace `postStart(ApplicationLifecycleEvent evt)` with:
    ```java
    void onStart(@Observes StartupEvent event) {
        // Startup logic here
        System.out.println("Application started");
    }
    ```
- Why: Quarkus uses CDI lifecycle events instead of application server listeners
- Depends on: Step 32
- Verify: `grep -q "@Observes StartupEvent" src/main/java/com/redhat/coolstore/utils/StartupListener.java && ! grep -q "ApplicationLifecycleListener" src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Step 34: Update Producers utility
- Phase: Lifecycle
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Replace all `import javax.enterprise.inject.Produces;` with `import jakarta.enterprise.inject.Produces;`
  - Replace all `import javax.enterprise.inject.spi.InjectionPoint;` with `import jakarta.enterprise.inject.spi.InjectionPoint;`
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: Step 33
- Verify: `grep -q "jakarta.enterprise.inject" src/main/java/com/redhat/coolstore/utils/Producers.java`

### Step 35: Update Transformers utility
- Phase: Lifecycle
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do:
  - Replace any `javax.*` imports with `jakarta.*` equivalents if present
  - No changes needed if only using standard Java libraries
- Why: Ensure Jakarta namespace consistency
- Depends on: Step 34
- Verify: `! grep -q "import javax\\." src/main/java/com/redhat/coolstore/utils/Transformers.java || grep -q "jakarta" src/main/java/com/redhat/coolstore/utils/Transformers.java`

### Step 36: Delete persistence.xml
- Phase: Cleanup
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file - configuration moved to application.properties
- Why: Quarkus configures persistence through application.properties
- Depends on: Step 35
- Verify: `! test -f src/main/resources/META-INF/persistence.xml`

### Step 37: Delete web.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file - not needed in Quarkus
- Why: Quarkus doesn't use web.xml deployment descriptors
- Depends on: Step 36
- Verify: `! test -f src/main/webapp/WEB-INF/web.xml`

### Step 38: Delete beans.xml
- Phase: Cleanup
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file - CDI is auto-enabled in Quarkus
- Why: Quarkus Arc enables CDI by default
- Depends on: Step 37
- Verify: `! test -f src/main/webapp/WEB-INF/beans.xml`

### Step 39: Delete WebLogic ApplicationLifecycleEvent stub
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this file - no longer needed
- Why: WebLogic-specific code removed
- Depends on: Step 38
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleEvent.java`

### Step 40: Delete WebLogic ApplicationLifecycleListener stub
- Phase: Cleanup
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this file - no longer needed
- Why: WebLogic-specific code removed
- Depends on: Step 39
- Verify: `! test -f src/main/java/weblogic/application/ApplicationLifecycleListener.java`

### Step 41: Delete WebLogic NonCatalogLogger stub
- Phase: Cleanup
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Delete this file - no longer needed
- Why: WebLogic-specific code removed
- Depends on: Step 40
- Verify: `! test -f src/main/java/weblogic/i18n/logging/NonCatalogLogger.java`

### Step 42: Final verification - Check for remaining javax imports
- Phase: Cleanup
- File: src/main/java
- Action: MODIFY
- What to do: Search for any remaining `import javax.` statements in Java EE packages (ejb, persistence, inject, ws.rs, jms, enterprise) and replace with `jakarta.*` equivalents
- Why: Ensure complete migration to Jakarta namespace
- Depends on: Step 41
- Verify: `! grep -r "import javax\\.\\(ejb\\|persistence\\|inject\\|ws\\.rs\\|jms\\|enterprise\\)" src/main/java --include="*.java"`

---

## Verification
After completing all steps, build the project:
```bash
mvn clean compile
```

Expected output: BUILD SUCCESS with no compilation errors.

To run the application in dev mode:
```bash
mvn quarkus:dev
```

---

## Notes

**Critical Gotchas:**
1. **ShoppingCartService scope**: The @Stateful EJB becomes @SessionScoped, which requires HTTP session management. Consider if @ApplicationScoped with manual state management would be better for stateless REST.
2. **JMS to Kafka topic mapping**: The JMS topic "topic/orders" maps to Kafka topic "orders". Ensure message producers also migrate.
3. **Custom audit library**: The system-scoped dependency remains unchanged. Verify it works in JVM mode during testing.
4. **Flyway migrations**: Ensure db/migration folder contains all SQL scripts and they run in correct order.
5. **Transaction management**: `@Transactional` works the same way in Quarkus, but verify transaction boundaries in complex workflows.
6. **JNDI removal**: All JNDI lookups must be replaced with @Inject. Search for `InitialContext`, `lookup`, and `java:jboss` to find any missed occurrences.

**Testing recommendations:**
- Test each phase with `mvn compile` before proceeding
- Test REST endpoints with curl or REST client after Step 30
- Test messaging with a local Kafka broker after Step 32
- Test database operations after Step 27
