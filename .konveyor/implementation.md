# Implementation Plan

## Goal
Migrate Java EE 7 monolithic WAR application from WebLogic/JBoss to Quarkus 3 standalone JAR runtime.
- Domain skill: **javaee-to-quarkus**

## Project Summary
- Type: Maven WAR project (Java EE 7)
- Files affected: 34
- Estimated complexity: **Medium**
- Hardest steps:
  1. Step 32 (COMPLEX) — Convert InventoryNotificationMDB manual listener
  2. Step 33 (COMPLEX) — Convert OrderServiceMDB
  3. Step 18 (COMPLEX) — Replace JNDI lookup in ShoppingCartService

## Steps

### Step 1: Update pom.xml packaging
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Change `<packaging>war</packaging>` to `<packaging>jar</packaging>`
- Why: Quarkus produces standalone JARs, not WARs
- Depends on: none
- Verify: `grep -A1 "<artifactId>monolith</artifactId>" pom.xml | grep jar`

### Step 2: Add Quarkus BOM to pom.xml
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Add dependency management section before `<dependencies>`:
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
- Verify: `grep "quarkus-bom" pom.xml`

### Step 3: Remove Java EE umbrella dependencies from pom.xml
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Remove these dependencies:
  - `javax:javaee-web-api:7.0`
  - `javax:javaee-api:7.0`
  - `org.jboss.spec.javax.jms:jboss-jms-api_2.0_spec`
  - `org.jboss.spec.javax.rmi:jboss-rmi-api_1.0_spec`
- Why: Replaced by individual Quarkus extensions
- Depends on: Step 2
- Verify: `grep -c "javaee-api" pom.xml` returns 0

### Step 4: Add Quarkus core extensions to pom.xml
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Add these dependencies (no version numbers needed):
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-arc</artifactId>
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
    <artifactId>quarkus-jdbc-postgresql</artifactId>
</dependency>
```
- Why: Core Quarkus extensions for CDI, REST, JPA, and databases
- Depends on: Step 3
- Verify: `grep "quarkus-arc" pom.xml`

### Step 5: Add Quarkus Flyway extension to pom.xml
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Replace `org.flywaydb:flyway-core:4.1.2` with:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-flyway</artifactId>
</dependency>
```
- Why: Quarkus manages Flyway version and integration
- Depends on: Step 4
- Verify: `grep "quarkus-flyway" pom.xml`

### Step 6: Add Quarkus messaging extension to pom.xml
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Add dependency:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-reactive-messaging-amqp</artifactId>
</dependency>
```
- Why: Replaces JMS/MDB with SmallRye Reactive Messaging
- Depends on: Step 5
- Verify: `grep "reactive-messaging" pom.xml`

### Step 7: Remove maven-war-plugin from pom.xml
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Remove the entire `<plugin>` block for `maven-war-plugin`
- Why: No longer producing WAR files
- Depends on: Step 6
- Verify: `grep -c "maven-war-plugin" pom.xml` returns 0

### Step 8: Add quarkus-maven-plugin to pom.xml
- Phase: Build Config
- File: pom.xml
- Action: MODIFY
- What to do: Add plugin after maven-compiler-plugin:
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
- Why: Quarkus build plugin required for compilation and dev mode
- Depends on: Step 7
- Verify: `grep "quarkus-maven-plugin" pom.xml`

### Step 9: Create application.properties
- Phase: App Config
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Create file with this content:
```properties
# Datasource configuration
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore
quarkus.datasource.username=${DB_USER:coolstore}
quarkus.datasource.password=${DB_PASS:coolstore}

# Hibernate configuration
quarkus.hibernate-orm.database.generation=none
quarkus.hibernate-orm.log.sql=false

# Flyway configuration
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration

# Dev profile - use H2 in-memory database
%dev.quarkus.datasource.db-kind=h2
%dev.quarkus.datasource.jdbc.url=jdbc:h2:mem:coolstore;DB_CLOSE_DELAY=-1
%dev.quarkus.datasource.username=sa
%dev.quarkus.datasource.password=
%dev.quarkus.hibernate-orm.log.sql=true

# Messaging configuration - orders topic (incoming)
mp.messaging.incoming.orders.connector=smallrye-amqp
mp.messaging.incoming.orders.address=orders
mp.messaging.incoming.orders.broadcast=true

