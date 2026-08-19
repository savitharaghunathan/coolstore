package com.redhat.coolstore.utils;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StartupListener {

    private static final Logger log = LoggerFactory.getLogger(StartupListener.class);

    public void onStart(@Observes StartupEvent evt) {
        log.info("AppListener(postStart)");
    }

    public void onStop(@Observes ShutdownEvent evt) {
        log.info("AppListener(preStop)");
    }

}
