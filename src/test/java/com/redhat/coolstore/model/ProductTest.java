package com.redhat.coolstore.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;;

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
