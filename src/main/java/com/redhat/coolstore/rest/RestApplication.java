package com.redhat.coolstore.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * JAX-RS application. TODO(port) markers burned down (task 1.5) with these decisions:
 * - KEEP @ApplicationPath("/services"): the AngularJS SPA (app/services/*.js) calls
 *   /services/... paths; migrating to quarkus.rest.path would be churn with no gain.
 * - KEEP the Application subclass as an explicit, stable base-path declaration;
 *   quarkus-resteasy-reactive-jackson is already configured in pom.xml.
 */
@ApplicationPath("/services")
public class RestApplication extends Application {

}
