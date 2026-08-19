package com.redhat.coolstore.utils;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.sql.DataSource;

/**
 * Created by tqvarnst on 2017-04-04.
 * Migrated from @Singleton @Startup (EJB) to @ApplicationScoped with Quarkus @StartupEvent listener
 */
@ApplicationScoped
public class DataBaseMigrationStartup {

    private static final Logger logger = LoggerFactory.getLogger(DataBaseMigrationStartup.class);

    @Inject
    Flyway flyway;

    @Resource(lookup = "java:jboss/datasources/CoolstoreDS")
    DataSource dataSource;

    void startup(@Observes StartupEvent ev) {
        try {
            logger.info("Initializing/migrating the database using FlyWay");
            flyway.baseline();
            // Start the db.migration
            flyway.migrate();
        } catch (FlywayException e) {
            logger.error("FAILED TO INITIALIZE THE DATABASE: {}", e.getMessage(), e);
        }
    }

}
