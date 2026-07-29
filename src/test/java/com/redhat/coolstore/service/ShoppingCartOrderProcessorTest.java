package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ShoppingCartOrderProcessorTest {

    @Mock
    private Logger log;

    @InjectMocks
    private ShoppingCartOrderProcessor processor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testProcessLogsOrder() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartTotal(50.0);

        Product p = new Product();
        p.setItemId("329299");
        p.setPrice(34.99);

        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(p);
        sci.setQuantity(1);
        sci.setPrice(34.99);
        cart.addShoppingCartItem(sci);

        processor.process(cart);

        verify(log, atLeastOnce()).info(anyString());
    }

    @Test
    public void testProcessGeneratesValidJson() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartTotal(34.99);
        cart.setCartItemPromoSavings(0.0);
        cart.setShippingTotal(2.99);
        cart.setShippingPromoSavings(0.0);

        Product p = new Product();
        p.setItemId("329299");
        p.setPrice(34.99);

        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(p);
        sci.setQuantity(1);
        sci.setPrice(34.99);
        cart.addShoppingCartItem(sci);

        processor.process(cart);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(log, atLeastOnce()).info(msgCaptor.capture());

        // Verify at least one log message contains JSON-like content
        boolean foundJson = msgCaptor.getAllValues().stream()
            .anyMatch(msg -> msg.contains("\"orderValue\"") || msg.contains("Order JSON:"));
        assertTrue("Expected JSON content in log messages", foundJson);
    }
}
