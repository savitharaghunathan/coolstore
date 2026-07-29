# PLAN.md

## Goal
Migrate a Java EE 7 WebLogic application to Quarkus 3, replacing EJB with CDI, JMS/MDB with SmallRye Reactive Messaging, WAR with JAR packaging, and removing WebLogic-specific dependencies.
- Reference used: javaee-to-quarkus skill

## Project Summary
- Type: Maven (WAR → JAR)
- Files affected: 30 Java source files + 3 config files + pom.xml
- Estimated complexity: High
- Hardest steps:
  1. Converting 2 Message-Driven Beans (MDB) to SmallRye Reactive Messaging
  2. Replacing WebLogic JNDI lookups and lifecycle listeners
  3. Converting @Stateless/@Stateful EJBs to CDI beans with proper transaction handling

## Steps

### Step 1: Add Quarkus BOM to pom.xml
- File: pom.xml
- Action: MODIFY
- What to do:
    1. Add `<quarkus.platform.version>3.2.0.Final</quarkus.platform.version>` to properties
    2. Add Quarkus BOM to dependencyManagement section
    3. Add `<maven.compiler.release>17</maven.compiler.release>` (Quarkus 3 requires Java 17)
- Why: Establishes Quarkus platform version for dependency management
- Depends on: none
- Verify: `mvn dependency:tree | grep quarkus`

### Step 2: Change packaging from WAR to JAR
- File: pom.xml
- Action: MODIFY
- What to do:
    - Change `<packaging>war</packaging>` → `<packaging>jar</packaging>`
    - Update `<finalName>ROOT</finalName>` → `<finalName>coolstore-monolith</finalName>`
- Why: Quarkus uses JAR packaging (self-contained executable)
- Depends on: Step 1
- Verify: Check pom.xml for `<packaging>jar</packaging>`

### Step 3: Replace Java EE dependencies with Quarkus extensions
- File: pom.xml
- Action: MODIFY
- What to do:
    - REMOVE: javax/javaee-web-api:7.0
    - REMOVE: javax/javaee-api:7.0
    - REMOVE: jboss-jms-api_2.0_spec
    - REMOVE: jboss-rmi-api_1.0_spec
    - REMOVE: maven-war-plugin
    - ADD: quarkus-resteasy-reactive-jackson (REST + JSON)
    - ADD: quarkus-hibernate-orm-panache (JPA)
    - ADD: quarkus-jdbc-postgresql (database)
    - ADD: quarkus-smallrye-reactive-messaging-kafka (messaging)
    - ADD: quarkus-arc (CDI)
    - ADD: quarkus-flyway (database migrations)
    - KEEP: flyway-core, test dependencies
- Why: Quarkus provides replacements for Java EE APIs
- Depends on: Step 2
- Verify: `mvn dependency:tree | grep -E "quarkus|javax"`

### Step 4: Add Quarkus Maven plugin
- File: pom.xml
- Action: MODIFY
- What to do:
    1. Add quarkus-maven-plugin to build/plugins
    2. Update maven-compiler-plugin configuration to use Java 17
    3. Remove maven-war-plugin (no longer needed)
- Why: Enables Quarkus dev mode, native builds, and proper packaging
- Depends on: Step 3
- Verify: `mvn quarkus:dev --version`

### Step 5: Create application.properties
- File: src/main/resources/application.properties
- Action: CREATE
- What to do:
    ```properties
    # Datasource configuration (replaces persistence.xml JNDI lookup)
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
    
    # Kafka configuration (replaces JMS topic/orders)
    mp.messaging.outgoing.orders.connector=smallrye-kafka
    mp.messaging.outgoing.orders.topic=orders
    mp.messaging.outgoing.orders.value.serializer=org.apache.kafka.common.serialization.StringSerializer
    
    mp.messaging.incoming.orders-in.connector=smallrye-kafka
    mp.messaging.incoming.orders-in.topic=orders
    mp.messaging.incoming.orders-in.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
    
    # Application
    quarkus.http.port=8080
    ```
- Why: Replaces persistence.xml, moves config from XML to properties
- Depends on: Step 4
- Verify: File exists and contains datasource config

