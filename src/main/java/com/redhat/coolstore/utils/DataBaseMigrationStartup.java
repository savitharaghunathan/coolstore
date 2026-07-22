package com.redhat.coolstore.utils;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Logger;

/**
 * Created by tqvarnst on 2017-04-04.
 * 
 * NOTE: Flyway migration is now configured via application.properties
 * with quarkus.flyway.migrate-at-start=true
 * This class is kept for any custom startup logic if needed
 */
@ApplicationScoped
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    void onStart(@Observes StartupEvent event) {
        logger.info("Application started - Flyway migration handled by Quarkus");
    }

}