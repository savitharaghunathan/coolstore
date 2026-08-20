package com.redhat.coolstore.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

// NOTE: File already uses jakarta.* imports (no javax→jakarta conversion needed)
// TODO(port): [ARCH] Evaluate if this empty Application class is needed in Quarkus
// (JAX-RS auto-discovers resources without explicit Application subclass)
// TODO(port): [CONFIG] Evaluate migrating @ApplicationPath("/services") to
// quarkus.rest.path in application.properties per RULEBOOK §7
// TODO(port): [POM] Ensure quarkus-resteasy-reactive or quarkus-rest dependency
// is configured in pom.xml for JAX-RS support
// TODO(port): [TEST] Verify REST endpoint base path in tests if path configuration
// moves to application.properties
@ApplicationPath("/services")
public class RestApplication extends Application {

}
