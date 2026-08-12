# Migration Plan: CoolStore Monolith (Java EE 7 → Quarkus 3)

## Goal
Migrate the CoolStore Monolith application from Java EE 7 on JBoss EAP 7.4 to Quarkus 3, transforming from a WAR-based application server deployment to a standalone Quarkus JAR application.
- Reference used: javaee-to-quarkus skill (modules: build-config, app-config, ejb-to-cdi, messaging, lifecycle, cleanup)

## Project Summary
- Type: Maven (Java EE 7 WAR)
- Files affected: ~25 Java files, pom.xml, configuration files
- Estimated complexity: High
- Hardest steps: 
  1. Converting Message-Driven Beans (MDBs) to SmallRye Reactive Messaging
  2. Removing WebLogic-specific JNDI lookups and lifecycle listeners
  3. Migrating clustering/HA configuration to Quarkus clustering

## Architecture Overview
Based on code analysis, this application consists of:
- **Models**: 8 JPA entities (Order, Product, ShoppingCart, etc.)
- **Services**: 7 service classes (OrderService, CatalogService, PromoService, etc.)
- **REST Endpoints**: 3 JAX-RS resources (CartEndpoint, OrderEndpoint, ProductEndpoint)
- **Messaging**: 2 MDBs (OrderServiceMDB, InventoryNotificationMDB - one with WebLogic JNDI)
- **Persistence**: JPA with Flyway migrations
- **Security**: Keycloak integration
- **Infrastructure**: CDI, JAX-RS, JMS/ActiveMQ, PostgreSQL

---

## Steps

### Phase 1: Build Configuration (Steps 1-3)

#### Step 1: Update pom.xml - Add Quarkus BOM and change packaging
- File: pom.xml
- Action: MODIFY
- What to do:
  1. Change `<packaging>war</packaging>` → `<packaging>jar</packaging>`
  2. Add Quarkus BOM to `<dependencyManagement>`:
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
  3. Update Java version from 1.8 to 17 in maven-compiler-plugin
- Why: Quarkus 3 requires Java 17+ and uses JAR packaging instead of WAR
- Depends on: none
- Verify: `mvn validate` succeeds

