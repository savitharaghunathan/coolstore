package com.redhat.coolstore.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * JAX-RS application.
 *
 * TG3.5 base-path decision (TODO(port) resolved): KEEP @ApplicationPath("/services").
 * The AngularJS frontend (app/services/cart.js, app/services/catalog.js) calls
 * /services/cart and /services/products using absolute paths, matching the original
 * JBoss EAP deployment. Quarkus default routing would break the frontend contract.
 * Health and metrics remain on Quarkus management paths (/q/health/*, /q/metrics);
 * static resources are served from META-INF/resources at /.
 */
@ApplicationPath("/services")
public class RestApplication extends Application {

}
