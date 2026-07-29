package com.redhat.coolstore.utils;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.logging.Logger;

/**
 * Created by tqvarnst on 2017-04-04.
 * NOTE: Quarkus handles Flyway migration automatically via application.properties.
 * This class is kept for compatibility but is no longer strictly necessary.
 */
@ApplicationScoped
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    @PostConstruct
    private void startup() {
        // Quarkus automatically handles Flyway migration via application.properties
        // (quarkus.flyway.migrate-at-start=true)
        // This manual migration code is no longer needed with Quarkus
        logger.info("Database migration is handled automatically by Quarkus Flyway extension");
    }



}