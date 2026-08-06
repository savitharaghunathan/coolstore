# Implementation Plan: Java EE 7 to Quarkus 3 Migration

**Generated:** 2026-08-06T21:11:00Z  
**Target:** Quarkus 3.x  
**Source:** Java EE 7 (JBoss/WildFly)  
**Build Tool:** Maven 3.x  
**Java Version:** 8 → 17

---

## Migration Overview

This plan migrates the Coolstore e-commerce application from Java EE 7 to Quarkus 3. The migration follows a phased approach, with build verification after each phase.

### Key Transformations
- **Dependencies**: Replace Java EE with Quarkus BOM and extensions
- **EJB → CDI**: Convert stateless/stateful EJBs to CDI beans
- **JMS**: Migrate Message-Driven Beans to Quarkus Artemis JMS
- **Configuration**: Move from persistence.xml and JNDI to application.properties
- **Security**: Replace Keycloak adapter with Quarkus OIDC
- **Packaging**: WAR → JAR (fast-jar)
- **Java**: 1.8 → 17

---

## Phase 1: Project Setup and Dependencies

### Step 1: Update pom.xml - Set Quarkus BOM and properties
**Phase:** 1-setup  
**File:** `pom.xml`  
**Description:** Replace Java EE parent/dependencies with Quarkus BOM and set Java 17

**Transformation:**
1. Remove/replace Java EE dependencies
2. Add Quarkus BOM import (io.quarkus.platform:quarkus-bom:3.8.0)
3. Update maven-compiler-plugin to target Java 17
4. Change packaging from war to jar
5. Update properties: maven.compiler.source=17, maven.compiler.target=17

### Step 2: Add Quarkus Maven plugin
**Phase:** 1-setup  
**File:** `pom.xml`  
**Description:** Add quarkus-maven-plugin for building Quarkus applications

**Transformation:**
Add plugin configuration for io.quarkus.platform:quarkus-maven-plugin:3.8.0 in build/plugins section

### Step 3: Add Quarkus extensions for JAX-RS
**Phase:** 1-setup  
**File:** `pom.xml`  
**Description:** Add quarkus-resteasy-reactive-jackson for REST endpoints

**Transformation:**
Add dependencies:
- io.quarkus:quarkus-resteasy-reactive
- io.quarkus:quarkus-resteasy-reactive-jackson

### Step 4: Add Quarkus extensions for JPA and datasource
**Phase:** 1-setup  
**File:** `pom.xml`  
**Description:** Add Hibernate ORM with Panache and JDBC driver

**Transformation:**
Add dependencies:
- io.quarkus:quarkus-hibernate-orm
- io.quarkus:quarkus-jdbc-h2 (or appropriate driver)
- io.quarkus:quarkus-agroal (connection pooling)

### Step 5: Add Quarkus extensions for CDI
**Phase:** 1-setup  
**File:** `pom.xml`  
**Description:** Add Arc CDI extension (included in most extensions, but explicit is good)

**Transformation:**
Add dependency: io.quarkus:quarkus-arc

### Step 6: Add Quarkus Artemis JMS extension
**Phase:** 1-setup  
**File:** `pom.xml`  
**Description:** Add JMS support via Artemis

**Transformation:**
Add dependency: io.quarkus:quarkus-artemis-jms

### Step 7: Add Quarkus Flyway extension
**Phase:** 1-setup  
**File:** `pom.xml`  
**Description:** Migrate Flyway to Quarkus extension

**Transformation:**
Replace org.flywaydb:flyway-core:4.1.2 with io.quarkus:quarkus-flyway

### Step 8: Add Quarkus OIDC extension for Keycloak
**Phase:** 1-setup  
**File:** `pom.xml`  
**Description:** Replace Keycloak adapter with Quarkus OIDC

**Transformation:**
Add dependency: io.quarkus:quarkus-oidc

### Step 9: Handle system-scoped audit library
**Phase:** 1-setup  
**File:** `pom.xml`  
**Description:** Convert system-scoped dependency to proper Maven dependency

**Transformation:**
1. Remove system scope from com.redhat:audit-logging-library
2. Change version from 1.0.0 to 2.0.0 if lib/audit-logging-library-2.0.0.jar exists
3. Install JAR to local repo via maven-install-plugin or manual install command

---

## Phase 2: Configuration Migration

### Step 10: Create application.properties - Basic configuration
**Phase:** 2-configuration  
**File:** `src/main/resources/application.properties`  
**Description:** Create Quarkus configuration file with basic settings

**Transformation:**
Create new file with:
```properties
# Application name
quarkus.application.name=coolstore

# HTTP configuration
quarkus.http.port=8080
```

### Step 11: Configure datasource in application.properties
**Phase:** 2-configuration  
**File:** `src/main/resources/application.properties`  
**Description:** Migrate JNDI datasource (java:jboss/datasources/CoolstoreDS) to Quarkus config