#### Step 2: Replace Java EE dependencies with Quarkus extensions
- File: pom.xml
- Action: MODIFY
- What to do:
  1. REMOVE these dependencies:
     - `javax:javaee-web-api`
     - `javax:javaee-api`
     - `org.jboss.spec.javax.jms:jboss-jms-api_2.0_spec`
     - `org.jboss.spec.javax.rmi:jboss-rmi-api_1.0_spec`
     - `org.hibernate:hibernate-entitymanager` (test scope)
     - `org.glassfish:javax.json` (test scope)
  2. ADD Quarkus extensions (no version needed - managed by BOM):
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
         <artifactId>quarkus-jdbc-postgresql</artifactId>
     </dependency>
     <dependency>
         <groupId>io.quarkus</groupId>
         <artifactId>quarkus-flyway</artifactId>
     </dependency>
     <dependency>
         <groupId>io.quarkus</groupId>
         <artifactId>quarkus-narayana-jta</artifactId>
     </dependency>
     <dependency>
         <groupId>io.quarkus</groupId>
         <artifactId>quarkus-smallrye-reactive-messaging-amqp</artifactId>
     </dependency>
     <dependency>
         <groupId>io.quarkus</groupId>
         <artifactId>quarkus-oidc</artifactId>
     </dependency>
     <dependency>
         <groupId>io.quarkus</groupId>
         <artifactId>quarkus-jdbc-h2</artifactId>
         <scope>test</scope>
     </dependency>
     ```
  3. UPDATE Flyway dependency: change `org.flywaydb:flyway-core` version to 9.22.3 (compatible with Quarkus)
  4. UPDATE test dependencies: change Mockito to version 5.5.0, H2 to 2.2.224
- Why: Replace monolithic Java EE API with individual Quarkus extensions
- Depends on: Step 1
- Verify: `mvn dependency:tree` shows Quarkus dependencies

#### Step 3: Add Quarkus Maven plugin and remove WAR plugin
- File: pom.xml
- Action: MODIFY
- What to do:
  1. REMOVE `maven-war-plugin`
  2. ADD Quarkus Maven plugin:
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
  3. UPDATE maven-surefire-plugin version to 3.0.0
  4. HANDLE system-scoped dependency: The audit-logging-library JAR needs to be installed to local Maven repo or converted to a regular dependency. Add installation instruction to verification.
- Why: Quarkus requires its own build plugin for code generation and packaging
- Depends on: Step 2
- Verify: `mvn clean compile` succeeds (may have compilation errors due to javax imports - expected at this stage)

---

### Phase 2: Application Configuration (Steps 4-6)

#### Step 4: Create application.properties with database and Flyway config
- File: src/main/resources/application.properties
- Action: CREATE
- What to do:
  ```properties
  # Datasource configuration
  quarkus.datasource.db-kind=postgresql
  quarkus.datasource.username=postgresUser
  quarkus.datasource.password=postgresPW
  quarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5432/postgresDB
  
  # Hibernate ORM configuration
  quarkus.hibernate-orm.database.generation=none
  quarkus.hibernate-orm.log.sql=false
  quarkus.hibernate-orm.log.format-sql=true
  
  # Flyway configuration
  quarkus.flyway.migrate-at-start=true
  quarkus.flyway.locations=db/migration
  
  # Logging configuration
  quarkus.log.console.enable=true
  quarkus.log.console.format=%d{HH:mm:ss} %-5p [%c{2.}] (%t) %s%e%n
  quarkus.log.level=INFO
  
  # HTTP configuration
  quarkus.http.port=8080
  
  # Dev services (disable in production)
  quarkus.devservices.enabled=false
  ```
- Why: Quarkus uses application.properties instead of persistence.xml and JNDI datasources
- Depends on: Step 3
- Verify: File exists and is parseable

#### Step 5: Add OIDC/Keycloak configuration
- File: src/main/resources/application.properties
- Action: MODIFY
- What to do: Append Keycloak configuration:
  ```properties
  # Keycloak/OIDC configuration
  quarkus.oidc.enabled=true
  quarkus.oidc.auth-server-url=http://127.0.0.1:8081/realms/eap
  quarkus.oidc.client-id=coolstore
  quarkus.oidc.credentials.secret=secret
  quarkus.oidc.application-type=web-app
  
  # Security configuration
  quarkus.http.auth.permission.authenticated.paths=/*
  quarkus.http.auth.permission.authenticated.policy=authenticated
  quarkus.http.auth.permission.public.paths=/health,/q/*
  quarkus.http.auth.permission.public.policy=permit
  ```
- Why: Replace JAAS with Quarkus OIDC for Keycloak integration
- Depends on: Step 4
- Verify: Configuration syntax is valid

#### Step 6: COMPLEX - Add reactive messaging configuration for JMS/ActiveMQ
- File: src/main/resources/application.properties
- Action: MODIFY
- What to do: Append messaging configuration:
  ```properties
  # AMQP/ActiveMQ Artemis configuration
  amqp-host=127.0.0.1
  amqp-port=5672
  amqp-username=admin
  amqp-password=admin
  
  # Incoming channel for order processing (topic/orders)
  mp.messaging.incoming.order-topic.connector=smallrye-amqp
  mp.messaging.incoming.order-topic.address=orders
  mp.messaging.incoming.order-topic.durable=true
  
  # Note: ActiveMQ needs to be configured with AMQP protocol enabled
  # Or migrate to Apache Artemis (Quarkus-native messaging broker)
  ```
- Why: Replace JMS/MDB activation config with SmallRye Reactive Messaging channels
- Depends on: Step 5
- Verify: All required messaging properties are present

---

### Phase 3: Import Namespace Migration (Steps 7-8)

#### Step 7: Migrate javax.* to jakarta.* imports in all entity classes
- File: src/main/java/com/redhat/coolstore/model/*.java (8 files)
  - CatalogItemEntity.java
  - InventoryEntity.java
  - Order.java
  - OrderItem.java
  - Product.java
  - Promotion.java
  - ShoppingCart.java
  - ShoppingCartItem.java
- Action: MODIFY
- What to do: In each file, replace:
  - `javax.persistence.*` → `jakarta.persistence.*`
  - `javax.validation.*` → `jakarta.validation.*` (if present)
  - `javax.xml.bind.*` → `jakarta.xml.bind.*` (if present)
- Why: Jakarta EE 9+ uses jakarta namespace
- Depends on: Step 6
- Verify: `grep -r "import javax.persistence" src/main/java/com/redhat/coolstore/model/` returns nothing

#### Step 8: Migrate javax.* to jakarta.* imports in REST endpoints
- File: src/main/java/com/redhat/coolstore/rest/*.java (4 files)
  - CartEndpoint.java
  - OrderEndpoint.java
  - ProductEndpoint.java
  - RestApplication.java
- Action: MODIFY
- What to do: In each file, replace:
  - `javax.ws.rs.*` → `jakarta.ws.rs.*`
  - `javax.inject.*` → `jakarta.inject.*`
  - `javax.enterprise.context.*` → `jakarta.enterprise.context.*`
- Why: Jakarta EE namespace for JAX-RS and CDI
- Depends on: Step 7
- Verify: `grep -r "import javax.ws.rs" src/main/java/com/redhat/coolstore/rest/` returns nothing

---

### Phase 4: Persistence and CDI Migration (Steps 9-10)

#### Step 9: Migrate Resources.java EntityManager producer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
  1. Replace imports:
     - `javax.enterprise.context.*` → `jakarta.enterprise.context.*`
     - `javax.enterprise.inject.*` → `jakarta.enterprise.inject.*`
     - `javax.persistence.*` → `jakarta.persistence.*`
  2. REMOVE `@PersistenceContext` annotation
  3. CHANGE from producer pattern to direct injection:
     ```java
     @Dependent
     public class Resources {
         @Inject
         EntityManager em;
     
         @Produces
         public EntityManager getEntityManager() {
             return em;
         }
     }
     ```
- Why: Quarkus manages EntityManager lifecycle directly, no need for @PersistenceContext
- Depends on: Step 8
- Verify: File compiles without errors

#### Step 10: Migrate javax imports in service classes
- File: src/main/java/com/redhat/coolstore/service/*.java (5 files - excluding MDBs)
  - CatalogService.java
  - OrderService.java
  - ProductService.java
  - PromoService.java
  - ShippingService.java
- Action: MODIFY
- What to do: In each file, replace:
  - `javax.inject.*` → `jakarta.inject.*`
  - `javax.persistence.*` → `jakarta.persistence.*`
  - `javax.transaction.*` → `jakarta.transaction.*`
  - `javax.ejb.Stateless` → `jakarta.enterprise.context.ApplicationScoped` (if present)
  - `javax.ejb.TransactionAttribute` → `jakarta.transaction.Transactional` (if present)
- Why: Jakarta namespace and replace EJB annotations with CDI
- Depends on: Step 9
- Verify: Services compile and use jakarta imports

---

### Phase 5: Messaging Migration (Steps 11-12)

#### Step 11: COMPLEX - Convert OrderServiceMDB to SmallRye Reactive Messaging
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  1. REMOVE these imports:
     ```java
     import javax.ejb.ActivationConfigProperty;
     import javax.ejb.MessageDriven;
     import javax.jms.JMSException;
     import javax.jms.Message;
     import javax.jms.MessageListener;
     import javax.jms.TextMessage;
     ```
  2. ADD these imports:
     ```java
     import jakarta.enterprise.context.ApplicationScoped;
     import jakarta.inject.Inject;
     import org.eclipse.microprofile.reactive.messaging.Incoming;
     ```
  3. REPLACE class structure:
     - BEFORE:
       ```java
       @MessageDriven(name = "OrderServiceMDB", activationConfig = {...})
       public class OrderServiceMDB implements MessageListener {
           @Override
           public void onMessage(Message rcvMessage) {
               // JMS message handling
           }
       }
       ```
     - AFTER:
       ```java
       @ApplicationScoped
       public class OrderServiceMDB {
           @Inject
           OrderService orderService;
           
           @Inject
           CatalogService catalogService;
           
           @Incoming("order-topic")
           public void onMessage(String orderStr) {
               System.out.println("\nMessage recd !");
               System.out.println("Received order: " + orderStr);
               Order order = Transformers.jsonToOrder(orderStr);
               System.out.println("Order object is " + order);
               orderService.save(order);
               order.getItemList().forEach(orderItem -> {
                   catalogService.updateInventoryItems(
                       orderItem.getProductId(), 
                       orderItem.getQuantity()
                   );
               });
           }
       }
       ```
- Why: Quarkus uses SmallRye Reactive Messaging instead of JMS MDBs
- Depends on: Step 10
- Verify: Class compiles, @Incoming annotation present, no JMS imports remain

#### Step 12: COMPLEX - Convert InventoryNotificationMDB to SmallRye Reactive Messaging
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  1. REMOVE all WebLogic-specific code:
     - JNDI InitialContext setup
     - PortableRemoteObject usage
     - Manual TopicConnection/Session management
     - init() and close() methods
  2. REPLACE with SmallRye Reactive Messaging:
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
         
         @Incoming("order-topic")
         public void onMessage(String orderStr) {
             System.out.println("received message inventory");
             Order order = Transformers.jsonToOrder(orderStr);
             order.getItemList().forEach(orderItem -> {
                 int old_quantity = catalogService.getCatalogItemById(
                     orderItem.getProductId()
                 ).getInventory().getQuantity();
                 int new_quantity = old_quantity - orderItem.getQuantity();
                 if (new_quantity < LOW_THRESHOLD) {
                     System.out.println("Inventory for item " + 
                         orderItem.getProductId() + 
                         " is below threshold (" + LOW_THRESHOLD + 
                         "), contact supplier!");
                 } else {
                     orderItem.setQuantity(new_quantity);
                 }
             });
         }
     }
     ```
  3. NOTE: Both MDBs subscribe to same topic - this maintains the same behavior
- Why: Remove WebLogic-specific JNDI lookups and replace with Quarkus messaging
- Depends on: Step 11
- Verify: No javax.jms imports, no JNDI code, compiles successfully

---

### Phase 6: Lifecycle and Utilities Migration (Steps 13-14)

#### Step 13: COMPLEX - Replace WebLogic ApplicationLifecycleListener with Quarkus events
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  1. REMOVE import of weblogic.application.*
  2. REPLACE with Quarkus lifecycle events:
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
         
         void onStart(@Observes StartupEvent evt) {
             log.info("AppListener(postStart)");
         }
         
         void onStop(@Observes ShutdownEvent evt) {
             log.info("AppListener(preStop)");
         }
     }
     ```
- Why: Replace WebLogic-specific lifecycle with Quarkus CDI events
- Depends on: Step 12
- Verify: No weblogic imports, uses @Observes pattern

#### Step 14: Migrate remaining utility classes
- File: src/main/java/com/redhat/coolstore/utils/*.java
- Action: MODIFY
- What to do: In Transformers.java and any other utils:
  - Replace `javax.json.*` → `jakarta.json.*` (if present)
  - Replace `javax.inject.*` → `jakarta.inject.*`
  - Ensure Logger injection uses CDI producer or direct java.util.logging
- Why: Complete namespace migration
- Depends on: Step 13
- Verify: All utils compile with jakarta imports

---

### Phase 7: Configuration File Cleanup (Steps 15-17)

#### Step 15: Delete persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Remove the file entirely
- Why: Quarkus configures JPA through application.properties, persistence.xml is not used
- Depends on: Step 4
- Verify: File does not exist, application.properties contains datasource config

#### Step 16: Delete web.xml
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Remove the file entirely
- Why: Quarkus is not a servlet container, web.xml is not needed
- Depends on: Step 15
- Verify: File does not exist

#### Step 17: Move static web resources and handle JSP files
- File: src/main/webapp/* (excluding WEB-INF)
- Action: MODIFY
- What to do:
  1. CREATE directory: src/main/resources/META-INF/resources/
  2. MOVE these to META-INF/resources/:
     - index.jsp → index.html (convert JSP to HTML or use template engine)
     - health.jsp → health.html (or create Quarkus health endpoint)
     - coolstore.json
     - keycloak.json
     - app/
     - bower_components/
     - partials/
     - assets/ (if in webapp)
  3. NOTE: JSP files need conversion:
     - index.jsp contains Angular app - can be served as static HTML
     - health.jsp should be replaced with Quarkus SmallRye Health endpoint
  4. UPDATE keycloak.json paths if needed for new location
- Why: Quarkus serves static resources from META-INF/resources, JSPs not supported
- Depends on: Step 16
- Verify: Static files accessible from /index.html in dev mode

---

### Phase 8: WebLogic Stub Removal (Steps 18-19)

#### Step 18: Delete WebLogic stub classes
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Remove the entire weblogic package directory
- Why: WebLogic-specific code no longer needed
- Depends on: Step 13
- Verify: No src/main/java/weblogic directory exists

#### Step 19: Delete WebLogic stub event class
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Remove if exists (likely in same weblogic package)
- Why: WebLogic-specific code no longer needed
- Depends on: Step 18
- Verify: No weblogic package references remain in codebase

---

### Phase 9: Test Migration (Steps 20-21)

#### Step 20: Update test dependencies and imports
- File: src/test/java/com/redhat/coolstore/**/*.java (all test files)
- Action: MODIFY
- What to do:
  1. Replace all `javax.*` → `jakarta.*` imports
  2. Add Quarkus test annotations to test classes:
     ```java
     import io.quarkus.test.junit.QuarkusTest;
     
     @QuarkusTest
     public class YourTest {
         // existing test code
     }
     ```
  3. Update test EntityManager injection if needed
  4. Replace any JBoss Arquillian annotations with Quarkus test equivalents
