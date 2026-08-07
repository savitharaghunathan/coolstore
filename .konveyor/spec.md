# Migration Spec

## Goal
Migrate coolstore-monolith from Java EE 7 on JBoss EAP 7.4 to Quarkus 3 with cloud-native architecture.

## Source → Target
- **Source**: Java EE 7, JBoss EAP 7.4, WAR packaging, Java 8
- **Target**: Quarkus 3.x, JAR packaging (fast-jar), Java 17

## Scope
- **Files affected**: 30 Java source files + 3 WebLogic stubs to delete + config files
- **Estimated complexity**: Medium-High
- **Hardest areas**:
  1. ShoppingCartService - @Stateful bean with remote EJB lookup via JNDI
  2. InventoryNotificationMDB - Manual JMS setup with WebLogic-specific JNDI
  3. OrderServiceMDB - Message-Driven Bean migration to Quarkus Artemis JMS

## Key Decisions Applied

### 1. Java Version
- **Decision**: Java 17 (LTS, recommended for Quarkus 3)
- **Impact**: High
- **Effort**: Low
- **Rationale**: Java 17 provides excellent Quarkus 3 support with modern features and enterprise adoption

### 2. EJB Migration Strategy
- **Decision**: Convert to CDI beans with Quarkus extensions
- **Impact**: High
- **Effort**: Medium
- **Rationale**: 
  - @Stateless/@Singleton → @ApplicationScoped
  - @Stateful → @SessionScoped
  - Use @Transactional for declarative transaction management
  - Aligns with Quarkus best practices without legacy EJB baggage

### 3. Messaging Migration
- **Decision**: Quarkus Artemis extension (JMS compatible)
- **Impact**: High
- **Effort**: Low
- **Rationale**: Maintains JMS API compatibility, minimal code changes for MDBs, supports clustering

### 4. Database Persistence
- **Decision**: Hibernate ORM with Quarkus + Flyway
- **Impact**: Medium
- **Effort**: Low
- **Rationale**: JPA entities and EntityManager work with minimal changes, Flyway continuity preserved

### 5. REST API Migration
- **Decision**: Quarkus RESTEasy Reactive
- **Impact**: Medium
- **Effort**: Low
- **Rationale**: Better performance, maintains JAX-RS compatibility (@Path, @GET, @POST annotations preserved)

### 6. WebLogic API Replacement
- **Decision**: Replace with Quarkus lifecycle events
- **Impact**: Medium
- **Effort**: Low
- **Rationale**: @Observes StartupEvent/ShutdownEvent directly replaces ApplicationLifecycleListener

### 7. Security Migration
- **Decision**: Use quarkus-oidc extension
- **Impact**: High
- **Effort**: Medium
- **Rationale**: Modern OpenID Connect integration with Keycloak, migrate keycloak.json to application.properties

### 8. Build Packaging
- **Decision**: JAR packaging (Quarkus fast-jar)
- **Impact**: High
- **Effort**: Low
- **Rationale**: Default Quarkus packaging with optimal startup time, eliminates WAR/app server deployment

### 9. System Dependencies
- **Decision**: Convert to standard Maven dependency
- **Impact**: Low
- **Effort**: Low
- **Rationale**: Install audit-logging-library to Maven repository, eliminate fragile systemPath

### 10. Testing Framework
- **Decision**: Migrate to @QuarkusTest with JUnit 5
- **Impact**: Medium
- **Effort**: Medium
- **Rationale**: Fast test execution with full CDI/persistence support, modern testing practices

### 11. Configuration Format
- **Decision**: application.properties
- **Impact**: Medium
- **Effort**: Low
- **Rationale**: Unified configuration, migrate datasource/JMS/security from XML/JSON to properties

### 12. Development Workflow
- **Decision**: Quarkus Dev Mode with live reload
- **Impact**: Medium
- **Effort**: Low
- **Rationale**: `mvn quarkus:dev` provides live reload, continuous testing, dev services for rapid iteration

## Approach

The migration follows a phased approach based on dependency order and risk:

### Phase 1: Project Setup & Configuration
- Create Quarkus project structure with Maven BOM
- Add required Quarkus extensions
- Migrate configuration from XML/JSON to application.properties
- Update pom.xml for Quarkus dependencies