**Transformation:**
Add datasource properties:
```properties
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:coolstore
quarkus.datasource.jdbc.driver=org.h2.Driver
quarkus.datasource.username=sa
quarkus.datasource.password=
```

### Step 12: Configure Hibernate ORM in application.properties
**Phase:** 2-configuration  
**File:** `src/main/resources/application.properties`  
**Description:** Migrate persistence.xml settings to application.properties

**Transformation:**
Add Hibernate properties:
```properties
quarkus.hibernate-orm.database.generation=none
quarkus.hibernate-orm.log.sql=true
quarkus.hibernate-orm.dialect=org.hibernate.dialect.H2Dialect
```

### Step 13: Configure Flyway in application.properties
**Phase:** 2-configuration  
**File:** `src/main/resources/application.properties`  
**Description:** Enable Flyway migrations

**Transformation:**
Add Flyway properties:
```properties
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration
```

### Step 14: Configure Artemis JMS in application.properties
**Phase:** 2-configuration  
**File:** `src/main/resources/application.properties`  
**Description:** Configure JMS connection and topics

**Transformation:**
Add Artemis properties:
```properties
quarkus.artemis.url=tcp://localhost:61616
quarkus.artemis.username=admin
quarkus.artemis.password=admin
```

### Step 15: Migrate Keycloak configuration to OIDC
**Phase:** 2-configuration  
**File:** `src/main/resources/application.properties`  
**Description:** Convert keycloak.json to quarkus.oidc.* properties

**Transformation:**
Read src/main/webapp/keycloak.json and map to:
```properties
quarkus.oidc.auth-server-url=<from keycloak.json>
quarkus.oidc.client-id=<from keycloak.json>
quarkus.oidc.credentials.secret=<from keycloak.json>
quarkus.oidc.application-type=web-app
```

### Step 16: Configure REST path in application.properties
**Phase:** 2-configuration  
**File:** `src/main/resources/application.properties`  
**Description:** Set REST endpoint base path (from @ApplicationPath)

**Transformation:**
Add property:
```properties
quarkus.resteasy-reactive.path=/services
```

### Step 17: Remove persistence.xml
**Phase:** 2-configuration  
**File:** `src/main/resources/META-INF/persistence.xml`  
**Description:** Delete persistence.xml as configuration moved to application.properties

**Transformation:**
Delete file (configuration migrated to application.properties)

### Step 18: Remove or update beans.xml
**Phase:** 2-configuration  
**File:** `src/main/webapp/WEB-INF/beans.xml`  
**Description:** Quarkus uses CDI 4.0 - beans.xml optional for basic usage

**Transformation:**
If beans.xml only contains basic config, delete it. If it has specific interceptors/alternatives, move to src/main/resources/META-INF/beans.xml with bean-discovery-mode="all"

### Step 19: Remove web.xml
**Phase:** 2-configuration  
**File:** `src/main/webapp/WEB-INF/web.xml`  
**Description:** Quarkus doesn't use web.xml

**Transformation:**
Delete file (JAX-RS configuration moved to application.properties, security to OIDC)

---

## Phase 3: Code Migration - EJB to CDI

### Step 20: Convert CatalogService from Stateless EJB to CDI
**Phase:** 3-ejb-to-cdi  
**File:** `src/main/java/com/redhat/coolstore/service/CatalogService.java`  
**Description:** Replace @Stateless with @ApplicationScoped

**Transformation:**
1. Remove: import javax.ejb.Stateless
2. Add: import jakarta.enterprise.context.ApplicationScoped
3. Replace: @Stateless → @ApplicationScoped
4. Keep @Inject and other CDI annotations (update javax.inject → jakarta.inject)

### Step 21: Convert ShoppingCartService from Stateful EJB to CDI
**Phase:** 3-ejb-to-cdi  
**File:** `src/main/java/com/redhat/coolstore/service/ShoppingCartService.java`  
**Description:** Replace @Stateful with @SessionScoped or @ApplicationScoped

**Transformation:**
1. Remove: import javax.ejb.Stateful
2. Add: import jakarta.enterprise.context.ApplicationScoped (or SessionScoped if stateful behavior needed)
3. Replace: @Stateful → @ApplicationScoped
4. Update imports: javax.inject → jakarta.inject
5. If session state is needed, consider using @SessionScoped with appropriate scope configuration

### Step 22: Convert OrderService from Stateless EJB to CDI
**Phase:** 3-ejb-to-cdi  
**File:** `src/main/java/com/redhat/coolstore/service/OrderService.java`  
**Description:** Replace @Stateless with @ApplicationScoped

**Transformation:**
1. Remove: import javax.ejb.Stateless
2. Add: import jakarta.enterprise.context.ApplicationScoped
3. Replace: @Stateless → @ApplicationScoped
4. Update imports: javax.* → jakarta.*

