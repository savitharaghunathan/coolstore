package com.redhat.coolstore.model;

// MIGRATION: JUnit 4 → JUnit 5
// Changed imports from org.junit.Test and org.junit.Assert to org.junit.jupiter.api
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * POJO unit test for Promotion model.
 *
 * MIGRATION NOTE: @QuarkusTest annotation NOT used - this is a simple POJO test
 * with no runtime dependencies (no CDI, JPA, EJB, or injections).
 * @QuarkusTest is required only for tests that need Quarkus container context.
 *
 * Per Rule 8 (Testing): Arquillian dependencies have been removed.
 * This test uses standard JUnit 5 for pure unit testing.
 */
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