### Step 6: Delete persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Remove file completely
- Why: Configuration moved to application.properties
- Depends on: Step 5
- Verify: `! -f src/main/resources/META-INF/persistence.xml`

### Step 7: Delete web.xml
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Remove file completely
- Why: Quarkus auto-configures JAX-RS, no web.xml needed
- Depends on: Step 5
- Verify: `! -f src/main/webapp/WEB-INF/web.xml`

### Step 8: Delete beans.xml
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Remove file completely
- Why: Quarkus CDI is enabled by default
- Depends on: Step 5
- Verify: `! -f src/main/webapp/WEB-INF/beans.xml`

### Step 9: Convert CatalogService (@Stateless → CDI)
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
    - REMOVE: `import javax.ejb.Stateless;`
    - REMOVE: `@Stateless` annotation
    - ADD: `import javax.enterprise.context.ApplicationScoped;`
    - ADD: `@ApplicationScoped` annotation
    - ADD: `import javax.transaction.Transactional;`
    - ADD: `@Transactional` to methods that modify data (updateInventoryItems)
- Why: Quarkus uses CDI @ApplicationScoped instead of @Stateless EJB
- Depends on: Step 3
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/CatalogService.java`

### Step 10: Convert OrderService (@Stateless → CDI)
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
    - REMOVE: `@Stateless`
    - ADD: `@ApplicationScoped`
    - ADD: `@Transactional` to save/update methods
- Why: Replace EJB with CDI
- Depends on: Step 9
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/OrderService.java`

### Step 11: Convert ProductService (@Stateless → CDI)
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
    - REMOVE: `@Stateless`
    - ADD: `@ApplicationScoped`
- Why: Replace EJB with CDI
- Depends on: Step 9
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/ProductService.java`

### Step 12: Convert ShippingService (@Stateless → CDI)
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
    - REMOVE: `@Stateless`
    - ADD: `@ApplicationScoped`
- Why: Replace EJB with CDI
- Depends on: Step 9
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/ShippingService.java`

### Step 13: Convert ShoppingCartOrderProcessor (@Stateless → CDI)
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
    - REMOVE: `@Stateless`
    - ADD: `@ApplicationScoped`
    - ADD: `@Transactional` to methods that persist orders
- Why: Replace EJB with CDI
- Depends on: Step 9
- Verify: `grep -q "@ApplicationScoped" src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 14: Convert ShoppingCartService (@Stateful → CDI)
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
    - REMOVE: `import javax.ejb.Stateful;`
    - REMOVE: `@Stateful`
    - ADD: `import javax.enterprise.context.SessionScoped;`
    - ADD: `@SessionScoped`
    - ADD: `implements Serializable` (required for @SessionScoped)
- Why: @Stateful EJB → @SessionScoped CDI bean (maintains per-session state)
- Depends on: Step 9
- Verify: `grep -q "@SessionScoped" src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

### Step 15: COMPLEX — Convert OrderServiceMDB to Reactive Messaging
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
    - BEFORE: @MessageDriven MDB with JMS MessageListener
    - AFTER: CDI bean with @Incoming reactive consumer
    - Specific changes:
        1. REMOVE: `import javax.ejb.MessageDriven;`
        2. REMOVE: `import javax.jms.*;`
        3. REMOVE: `@MessageDriven` annotation
        4. REMOVE: `implements MessageListener`
        5. ADD: `import org.eclipse.microprofile.reactive.messaging.Incoming;`
        6. ADD: `import javax.enterprise.context.ApplicationScoped;`
        7. ADD: `@ApplicationScoped` to class
        8. REPLACE method signature:
           ```java
           // OLD:
           public void onMessage(Message rcvMessage) {
               TextMessage msg = (TextMessage) rcvMessage;
               String orderStr = msg.getBody(String.class);
               // ...
           }
           
           // NEW:
           @Incoming("orders-in")
           public void processOrder(String orderStr) {
               System.out.println("Received order: " + orderStr);
               Order order = Transformers.jsonToOrder(orderStr);
               // ... rest of logic
           }
           ```
