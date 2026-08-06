package com.redhat.coolstore.utils;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.runtime.Startup;
import javax.sql.DataSource;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by tqvarnst on 2017-04-04.
 * 
 * Note: This class is no longer needed in Quarkus as Flyway migration
 * is handled automatically via application.properties configuration:
 * quarkus.flyway.migrate-at-start=true
 * 
 * Keeping for reference but marking as deprecated.
 */
@Deprecated
@ApplicationScoped
@Startup
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    @Inject
    DataSource dataSource;

    // Note: Flyway migration now handled by Quarkus Flyway extension
    // This startup class is no longer needed but kept for compatibility
}