package com.redhat.coolstore.persistence;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import java.util.logging.Logger;

@Dependent
public class Resources {

    @Produces
    public Logger getLogger(jakarta.enterprise.inject.spi.InjectionPoint ip) {
        return Logger.getLogger(ip.getMember().getDeclaringClass().getName());
    }
}
