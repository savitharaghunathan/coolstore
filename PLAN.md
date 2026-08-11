# Java EE to Quarkus 3 Migration Plan - CoolStore Monolith

## Executive Summary

This document outlines the migration plan for the CoolStore Monolith application from Java EE 7 (JBoss EAP 7.4) to Quarkus 3. The application is an e-commerce shopping cart system with REST APIs, JMS messaging, JPA persistence, and EJB components.

## Current State Assessment

### Application Overview
- **Name**: coolstore-monolith
- **Current Runtime**: JBoss EAP 7.4 (Java EE 7)
- **Packaging**: WAR
- **Java Version**: 1.8
- **Build Tool**: Maven 3.8.5
- **Database**: PostgreSQL (via datasource: CoolstoreDS)

### Technology Stack Analysis

#### 1. Java EE APIs in Use
Based on code analysis of 30 Java files (1791 lines):

**Core APIs:**
- **CDI (Contexts and Dependency Injection)**: `@Inject`, `@Produces`, `@SessionScoped`, `@Dependent`
- **EJB 3.x**: `@Stateless`, `@Stateful`, `@MessageDriven`, `@Remote`, `@ActivationConfigProperty`
- **JPA 2.1**: `@PersistenceContext`, `@Entity`, `EntityManager`
- **JAX-RS 2.0**: `@Path`, `@GET`, `@POST`, `@DELETE`, `@PathParam`, `@Produces`
- **JMS 2.0**: `MessageListener`, `Topic`, `TopicConnection`, JMS APIs
- **Bean Validation**: (likely in model classes)
- **Lifecycle Callbacks**: `@PostConstruct`, `@PreDestroy`

**Vendor-Specific APIs:**
- **WebLogic JNDI**: `weblogic.jndi.WLInitialContextFactory` in `InventoryNotificationMDB.java`
- **WildFly Naming**: `org.wildfly.naming.client.WildFlyInitialContextFactory` in `ShoppingCartService.java`

#### 2. Application Architecture

**Layer Structure:**
```
├── Model Layer (8 classes)
│   ├── JPA Entities: CatalogItemEntity, InventoryEntity, Order
│   └── DTOs: Product, Promotion, ShoppingCart, ShoppingCartItem, OrderItem
│
├── Persistence Layer (1 class)
│   └── Resources.java - EntityManager producer
│
├── Service Layer (11 classes)
│   ├── Stateless EJBs: OrderService, ShippingService
│   ├── Stateful EJB: ShoppingCartService
│   ├── Message Driven Beans: OrderServiceMDB, InventoryNotificationMDB
│   └── CDI Beans: CatalogService, ProductService, PromoService, ShoppingCartOrderProcessor
│
├── REST Layer (4 classes)
│   ├── CartEndpoint (SessionScoped)
│   ├── OrderEndpoint
│   ├── ProductEndpoint
│   └── RestApplication (JAX-RS Application)
│
└── Utilities (3 classes)
    ├── DataBaseMigrationStartup (Flyway integration)
    ├── Producers (Logger producer)
    └── Transformers (JSON utilities)
```

**WebLogic-Specific Components:**
```
weblogic/
├── application/
│   ├── ApplicationLifecycleEvent.java
│   └── ApplicationLifecycleListener.java
└── i18n/logging/
    └── NonCatalogLogger.java
```

#### 3. Dependencies Analysis (pom.xml)

**Java EE Dependencies:**
- `javaee-web-api:7.0` (provided)
- `javaee-api:7.0` (provided)
- `jboss-jms-api_2.0_spec:2.0.0.Final`
- `jboss-rmi-api_1.0_spec:1.0.2.Final`

**Third-Party Libraries:**
- `flyway-core:4.1.2` - Database migrations
- `audit-logging-library:1.0.0` - Custom library (system dependency from `lib/` folder)

**Test Dependencies:**
- JUnit 4.13.2
- Mockito 1.10.19
- H2 1.4.200
- Hibernate EntityManager 5.1.17.Final
- javax.json 1.0.4

#### 4. Configuration Files

**JPA Configuration** (`src/main/resources/META-INF/persistence.xml`):
- Persistence unit: "primary"
- Datasource JNDI: `java:jboss/datasources/CoolstoreDS`
- Hibernate properties configured
- Schema generation: none (using Flyway)

**CDI Configuration** (`src/main/webapp/WEB-INF/beans.xml`):
- Bean discovery mode: "all"
- Version: 1.1

**Web Deployment** (`src/main/webapp/WEB-INF/web.xml`):
- Version: 3.0
- Distributable enabled (clustering support)

**Database Migrations** (`src/main/resources/db/`):
- Flyway migration scripts (SQL files)

**Keycloak Integration** (`src/main/webapp/keycloak.json`):
- OAuth/OIDC configuration for authentication

#### 5. JMS Configuration Requirements

**Topics:**
- `topic/orders` - Order processing messages
- Used by: `OrderServiceMDB`, `InventoryNotificationMDB`, `ShoppingCartOrderProcessor`

**Message Flow:**
1. `ShoppingCartOrderProcessor` sends order to topic
2. `OrderServiceMDB` listens and persists order, updates inventory
3. `InventoryNotificationMDB` listens and checks inventory thresholds

#### 6. Problem Areas Identified

**Critical Issues:**
1. **WebLogic-specific JNDI** in `InventoryNotificationMDB.java`:
   - Uses `weblogic.jndi.WLInitialContextFactory`
   - Hardcoded WebLogic provider URL `t3://localhost:7001`
   - Manual JMS topic subscription (not MDB-based)

2. **EJB Remote Lookups** in `ShoppingCartService.java`:
   - Uses WildFly naming for remote EJB lookup
   - `ShippingServiceRemote` interface with `@Remote`

