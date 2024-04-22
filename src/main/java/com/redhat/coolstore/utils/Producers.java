package com.redhat.coolstore.utils;

import jakarta.enterprise.inject.InjectionPoint;
import jakarta.enterprise.inject.spi.CDI;
import java.util.logging.Logger;

public class Producers {

    private Logger log = CDI.current().select(Logger.class).get();

    @SuppressWarnings("unused")
    public Logger produceLog(InjectionPoint injectionPoint) {
        return CDI.current().select(Logger.class, injectionPoint.getMember().getDeclaringClass()).get();
    }

}
