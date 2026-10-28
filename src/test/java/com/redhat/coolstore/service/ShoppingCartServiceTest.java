package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ShoppingCartServiceTest {

    @Mock
    private ProductService productServices;

    @Mock
    private PromoService ps;

    @Mock
    private ShoppingCartOrderProcessor shoppingCartOrderProcessor;

    @Mock
    private Logger log;

    @InjectMocks
    private ShoppingCartService shoppingCartService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetShoppingCartReturnsCart() {
        ShoppingCart cart = shoppingCartService.getShoppingCart("cart-1");
        assertNotNull(cart);
    }

    @Test
    public void testGetShoppingCartReturnsSameInstance() {
        // TG 3.5 refactor: carts are keyed by cartId (RULEBOOK §2 line 118 fix).
        // Same cartId returns the same instance; different cartIds get separate carts.
        ShoppingCart cart1a = shoppingCartService.getShoppingCart("cart-1");
        ShoppingCart cart1b = shoppingCartService.getShoppingCart("cart-1");
        ShoppingCart cart2 = shoppingCartService.getShoppingCart("cart-2");
        assertSame(cart1a, cart1b);
        assertNotSame(cart1a, cart2);
    }

    @Test
    public void testGetProductDelegatesToProductService() {
        Product p = new Product();
        p.setItemId("329299");
        when(productServices.getProductByItemId("329299")).thenReturn(p);

        Product result = shoppingCartService.getProduct("329299");
        assertNotNull(result);
        assertEquals("329299", result.getItemId());
        verify(productServices).getProductByItemId("329299");
    }

    @Test
    public void testGetProductReturnsNullForUnknown() {
        when(productServices.getProductByItemId("UNKNOWN")).thenReturn(null);

        Product result = shoppingCartService.getProduct("UNKNOWN");
        assertNull(result);
    }
}