3. **System Scope Dependency**:
   - `audit-logging-library-1.0.0.jar` in `lib/` folder
   - Needs Maven repository or alternative

4. **WebLogic Application Lifecycle Listeners**:
   - Custom WebLogic APIs in `weblogic/application/` package

5. **Session Scope in REST**:
   - `CartEndpoint` uses `@SessionScoped` - needs review for stateless approach

6. **Clustering/Distribution**:
   - `<distributable/>` in web.xml
   - ActiveMQ clustering configuration

## Migration Strategy

### Phase 1: Foundation Setup
### Phase 2: Core Migration
### Phase 3: JMS & Messaging
### Phase 4: Testing & Validation
### Phase 5: Deployment & Optimization

## Detailed Migration Steps

---

## PHASE 1: Foundation Setup

### Step 1.1: Create New Quarkus Project Structure

**Action**: Initialize Quarkus 3 project with required extensions

**Commands**:
```bash
# Backup original pom.xml
cp pom.xml pom.xml.javaee.backup

# Create new Quarkus project structure (or migrate pom.xml)
mvn io.quarkus:quarkus-maven-plugin:3.15.0:create \
  -DprojectGroupId=com.redhat.coolstore \
  -DprojectArtifactId=monolith \
  -DprojectVersion=1.0.0-SNAPSHOT \
  -Dextensions="resteasy-reactive-jackson,hibernate-orm-panache,jdbc-postgresql,smallrye-reactive-messaging-amqp,oidc,flyway,micrometer-registry-prometheus"
```

**Manual pom.xml Updates**:
- Change parent to Quarkus BOM
- Update packaging from `war` to `jar`
- Add Quarkus extensions (see Step 1.2)
- Update Java version from 1.8 to 11+ (recommend 17)

**Files Modified**:
- `pom.xml`

---

### Step 1.2: Add Required Quarkus Extensions

**Action**: Configure all necessary Quarkus extensions in pom.xml

**Required Extensions**:

```xml
<dependencies>
  <!-- RESTEasy Reactive (JAX-RS) -->
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
  </dependency>
  
  <!-- Hibernate ORM with Panache (JPA) -->
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-hibernate-orm</artifactId>
  </dependency>
  
  <!-- PostgreSQL JDBC Driver -->
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-postgresql</artifactId>
  </dependency>
  
  <!-- Flyway Database Migration -->
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-flyway</artifactId>
  </dependency>
  
  <!-- SmallRye Reactive Messaging (JMS replacement) -->
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-reactive-messaging-amqp</artifactId>
  </dependency>
  <!-- OR for in-memory messaging during dev -->
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-reactive-messaging-in-memory</artifactId>
    <scope>test</scope>
  </dependency>
  
  <!-- OIDC (Keycloak) -->
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-oidc</artifactId>
  </dependency>
  
  <!-- CDI (included by default) -->
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-arc</artifactId>
  </dependency>
  
  <!-- Metrics -->
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
  </dependency>
  
  <!-- Health Checks -->
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-health</artifactId>
  </dependency>
  
  <!-- Testing -->
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit5</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

**Dependency Mapping**:
| Java EE API | Quarkus Extension |
|-------------|-------------------|
| JAX-RS 2.0 | quarkus-resteasy-reactive-jackson |
| JPA 2.1 | quarkus-hibernate-orm |
| CDI 1.2 | quarkus-arc (built-in) |
| JMS 2.0 | quarkus-smallrye-reactive-messaging-amqp |
| Bean Validation | quarkus-hibernate-validator |
| JSON-P/JSON-B | quarkus-resteasy-reactive-jackson |

**Files Modified**:
- `pom.xml`

---

### Step 1.3: Configure Quarkus Application Properties

**Action**: Create `application.properties` to replace Java EE XML configurations

**File**: `src/main/resources/application.properties`

```properties
# Application
quarkus.application.name=coolstore-monolith
quarkus.http.port=8080

# Datasource (replaces persistence.xml datasource JNDI)
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=postgresUser
quarkus.datasource.password=postgresPW
quarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5432/postgresDB

# Hibernate ORM (replaces persistence.xml properties)
quarkus.hibernate-orm.database.generation=none
quarkus.hibernate-orm.log.sql=false
quarkus.hibernate-orm.log.format-sql=true
quarkus.hibernate-orm.jdbc.statement-batch-size=20

# Flyway
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db

# OIDC (replaces keycloak.json)
quarkus.oidc.auth-server-url=http://127.0.0.1:8081/realms/eap
quarkus.oidc.client-id=coolstore
quarkus.oidc.credentials.secret=<your-client-secret>
quarkus.oidc.application-type=web-app

# AMQP/Messaging (replaces JMS topic configuration)
# Configure connector to Artemis or use in-memory for dev
amqp-host=localhost
amqp-port=5672
mp.messaging.outgoing.orders.connector=smallrye-amqp
mp.messaging.outgoing.orders.address=orders
mp.messaging.incoming.orders-in.connector=smallrye-amqp
mp.messaging.incoming.orders-in.address=orders

# Logging
quarkus.log.console.enable=true
quarkus.log.console.format=%d{HH:mm:ss} %-5p [%c{2.}] (%t) %s%e%n
quarkus.log.level=INFO