- Why: Tests need to run in Quarkus test framework
- Depends on: Step 10
- Verify: Tests compile (may not pass yet, but should compile)

#### Step 21: Update test resources
- File: src/test/resources/META-INF/persistence.xml
- Action: DELETE or MODIFY
- What to do:
  - If exists, DELETE it
  - Test datasource config should go in src/test/resources/application.properties:
    ```properties
    quarkus.datasource.db-kind=h2
    quarkus.datasource.jdbc.url=jdbc:h2:mem:test
    quarkus.hibernate-orm.database.generation=drop-and-create
    ```
- Why: Quarkus tests use application.properties
- Depends on: Step 20
- Verify: Test configuration exists in application.properties

---

### Phase 10: Final Cleanup and Adjustments (Steps 22-25)

#### Step 22: Handle audit-logging-library dependency
- File: pom.xml
- Action: MODIFY
- What to do:
  1. The system-scoped dependency needs to be addressed
  2. OPTIONS:
     a) Install to local Maven repo: 
        `mvn install:install-file -Dfile=lib/audit-logging-library-1.0.0.jar -DgroupId=com.enterprise -DartifactId=audit-logging-library -Dversion=1.0.0 -Dpackaging=jar`
     b) Change to regular dependency and configure local repo
     c) If source available, include as module
  3. REMOVE `<scope>system</scope>` and `<systemPath>` tags
  4. Ensure dependency is available during build
