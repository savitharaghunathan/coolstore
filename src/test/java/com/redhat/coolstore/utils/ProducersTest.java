package com.redhat.coolstore.utils;

import org.junit.jupiter.api.Test;

import javax.enterprise.inject.spi.InjectionPoint;
import java.lang.reflect.Member;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ProducersTest {

    @Test
    public void testProduceLogReturnsLoggerWithCorrectName() {
        Producers producers = new Producers();

        InjectionPoint ip = mock(InjectionPoint.class);
        Member member = mock(Member.class);
        when(ip.getMember()).thenReturn(member);
        when(member.getDeclaringClass()).thenReturn((Class) String.class);

        Logger logger = producers.produceLog(ip);

        assertNotNull(logger);
        assertEquals("java.lang.String", logger.getName());
    }

    @Test
    public void testProduceLogDifferentClass() {
        Producers producers = new Producers();

        InjectionPoint ip = mock(InjectionPoint.class);
        Member member = mock(Member.class);
        when(ip.getMember()).thenReturn(member);
        when(member.getDeclaringClass()).thenReturn((Class) Integer.class);

        Logger logger = producers.produceLog(ip);

        assertEquals("java.lang.Integer", logger.getName());
    }
}