# Dev mode
%dev.quarkus.http.port=8080
%dev.quarkus.hibernate-orm.log.sql=true
```

**Files Created**:
- `src/main/resources/application.properties`

**Files to Remove**:
- `src/main/resources/META-INF/persistence.xml` (after migration)
- `src/main/webapp/WEB-INF/beans.xml` (CDI enabled by default)
- `src/main/webapp/WEB-INF/web.xml` (not needed)
- `src/main/webapp/keycloak.json` (replaced by properties)

---

## PHASE 2: Core Migration

### Step 2.1: Migrate Model/Entity Classes

**Action**: Update JPA entities to use Quarkus Hibernate ORM conventions

**Files to Update**:
1. `src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java`
2. `src/main/java/com/redhat/coolstore/model/InventoryEntity.java`
3. `src/main/java/com/redhat/coolstore/model/Order.java`
4. `src/main/java/com/redhat/coolstore/model/OrderItem.java`

**Changes Required**:
- Change `javax.persistence.*` imports to `jakarta.persistence.*`
- Review and potentially adopt Panache patterns (optional, recommended)
- Ensure `@Entity`, `@Table`, `@Id` annotations are correct
- Add `@NamedQuery` if needed for common queries

**Example - CatalogItemEntity.java**:
```java
// BEFORE (Java EE)
import javax.persistence.*;

@Entity
@Table(name = "CATALOG")
public class CatalogItemEntity implements Serializable {
    // ...
}

// AFTER (Quarkus)
import jakarta.persistence.*;

@Entity
@Table(name = "CATALOG")
public class CatalogItemEntity {  // Serializable not required
    // ... same fields and methods
}
```

**Optional Panache Migration** (Recommended for new code):
```java
import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
@Table(name = "CATALOG")
public class CatalogItemEntity extends PanacheEntity {
    // Remove @Id field if using PanacheEntity (provides id automatically)
    // Add custom queries as static methods
}
```

**Files Modified**:
- All entity classes in `src/main/java/com/redhat/coolstore/model/`

---

### Step 2.2: Update Persistence Layer

**Action**: Migrate EntityManager injection to Quarkus CDI

**Files to Update**:
1. `src/main/java/com/redhat/coolstore/persistence/Resources.java`

**Changes**:
```java
// BEFORE (Java EE)
package com.redhat.coolstore.persistence;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Dependent
public class Resources {
    @PersistenceContext
    private EntityManager em;

    @Produces
    public EntityManager getEntityManager() {
        return em;
    }
}

// AFTER (Quarkus)
package com.redhat.coolstore.persistence;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Dependent
public class Resources {
    @PersistenceContext
    private EntityManager em;

    @Produces
    public EntityManager getEntityManager() {
        return em;
    }
}
```

**Alternative (Direct Injection)**:
In Quarkus, you can directly inject EntityManager without producer:
```java
@ApplicationScoped
public class SomeService {
    @Inject
    EntityManager em;  // Direct injection works in Quarkus
}
```

**Decision**: Keep `Resources.java` for compatibility or remove and use direct injection.

**Files Modified**:
- `src/main/java/com/redhat/coolstore/persistence/Resources.java`

---

### Step 2.3: Migrate Service Layer - Stateless Services

**Action**: Convert `@Stateless` EJBs to `@ApplicationScoped` CDI beans

**Files to Update**:
1. `src/main/java/com/redhat/coolstore/service/OrderService.java`
2. `src/main/java/com/redhat/coolstore/service/CatalogService.java`
3. `src/main/java/com/redhat/coolstore/service/ProductService.java`
4. `src/main/java/com/redhat/coolstore/service/PromoService.java`

**Migration Pattern**:
```java
// BEFORE (Java EE)
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;

@Stateless
public class OrderService {
    @Inject
    private EntityManager em;
    
    public void save(Order order) {
        em.persist(order);
    }
}

// AFTER (Quarkus)
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class OrderService {
    @Inject
    EntityManager em;
    
    @Transactional  // Add transaction management
    public void save(Order order) {
        em.persist(order);
    }
}
```

**Key Changes**:
- Replace `@Stateless` with `@ApplicationScoped`
- Add `@Transactional` to methods that modify data
- Update imports from `javax.*` to `jakarta.*`
- Add `@Transactional` annotation to transactional methods

**Special Case - OrderService.java**:
- Uses audit library (`FileSystemAuditLogger`)
- `@PostConstruct` and `@PreDestroy` work the same
- Verify audit library compatibility or replace

**Files Modified**:
- `src/main/java/com/redhat/coolstore/service/OrderService.java`
- `src/main/java/com/redhat/coolstore/service/CatalogService.java`
- `src/main/java/com/redhat/coolstore/service/ProductService.java`
- `src/main/java/com/redhat/coolstore/service/PromoService.java`

---

### Step 2.4: Migrate Stateful EJB - ShoppingCartService

**Action**: Convert `@Stateful` EJB to session-scoped CDI bean

**File to Update**:
`src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

**Current Issues**:
1. Uses `@Stateful` for per-user shopping cart state
2. Has remote EJB lookup for `ShippingService`
3. Uses WildFly-specific JNDI

**Migration Strategy**:
```java
// BEFORE (Java EE)
import javax.ejb.Stateful;

@Stateful
public class ShoppingCartService {
    private ShoppingCart cart = new ShoppingCart();
    
    private static ShippingServiceRemote lookupShippingServiceRemote() {
        // WildFly JNDI lookup
        final Context context = new InitialContext(jndiProperties);
        return (ShippingServiceRemote) context.lookup("ejb:/ROOT/ShippingService!...");
    }
}

// AFTER (Quarkus)
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import java.io.Serializable;

@SessionScoped
public class ShoppingCartService implements Serializable {
    private ShoppingCart cart = new ShoppingCart();
    
    @Inject
    ShippingService shippingService;  // Direct CDI injection
    
    // Remove JNDI lookup method entirely
}
```

**Key Changes**:
1. Replace `@Stateful` with `@SessionScoped`
2. Implement `Serializable` (required for passivation)
3. Remove remote EJB lookup - use direct `@Inject`
4. Update `ShippingService` injection (remove Remote interface requirement)
5. Add `@Transactional` where needed

**Files Modified**:
- `src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`

**Files to Review**:
- `src/main/java/com/redhat/coolstore/service/ShippingService.java` (remove @Remote, @Stateless)
- `src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java` (remove or merge)

