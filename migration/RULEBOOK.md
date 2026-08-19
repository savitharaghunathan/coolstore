# Java EE 7 → Quarkus Migration Rulebook

## Target Platform
- **Source**: Java EE 7 (JBoss EAP 7.4) with ActiveMQ messaging
- **Target**: Quarkus 3.x with SmallRye Reactive Messaging

## Current Codebase Summary
- 51 Java source files
- Key patterns: `@MessageDriven` (OrderServiceMDB), `@Stateless` (ShoppingCartOrderProcessor), `@Stateful` (ShoppingCartService)
- Messaging: ActiveMQ Topic (`topic/orders`) via JMS with `@MessageDriven` and `JMSContext`
- REST: JAX-RS endpoints for catalog, cart, order, product services
- Persistence: JPA with Hibernate
- Security: Keycloak integration

## Namespace Changes (javax → jakarta)
```
javax.ejb.* → jakarta.ejb.*
javax.jms.* → jakarta.jms.*
javax.inject.* → jakarta.inject.*
javax.persistence.* → jakarta.persistence.*
javax.ws.rs.* → jakarta.ws.rs.*
javax.servlet.* → jakarta.servlet.*
```

## Key Migration Rules by Pattern

### 1. Messaging: ActiveMQ JMS → SmallRye Reactive Messaging

**Pattern 1A: Message Producer (ShoppingCartOrderProcessor)**
```
// OLD (JBoss EAP + JMS):
@Stateless
public class ShoppingCartOrderProcessor {
  @Inject private JMSContext context;
  @Resource(lookup = "java:/topic/orders") private Topic ordersTopic;
  
  public void process(ShoppingCart cart) {
    context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));
  }
}

// NEW (Quarkus + SmallRye Reactive Messaging):
@ApplicationScoped
public class ShoppingCartOrderProcessor {
  @Channel("orders") Emitter<String> orders;
  
  public void process(ShoppingCart cart) {
    orders.send(Transformers.shoppingCartToJson(cart));
  }
}
```

**Pattern 1B: Message Consumer (OrderServiceMDB)**
```
// OLD (JBoss EAP + MessageDriven):
@MessageDriven(name = "OrderServiceMDB", activationConfig = {
  @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "topic/orders"),
  @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
  ...
})
public class OrderServiceMDB implements MessageListener {
  public void onMessage(Message rcvMessage) {
    TextMessage msg = (TextMessage) rcvMessage;
    String orderStr = msg.getBody(String.class);
    Order order = Transformers.jsonToOrder(orderStr);
    orderService.save(order);
  }
}

// NEW (Quarkus + SmallRye Reactive Messaging):
@ApplicationScoped
public class OrderServiceMDB {
  @Inject OrderService orderService;
  @Inject CatalogService catalogService;
  
  @Incoming("orders")
  @Transactional  // REQUIRED: SmallRye @Incoming methods don't auto-run in transactions (unlike EJB MDBs)
  public void onMessage(String orderStr) {
    Order order = Transformers.jsonToOrder(orderStr);
    orderService.save(order);  // Calls em.persist() — needs active transaction
    order.getItemList().forEach(item -> {
      catalogService.updateInventoryItems(item.getProductId(), item.getQuantity());
    });
  }
}

// IMPORTANT: Transaction Model Difference
// Java EE EJB @MessageDriven beans run under container-managed transactions (REQUIRED by default)
// Quarkus SmallRye Reactive Messaging @Incoming methods do NOT auto-run in transactions.
// Result: Any @Incoming method that performs JPA writes (persist, merge, remove) MUST be
// annotated with @Transactional (jakarta.transaction.Transactional) to avoid
// TransactionRequiredException at runtime. This applies to all @Incoming methods in
// this migration.
```

**Configuration in application.properties:**
```
# Kafka (if using Kafka) or AMQP
mp.messaging.outgoing.orders.connector=smallrye-kafka
mp.messaging.outgoing.orders.topic=orders
mp.messaging.incoming.orders.connector=smallrye-kafka
mp.messaging.incoming.orders.topic=orders

# Or for in-memory/testing:
# mp.messaging.outgoing.orders.connector=smallrye-in-memory
# mp.messaging.incoming.orders.connector=smallrye-in-memory
```

### 2. EJB → CDI Conversion

**@Stateless → @ApplicationScoped**
- `@Stateless` services become `@ApplicationScoped` beans (no state per request)
- `ShoppingCartOrderProcessor`: `@Stateless` → `@ApplicationScoped`
- `ShippingService`: `@Stateless` → `@ApplicationScoped`

**@Stateful → @SessionScoped (or remove if not needed)**
- `@Stateful` beans maintain per-client state; in Quarkus REST apps, prefer stateless design
- `ShoppingCartService`: Check if state is truly needed; if so, use `@SessionScoped` with explicit scope management

**@MessageDriven → @Incoming method**
- No direct CDI equivalent; use SmallRye `@Incoming` annotation instead
- `OrderServiceMDB`: Remove `implements MessageListener`, add `@Incoming("channel-name")` method

**@EJB injection → @Inject**
- Already done in current code (uses `@Inject`); no changes needed

### 3. REST Endpoints
- Keep `@Path`, `@GET`, `@POST`, `@Produces`, `@Consumes` as-is (jakarta.ws.rs)
- Change imports from `javax.ws.rs` to `jakarta.ws.rs`

### 4. Persistence & Database
- Keep JPA `@Entity` classes (jakarta.persistence)
- Update persistence.xml → application.properties for Quarkus:
  ```
  quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/postgresDB
  quarkus.datasource.username=postgresUser
  quarkus.datasource.password=postgresPW
  quarkus.hibernate-orm.database.generation=update
  ```