# Messaging configuration - orders topic (outgoing)
mp.messaging.outgoing.orders-out.connector=smallrye-amqp
mp.messaging.outgoing.orders-out.address=orders

# Dev profile - use in-memory connector for messaging
%dev.mp.messaging.incoming.orders.connector=smallrye-in-memory
%dev.mp.messaging.outgoing.orders-out.connector=smallrye-in-memory
```
- Why: Quarkus uses application.properties instead of XML config files
- Depends on: Step 8
- Verify: File exists with datasource and messaging config

### Step 10: Delete persistence.xml
- Phase: App Config
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file — replaced by application.properties
- Why: Quarkus configures JPA via application.properties
- Depends on: Step 9
- Verify: File no longer exists

### Step 11: Delete beans.xml
- Phase: App Config
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file — CDI enabled automatically in Quarkus
- Why: Quarkus enables CDI by default
- Depends on: Step 9
- Verify: File no longer exists

### Step 12: Delete web.xml
- Phase: App Config
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file — not needed for Quarkus JAR packaging
- Why: Quarkus does not use deployment descriptors
- Depends on: Step 9
- Verify: File no longer exists

### Step 13: Migrate ShippingService annotations
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
  - Remove: `import javax.ejb.Remote;` and `import javax.ejb.Stateless;`
  - Remove: `@Stateless` and `@Remote` annotations
  - Add: `import jakarta.enterprise.context.ApplicationScoped;`
  - Add: `@ApplicationScoped` annotation to the class
  - Remove: `implements ShippingServiceRemote` clause
- Why: Quarkus uses CDI beans instead of EJBs
- Depends on: Step 12
- Verify: `grep -c "@Stateless" src/main/java/com/redhat/coolstore/service/ShippingService.java` returns 0

### Step 14: Delete ShippingServiceRemote interface
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Delete this file — EJB remote interfaces not needed
- Why: Direct injection replaces remote EJB lookups
- Depends on: Step 13
- Verify: File no longer exists

### Step 15: Migrate ShoppingCartService annotations
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - Remove: `import javax.ejb.Stateful;`
  - Remove: `import javax.inject.Inject;`
  - Remove: `import javax.naming.Context;`
  - Remove: `import javax.naming.InitialContext;`
  - Remove: `import javax.naming.NamingException;`
  - Remove: `import java.util.Hashtable;`
  - Remove: `@Stateful` annotation
  - Add: `import jakarta.inject.Inject;`
  - Add: `import jakarta.enterprise.context.ApplicationScoped;`
  - Add: `@ApplicationScoped` annotation to the class
- Why: Replace Stateful EJB with CDI bean, prepare for JNDI removal
- Depends on: Step 14
- Verify: `grep -c "@Stateful" src/main/java/com/redhat/coolstore/service/ShoppingCartService.java` returns 0

### Step 16: Migrate CatalogService imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
  - Replace: `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace: `import javax.persistence.EntityManager;` with `import jakarta.persistence.EntityManager;`
- Why: Jakarta namespace for EE 9+ / Quarkus 3
- Depends on: Step 15
- Verify: `grep -c "jakarta.inject" src/main/java/com/redhat/coolstore/service/CatalogService.java` returns 1

### Step 17: Migrate ProductService imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
  - Replace: `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
- Why: Jakarta namespace for EE 9+ / Quarkus 3
- Depends on: Step 16
- Verify: `grep -c "jakarta.inject" src/main/java/com/redhat/coolstore/service/ProductService.java` returns 1

### Step 18: COMPLEX — Replace JNDI lookup in ShoppingCartService
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
  - BEFORE:
```java
private static ShippingServiceRemote lookupShippingServiceRemote() {
    try {
        final Hashtable<String, String> jndiProperties = new Hashtable<>();
        jndiProperties.put(Context.INITIAL_CONTEXT_FACTORY, "org.wildfly.naming.client.WildFlyInitialContextFactory");
        final Context context = new InitialContext(jndiProperties);
        return (ShippingServiceRemote) context.lookup("ejb:/ROOT/ShippingService!" + ShippingServiceRemote.class.getName());
    } catch (NamingException e) {
        throw new RuntimeException(e);
    }
}
```
  - AFTER:
```java
@Inject
ShippingService shippingService;
```
  - Specific changes:
    1. Remove: entire `lookupShippingServiceRemote()` method
    2. Add: `@Inject ShippingService shippingService;` field at class level
    3. Replace: all calls to `lookupShippingServiceRemote()` with `shippingService`
- Why: Direct injection replaces JNDI lookups in Quarkus
- Depends on: Step 17
- Verify: `grep -c "InitialContext\|JNDI" src/main/java/com/redhat/coolstore/service/ShoppingCartService.java` returns 0

### Step 19: Migrate PromoService imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
  - Replace: `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
