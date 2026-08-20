package com.redhat.coolstore.rest;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.model.OrderItem;
import com.redhat.coolstore.service.OrderService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
public class OrderEndpointTest {

    @Mock
    private OrderService os;

    @InjectMocks
    private OrderEndpoint endpoint;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
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

        Response response = endpoint.getOrder(1L);
        assertEquals(200, response.getStatus());
        Order result = response.readEntity(Order.class);
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

        Response response = endpoint.getOrder(5L);
        assertEquals(200, response.getStatus());
        Order result = response.readEntity(Order.class);
        assertEquals(1, result.getItemList().size());
        assertEquals("329299", result.getItemList().get(0).getProductId());
    }

    @Test
    public void testGetOrderNotFound() {
        when(os.getOrderById(999L)).thenReturn(null);

        Response response = endpoint.getOrder(999L);
        assertEquals(404, response.getStatus());
    }
}