- Why: Quarkus uses SmallRye Reactive Messaging instead of JMS MDB
- Depends on: Step 5, Step 10
- Verify: `grep -q "@Incoming" src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

### Step 16: COMPLEX — Convert InventoryNotificationMDB to Reactive Messaging
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
    - BEFORE: Manual JNDI lookup with WebLogic-specific code
    - AFTER: @Incoming reactive consumer
    - Specific changes:
        1. REMOVE: All WebLogic JNDI code (getInitialContext, JNDI_FACTORY, etc.)
        2. REMOVE: `import javax.jms.*;`, `import javax.naming.*;`, `import javax.rmi.*;`
        3. REMOVE: TopicConnection, TopicSession, TopicSubscriber fields
        4. REMOVE: init() and close() methods
        5. ADD: `@ApplicationScoped` annotation
        6. ADD: `@Incoming("orders-in")` to onMessage
        7. REPLACE method signature:
           ```java
           // OLD:
           public void onMessage(Message rcvMessage) {
               TextMessage msg = (TextMessage) rcvMessage;
               String orderStr = msg.getBody(String.class);
               // ...
           }
           
           // NEW:
           @Incoming("orders-in")
           public void processInventoryNotification(String orderStr) {
               System.out.println("received message inventory");
               Order order = Transformers.jsonToOrder(orderStr);
               // ... rest of logic
           }
           ```
- Why: Remove WebLogic JNDI lookups, use Quarkus reactive messaging
- Depends on: Step 5, Step 9
- Verify: `! grep -q "weblogic.jndi" src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

### Step 17: Create message producer for checkout
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
    1. ADD: `import org.eclipse.microprofile.reactive.messaging.Channel;`
    2. ADD: `import org.eclipse.microprofile.reactive.messaging.Emitter;`
    3. ADD field: `@Inject @Channel("orders") Emitter<String> ordersEmitter;`
    4. In checkout method, replace JMS send with: `ordersEmitter.send(orderJson);`
