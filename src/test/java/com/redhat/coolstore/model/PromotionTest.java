package com.redhat.coolstore.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class PromotionTest {

    @Test
    public void testParameterizedConstructor() {
        Promotion promo = new Promotion("329299", 0.25);
        assertEquals("329299", promo.getItemId());
        assertEquals(0.25, promo.getPercentOff(), 0.001);
    }

    @Test
    public void testDefaultConstructorAndSetters() {
        Promotion promo = new Promotion();
        promo.setItemId("ABC");
        promo.setPercentOff(0.10);
        assertEquals("ABC", promo.getItemId());
        assertEquals(0.10, promo.getPercentOff(), 0.001);
    }
}