---

### Step 2.5: Migrate Remote EJB - ShippingService

**Action**: Convert remote EJB to local CDI bean

**Files to Update**:
1. `src/main/java/com/redhat/coolstore/service/ShippingService.java`
2. `src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java`

**Changes**:
```java
// BEFORE (Java EE)
// ShippingServiceRemote.java
import javax.ejb.Remote;
@Remote
public interface ShippingServiceRemote {
    double calculateShipping(ShoppingCart sc);
    double calculateShippingInsurance(ShoppingCart sc);
}

// ShippingService.java
import javax.ejb.Stateless;
import javax.ejb.Remote;

@Stateless
@Remote
public class ShippingService implements ShippingServiceRemote {
    // implementation
}

// AFTER (Quarkus)
// Delete ShippingServiceRemote.java OR merge into ShippingService

// ShippingService.java
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShippingService {
    public double calculateShipping(ShoppingCart sc) {
        // same implementation
    }
    
    public double calculateShippingInsurance(ShoppingCart sc) {
        // same implementation
    }
}
```

**Decision**: Remove the Remote interface since all calls are now local (no distribution).

**Files Modified**:
- `src/main/java/com/redhat/coolstore/service/ShippingService.java`

**Files to Delete**:
- `src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java`

---

### Step 2.6: Migrate REST Endpoints

**Action**: Update JAX-RS endpoints to use Jakarta EE namespaces and Quarkus patterns

**Files to Update**:
1. `src/main/java/com/redhat/coolstore/rest/RestApplication.java`
2. `src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`
3. `src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`
4. `src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

**RestApplication.java Changes**:
```java
// BEFORE (Java EE)
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/rest")
public class RestApplication extends Application {
}

// AFTER (Quarkus)
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/rest")
public class RestApplication extends Application {
}

// OR remove entirely and configure in application.properties:
# quarkus.resteasy-reactive.path=/rest
```

**CartEndpoint.java Changes**:
```java
// BEFORE (Java EE)
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.Serializable;

@SessionScoped
@Path("/cart")
public class CartEndpoint implements Serializable {
    @Inject
    private ShoppingCartService shoppingCartService;
    // ...
}

// AFTER (Quarkus)
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.io.Serializable;

@SessionScoped
@Path("/cart")
public class CartEndpoint implements Serializable {
    @Inject
    ShoppingCartService shoppingCartService;
    // ... same methods
}
```

**Note on SessionScoped REST endpoints**:
- Review if session state is needed or if stateless approach is better
- For clustering, ensure session replication is configured
- Consider using JWT tokens for stateless auth instead

**Files Modified**:
- `src/main/java/com/redhat/coolstore/rest/RestApplication.java`
- `src/main/java/com/redhat/coolstore/rest/CartEndpoint.java`
- `src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java`
- `src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java`

---

### Step 2.7: Update Utility Classes

**Action**: Migrate utility and producer classes

**Files to Update**:
1. `src/main/java/com/redhat/coolstore/utils/Producers.java`
2. `src/main/java/com/redhat/coolstore/utils/StartupListener.java`
3. `src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`
4. `src/main/java/com/redhat/coolstore/utils/Transformers.java`

**Producers.java**:
```java
// BEFORE
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import java.util.logging.Logger;

public class Producers {
    @Produces
    public Logger produceLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember()...);
    }
}

// AFTER
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.jboss.logging.Logger;

@ApplicationScoped
public class Producers {
    @Produces
    public Logger produceLog(InjectionPoint injectionPoint) {
        return Logger.getLogger(injectionPoint.getMember()...);
    }
}
```

**Note**: Quarkus recommends using JBoss Logging over java.util.logging.

**DataBaseMigrationStartup.java**:
```java
// BEFORE
import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;

@Singleton
@Startup
public class DataBaseMigrationStartup {
    @PostConstruct
    public void init() {
        // Flyway migration
    }
}

// AFTER - Flyway is configured in application.properties
// This class may not be needed if using:
# quarkus.flyway.migrate-at-start=true

// OR keep for custom logic:
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class DataBaseMigrationStartup {
    void onStart(@Observes StartupEvent ev) {
        // Custom startup logic
    }
}
```

**Files Modified**:
- `src/main/java/com/redhat/coolstore/utils/Producers.java`
- `src/main/java/com/redhat/coolstore/utils/StartupListener.java`
- `src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java`
- `src/main/java/com/redhat/coolstore/utils/Transformers.java` (update imports)

---

## PHASE 3: JMS & Messaging Migration

### Step 3.1: Remove WebLogic-Specific JMS Code

**Action**: Completely rewrite InventoryNotificationMDB to use reactive messaging

**File to Update**:
`src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`

**Current Problems**:
- Uses WebLogic JNDI (`weblogic.jndi.WLInitialContextFactory`)
- Manual topic subscription instead of MDB activation
- Not a true MDB (implements MessageListener but no @MessageDriven)

**Complete Rewrite**:
```java
// BEFORE (Java EE with WebLogic)
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;

public class InventoryNotificationMDB implements MessageListener {
    private final static String JNDI_FACTORY = "weblogic.jndi.WLInitialContextFactory";
    // ... manual JMS setup
    
    public void onMessage(Message rcvMessage) {
        // process message
    }
}

