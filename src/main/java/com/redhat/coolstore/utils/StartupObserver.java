package com.redhat.coolstore.utils;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.logging.Logger;

@ApplicationScoped
public class StartupObserver {

    private static final Logger log = Logger.getLogger(StartupObserver.class.getName());

    void onStart(@Observes StartupEvent ev) {
        log.info("Application started");
    }

    void onStop(@Observes ShutdownEvent ev) {
        log.info("Application stopping");
    }
}
