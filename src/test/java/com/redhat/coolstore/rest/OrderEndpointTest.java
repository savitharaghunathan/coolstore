package com.redhat.coolstore.rest;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.model.OrderItem;
import com.redhat.coolstore.service.OrderService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OrderEndpointTest {

    @Mock
    private OrderService os;

    @InjectMocks
    private OrderEndpoint endpoint;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testListAllOrders() {
        Order o1 = new Order();
        o1.setCustomerName("Alice");
        Order o2 = new Order();
        o2.setCustomerName("Bob");
        when(os.getOrders()).thenReturn(Arrays.asList(o1, o2));

        List<Order> result = endpoint.listAll();
        assertEquals(2, result.size());
        assertEquals("Alice", result.get(0).getCustomerName());
    }

    @Test
    public void testGetOrderById() {
        Order order = new Order();
        order.setCustomerName("Charlie");
        order.setOrderValue(50.0);
        when(os.getOrderById(1L)).thenReturn(order);

        Order result = endpoint.getOrder(1L);
        assertNotNull(result);
        assertEquals("Charlie", result.getCustomerName());
    }

    @Test
    public void testGetOrderWithNestedItems() {
        OrderItem item = new OrderItem();
        item.setProductId("329299");
        item.setQuantity(2);

        Order order = new Order();
        order.setCustomerName("Diana");
        order.setItemList(Arrays.asList(item));
        when(os.getOrderById(5L)).thenReturn(order);

        Order result = endpoint.getOrder(5L);
        assertEquals(1, result.getItemList().size());
        assertEquals("329299", result.getItemList().get(0).getProductId());
    }
}