- SessionFactory/EntityManager: Quarkus auto-configures via `@PersistenceContext` or `@PersistenceUnit`

### 5. Security & Keycloak
- Remove `@RolesAllowed`, `@PermitAll` if used; switch to Quarkus OIDC:
  ```
  quarkus.oidc.auth-server-url=http://localhost:8081/realms/eap
  quarkus.oidc.client-id=coolstore
  quarkus.oidc.credentials.secret=<secret>
  ```

### 6. Logging
- Replace `System.out.println()` with SLF4J:
  ```
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ClassName.class);
  log.info("message");
  ```
- JBoss Logging API: No longer needed; use SLF4J

### 7. Configuration
- Delete `src/main/webapp/WEB-INF/web.xml` (Quarkus uses annotations + `application.properties`)
- Delete `src/main/resources/META-INF/persistence.xml` (Quarkus handles JPA config)
- All runtime config → `src/main/resources/application.properties`

### 8. Testing
- Replace `@RunWith(Arquillian.class)` with `@QuarkusTest`
- Replace `@Deployment` with Quarkus test fixture setup
- Use `@QuarkusTestResource` for database/Kafka setup

## File Naming & Output Structure
```
src/main/java/com/redhat/coolstore/**/*.java  → same path (updated imports)
src/main/resources/application.properties      ← NEW (consolidates config)
src/main/webapp/WEB-INF/web.xml               → DELETE
src/main/resources/META-INF/persistence.xml   → DELETE
pom.xml                                         → UPDATE (Quarkus parent, extensions)
```

## Messaging Connector Decision

**RESOLVED (Step 2 Amendment 2):** Use **Apache Kafka** as the SmallRye Reactive Messaging connector.

**Rationale:**
- Production-ready for distributed systems
- Aligns with Quarkus best practices for cloud-native deployments
- Better suited for high-volume order processing (versus in-memory for testing only)
- ActiveMQ can be replaced with Kafka at deployment sites

**Configuration in application.properties:**
```properties
# SmallRye Reactive Messaging - Kafka Connector
mp.messaging.outgoing.orders.connector=smallrye-kafka
mp.messaging.outgoing.orders.topic=orders
mp.messaging.outgoing.orders.value.serializer=org.apache.kafka.common.serialization.StringSerializer

mp.messaging.incoming.orders.connector=smallrye-kafka
mp.messaging.incoming.orders.topic=orders
mp.messaging.incoming.orders.group.id=coolstore-orders
mp.messaging.incoming.orders.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
```

**pom.xml additions:**
```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-smallrye-reactive-messaging-kafka</artifactId>
</dependency>
<dependency>
  <groupId>org.apache.kafka</groupId>
  <artifactId>kafka-clients</artifactId>
</dependency>
```

**Files affected:** OrderServiceMDB, ShoppingCartOrderProcessor, InventoryNotificationMDB, and all related tests

## Remaining Gaps & TODOs
- [ ] Keycloak realm/client setup for `quarkus-oidc`
- [ ] @Stateful bean state persistence (if truly needed)
- [ ] WebSocket support (if used; Quarkus has different model)
- [ ] Custom validators/interceptors (if any)

## Deviation Log

### Step 2 (Stress Test) — 2026-08-19

**Status**: Awaiting amendment approval before proceeding to Step 3

**Amendments Queued** (see migration/stress-test/report.md for full evidence):

1. **Amendment 1: Transaction Demarcation Missing from Pattern 1B**
   - **Section**: Pattern 1B (Message Consumer), lines 71–84
   - **Issue**: Example omits `@Transactional` but calls `orderService.save(order)` → EntityManager.persist(). SmallRye @Incoming methods don't run in transaction by default → TransactionRequiredException at runtime.
   - **Fix**: Add `@Transactional` to Pattern 1B example. Add note explaining SmallRye transaction model differs from EJB.
   - **Evidence**: Translator A (rulebook-literal) fails at runtime; Translator B (native) fixed it correctly.
   - **Rule invoked**: Rule 6 (Recurring failures move upstream)

2. **Amendment 2: Messaging Connector Explicitly Unresolved**
   - **Section**: Known Gaps, line 169
   - **Issue**: Rulebook lists "ActiveMQ → SmallRye binding (Kafka? AMQP? in-memory for testing?)" as open. No guidance for translators on which connector to use.
   - **Fix**: Either (a) pick Kafka/AMQP/in-memory explicitly + document in inventory-decisions.md, or (b) require TODOs in application.properties naming the unresolved gap.
   - **Evidence**: Translator A didn't flag; Translator B silently chose in-memory. Needs explicit decision for fan-out.
   - **Rule invoked**: Rule 8 (Unknown is an answer; searchable artifact beats stalled batch)

**Amendments Applied**: 2026-08-19 16:15Z ✅
- Amendment 1: Pattern 1B updated with @Transactional + transaction model explanation
- Amendment 2: Kafka chosen as SmallRye Reactive Messaging connector; configuration documented

**Step 2 Re-round**: 2026-08-19 16:30Z ✅ COMPLETE
- Fresh files selected: OrderService.java, CatalogService.java, InventoryNotificationMDB.java
- Amendment 1 (@Transactional): VALIDATED — correctly applied, no TransactionRequiredException risk
- Amendment 2 (Kafka): VALIDATED — Kafka configuration explicit, no ambiguity
- Pilot run adherence: 95% (up from 82% in initial pilot)
- See migration/stress-test/report-reround.md for full findings
- **Status**: READY FOR STEP 3 FAN-OUT

**Process**: Step 2 complete. Proceed to Step 3 (Translate All 51 Files).
