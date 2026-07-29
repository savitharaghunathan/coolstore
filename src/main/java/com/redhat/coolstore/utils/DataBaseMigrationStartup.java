package com.redhat.coolstore.utils;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Logger;

/**
 * Created by tqvarnst on 2017-04-04.
 * 
 * Note: In Quarkus, Flyway migration is handled automatically via application.properties
 * (quarkus.flyway.migrate-at-start=true). This class is kept for logging purposes only.
 */
@ApplicationScoped
@Startup
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    void onStart(@Observes StartupEvent ev) {
        logger.info("Database migration handled automatically by Quarkus Flyway extension");
    }
}