- Why: Jakarta namespace for EE 9+ / Quarkus 3
- Depends on: Step 18
- Verify: `grep -c "jakarta.inject" src/main/java/com/redhat/coolstore/service/PromoService.java` returns 1

### Step 20: Migrate OrderService imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
  - Replace: `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace: `import javax.persistence.EntityManager;` with `import jakarta.persistence.EntityManager;`
- Why: Jakarta namespace for EE 9+ / Quarkus 3
- Depends on: Step 19
- Verify: `grep -c "jakarta.persistence" src/main/java/com/redhat/coolstore/service/OrderService.java` returns 1

### Step 21: Migrate Resources imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  - Replace: `import javax.enterprise.inject.Produces;` with `import jakarta.enterprise.inject.Produces;`
  - Replace: `import javax.persistence.EntityManager;` with `import jakarta.persistence.EntityManager;`
  - Replace: `import javax.persistence.PersistenceContext;` with `import jakarta.persistence.PersistenceContext;`
- Why: Jakarta namespace for EE 9+ / Quarkus 3
- Depends on: Step 20
- Verify: `grep -c "jakarta.persistence" src/main/java/com/redhat/coolstore/persistence/Resources.java` returns 2

### Step 22: Migrate CartEndpoint imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
  - Replace: `import javax.enterprise.context.SessionScoped;` with `import jakarta.enterprise.context.SessionScoped;`
  - Replace: `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace: `import javax.ws.rs.*;` with `import jakarta.ws.rs.*;`
- Why: Jakarta namespace for EE 9+ / Quarkus 3
- Depends on: Step 21
- Verify: `grep -c "jakarta.ws.rs" src/main/java/com/redhat/coolstore/rest/CartEndpoint.java` returns 1

### Step 23: Migrate OrderEndpoint imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
  - Replace: `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace: `import javax.ws.rs.*;` with `import jakarta.ws.rs.*;`
- Why: Jakarta namespace for EE 9+ / Quarkus 3
- Depends on: Step 22
- Verify: `grep -c "jakarta.ws.rs" src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java` returns 1

### Step 24: Migrate ProductEndpoint imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
  - Replace: `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Replace: `import javax.ws.rs.*;` with `import jakarta.ws.rs.*;`
- Why: Jakarta namespace for EE 9+ / Quarkus 3
- Depends on: Step 23
- Verify: `grep -c "jakarta.ws.rs" src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java` returns 1

### Step 25: Migrate RestApplication imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
  - Replace: `import javax.ws.rs.ApplicationPath;` with `import jakarta.ws.rs.ApplicationPath;`
  - Replace: `import javax.ws.rs.core.Application;` with `import jakarta.ws.rs.core.Application;`
- Why: Jakarta namespace for EE 9+ / Quarkus 3
- Depends on: Step 24
- Verify: `grep -c "jakarta.ws.rs" src/main/java/com/redhat/coolstore/rest/RestApplication.java` returns 2

### Step 26: Migrate all model entity imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace all `import javax.persistence.*;` with `import jakarta.persistence.*;`
- Why: Jakarta namespace for EE 9+ / Quarkus 3
- Depends on: Step 25
- Verify: `grep -c "jakarta.persistence" src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java` returns 1

### Step 27: Migrate InventoryEntity imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace all `import javax.persistence.*;` with `import jakarta.persistence.*;`
- Why: Jakarta namespace for EE 9+ / Quarkus 3
- Depends on: Step 26
- Verify: `grep -c "jakarta.persistence" src/main/java/com/redhat/coolstore/model/InventoryEntity.java` returns 1

### Step 28: Migrate Order imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Replace all `import javax.persistence.*;` with `import jakarta.persistence.*;`
- Why: Jakarta namespace for EE 9+ / Quarkus 3
- Depends on: Step 27
- Verify: `grep -c "jakarta.persistence" src/main/java/com/redhat/coolstore/model/Order.java` returns 1