- Why: Replace JMS producer with reactive messaging Emitter
- Depends on: Step 5, Step 13
- Verify: `grep -q "Emitter" src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

### Step 18: COMPLEX — Replace WebLogic lifecycle listener with Quarkus events
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
    - BEFORE: Extends weblogic.application.ApplicationLifecycleListener
    - AFTER: Uses Quarkus @Observes lifecycle events
    - Specific changes:
        1. REMOVE: `import weblogic.application.*;`
        2. REMOVE: `extends ApplicationLifecycleListener`
        3. ADD: `import io.quarkus.runtime.StartupEvent;`
        4. ADD: `import io.quarkus.runtime.ShutdownEvent;`
        5. ADD: `import javax.enterprise.context.ApplicationScoped;`
        6. ADD: `import javax.enterprise.event.Observes;`
        7. ADD: `@ApplicationScoped` to class
        8. REPLACE methods:
           ```java
           // OLD:
           public void postStart(ApplicationLifecycleEvent evt) {
               log.info("AppListener(postStart)");
           }
           public void preStop(ApplicationLifecycleEvent evt) {
               log.info("AppListener(preStop)");
           }
           
           // NEW:
           void onStart(@Observes StartupEvent event) {
               log.info("AppListener(postStart)");
           }
           void onStop(@Observes ShutdownEvent event) {
               log.info("AppListener(preStop)");
           }
           ```
- Why: WebLogic-specific lifecycle API not available in Quarkus
- Depends on: Step 3
- Verify: `grep -q "@Observes StartupEvent" src/main/java/com/redhat/coolstore/utils/StartupListener.java`

### Step 19: Delete WebLogic stub class
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Remove the entire weblogic package directory
- Why: No longer needed after migration to Quarkus lifecycle events
- Depends on: Step 18
- Verify: `! -d src/main/java/weblogic`

### Step 20: Update EntityManager injection in Resources.java
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
    - Change `@PersistenceContext` → `@Inject EntityManager`
    - REMOVE: `import javax.persistence.PersistenceContext;`
    - Ensure: `import javax.inject.Inject;` exists
- Why: Quarkus uses standard CDI injection for EntityManager
- Depends on: Step 3
- Verify: `grep -q "@Inject" src/main/java/com/redhat/coolstore/persistence/Resources.java`

### Step 21: Update all javax.* imports to jakarta.*
- File: All Java files in src/main/java (30 files)
- Action: MODIFY
- What to do:
    - Replace `javax.persistence.` → `jakarta.persistence.`
    - Replace `javax.enterprise.` → `jakarta.enterprise.`
    - Replace `javax.inject.` → `jakarta.inject.`
    - Replace `javax.ws.rs.` → `jakarta.ws.rs.`
    - Replace `javax.transaction.` → `jakarta.transaction.`
    - Keep `javax.json` (not part of Jakarta EE in Quarkus 3)
- Why: Quarkus 3 uses Jakarta EE namespace
- Depends on: All previous steps
- Verify: `! grep -r "import javax\\.persistence" src/main/java && grep -q "import jakarta.persistence" src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`

### Step 22: Update REST application class
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
    - REMOVE: `@ApplicationPath("/api")` (Quarkus auto-configures)
    - REMOVE: `extends Application` (not needed)
    - DELETE the entire class if it only contains @ApplicationPath
- Why: Quarkus automatically registers REST endpoints at /
- Depends on: Step 21
- Verify: Check if file can be deleted or simplified

### Step 23: Move webapp resources to META-INF/resources
- File: src/main/webapp/* (entire directory)
- Action: MODIFY
- What to do:
    1. Create directory: `src/main/resources/META-INF/resources`
    2. Move all files from `src/main/webapp/` to `src/main/resources/META-INF/resources/`
    3. EXCLUDE: WEB-INF directory (already deleted)
- Why: Quarkus serves static files from META-INF/resources in JAR packaging
- Depends on: Step 7, Step 8
- Verify: `test -d src/main/resources/META-INF/resources && test -f src/main/resources/META-INF/resources/index.jsp`

### Step 24: Update system dependency for audit library
- File: pom.xml
- Action: MODIFY
- What to do:
    - Review the system-scoped dependency on audit-logging-library
    - Consider installing to local Maven repo or converting to standard dependency
    - If keeping system scope, ensure path is correct for JAR packaging
- Why: System-scoped dependencies can cause issues with Quarkus
- Depends on: Step 4
- Verify: `mvn dependency:tree | grep audit-logging`

### Step 25: Add dev mode PostgreSQL configuration
- File: src/main/resources/application.properties
- Action: MODIFY
- What to do:
    - Add profile-specific config for dev mode:
      ```properties
      %dev.quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore_dev
      %dev.quarkus.hibernate-orm.database.generation=drop-and-create
      %dev.quarkus.hibernate-orm.log.sql=true
      ```
- Why: Different config for development vs production
- Depends on: Step 5
- Verify: `grep -q "%dev.quarkus.datasource" src/main/resources/application.properties`

### Step 26: Create Logger producer (if not exists)
- File: src/main/java/com/redhat/coolstore/utils/LoggerProducer.java
- Action: CREATE (if needed)
- What to do:
    ```java
    package com.redhat.coolstore.utils;
    
    import jakarta.enterprise.inject.Produces;
    import jakarta.enterprise.inject.spi.InjectionPoint;
    import java.util.logging.Logger;
    
    public class LoggerProducer {
        @Produces
        public Logger produceLogger(InjectionPoint injectionPoint) {
            return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
        }
    }
    ```
- Why: Enables @Inject Logger throughout application
- Depends on: Step 21
- Verify: File exists or verify Logger injection works

### Step 27: Update test dependencies for Quarkus
- File: pom.xml
- Action: MODIFY
- What to do:
    - ADD: `quarkus-junit5` (scope: test)
    - ADD: `rest-assured` (scope: test)
    - UPDATE: JUnit 4 → JUnit 5 (junit-jupiter)
    - Keep: mockito, H2 dependencies
- Why: Quarkus testing requires JUnit 5
- Depends on: Step 3
- Verify: `mvn test-compile`

### Step 28: Verify no javax.ejb imports remain
- File: All Java files
- Action: VERIFY
- What to do:
    - Run: `grep -r "import javax.ejb" src/main/java`
    - Run: `grep -r "import javax.jms" src/main/java`
    - Run: `grep -r "weblogic" src/main/java`
    - Ensure: All return empty results
- Why: Confirms complete removal of EJB and WebLogic dependencies
- Depends on: All previous steps
- Verify: `! grep -r "import javax.ejb" src/main/java`

### Step 29: Build verification
- File: N/A
- Action: VERIFY
- What to do:
    - Run: `mvn clean compile`
    - Fix any compilation errors
    - Ensure: Build completes successfully
- Why: Confirms code compiles with Quarkus dependencies
- Depends on: All previous steps
- Verify: `mvn clean compile` exits with code 0

### Step 30: Create README updates
- File: README.md
- Action: MODIFY
- What to do:
    - Add section: "Running with Quarkus"
    - Document: `mvn quarkus:dev` for development
    - Document: `mvn package` to build
    - Document: `java -jar target/quarkus-app/quarkus-run.jar` to run
    - Update: Database setup instructions
    - Update: Kafka setup instructions (replacing JMS)
- Why: Users need updated instructions for Quarkus runtime
- Depends on: Step 29
- Verify: README contains Quarkus run instructions

## Verification

After completing all steps, run these commands in order:

```bash
# 1. Clean build
mvn clean compile
# Expected: BUILD SUCCESS

