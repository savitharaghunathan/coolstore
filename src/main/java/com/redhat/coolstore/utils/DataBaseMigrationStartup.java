package com.redhat.coolstore.utils;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Created by tqvarnst on 2017-04-04.
 * Migrated from @Singleton @Startup (EJB) to @ApplicationScoped with Quarkus @StartupEvent listener
 */
@ApplicationScoped
public class DataBaseMigrationStartup {

    private static final Logger log = LoggerFactory.getLogger(DataBaseMigrationStartup.class);

    @Inject
    Flyway flyway;

    // Schema authority switch (TG 2.3/6.1): Flyway owns the schema in dev/prod;
    // under %test Hibernate drop-and-create owns it (running both double-creates).
    @Inject
    @ConfigProperty(name = "flyway.migrate.enabled", defaultValue = "true")
    boolean migrateEnabled;

    void startup(@Observes StartupEvent ev) throws FlywayException {
        if (log != null) {
            log.info("Initializing/migrating the database using FlyWay");
        }
        if (!migrateEnabled) {
            log.info("Flyway migration skipped (flyway.migrate.enabled=false)");
            return;
        }
        // mig-deploy fix: baselining is only valid on a database that has never
        // been tracked by Flyway. On redeploy against an already-migrated schema
        // (e.g. rolling out a new image over an existing PVC) baseline() throws
        // "Unable to baseline schema history table ... as it already contains
        // migrations" and crashes startup. Guard it: skip baseline when the
        // schema history is already initialized.
        try {
            flyway.baseline();
        } catch (FlywayException e) {
            log.info("Flyway schema history already initialized - skipping baseline ({}))",
                    e.getMessage());
        }
        // Start the db.migration
        flyway.migrate();
    }

}
