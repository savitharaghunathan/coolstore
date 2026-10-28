package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ShoppingCartOrderProcessor test — ported from the Java EE JMS baseline to the
 * SmallRye Reactive Messaging Emitter (RULEBOOK Pattern 1A) by MigIQ execute stage
 * (TG 6.1: fix tests relying on removed Java EE machinery). The JMSContext/Topic/
 * JMSProducer mocks were replaced with an Emitter&lt;String&gt; mock; the assertions
 * (message sent, valid order JSON shape) are preserved from the baseline.
 */
public class ShoppingCartOrderProcessorTest {

    @Mock
    private Emitter<String> orders;

    @InjectMocks
    private ShoppingCartOrderProcessor processor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testProcessSendsMessageToOrdersChannel() {
        ShoppingCart cart = buildCart();

        processor.process(cart);

        verify(orders).send(anyString());
    }

    @Test
    public void testProcessSendsValidJson() {
        ShoppingCart cart = buildCart();

        processor.process(cart);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(orders).send(jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        assertTrue(json.contains("\"orderValue\""));
        assertTrue(json.contains("\"customerName\""));
        assertTrue(json.contains("\"items\""));
        assertTrue(json.contains("\"productSku\":\"329299\""));
    }

    private ShoppingCart buildCart() {
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
        return cart;
    }
}