// AFTER (Quarkus with Reactive Messaging)
package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class InventoryNotificationService {
    
    private static final Logger LOG = Logger.getLogger(InventoryNotificationService.class);
    private static final int LOW_THRESHOLD = 50;

    @Inject
    CatalogService catalogService;

    @Incoming("orders-in")
    @Blocking
    public void processInventoryNotification(String orderJson) {
        LOG.info("Received message for inventory check");
        
        Order order = Transformers.jsonToOrder(orderJson);
        
        order.getItemList().forEach(orderItem -> {
            int oldQuantity = catalogService.getCatalogItemById(
                orderItem.getProductId()).getInventory().getQuantity();
            int newQuantity = oldQuantity - orderItem.getQuantity();
            
            if (newQuantity < LOW_THRESHOLD) {
                LOG.warnf("Inventory for item %s is below threshold (%d), contact supplier!",
                    orderItem.getProductId(), LOW_THRESHOLD);
            } else {
                orderItem.setQuantity(newQuantity);
            }
        });
    }
}
```

**Configuration** (application.properties):
```properties
# Configure incoming channel
mp.messaging.incoming.orders-in.connector=smallrye-amqp
mp.messaging.incoming.orders-in.address=orders
mp.messaging.incoming.orders-in.durable=true
```

**Files Modified**:
- `src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java` → Rename to `InventoryNotificationService.java`

**Files to Delete**:
- Remove all JNDI/WebLogic initialization code

---

### Step 3.2: Migrate OrderServiceMDB

**Action**: Convert MDB to reactive messaging consumer

**File to Update**:
`src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`

**Changes**:
```java
// BEFORE (Java EE)
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

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
        TextMessage msg = (TextMessage) rcvMessage;
        String orderStr = msg.getBody(String.class);
        Order order = Transformers.jsonToOrder(orderStr);
        orderService.save(order);
        // ...
    }
}

// AFTER (Quarkus)
package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OrderMessageProcessor {
    
    private static final Logger LOG = Logger.getLogger(OrderMessageProcessor.class);

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Incoming("orders-in")
    @Blocking
    @Transactional
    public void processOrder(String orderJson) {
        LOG.info("\nMessage received!");
        LOG.infof("Received order: %s", orderJson);
        
        Order order = Transformers.jsonToOrder(orderJson);
        LOG.infof("Order object is %s", order);
        
        orderService.save(order);
        
        order.getItemList().forEach(orderItem -> {
            catalogService.updateInventoryItems(
                orderItem.getProductId(), 
                orderItem.getQuantity());
        });
    }
}
```

**Files Modified**:
- `src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java` → Rename to `OrderMessageProcessor.java`

---

### Step 3.3: Migrate Message Producer

**Action**: Update ShoppingCartOrderProcessor to use reactive messaging

**File to Update**:
`src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

**Changes**:
```java
// BEFORE (Java EE)
import javax.annotation.Resource;
import javax.inject.Inject;
import javax.jms.*;

@ApplicationScoped
public class ShoppingCartOrderProcessor {
    @Inject
    @JMSConnectionFactory("java:jboss/DefaultJMSConnectionFactory")
    private JMSContext context;
    
    @Resource(lookup = "java:/topic/orders")
    private Topic ordersTopic;
    
    public void process(ShoppingCart cart) {
        String orderStr = Transformers.shoppingCartToJson(cart);
        context.createProducer().send(ordersTopic, orderStr);
    }
}

// AFTER (Quarkus)
package com.redhat.coolstore.service;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ShoppingCartOrderProcessor {
    
    private static final Logger LOG = Logger.getLogger(ShoppingCartOrderProcessor.class);

    @Inject
    @Channel("orders")
    Emitter<String> ordersEmitter;

    public void process(ShoppingCart cart) {
        String orderJson = Transformers.shoppingCartToJson(cart);
        LOG.infof("Sending order: %s", orderJson);
        ordersEmitter.send(orderJson);
    }
}
```

**Configuration** (application.properties):
```properties
# Configure outgoing channel
mp.messaging.outgoing.orders.connector=smallrye-amqp
mp.messaging.outgoing.orders.address=orders
```

**Files Modified**:
- `src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java`

---

### Step 3.4: Configure Messaging Infrastructure

**Action**: Set up AMQP broker configuration for Quarkus

**Options**:

**Option A: Use Apache Artemis (ActiveMQ successor)**
```properties
# application.properties
mp.messaging.connector.smallrye-amqp.host=localhost
mp.messaging.connector.smallrye-amqp.port=5672
mp.messaging.connector.smallrye-amqp.username=admin
mp.messaging.connector.smallrye-amqp.password=admin

# Outgoing
mp.messaging.outgoing.orders.connector=smallrye-amqp
mp.messaging.outgoing.orders.address=orders
mp.messaging.outgoing.orders.durable=true

# Incoming
mp.messaging.incoming.orders-in.connector=smallrye-amqp
mp.messaging.incoming.orders-in.address=orders
mp.messaging.incoming.orders-in.durable=true
mp.messaging.incoming.orders-in.broadcast=true
```

**Option B: Use In-Memory for Development**
```properties
# Dev profile
%dev.mp.messaging.outgoing.orders.connector=smallrye-in-memory
%dev.mp.messaging.incoming.orders-in.connector=smallrye-in-memory
%dev.mp.messaging.incoming.orders-in.source=orders
```

**Deployment Notes**:
- For production: Deploy Apache Artemis broker
- Update OpenShift/Kubernetes deployment to include Artemis service
- Configure connection pooling and failover

**Files Modified**:
- `src/main/resources/application.properties`

---

## PHASE 4: Testing & Validation

### Step 4.1: Update Test Dependencies

**Action**: Migrate from JUnit 4 to JUnit 5 and add Quarkus test support

**pom.xml Changes**:
```xml
<!-- Remove old test dependencies -->
<!-- REMOVE:
<dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>
    <version>4.13.2</version>
    <scope>test</scope>
</dependency>
-->

<!-- Add Quarkus test dependencies -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit5</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-test-h2</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-test-security</artifactId>
    <scope>test</scope>
</dependency>
```

**Files Modified**:
- `pom.xml`

---

### Step 4.2: Create Quarkus Integration Tests

