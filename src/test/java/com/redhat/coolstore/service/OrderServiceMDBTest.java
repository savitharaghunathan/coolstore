package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OrderServiceMDBTest {

    @Mock
    private OrderService orderService;

    @Mock
    private CatalogService catalogService;

    @InjectMocks
    private OrderServiceMDB orderServiceMDB;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    private String buildOrderJson() {
        return "{\"orderValue\":34.99," +
               "\"customerName\":\"Test User\"," +
               "\"customerEmail\":\"test@example.com\"," +
               "\"retailPrice\":34.99," +
               "\"discount\":0.0," +
               "\"shippingFee\":2.99," +
               "\"shippingDiscount\":0.0," +
               "\"items\":[{\"productSku\":\"329299\",\"quantity\":1}]}";
    }

    @Test
    public void testOnMessageDeserializesAndSavesOrder() {
        String orderJson = buildOrderJson();

        orderServiceMDB.onMessage(orderJson);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderService).save(orderCaptor.capture());

        Order saved = orderCaptor.getValue();
        assertEquals("Test User", saved.getCustomerName());
        assertEquals("test@example.com", saved.getCustomerEmail());
        assertEquals(34.99, saved.getOrderValue(), 0.01);
    }

    @Test
    public void testOnMessageUpdatesInventory() {
        String orderJson = buildOrderJson();

        orderServiceMDB.onMessage(orderJson);

        verify(catalogService).updateInventoryItems("329299", 1);
    }

    @Test
    public void testOnMessageWithMultipleItems() {
        String json = "{\"orderValue\":60.0," +
                      "\"customerName\":\"Multi\"," +
                      "\"customerEmail\":\"multi@example.com\"," +
                      "\"retailPrice\":60.0," +
                      "\"discount\":0.0," +
                      "\"shippingFee\":4.99," +
                      "\"shippingDiscount\":0.0," +
                      "\"items\":[" +
                      "{\"productSku\":\"329299\",\"quantity\":1}," +
                      "{\"productSku\":\"165613\",\"quantity\":2}" +
                      "]}";

        orderServiceMDB.onMessage(json);

        verify(catalogService).updateInventoryItems("329299", 1);
        verify(catalogService).updateInventoryItems("165613", 2);
    }
}