# 2. Check no Java EE/WebLogic imports remain
grep -r "import javax.ejb" src/main/java
grep -r "import javax.jms" src/main/java  
grep -r "weblogic" src/main/java
# Expected: No matches

# 3. Check Quarkus dependencies
mvn dependency:tree | grep quarkus
# Expected: Multiple quarkus-* dependencies listed

# 4. Check Jakarta namespace migration
grep -r "import jakarta.persistence" src/main/java
grep -r "import jakarta.enterprise" src/main/java
# Expected: Multiple matches

# 5. Verify packaging
grep "<packaging>" pom.xml
# Expected: <packaging>jar</packaging>

# 6. Run tests (if available)
mvn test
# Expected: Tests pass or compile

# 7. Try dev mode (requires PostgreSQL and Kafka running)
mvn quarkus:dev
# Expected: Application starts on port 8080
```

## Notes

### Migration Complexity
This is a **HIGH complexity** migration due to:
1. **Two MDB conversions** requiring architectural changes (JMS → Kafka)
2. **WebLogic-specific JNDI code** that must be completely removed
3. **Lifecycle listener** requiring Quarkus event model understanding
4. **6 EJB services** requiring careful transaction boundary analysis

### Key Decisions Made
1. **Messaging platform**: Assumed Kafka as replacement for JMS (most common in Quarkus)
   - Alternative: SmallRye AMQP or JMS connector if JMS broker stays
2. **Database**: Configured for PostgreSQL (inferred from common practice)
   - Adjust application.properties if using different database
3. **Session management**: Kept @SessionScoped for ShoppingCartService (equivalent to @Stateful)
4. **Java version**: Target Java 17 (minimum for Quarkus 3)

### Risk Areas
1. **Two consumers on same topic**: Both OrderServiceMDB and InventoryNotificationMDB consume "orders" topic
   - May need different consumer groups or separate topics in Kafka
2. **System-scoped dependency**: audit-logging-library may cause packaging issues
3. **Flyway migrations**: Ensure db/migration scripts compatible with target database
4. **Session state**: ShoppingCartService session state may behave differently (test thoroughly)

### Post-Migration Testing Required
1. Verify REST endpoints: `/api/cart/*`, `/api/products/*`, `/api/orders/*`
2. Test message flow: Cart checkout → Kafka → Order processing → Inventory update
3. Test session state: Shopping cart persistence across requests
4. Performance test: Compare with Java EE baseline
5. Integration test: Full checkout workflow end-to-end

### Rollback Plan
If migration fails:
1. Revert to commit before Step 1
2. Keep graph.json and PLAN.md for future reference
3. Consider incremental migration (e.g., REST endpoints first, messaging later)
