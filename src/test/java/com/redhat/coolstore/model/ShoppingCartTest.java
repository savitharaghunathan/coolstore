package com.redhat.coolstore.model;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShoppingCartTest {

    private ShoppingCart cart;

    @Before
    public void setUp() {
        cart = new ShoppingCart();
    }

    @Test
    public void testAddItem() {
        ShoppingCartItem item = new ShoppingCartItem();
        Product p = new Product();
        p.setItemId("123");
        item.setProduct(p);
        item.setQuantity(2);

        cart.addShoppingCartItem(item);
        assertEquals(1, cart.getShoppingCartItemList().size());
        assertSame(item, cart.getShoppingCartItemList().get(0));
    }

    @Test
    public void testAddNullItemIgnored() {
        cart.addShoppingCartItem(null);
        assertEquals(0, cart.getShoppingCartItemList().size());
    }

    @Test
    public void testRemoveItem() {
        ShoppingCartItem item = new ShoppingCartItem();
        cart.addShoppingCartItem(item);
        assertTrue(cart.removeShoppingCartItem(item));
        assertEquals(0, cart.getShoppingCartItemList().size());
    }

    @Test
    public void testRemoveNullReturnsFalse() {
        assertFalse(cart.removeShoppingCartItem(null));
    }

    @Test
    public void testResetShoppingCartItemList() {
        ShoppingCartItem item = new ShoppingCartItem();
        cart.addShoppingCartItem(item);
        cart.resetShoppingCartItemList();
        assertEquals(0, cart.getShoppingCartItemList().size());
    }

    @Test
    public void testTotalsGettersAndSetters() {
        cart.setCartItemTotal(100.0);
        cart.setCartItemPromoSavings(-10.0);
        cart.setShippingTotal(5.99);
        cart.setShippingPromoSavings(-5.99);
        cart.setCartTotal(89.01);

        assertEquals(100.0, cart.getCartItemTotal(), 0.001);
        assertEquals(-10.0, cart.getCartItemPromoSavings(), 0.001);
        assertEquals(5.99, cart.getShippingTotal(), 0.001);
        assertEquals(-5.99, cart.getShippingPromoSavings(), 0.001);
        assertEquals(89.01, cart.getCartTotal(), 0.001);
    }

    @Test
    public void testEmptyCartDefaults() {
        assertEquals(0, cart.getShoppingCartItemList().size());
        assertEquals(0.0, cart.getCartItemTotal(), 0.001);
        assertEquals(0.0, cart.getCartTotal(), 0.001);
        assertEquals(0.0, cart.getShippingTotal(), 0.001);
    }
}
