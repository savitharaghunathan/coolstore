package com.redhat.coolstore.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * JAX-RS application bootstrap.
 *
 * [Decision — migration plan TG1.4/TG3.3, resolved]:
 * The base path stays pinned to {@code @ApplicationPath("/services")}, so every
 * resource class path resolves under {@code /services/rest/*} (e.g.
 * {@code /services/rest/products}, {@code /services/rest/cart}, {@code /services/rest/orders}).
 *
 * Rationale:
 * - The vendored frontend (coolstore.json + AngularJS services) already calls
 *   {@code /services/rest/*}; changing the base path would require frontend rework.
 * - Keeping the path in code (instead of migrating to {@code quarkus.rest.path}
 *   in application.properties) leaves a single source of truth and no config drift
 *   between dev/test/prod profiles.
 * - The class is intentionally kept rather than deleted for the same reason:
 *   it is the only place the "/services" prefix is declared.
 *
 * REST layer: {@code quarkus-resteasy-reactive-jackson} (verified in pom.xml).
 * The base path is covered by @QuarkusTest characterization tests asserting
 * {@code /services/rest/products} responds 200 (see ProductEndpointTest).
 */
@ApplicationPath("/services")
public class RestApplication extends Application {

}
