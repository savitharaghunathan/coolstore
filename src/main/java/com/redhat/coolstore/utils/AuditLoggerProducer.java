package com.redhat.coolstore.utils;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enterprise.audit.logging.config.AuditConfiguration;
import com.enterprise.audit.logging.service.StreamableAuditLogger;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * CDI producer for audit-logging-library 2.x StreamableAuditLogger over TCP
 * (rule audit-logging-0003: replaces FileSystemAuditLogger).
 * TCP endpoint is externalized via MicroProfile Config (task 2.4):
 *   audit.logging.host (env AUDIT_LOGGING_HOST, default localhost)
 *   audit.logging.port (env AUDIT_LOGGING_PORT, default 9999)
 */
@ApplicationScoped
public class AuditLoggerProducer {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggerProducer.class);

    @Produces
    @ApplicationScoped
    @Unremovable
    @Named("streamableAuditLogger")
    public StreamableAuditLogger produce(
            @ConfigProperty(name = "audit.logging.host", defaultValue = "localhost") String host,
            @ConfigProperty(name = "audit.logging.port", defaultValue = "9999") int port) {
        Properties props = new Properties();
        props.setProperty("audit.stream.host", host);
        props.setProperty("audit.stream.port", String.valueOf(port));
        AuditConfiguration config = new AuditConfiguration();
        config.loadFromProperties(props);
        log.info("StreamableAuditLogger configured for TCP {}:{}", host, port);
        return new StreamableAuditLogger(config);
    }
}
