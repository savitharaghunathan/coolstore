package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.Topic;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ShoppingCartOrderProcessorTest {

    @Mock
    private JMSContext context;

    @Mock
    private Topic ordersTopic;

    @Mock
    private JMSProducer producer;

    @Mock
    private Logger log;

    @InjectMocks
    private ShoppingCartOrderProcessor processor;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(context.createProducer()).thenReturn(producer);
    }

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

        verify(context).createProducer();
        verify(producer).send(eq(ordersTopic), anyString());
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
        verify(producer).send(eq(ordersTopic), jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        assertTrue(json.contains("\"orderValue\""));
        assertTrue(json.contains("\"customerName\""));
        assertTrue(json.contains("\"items\""));
        assertTrue(json.contains("\"productSku\":\"329299\""));
    }
}
