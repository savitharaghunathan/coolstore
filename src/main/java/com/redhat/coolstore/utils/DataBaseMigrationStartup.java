package com.redhat.coolstore.utils;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Logger;

/**
 * Database migration is now handled by Quarkus Flyway extension
 * configured in application.properties with quarkus.flyway.migrate-at-start=true
 * This class is kept for potential custom startup logic
 */
@ApplicationScoped
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    void onStart(@Observes StartupEvent ev) {
        logger.info("Database migration is handled by Quarkus Flyway extension");
    }
}