### Phase 2: Model & Persistence Layer
- Migrate JPA entities (minimal changes, annotation compatibility)
- Update persistence.xml or migrate to Quarkus datasource config
- Migrate Flyway configuration
- Update Resources.java (EntityManager producer)

### Phase 3: Service Layer - Simple EJBs
- Convert @Stateless beans to @ApplicationScoped
- Convert @Singleton bean to @ApplicationScoped
- Add @Transactional annotations for transaction boundaries
- Remove EJB-specific transaction attributes

### Phase 4: Service Layer - Complex Components
- Migrate ShoppingCartService (@Stateful) to @SessionScoped
- Replace remote EJB lookup with CDI injection
- Convert OrderServiceMDB to Quarkus JMS consumer
- Refactor InventoryNotificationMDB to @MessageDriven with Artemis

### Phase 5: REST & API Layer
- Migrate JAX-RS endpoints to RESTEasy Reactive
- Update RestApplication with Quarkus-compatible configuration
- Verify JAX-RS annotations compatibility

### Phase 6: Utilities & Lifecycle
- Replace WebLogic lifecycle listeners with Quarkus events
- Update Producers.java for Quarkus CDI
- Migrate Transformers.java (likely no changes)
- Remove WebLogic stub classes

### Phase 7: Security & Testing
- Migrate Keycloak configuration to quarkus-oidc
- Update tests from JUnit 4 to JUnit 5
- Add @QuarkusTest annotations
- Update test dependencies

### Phase 8: Cleanup & Verification
- Delete WebLogic stub files
- Remove obsolete configuration files (web.xml, beans.xml if not needed)
- Verify build with `mvn clean package`
- Test in Dev Mode with `mvn quarkus:dev`

## Domain Skill
**None** - This plan is based on comprehensive project analysis using graphify and pattern detection. No domain-specific migration skill was found, so the approach is structured according to architectural layers and dependency order.

## Migration Phases

| Phase | Focus | Complexity | Files Affected |
|-------|-------|-----------|----------------|
| 1 | Project Setup | Medium | pom.xml, application.properties (new) |
| 2 | Model & Persistence | Low | 9 files (entities + persistence) |
| 3 | Service - Simple | Medium | 6 files (stateless/singleton EJBs) |
| 4 | Service - Complex | High | 4 files (stateful, MDBs) |
| 5 | REST API | Low | 4 files (endpoints) |
| 6 | Utils & Lifecycle | Medium | 4 files (lifecycle, producers) |
| 7 | Security & Testing | Medium | Config + test files |
| 8 | Cleanup | Low | Delete 3 WebLogic stubs |

## Required Quarkus Extensions

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-arc</artifactId> <!-- CDI -->
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
    <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-artemis-jms</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-oidc</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-narayana-jta</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit5</artifactId>
    <scope>test</scope>
</dependency>
```

## Migration Risks & Mitigations

| Risk | Severity | Mitigation |
|------|----------|------------|
| Clustering/HA features may require redesign | Medium | Evaluate cloud-native HA patterns, use Quarkus clustering extensions |
| Message-Driven Bean behavior differences | Low | Thorough testing of JMS message processing with Artemis |
| Transaction boundary changes from EJB to CDI | Medium | Explicit @Transactional annotations, integration testing |
| WebLogic-specific code replacement | Low | Well-defined lifecycle events in Quarkus, straightforward mapping |
| Remote EJB lookup in ShoppingCartService | High | Refactor to CDI injection, eliminate JNDI lookups |
| Session state management for @Stateful bean | Medium | Verify @SessionScoped behavior, may need session persistence config |

## Success Criteria

- ✅ Application builds successfully with `mvn clean package`
- ✅ All tests pass with JUnit 5 and @QuarkusTest
- ✅ Application runs in Dev Mode (`mvn quarkus:dev`)
- ✅ REST endpoints respond correctly
- ✅ JMS messaging works with Artemis
- ✅ Database operations function with Hibernate ORM
- ✅ Flyway migrations execute successfully
- ✅ Keycloak authentication works with quarkus-oidc
- ✅ No WebLogic dependencies remain
- ✅ Application packages as fast-jar format
