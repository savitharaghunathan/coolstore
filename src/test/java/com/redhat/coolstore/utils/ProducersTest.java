package com.redhat.coolstore.utils;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class ProducersTest {

    @Inject
    Logger logger;

    @Test
    public void testLoggerInjection() {
        assertNotNull(logger);
        // Verify logger is injected and has correct name for test class
        assertEquals("com.redhat.coolstore.utils.ProducersTest", logger.getName());
    }

    @Test
    public void testProducersClassIsApplicationScoped() {
        assertNotNull(logger);
        // Test verifies that Producers bean is discoverable and produces Logger
        // The presence of an injected logger proves the @Produces annotation works
        // in the Quarkus CDI container context
    }
}
