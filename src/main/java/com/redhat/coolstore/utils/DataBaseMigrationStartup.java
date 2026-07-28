package com.redhat.coolstore.utils;

import jakarta.inject.Inject;
import java.util.logging.Logger;

/**
 * Database migration is handled automatically by Quarkus Flyway extension
 * configured in application.properties with quarkus.flyway.migrate-at-start=true
 * 
 * This class is no longer needed but kept for reference.
 */
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    // Migration now handled by Quarkus Flyway extension automatically

}