### Step 29: Migrate OrderItem imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace all `import javax.persistence.*;` with `import jakarta.persistence.*;`
- Why: Jakarta namespace for EE 9+ / Quarkus 3
- Depends on: Step 28
- Verify: `grep -c "jakarta.persistence" src/main/java/com/redhat/coolstore/model/OrderItem.java` returns 1

### Step 30: Migrate Producers imports
- Phase: EJB to CDI
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
  - Replace: `import javax.enterprise.inject.Produces;` with `import jakarta.enterprise.inject.Produces;`
  - Replace: `import javax.enterprise.inject.spi.InjectionPoint;` with `import jakarta.enterprise.inject.spi.InjectionPoint;`
- Why: Jakarta namespace for EE 9+ / Quarkus 3
- Depends on: Step 29
- Verify: `grep -c "jakarta.enterprise" src/main/java/com/redhat/coolstore/utils/Producers.java` returns 2

### Step 31: Migrate ShoppingCartOrderProcessor to use Emitter
- Phase: Messaging
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
  - Remove: all JMS imports (`javax.jms.*`, `javax.annotation.Resource`)
  - Add: `import org.eclipse.microprofile.reactive.messaging.Channel;`
  - Add: `import org.eclipse.microprofile.reactive.messaging.Emitter;`
  - Add: `import jakarta.inject.Inject;`
  - Replace:
```java
@Resource(mappedName = "topic/orders")
private Topic ordersTopic;
@Inject private JMSContext context;

public void process(ShoppingCart cart) {
    context.createProducer().send(ordersTopic, new Transformers().shoppingCartToJson(cart));
}
```
  - With:
```java
@Inject @Channel("orders-out") Emitter<String> emitter;

public void process(ShoppingCart cart) {
    emitter.send(new Transformers().shoppingCartToJson(cart));
}
```
- Why: SmallRye Reactive Messaging replaces JMS producers
- Depends on: Step 30
- Verify: `grep -c "Emitter" src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java` returns 1

### Step 32: COMPLEX — Convert InventoryNotificationMDB
- Phase: Messaging
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Manual MessageListener with JNDI setup
  - AFTER: @Incoming method
  - Specific changes:
    1. Remove: all imports except `com.redhat.coolstore.*`
    2. Add: `import jakarta.enterprise.context.ApplicationScoped;`
    3. Add: `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    4. Add: `import jakarta.inject.Inject;`
    5. Remove: `implements MessageListener`
    6. Add: `@ApplicationScoped` annotation to class
    7. Replace entire class implementation with:
```java
@ApplicationScoped
public class InventoryNotificationMDB {

    private static final int LOW_THRESHOLD = 50;

    @Inject
    private CatalogService catalogService;

    @Incoming("orders")
    public void onMessage(String orderStr) {
        try {
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
        } catch (Exception e) {
            System.err.println("An exception occurred: " + e.getMessage());
        }
    }
}
```
- Why: SmallRye Reactive Messaging replaces manual JMS message listeners
- Depends on: Step 31
- Verify: `grep -c "@Incoming" src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java` returns 1

### Step 33: COMPLEX — Convert OrderServiceMDB
- Phase: Messaging
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: @MessageDriven with @ActivationConfigProperty
  - AFTER: @Incoming method
  - Specific changes:
    1. Remove: `import javax.ejb.*;`
    2. Remove: `import javax.jms.*;`
    3. Remove: `import javax.inject.Inject;`
    4. Add: `import jakarta.inject.Inject;`
    5. Add: `import jakarta.enterprise.context.ApplicationScoped;`
    6. Add: `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    7. Remove: `@MessageDriven(...)` annotation
    8. Add: `@ApplicationScoped` annotation
    9. Remove: `implements MessageListener`
    10. Replace method signature and implementation:
```java
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
```
- Why: SmallRye Reactive Messaging replaces MDB
- Depends on: Step 32
- Verify: `grep -c "@Incoming" src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java` returns 1

### Step 34: COMPLEX — Convert StartupListener lifecycle
- Phase: Lifecycle
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - BEFORE: WebLogic ApplicationLifecycleListener
  - AFTER: Quarkus lifecycle events
  - Specific changes:
    1. Remove: `import weblogic.application.*;`
    2. Remove: `import javax.inject.Inject;`
    3. Add: `import jakarta.inject.Inject;`
    4. Add: `import jakarta.enterprise.context.ApplicationScoped;`
    5. Add: `import jakarta.enterprise.event.Observes;`
    6. Add: `import io.quarkus.runtime.StartupEvent;`
    7. Add: `import io.quarkus.runtime.ShutdownEvent;`
    8. Remove: `extends ApplicationLifecycleListener`
    9. Add: `@ApplicationScoped` annotation
    10. Replace methods:
