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
    public void testGetShoppingCartReturnsSameInstancePerCartId() {
        // Corrected after multi-cart fix (task 1.5): same cartId -> same cart, different cartId -> independent cart
        assertSame(shoppingCartService.getShoppingCart("cart-1"), shoppingCartService.getShoppingCart("cart-1"));
        assertNotSame(shoppingCartService.getShoppingCart("cart-1"), shoppingCartService.getShoppingCart("cart-2"));
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
