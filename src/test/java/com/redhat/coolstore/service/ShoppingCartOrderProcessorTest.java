package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

/**
 * Characterization tests for ShoppingCartOrderProcessor (TG1.1).
 *
 * Replaces the Java EE 7 JMS test (JMSContext/JMSProducer/Topic mocks, javax.jms imports)
 * with the SmallRye Reactive Messaging contract per RULEBOOK Pattern 1A:
 * {@code process(ShoppingCart)} must send the cart's serialized JSON to the
 * {@code @Channel("orders") Emitter<String>}.
 */
public class ShoppingCartOrderProcessorTest {

    @Mock
    private Emitter<String> ordersEmitter;

    @InjectMocks
    private ShoppingCartOrderProcessor processor;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private ShoppingCart oneItemCart() {
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
        return cart;
    }

    @Test
    public void testProcessSendsMessageToOrdersChannel() {
        ShoppingCart cart = oneItemCart();

        processor.process(cart);

        verify(ordersEmitter).send(anyString());
    }

    @Test
    public void testProcessSendsValidJson() {
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

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(ordersEmitter).send(jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        assertTrue(json.contains("\"orderValue\""));
        assertTrue(json.contains("\"customerName\""));
        assertTrue(json.contains("\"items\""));
        assertTrue(json.contains("\"productSku\":\"329299\""));
    }
}
