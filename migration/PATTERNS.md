# Critical Translation Patterns for Coolstore

This document shows the exact transformations needed for the three most critical files in coolstore. These are the patterns to validate in Step 2 (Stress Test).

## Pattern 1: Message Producer (ShoppingCartOrderProcessor)

### OLD (Java EE 7 + JMS)
```java
package com.redhat.coolstore.service;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.Topic;
import javax.annotation.Resource;
import java.util.logging.Logger;

@Stateless
public class ShoppingCartOrderProcessor {

    @Inject
    Logger log;

    @Inject
    private transient JMSContext context;

    @Resource(lookup = "java:/topic/orders")
    private Topic ordersTopic;

    public void process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        context.createProducer().send(ordersTopic, 
            Transformers.shoppingCartToJson(cart));
    }
}
```

### NEW (Quarkus + SmallRye Reactive Messaging)
```java
package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ShoppingCartOrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(ShoppingCartOrderProcessor.class);

    @Inject
    @Channel("orders")
    Emitter<String> orders;

    public void process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        orders.send(Transformers.shoppingCartToJson(cart));
    }
}
```

**Changes:**
- `@Stateless` → `@ApplicationScoped`
- `javax.*` → `jakarta.*`
- Remove `@Inject private transient JMSContext` + Topic
- Add `@Inject @Channel("orders") Emitter<String> orders`
- Replace `context.createProducer().send(ordersTopic, msg)` with `orders.send(msg)`
- Replace JDK Logger with SLF4J
- Delete `@Resource(lookup=...)` annotation

**Configuration in application.properties:**
```properties
mp.messaging.outgoing.orders.connector=smallrye-kafka
mp.messaging.outgoing.orders.topic=orders
mp.messaging.outgoing.orders.value.serializer=org.apache.kafka.common.serialization.StringSerializer
```

---

## Pattern 2: Message Consumer (OrderServiceMDB)

### OLD (Java EE 7 + MessageDriven)
```java
package com.redhat.coolstore.service;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

@MessageDriven(name = "OrderServiceMDB", activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "topic/orders"),
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
})
public class OrderServiceMDB implements MessageListener { 

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Override
    public void onMessage(Message rcvMessage) {
        System.out.println("\nMessage recd !");
        TextMessage msg = null;
        try {
            if (rcvMessage instanceof TextMessage) {
                msg = (TextMessage) rcvMessage;
                String orderStr = msg.getBody(String.class);
                System.out.println("Received order: " + orderStr);
                Order order = Transformers.jsonToOrder(orderStr);
                System.out.println("Order object is " + order);
                orderService.save(order);
                order.getItemList().forEach(orderItem -> {
                    catalogService.updateInventoryItems(
                        orderItem.getProductId(), 
                        orderItem.getQuantity());
                });
            }
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }
}
```

### NEW (Quarkus + SmallRye Reactive Messaging)
```java
package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class OrderServiceMDB { 

    private static final Logger log = LoggerFactory.getLogger(OrderServiceMDB.class);

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Incoming("orders")
    public void onMessage(String orderStr) {
        log.info("Message received!");
        log.info("Received order: {}", orderStr);
        Order order = Transformers.jsonToOrder(orderStr);
        log.info("Order object is {}", order);
        orderService.save(order);
        order.getItemList().forEach(orderItem -> {
            catalogService.updateInventoryItems(
                orderItem.getProductId(), 
                orderItem.getQuantity());
        });
    }
}
```

**Changes:**
- Remove `implements MessageListener`
- Delete entire `@MessageDriven` annotation + `@ActivationConfigProperty` block
- Replace `public void onMessage(Message rcvMessage)` with `@Incoming("orders") public void onMessage(String orderStr)`
- Remove type cast and try-catch (exception handling is automatic)
- Replace `System.out.println()` with `log.info()`
- Remove JMSException handling (SmallRye handles it)

**Configuration in application.properties:**
```properties
mp.messaging.incoming.orders.connector=smallrye-kafka
mp.messaging.incoming.orders.topic=orders
mp.messaging.incoming.orders.group.id=orders-group
mp.messaging.incoming.orders.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
```

---

## Pattern 3: @Stateless Service → @ApplicationScoped

### OLD (Java EE 7 + Stateless EJB)
```java
package com.redhat.coolstore.service;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.logging.Logger;

@Stateless
public class CatalogService {

    @Inject
    Logger log;

    @PersistenceContext
    private EntityManager em;

    public void updateCatalog(Long productId, String name) {
        log.info("Updating product: " + productId);
        // ... database work
    }
}
```

### NEW (Quarkus + ApplicationScoped CDI)
```java
package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    @PersistenceContext
    private EntityManager em;

    public void updateCatalog(Long productId, String name) {
        log.info("Updating product: {}", productId);
        // ... database work (same as before)
    }
}
```

**Changes:**
- `@Stateless` → `@ApplicationScoped`
- `javax.ejb.*` → remove (not needed)
- `javax.inject.*` → `jakarta.inject.*`
- `javax.persistence.*` → `jakarta.persistence.*`
- Replace JDK Logger with SLF4J
- `@PersistenceContext` stays as-is (jakarta.persistence)

---

## Testing Pattern: Messaging Tests

### OLD (OrderServiceMDBTest)
```java
@RunWith(Arquillian.class)
public class OrderServiceMDBTest {

    @Mock
    private OrderService orderService;

    @Mock
    private CatalogService catalogService;

    private OrderServiceMDB orderServiceMDB;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        orderServiceMDB = new OrderServiceMDB();
        // field injection
    }

    @Test
    public void testOnMessageDeserializesAndSavesOrder() throws JMSException {
        TextMessage msg = mock(TextMessage.class);
        when(msg.getBody(String.class)).thenReturn("{\"orderId\":123}");
        
        orderServiceMDB.onMessage(msg);
        
        verify(orderService).save(any(Order.class));
    }
}
```

### NEW (OrderServiceMDBTest with Quarkus)
```java
@QuarkusTest
public class OrderServiceMDBTest {

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Inject
    OrderServiceMDB orderServiceMDB;

    @Test
    public void testOnMessageDeserializesAndSavesOrder() {
        String orderJson = "{\"orderId\":123}";
        
        orderServiceMDB.onMessage(orderJson);
        
        // Assert via orderService or database query
        Order saved = orderService.findById(123L);
        assertNotNull(saved);
    }
}
```

**Changes:**
- `@RunWith(Arquillian.class)` → `@QuarkusTest`
- Remove `@Mock` + Mockito setup; use `@Inject` for real beans
- Mock TextMessage → pass String directly
- Verify via real database queries instead of Mockito verifies

---

## Important Notes

1. **No More JMS Imports** — All `javax.jms` imports are deleted
2. **SmallRye Dependencies** — pom.xml must include:
   ```xml
   <dependency>
     <groupId>io.quarkus</groupId>
     <artifactId>quarkus-smallrye-reactive-messaging</artifactId>
   </dependency>
   <dependency>
     <groupId>io.quarkus</groupId>
     <artifactId>quarkus-smallrye-reactive-messaging-kafka</artifactId>
   </dependency>
   ```
3. **Exception Handling** — SmallRye handles exceptions; you don't catch JMSException anymore
4. **Channel Configuration** — All topic/queue names move to application.properties
5. **Logger Choice** — SLF4J is Quarkus standard; JDK Logger is removed

---

**These patterns form the core of the migration. All other files follow similar rules.**