```java
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
- Why: Quarkus lifecycle events replace server-specific listeners
- Depends on: Step 33
- Verify: `grep -c "@Observes StartupEvent" src/main/java/com/redhat/coolstore/utils/StartupListener.java` returns 1

### Step 35: Update DataBaseMigrationStartup
- Phase: Lifecycle
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: MODIFY
- What to do:
  - Remove: entire Flyway manual migration logic from `@PostConstruct` method
  - Replace: `import javax.annotation.PostConstruct;` with `import jakarta.annotation.PostConstruct;`
  - Replace: `import javax.inject.Inject;` with `import jakarta.inject.Inject;`
  - Simplify class to just log startup (Flyway auto-migrates via application.properties)
- Why: Quarkus Flyway extension handles migration automatically
- Depends on: Step 34
- Verify: `grep -c "jakarta.annotation" src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java` returns 1

### Step 36: Delete weblogic stub directory
- Phase: Cleanup
- File: src/main/java/weblogic/
- Action: DELETE
- What to do: Delete entire directory and all contents (ApplicationLifecycleEvent.java, ApplicationLifecycleListener.java)
- Why: WebLogic-specific stubs no longer needed
- Depends on: Step 35
- Verify: `test ! -d src/main/java/weblogic`

### Step 37: Verify no javax.* EE imports remain
- Phase: Cleanup
- File: (verification step - all Java files)
- Action: MODIFY (if needed)
- What to do: Run `grep -rn "import javax\.(ejb\|jms\|inject\|enterprise\|persistence\|ws\.rs\|transaction\|annotation)\." --include="*.java" src/` and fix any remaining imports
- Why: All Java EE javax.* imports should be replaced with jakarta.* or removed
- Depends on: Step 36
- Verify: Command returns zero results (javax.sql, javax.crypto, javax.naming OK to keep)

### Step 38: Verify no JNDI lookups remain
- Phase: Cleanup
- File: (verification step - all Java files)
- Action: MODIFY (if needed)
- What to do: Run `grep -rn "InitialContext\|Context.lookup\|INITIAL_CONTEXT_FACTORY" --include="*.java" src/` and remove any remaining JNDI code
- Why: Direct injection replaces all JNDI lookups
- Depends on: Step 37
- Verify: Command returns zero results

### Step 39: Verify no EJB annotations remain
- Phase: Cleanup
- File: (verification step - all Java files)
- Action: MODIFY (if needed)
- What to do: Run `grep -rn "@Stateless\|@Stateful\|@MessageDriven\|@EJB\|@Local\b\|@Remote\b" --include="*.java" src/` and remove any remaining EJB annotations
- Why: All EJBs should be converted to CDI beans
- Depends on: Step 38
- Verify: Command returns zero results

### Step 40: Final build verification
- Phase: Cleanup
- File: (verification step - entire project)
- Action: MODIFY (if needed)
- What to do: Run `mvn clean compile` and fix any compilation errors
- Why: Ensure the migration is complete and compiles successfully
- Depends on: Step 39
- Verify: Build completes with "BUILD SUCCESS"

## Verification
Build command: `mvn compile`

After all steps complete, also run:
- `mvn quarkus:dev` — verify application starts in dev mode
- `mvn test` — run tests (failures expected but documented)

## Notes

### Gotchas
1. **InventoryNotificationMDB** has manual JNDI-based JMS setup — different pattern than standard @MessageDriven
2. **ShoppingCartService** has hardcoded WildFly JNDI context factory — must be removed completely
3. **System-scoped dependency** `audit-logging-library` in pom.xml — may need to be installed to local Maven repo or moved to a proper repository
4. **SessionScoped REST endpoint** (CartEndpoint) — works in Quarkus but consider changing to ApplicationScoped with proper session management

### MDB channel configuration
Both MDB classes subscribe to the same topic "orders" with broadcast=true, so both will receive each message. This is intentional based on the original design.

### Test expectations
Tests are likely to fail initially because:
- Database schema may need updates for Quarkus
- Messaging requires AMQP broker in test environment
- Session management may behave differently

These are expected and should be documented, not fixed during migration.