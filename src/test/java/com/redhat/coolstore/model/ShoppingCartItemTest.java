package com.redhat.coolstore.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShoppingCartItemTest {

    @Test
    public void testConstructionAndGetters() {
        Product p = new Product();
        p.setItemId("ABC");
        p.setPrice(19.99);

        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(p);
        sci.setQuantity(3);
        sci.setPrice(19.99);
        sci.setPromoSavings(-2.00);

        assertEquals(3, sci.getQuantity());
        assertEquals(19.99, sci.getPrice(), 0.001);
        assertEquals(-2.00, sci.getPromoSavings(), 0.001);
        assertSame(p, sci.getProduct());
    }

    @Test
    public void testToOrderItem() {
        Product p = new Product();
        p.setItemId("ITEM-1");

        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(p);
        sci.setQuantity(5);

        OrderItem oi = sci.toOrderItem();
        assertEquals("ITEM-1", oi.getProductId());
        assertEquals(5, oi.getQuantity());
    }

    @Test
    public void testDefaultValues() {
        ShoppingCartItem sci = new ShoppingCartItem();
        assertEquals(0.0, sci.getPrice(), 0.001);
        assertEquals(0, sci.getQuantity());
        assertEquals(0.0, sci.getPromoSavings(), 0.001);
        assertNull(sci.getProduct());
    }
}