- Why: System-scoped dependencies don't work well in Quarkus builds
- Depends on: Step 3
- Verify: `mvn dependency:tree` shows audit-logging-library without errors

#### Step 23: Update Maven finalName
- File: pom.xml
- Action: MODIFY
- What to do:
  - Change `<finalName>ROOT</finalName>` to `<finalName>coolstore-monolith</finalName>`
  - Or remove entirely to use default: `${project.artifactId}-${project.version}`
- Why: JAR naming convention different from WAR, ROOT.war was for app server context root
- Depends on: Step 1
- Verify: Build produces correctly named JAR

#### Step 24: Add Quarkus dev/prod profiles
- File: pom.xml
- Action: MODIFY
- What to do:
  Replace the commented "TODO: Add OpenShift profile" with:
  ```xml
  <profiles>
      <profile>
          <id>native</id>
          <activation>
              <property>
                  <name>native</name>
              </property>
          </activation>
          <properties>
              <skipITs>false</skipITs>
              <quarkus.package.type>native</quarkus.package.type>
          </properties>
      </profile>
  </profiles>
  ```
- Why: Enable optional native compilation profile for Quarkus
- Depends on: Step 23
- Verify: Profile exists in pom.xml

#### Step 25: Create Quarkus main application class (optional but recommended)
- File: src/main/java/com/redhat/coolstore/Application.java
- Action: CREATE
- What to do:
  ```java
  package com.redhat.coolstore;
  
  import io.quarkus.runtime.Quarkus;
  import io.quarkus.runtime.QuarkusApplication;
  import io.quarkus.runtime.annotations.QuarkusMain;
  
  @QuarkusMain
  public class Application implements QuarkusApplication {
      
      public static void main(String... args) {
          Quarkus.run(Application.class, args);
      }
      
      @Override
      public int run(String... args) throws Exception {
          Quarkus.waitForExit();
          return 0;
      }
  }
  ```
