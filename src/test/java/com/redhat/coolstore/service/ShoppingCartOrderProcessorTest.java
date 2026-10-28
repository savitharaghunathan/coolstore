package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

/**
 * Task 1.3: legacy javax.jms JMSContext/JMSProducer/Topic doubles replaced with a
 * SmallRye Reactive Messaging Emitter mock; JUnit 4 -> JUnit 5 (mockito-junit-jupiter).
 */
@ExtendWith(MockitoExtension.class)
public class ShoppingCartOrderProcessorTest {

    @Mock
    private Emitter<String> orders;

    @InjectMocks
    private ShoppingCartOrderProcessor processor;

    @Test
    public void testProcessSendsMessageToOrdersChannel() {
        processor.process(buildCart());
        verify(orders).send(anyString());
    }

    @Test
    public void testProcessSendsValidJson() {
        processor.process(buildCart());
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