### Step 23: Convert InventoryService from Stateless EJB to CDI
**Phase:** 3-ejb-to-cdi  
**File:** `src/main/java/com/redhat/coolstore/service/InventoryService.java`  
**Description:** Replace @Stateless with @ApplicationScoped

**Transformation:**
1. Remove: import javax.ejb.Stateless
2. Add: import jakarta.enterprise.context.ApplicationScoped
3. Replace: @Stateless → @ApplicationScoped
4. Update imports: javax.* → jakarta.*

---

## Phase 4: Code Migration - JMS Message-Driven Beans

### Step 24: Convert InventoryNotificationMDB to Quarkus JMS
**Phase:** 4-jms-migration  
**File:** `src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java`  
**Description:** Replace @MessageDriven with CDI bean and JMS listener

**Transformation:**
1. Remove: @MessageDriven and activationConfig
2. Add: @ApplicationScoped
3. Replace: implements MessageListener → keep onMessage method
4. Add: @JMSListener annotation or use io.quarkus.artemis.jms.runtime.JMSConsumer
5. Configure destination via annotations or application.properties
6. Update imports: javax.jms.* → jakarta.jms.*

### Step 25: Convert OrderServiceMDB to Quarkus JMS
**Phase:** 4-jms-migration  
**File:** `src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java`  
**Description:** Replace @MessageDriven with CDI bean and JMS listener

**Transformation:**
1. Remove: @MessageDriven and activationConfig
2. Add: @ApplicationScoped
3. Keep onMessage method structure
4. Add JMS consumer configuration via Quarkus Artemis
5. Update imports: javax.jms.* → jakarta.jms.*

---

## Phase 5: Code Migration - JAX-RS and Entities

### Step 26: Update REST endpoint imports
**Phase:** 5-jaxrs-entities  
**File:** `src/main/java/com/redhat/coolstore/rest/*.java`  
**Description:** Update JAX-RS imports from javax to jakarta

**Transformation:**
Update all REST endpoint files:
- javax.ws.rs.* → jakarta.ws.rs.*
- Keep @Path, @GET, @POST, @DELETE, @Produces, @Consumes unchanged
- Update javax.inject.Inject → jakarta.inject.Inject

### Step 27: Update JPA entity imports
**Phase:** 5-jaxrs-entities  
**File:** `src/main/java/com/redhat/coolstore/model/*.java`  
**Description:** Update JPA imports from javax to jakarta

**Transformation:**
Update all entity files:
- javax.persistence.* → jakarta.persistence.*
- Keep @Entity, @Table, @Id, @GeneratedValue, @Column, etc. unchanged

### Step 28: Remove or update RestApplication
**Phase:** 5-jaxrs-entities  
**File:** `src/main/java/com/redhat/coolstore/rest/RestApplication.java`  
**Description:** Remove JAX-RS Application class (handled by application.properties)

**Transformation:**
Delete file - @ApplicationPath value already migrated to quarkus.resteasy-reactive.path

---

## Phase 6: Transaction and Context Management

### Step 29: Update transaction annotations
**Phase:** 6-transactions  
**File:** `src/main/java/com/redhat/coolstore/service/*.java`  
**Description:** Update transaction imports to Jakarta

**Transformation:**
- javax.transaction.Transactional → jakarta.transaction.Transactional
- Keep @Transactional usage unchanged - Quarkus supports JTA

### Step 30: Update CDI context annotations
**Phase:** 6-transactions  
**File:** All Java files using CDI  
**Description:** Update remaining javax CDI imports to jakarta

**Transformation:**
- javax.enterprise.context.* → jakarta.enterprise.context.*
- javax.inject.* → jakarta.inject.*
- javax.enterprise.event.* → jakarta.enterprise.event.*

---

## Verification

### Build Command
```bash
mvn clean package -DskipTests
```

### Smoke Command
```bash
java -jar target/quarkus-app/quarkus-run.jar &
sleep 5
curl http://localhost:8080/services/products
kill %1
```

### Test Command
```bash
mvn test
```

---

## Success Criteria

1. ✅ Build completes without errors
2. ✅ Application starts successfully (smoke test)
3. ✅ REST endpoints respond
4. ✅ Database connection established
5. ✅ Flyway migrations execute
6. ⚠️ Tests pass (document failures, don't fix)

---

## Rollback Plan

Each step is committed individually. If build gate fails after 3 fix attempts:
- Identify the failing commit
- Revert: `git revert <commit-hash>`
- Mark remaining steps as skipped
- Document failure in execute.json

---

## Notes

- System-scoped JAR: May need manual install before build
- Keycloak: Requires running Keycloak instance for full functionality
- JMS: Requires Artemis broker for messaging features
- Native compilation: Not included in this migration (can be added later)