- Why: Provides explicit entry point for Quarkus application (optional but clear)
- Depends on: All previous steps
- Verify: Class exists and compiles

---

## Verification

After completing all steps, verify the migration:

### Build Verification
```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package application
mvn package

# The output should be: target/coolstore-monolith-1.0.0-SNAPSHOT-runner.jar
```

### Runtime Verification
```bash
# Ensure PostgreSQL is running
podman run --name myPostgresDb \
   -p 5432:5432 \
   -e POSTGRES_USER=postgresUser \
   -e POSTGRES_PASSWORD=postgresPW \
   -e POSTGRES_DB=postgresDB \
   -d postgres

# Ensure Keycloak is running on port 8081 with eap realm configured

# Run Quarkus application
java -jar target/quarkus-app/quarkus-run.jar

# Or use Quarkus dev mode
mvn quarkus:dev

# Application should start on http://localhost:8080
```

### Functional Verification
1. Access http://localhost:8080 - should display the CoolStore UI
2. Click "Sign in" and authenticate with Keycloak user
3. Add items to cart and checkout
4. Verify both MDB listeners process the order message (check console output)
5. Verify database contains order data
6. Check health endpoint: http://localhost:8080/q/health
7. Check metrics: http://localhost:8080/q/metrics

### Code Quality Checks
```bash
# No javax.* Java EE imports should remain
grep -r "import javax\\.ejb" src/main/java/
grep -r "import javax\\.jms" src/main/java/
grep -r "import javax\\.persistence" src/main/java/

# All should return empty

# No WebLogic references
grep -r "import weblogic" src/main/java/
# Should return empty

# Verify Jakarta imports present
grep -r "import jakarta" src/main/java/ | wc -l
# Should return > 0
```

