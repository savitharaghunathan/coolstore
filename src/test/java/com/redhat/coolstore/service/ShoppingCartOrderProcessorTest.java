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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ShoppingCartOrderProcessorTest - JUnit 5 migration from JUnit 4 + messaging port.
 *
 * Migration notes:
 * - JUnit 4 @Test -> org.junit.jupiter.api.Test
 * - JUnit 4 Assert.* -> org.junit.jupiter.api.Assertions.*
 * - @Before setUp() with MockitoAnnotations.initMocks(this) and the JMS stub
 *   (when(context.createProducer()).thenReturn(producer)) replaced by
 *   @ExtendWith(MockitoExtension.class) (mock creation + @InjectMocks injection).
 * - javax.jms JMSContext/JMSProducer/Topic mocks REMOVED: ShoppingCartOrderProcessor
 *   was ported from JMS (topic producer) to SmallRye Reactive Messaging
 *   @Inject @Channel("orders") Emitter<String> (Kafka connector). The single
 *   @Mock Emitter<String> is injected into the processor's {@code orders} field by
 *   type via @InjectMocks (Mockito injects the only Emitter-typed mock regardless of
 *   field name).
 * - The java.util.logging.Logger mock was removed: the ported processor uses a
 *   static final SLF4J logger (not injectable, never used by the test).
 * - Verification updated from verify(producer).send(eq(ordersTopic), anyString()) to
 *   verify(ordersEmitter).send(anyString()) - matching
 *   ShoppingCartOrderProcessor.process(), which calls
 *   orders.send(Transformers.shoppingCartToJson(cart)).
 * - Test method names, cart fixtures and JSON assertion values unchanged.
 */
@ExtendWith(MockitoExtension.class)
public class ShoppingCartOrderProcessorTest {

    @Mock
    private Emitter<String> ordersEmitter;

    @InjectMocks
    private ShoppingCartOrderProcessor processor;

    @Test
    public void testProcessSendsMessageToTopic() {
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