**Action**: Create REST API integration tests

**New File**: `src/test/java/com/redhat/coolstore/rest/CartEndpointTest.java`

```java
package com.redhat.coolstore.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
public class CartEndpointTest {

    @Test
    public void testGetCart() {
        given()
            .when().get("/rest/cart/123")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("cartId", notNullValue());
    }

    @Test
    public void testAddItemToCart() {
        given()
            .when().post("/rest/cart/123/329299/1")
            .then()
                .statusCode(200)
                .body("shoppingCartItemList.size()", notNullValue());
    }
}
```

**Test Configuration**: `src/test/resources/application.properties`
```properties
# Test database
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:testdb
quarkus.hibernate-orm.database.generation=drop-and-create
quarkus.hibernate-orm.sql-load-script=import-test.sql

# Disable OIDC for tests
quarkus.oidc.enabled=false

# In-memory messaging for tests
mp.messaging.outgoing.orders.connector=smallrye-in-memory
mp.messaging.incoming.orders-in.connector=smallrye-in-memory
```

**Files Created**:
- `src/test/java/com/redhat/coolstore/rest/CartEndpointTest.java`
- `src/test/java/com/redhat/coolstore/rest/ProductEndpointTest.java`
- `src/test/resources/application.properties`
- `src/test/resources/import-test.sql`

---

### Step 4.3: Test Message Processing

**Action**: Create tests for reactive messaging

**New File**: `src/test/java/com/redhat/coolstore/service/OrderMessageProcessorTest.java`

```java
package com.redhat.coolstore.service;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class OrderMessageProcessorTest {

    @Inject
    @Any
    InMemoryConnector connector;

    @Test
    public void testOrderProcessing() {
        InMemorySink<String> orders = connector.sink("orders");
        
        // Test message sending and processing
        // ...
    }
}
```

**Files Created**:
- `src/test/java/com/redhat/coolstore/service/OrderMessageProcessorTest.java`

---

### Step 4.4: Manual Testing Plan

**Action**: Create manual test checklist

**Test Scenarios**:
1. ✅ Start application: `mvn quarkus:dev`
2. ✅ Access UI: http://localhost:8080
3. ✅ View products: GET /rest/products
4. ✅ Add item to cart: POST /rest/cart/{id}/{itemId}/{qty}
5. ✅ View cart: GET /rest/cart/{id}
6. ✅ Checkout: POST /rest/cart/checkout/{id}
7. ✅ Verify order persistence in database
8. ✅ Verify JMS message processing (check logs)
9. ✅ Verify inventory update after checkout
10. ✅ Login with Keycloak (if OIDC enabled)

**Files Created**:
- `TESTING.md` (test plan document)

---

## PHASE 5: Deployment & Optimization

### Step 5.1: Remove WebLogic-Specific Code

**Action**: Delete WebLogic application lifecycle listeners

**Files to Delete**:
- `src/main/java/weblogic/application/ApplicationLifecycleEvent.java`
- `src/main/java/weblogic/application/ApplicationLifecycleListener.java`
- `src/main/java/weblogic/i18n/logging/NonCatalogLogger.java`

**Replacement**: Use Quarkus lifecycle events if needed

```java
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class ApplicationLifecycle {
    
    void onStart(@Observes StartupEvent ev) {
        // Startup logic
    }

    void onStop(@Observes ShutdownEvent ev) {
        // Shutdown logic
    }
}
```

**Files to Delete**:
- `src/main/java/weblogic/` (entire directory)

---

### Step 5.2: Handle Audit Library Dependency

**Action**: Resolve system-scoped dependency

**Current Issue**:
```xml
<dependency>
    <groupId>com.enterprise</groupId>
    <artifactId>audit-logging-library</artifactId>
    <version>1.0.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/audit-logging-library-1.0.0.jar</systemPath>
</dependency>
```

**Options**:

**Option A: Install to local Maven repo**
```bash
mvn install:install-file \
  -Dfile=lib/audit-logging-library-1.0.0.jar \
  -DgroupId=com.enterprise \
  -DartifactId=audit-logging-library \
  -Dversion=1.0.0 \
  -Dpackaging=jar
```

**Option B: Use Jandex indexing for Quarkus**
```xml
<dependency>
    <groupId>com.enterprise</groupId>
    <artifactId>audit-logging-library</artifactId>
    <version>1.0.0</version>
</dependency>
```
Add to application.properties:
```properties
quarkus.index-dependency.audit.group-id=com.enterprise
quarkus.index-dependency.audit.artifact-id=audit-logging-library
```

**Option C: Replace with Quarkus logging/audit**
- Consider replacing with Quarkus Audit extension or custom implementation

**Files Modified**:
- `pom.xml`
- Potentially `src/main/java/com/redhat/coolstore/service/OrderService.java`

---

### Step 5.3: Configure Native Image (Optional)

**Action**: Prepare for GraalVM native compilation

**pom.xml Profile**:
```xml
<profiles>
    <profile>
        <id>native</id>
        <properties>
            <quarkus.package.type>native</quarkus.package.type>
        </properties>
    </profile>
</profiles>
```

**Native Hints** (if needed):
Create `src/main/resources/META-INF/native-image/reflect-config.json` for reflection configuration.

**Build Native**:
```bash
mvn package -Pnative
```

**Files Created**:
- Native image build configuration (if needed)

---

### Step 5.4: Configure Health Checks

**Action**: Add health check endpoints

**New File**: `src/main/java/com/redhat/coolstore/health/DatabaseHealthCheck.java`

```java
package com.redhat.coolstore.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    @Inject
    EntityManager em;

    @Override
    public HealthCheckResponse call() {
        try {
            em.createNativeQuery("SELECT 1").getSingleResult();
            return HealthCheckResponse.up("Database connection");
        } catch (Exception e) {
            return HealthCheckResponse.down("Database connection");
        }
    }
}
```