---

## Notes

### Clustering/HA Considerations
The original application ran on JBoss EAP with clustering (`standalone-full-ha.xml`, ActiveMQ clustering). For Quarkus:
- **Messaging**: Configure AMQP broker (Artemis) in clustered mode externally
- **Session Replication**: If needed, add `quarkus-infinispan-client` for distributed caching
- **Load Balancing**: Use external load balancer (Nginx, HAProxy) for multiple Quarkus instances
- The `<distributable/>` tag from web.xml is not directly applicable - Quarkus handles clustering differently

### ActiveMQ → Artemis Migration
The current setup uses ActiveMQ with JMS API. Recommended approach:
1. **Option A**: Keep ActiveMQ, enable AMQP protocol (port 5672), configure SmallRye AMQP connector
2. **Option B**: Migrate to Apache Artemis (RedHat's supported messaging broker, Quarkus-native)
3. Both MDBs subscribe to the same topic (`topic/orders`) - this is preserved with the `order-topic` channel

### Keycloak Configuration
- Update `keycloak.json` with correct realm settings
- Verify `quarkus.oidc.*` properties match your Keycloak setup
- Client secret may need to be obtained from Keycloak admin console

### Flyway Migrations
- SQL scripts in `src/main/resources/db/migration/` will run automatically on startup
- Verify V1_1 and V1_2 scripts are compatible with Flyway 9.x syntax
- Set `quarkus.flyway.migrate-at-start=false` in production if controlled externally

### JSP to HTML Conversion
- `index.jsp` appears to be an AngularJS application - can likely be converted to pure HTML
- `health.jsp` should be replaced with Quarkus SmallRye Health extension
- Review bower_components for outdated frontend dependencies

### Performance Notes
- Quarkus startup time will be significantly faster than JBoss EAP (~seconds vs minutes)
- Memory footprint will be smaller
- Native compilation (GraalVM) can reduce both further but requires additional testing

### Breaking Changes
- **No EJB Remote interfaces**: If other applications call this app via EJB remote, they must migrate to REST
- **No JNDI**: All JNDI lookups must be replaced with CDI injection
- **Reactive Messaging**: MDB message handling becomes async by default
- **Configuration**: All JNDI datasources, JMS resources must be in application.properties

### Migration Complexity Factors
1. **WebLogic-specific code**: InventoryNotificationMDB has manual JNDI/JMS setup (HIGH complexity)
2. **Dual MDB subscribers**: Both MDBs on same topic - verify intended behavior is preserved
3. **System-scoped JAR dependency**: Needs manual installation to Maven repo
4. **Clustering requirements**: Needs architectural decision on how to handle HA
5. **JSP conversion**: Frontend modernization may be needed

### Recommended Next Steps After Migration
1. Add SmallRye Health checks for custom health indicators
2. Add SmallRye Metrics for business metrics
3. Consider adding OpenAPI/Swagger documentation
4. Evaluate Hibernate Panache for simplified data access
5. Review frontend dependencies (bower is deprecated - consider npm/yarn)
6. Add Quarkus container image extension for easy containerization
7. Consider MicroProfile Fault Tolerance for resilience patterns
