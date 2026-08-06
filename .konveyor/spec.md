# Migration Spec

## Goal
Migrate a Java EE 7 monolithic web application from JBoss EAP 7.4 to Quarkus 3.x

## Source → Target
- **Source**: Java EE 7 on JBoss EAP 7.4 (Java 8, WAR packaging)
- **Target**: Quarkus 3.x (Java 17, JAR packaging)

## Scope
- **Files affected**: ~35 Java source files + configuration files
- **Estimated complexity**: Medium
- **Hardest areas**:
  1. Message-Driven Bean migration to Quarkus Artemis messaging
  2. WebLogic stub class replacement with Quarkus lifecycle events
  3. Frontend conversion from JSP to static SPA

## Key Decisions Applied

### D001 - Packaging: JAR
Standard JAR packaging (Quarkus default) - replaces WAR packaging with embedded server.

### D002 - Messaging: quarkus-artemis-jms
Apache ActiveMQ Artemis JMS extension for direct JMS API compatibility, minimizing changes to existing MDB patterns.

### D003 - Persistence: quarkus-hibernate-orm
Standard Quarkus Hibernate ORM maintains compatibility with existing JPA entities and EntityManager usage.

### D004 - Database Migrations: keep-flyway
Continue using Flyway via quarkus-flyway extension to preserve existing migration scripts in db/ directory.

### D005 - REST: quarkus-resteasy-reactive
RESTEasy Reactive (modern, Quarkus 3 recommended) maintains JAX-RS API compatibility with minimal code changes.

### D006 - CDI: quarkus-arc
Quarkus ArC (built-in CDI) replaces Java EE CDI with optimized build-time dependency injection.

### D007 - Security: quarkus-oidc
Quarkus OIDC for Keycloak integration, replacing legacy Keycloak adapter with OpenID Connect.

### D008 - Configuration: application.properties
Migrate all Java EE configuration (JNDI, persistence.xml, etc.) to application.properties.

### D009 - Java Version: Java 17
Upgrade from Java 8 to Java 17 LTS (recommended for Quarkus 3).

### D010 - System Dependencies: keep-system-path
Maintain audit-logging-library-1.0.0.jar as system-scoped dependency in lib/.

### D011 - WebLogic Stubs: replace
Replace WebLogic ApplicationLifecycleListener with Quarkus lifecycle events (@Observes StartupEvent/ShutdownEvent).

### D012 - Frontend: static-spa
Convert JSP-based frontend to static HTML/JS SPA served as static resources.

### D013 - Testing: junit5-rest-assured
Migrate from JUnit 4 to JUnit 5 with REST-assured for Quarkus-native testing.

### D014 - Dev Services: enable-all
Enable Quarkus Dev Services for PostgreSQL and Artemis to simplify local development.

### D015 - Observability: health-metrics-basic
Add SmallRye Health and Micrometer for cloud-native health checks and metrics.

## Approach

### Phase 1: Build Configuration
- Migrate Maven POM from Java EE WAR to Quarkus JAR
- Add Quarkus BOM and essential extensions
- Update Java compiler to 17
- Remove Java EE dependencies

### Phase 2: Data Layer
- Migrate JPA entities (minimal changes)
- Replace @PersistenceContext with CDI @Inject EntityManager
- Migrate Flyway startup from @Singleton EJB to Quarkus lifecycle
- Configure datasource in application.properties

### Phase 3: Service Layer
- Migrate Message-Driven Beans to Quarkus JMS listeners
- Remove EJB annotations (@Stateless, @MessageDriven)
- Replace WebLogic lifecycle listener with Quarkus events
- Update CDI scopes where needed

### Phase 4: REST API Layer
- Migrate JAX-RS endpoints (minimal changes)
- Replace @SessionScoped with appropriate Quarkus scope
- Update RestApplication configuration

### Phase 5: Configuration & Integration
- Migrate persistence.xml to application.properties
- Configure JMS topics and connection factory
- Configure Keycloak OIDC settings
- Update logging configuration

### Phase 6: Frontend & Static Resources
- Convert JSP files to static HTML
- Move AngularJS app to static resources
- Remove JSP dependencies

### Phase 7: Testing & Verification
- Migrate tests to JUnit 5
- Add Quarkus test annotations
- Add health and metrics endpoints
- Verify build and startup

### Phase 8: Cleanup
- Remove WebLogic stub classes
- Remove obsolete Java EE descriptors
- Remove unused dependencies
- Final verification

## Domain Skill
No domain-specific migration skill found - using general Java EE to Quarkus migration patterns.

## Build Command
```bash
mvn clean quarkus:dev
```

## Success Criteria
- Application builds successfully with `mvn clean package`
- Application starts in Quarkus dev mode
- All REST endpoints respond correctly
- JMS messaging works with Artemis
- Database migrations execute via Flyway
- Health and metrics endpoints are accessible
- Tests pass with JUnit 5