**Endpoints**:
- `/q/health/live` - Liveness probe
- `/q/health/ready` - Readiness probe

**Files Created**:
- `src/main/java/com/redhat/coolstore/health/DatabaseHealthCheck.java`
- `src/main/java/com/redhat/coolstore/health/MessagingHealthCheck.java`

---

### Step 5.5: Configure Metrics

**Action**: Enable Prometheus metrics

**Configuration** (application.properties):
```properties
# Metrics
quarkus.micrometer.enabled=true
quarkus.micrometer.registry-enabled-default=false
quarkus.micrometer.export.prometheus.enabled=true
quarkus.micrometer.export.prometheus.path=/q/metrics
```

**Custom Metrics** (optional):
```java
@Inject
MeterRegistry registry;

public void processOrder(Order order) {
    Counter counter = registry.counter("orders.processed");
    counter.increment();
    // ...
}
```

**Endpoints**:
- `/q/metrics` - Prometheus metrics

**Files Modified**:
- `src/main/resources/application.properties`

---

### Step 5.6: OpenShift/Kubernetes Deployment

**Action**: Create deployment manifests

**New File**: `src/main/kubernetes/deployment.yml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: coolstore-monolith
spec:
  replicas: 2
  selector:
    matchLabels:
      app: coolstore
  template:
    metadata:
      labels:
        app: coolstore
    spec:
      containers:
      - name: coolstore
        image: coolstore-monolith:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: QUARKUS_DATASOURCE_JDBC_URL
          value: jdbc:postgresql://postgresql:5432/coolstore
        - name: QUARKUS_DATASOURCE_USERNAME
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: username
        - name: QUARKUS_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: password
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8080
          initialDelaySeconds: 30
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 8080
          initialDelaySeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: coolstore
spec:
  type: LoadBalancer
  ports:
  - port: 8080
    targetPort: 8080
  selector:
    app: coolstore
```

