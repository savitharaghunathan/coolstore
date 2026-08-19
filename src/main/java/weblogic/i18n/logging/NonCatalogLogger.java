package weblogic.i18n.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NonCatalogLogger {

    private static final Logger log;

    public NonCatalogLogger() {
        log = LoggerFactory.getLogger(NonCatalogLogger.class);
    }

    public NonCatalogLogger(String logName) {
        log = LoggerFactory.getLogger(logName);
    }

    public void info(String msg) {
        log.info(msg);
    }
}
