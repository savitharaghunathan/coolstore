package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ShoppingCartServiceTest - JUnit 5 migration from JUnit 4 (mechanical conversion).
 *
 * Migration notes:
 * - JUnit 4 @Test -> org.junit.jupiter.api.Test
 * - JUnit 4 @Before setUp() with MockitoAnnotations.initMocks(this) replaced by
 *   @ExtendWith(MockitoExtension.class) (mock creation + @InjectMocks injection;
 *   mockito-junit-jupiter already in pom.xml).
 * - JUnit 4 Assert.* -> org.junit.jupiter.api.Assertions.*
 * - The java.util.logging.Logger @Mock from the original file is retained for fidelity;
 *   the Quarkus ShoppingCartService uses a static final SLF4J logger, so this mock is
 *   simply not injected anywhere (an unused @Mock does not violate MockitoExtension
 *   strict stubs - only unused stubbings do).
 * - Test methods, fixtures and assertion values unchanged.
 */
@ExtendWith(MockitoExtension.class)
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