**Quarkus Kubernetes Extension**:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-kubernetes</artifactId>
</dependency>
```

This auto-generates manifests from annotations/properties.

**Files Created**:
- `src/main/kubernetes/deployment.yml`
- `src/main/kubernetes/service.yml`

---

## Migration Checklist

### Pre-Migration
- [ ] Backup entire project
- [ ] Document current deployment architecture
- [ ] Set up Quarkus development environment
- [ ] Create migration git branch

### Phase 1: Foundation (Days 1-2)
- [ ] Create Quarkus project structure
- [ ] Add all required Quarkus extensions
- [ ] Configure application.properties
- [ ] Set up datasource configuration
- [ ] Configure Flyway migration
- [ ] Verify application starts in dev mode

### Phase 2: Core Migration (Days 3-5)
- [ ] Migrate all entity classes (javax → jakarta)
- [ ] Update persistence layer (Resources.java)
- [ ] Convert @Stateless EJBs to @ApplicationScoped
- [ ] Convert @Stateful EJB to @SessionScoped
- [ ] Remove remote EJB lookups
- [ ] Migrate REST endpoints
- [ ] Update utility classes
- [ ] Update Logger injection

### Phase 3: Messaging (Days 6-7)
- [ ] Rewrite InventoryNotificationMDB (remove WebLogic code)
- [ ] Convert OrderServiceMDB to reactive messaging
- [ ] Update ShoppingCartOrderProcessor to use Emitter
- [ ] Configure AMQP/messaging properties
- [ ] Set up Artemis broker (or in-memory for dev)
- [ ] Test message flow end-to-end

### Phase 4: Testing (Days 8-9)
- [ ] Update test dependencies (JUnit 4 → 5)
- [ ] Create QuarkusTest integration tests
- [ ] Test all REST endpoints
- [ ] Test message processing
- [ ] Test database operations
- [ ] Perform manual testing checklist
- [ ] Load testing (optional)

### Phase 5: Cleanup & Optimization (Days 10-11)
- [ ] Delete WebLogic-specific code
- [ ] Resolve audit library dependency
- [ ] Remove old configuration files (persistence.xml, beans.xml, web.xml)
- [ ] Add health checks
- [ ] Configure metrics
- [ ] Create deployment manifests
- [ ] Update README.md with Quarkus instructions

### Post-Migration
- [ ] Performance comparison testing
- [ ] Documentation updates
- [ ] Team training on Quarkus
- [ ] Production deployment plan
- [ ] Rollback plan

---

## Quarkus Extensions Required

| Extension | Artifact ID | Purpose |
|-----------|-------------|---------|
| RESTEasy Reactive | quarkus-resteasy-reactive-jackson | JAX-RS REST APIs |
| Hibernate ORM | quarkus-hibernate-orm | JPA persistence |
| PostgreSQL JDBC | quarkus-jdbc-postgresql | Database driver |
| Flyway | quarkus-flyway | Database migrations |
| Reactive Messaging AMQP | quarkus-smallrye-reactive-messaging-amqp | JMS replacement |
| OIDC | quarkus-oidc | Keycloak authentication |
| Arc | quarkus-arc | CDI (included by default) |
| Micrometer Prometheus | quarkus-micrometer-registry-prometheus | Metrics |
| SmallRye Health | quarkus-smallrye-health | Health checks |
| JUnit 5 | quarkus-junit5 | Testing |

---

## Package Import Migration Map

| Java EE (javax.*) | Jakarta EE (jakarta.*) | Notes |
|-------------------|------------------------|-------|
| javax.persistence.* | jakarta.persistence.* | JPA |
| javax.inject.* | jakarta.inject.* | CDI Inject |
| javax.enterprise.* | jakarta.enterprise.* | CDI Enterprise |
| javax.ws.rs.* | jakarta.ws.rs.* | JAX-RS |
| javax.ejb.* | N/A | Remove EJB, use CDI |
| javax.jms.* | N/A | Replace with Reactive Messaging |
| javax.annotation.* | jakarta.annotation.* | PostConstruct, PreDestroy |
| javax.transaction.* | jakarta.transaction.* | Transactions |

---

## Key Files Summary

### Files to Modify (30+ files)
1. **pom.xml** - Complete rewrite for Quarkus
2. **Model Classes (8 files)** - Update imports javax → jakarta
3. **Service Classes (11 files)** - EJB → CDI, add @Transactional
4. **REST Classes (4 files)** - Update imports
5. **Utility Classes (4 files)** - Update imports, logging
6. **MDB Classes (2 files)** - Rewrite as reactive messaging

### Files to Create
1. **src/main/resources/application.properties** - Main configuration
2. **Health checks** - DatabaseHealthCheck.java, MessagingHealthCheck.java
3. **Tests** - QuarkusTest classes
4. **Kubernetes manifests** - deployment.yml, service.yml

### Files to Delete
1. **src/main/resources/META-INF/persistence.xml**
2. **src/main/webapp/WEB-INF/beans.xml**
3. **src/main/webapp/WEB-INF/web.xml**
4. **src/main/webapp/keycloak.json**
5. **src/main/java/weblogic/** (entire directory)
6. **src/main/java/.../ShippingServiceRemote.java**

---

## Risk Assessment

### High Risk
1. **WebLogic JMS Code** - Complete rewrite required
2. **Stateful Session Beans** - Session management change
3. **Remote EJB Calls** - Architecture change
4. **Audit Library** - System dependency resolution

### Medium Risk
1. **Keycloak Integration** - Configuration migration
2. **Clustering/Distribution** - Different approach in Quarkus
3. **Transaction Management** - Explicit @Transactional needed

### Low Risk
1. **JPA Entities** - Straightforward import changes
2. **REST Endpoints** - Minimal changes
3. **Database Access** - Works the same way

---

## Testing Strategy

### Unit Tests
- Test service layer business logic
- Mock dependencies with Mockito
- Use H2 in-memory database

### Integration Tests
- Use @QuarkusTest annotation
- Test REST endpoints with RestAssured
- Test messaging with InMemoryConnector
- Test database with test datasource

### System Tests
- Full deployment with PostgreSQL
- Artemis broker for messaging
- Keycloak for authentication
- Load testing with realistic data

### Regression Tests
- Compare behavior with Java EE version
- Verify all features work identically
- Performance benchmarking

---

## Performance Expectations

### Startup Time
- **Java EE (JBoss EAP)**: 30-60 seconds
- **Quarkus JVM Mode**: 1-3 seconds
- **Quarkus Native**: < 0.1 seconds

### Memory Footprint
- **Java EE**: 500MB - 1GB+
- **Quarkus JVM**: 100-200MB
- **Quarkus Native**: 50-100MB

### Request Throughput
- Expected similar or better throughput
- Lower latency due to reactive stack
- Better resource utilization

---

## Rollback Plan

1. Keep Java EE version in separate branch
2. Maintain parallel deployments during transition
3. Use feature flags for gradual rollout
4. Database schema must remain compatible
5. JMS topics can be shared during migration

---

## Post-Migration Optimizations

### Phase 6 (Optional)
1. **Adopt Panache** - Simplify JPA repositories
2. **Native Compilation** - GraalVM native image
3. **Reactive Endpoints** - Convert to reactive REST
4. **GraphQL** - Add GraphQL API layer
5. **Service Mesh** - Istio integration
6. **Observability** - Distributed tracing with Jaeger

---

## Dependencies on External Systems

### PostgreSQL Database
- Same schema, no changes needed
- Connection properties in application.properties
- Flyway handles migrations

### Keycloak
- Update client configuration
- OIDC integration instead of adapter
- Token validation handled by Quarkus

### Message Broker
- Migrate from ActiveMQ/HornetQ to Artemis
- AMQP protocol for better compatibility
- Same topic names for compatibility

---

## Timeline Estimate

**Total Estimated Time**: 10-12 working days

- **Phase 1 (Foundation)**: 2 days
- **Phase 2 (Core Migration)**: 3 days
- **Phase 3 (Messaging)**: 2 days
- **Phase 4 (Testing)**: 2 days
- **Phase 5 (Deployment)**: 2 days
- **Contingency**: 1-2 days

---

## Success Criteria

1. ✅ All Java EE APIs replaced with Quarkus equivalents
2. ✅ Application starts successfully in Quarkus dev mode
3. ✅ All REST endpoints functional
4. ✅ JMS messaging replaced and working
5. ✅ Database persistence working correctly
6. ✅ Keycloak authentication working
7. ✅ All tests passing (unit + integration)
8. ✅ Health checks and metrics exposed
9. ✅ Deployable to Kubernetes/OpenShift
10. ✅ Performance equal or better than Java EE version

---

## Next Steps

1. **Review this plan** with development team
2. **Set up Quarkus development environment**
3. **Create migration git branch**
4. **Start with Phase 1** - Foundation setup
5. **Daily standups** to track progress
6. **Document issues** encountered during migration

---

## References

- [Quarkus Migration Guide](https://quarkus.io/guides/migration-guide)
- [SmallRye Reactive Messaging](https://smallrye.io/smallrye-reactive-messaging)
- [Jakarta EE Migration](https://jakarta.ee/resources/migration/)
- [Quarkus Hibernate ORM Guide](https://quarkus.io/guides/hibernate-orm)
- [Quarkus OIDC Guide](https://quarkus.io/guides/security-oidc-code-flow-authentication)

---

**Document Version**: 1.0  
**Created**: 2026-08-11  
**Project**: CoolStore Monolith Migration  
**Target**: Quarkus 3.x
