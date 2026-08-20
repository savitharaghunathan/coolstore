package com.redhat.coolstore.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Product model class.
 *
 * CHANGES FROM ORIGINAL (Quarkus 3.x Migration):
 * - Replaced org.junit.Test with org.junit.jupiter.api.Test (JUnit 4 → JUnit 5)
 * - Replaced org.junit.Assert with org.junit.jupiter.api.Assertions static import (JUnit 4 → JUnit 5)
 * - This is a plain POJO unit test with no Arquillian, CDI, or persistence annotations
 * - All assertion methods retain compatible signatures in JUnit 5
 * - Test logic and behavior preserved unchanged
 *
 * Migration Pattern: Plain unit tests for POJO model classes use JUnit 5 imports only,
 * no @QuarkusTest annotation needed (only required for integration tests with Arquillian).
 * Consistent with migration-kit standards for model/entity unit tests.
 */
public class ProductTest {

    @Test
    public void testConstructionAndGetters() {
        Product p = new Product();
        p.setItemId("123");
        p.setName("Test Product");
        p.setDesc("A test product");
        p.setPrice(29.99);
        p.setLocation("Warehouse");
        p.setQuantity(10);
        p.setLink("http://example.com");

        assertEquals("123", p.getItemId());
        assertEquals("Test Product", p.getName());
        assertEquals("A test product", p.getDesc());
        assertEquals(29.99, p.getPrice(), 0.001);
        assertEquals("Warehouse", p.getLocation());
        assertEquals(10, p.getQuantity());
        assertEquals("http://example.com", p.getLink());
    }

    @Test
    public void testDefaultValues() {
        Product p = new Product();
        assertNull(p.getItemId());
        assertNull(p.getName());
        assertNull(p.getDesc());
        assertEquals(0.0, p.getPrice(), 0.001);
        assertNull(p.getLocation());
        assertEquals(0, p.getQuantity());
        assertNull(p.getLink());
    }
}
