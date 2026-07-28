package com.redhat.coolstore.utils;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.logging.Logger;

/**
 * Created by tqvarnst on 2017-04-04.
 * 
 * Note: Quarkus Flyway extension handles migration automatically via application.properties
 */
@ApplicationScoped
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    @PostConstruct
    private void startup() {
        logger.info("Database migration handled by Quarkus Flyway extension");
    }

}