package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @BeforeEach
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
        ShoppingCart cart1 = shoppingCartService.getShoppingCart("cart-1");
        ShoppingCart cart2 = shoppingCartService.getShoppingCart("cart-2");
        assertSame(cart1, cart2);
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
