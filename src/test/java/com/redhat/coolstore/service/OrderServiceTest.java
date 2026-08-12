package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OrderServiceTest {

    @Mock
    private EntityManager em;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private CriteriaQuery<Order> criteriaQuery;

    @Mock
    private Root<Order> root;

    @Mock
    private TypedQuery<Order> typedQuery;

    @InjectMocks
    private OrderService orderService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSaveOrder() {
        Order order = new Order();
        order.setCustomerName("Test");

        orderService.save(order);

        verify(em).persist(order);
    }

    @Test
    public void testGetOrders() {
        List<Order> expected = Arrays.asList(new Order(), new Order());
        when(em.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(Order.class)).thenReturn(criteriaQuery);
        when(criteriaQuery.from(Order.class)).thenReturn(root);
        when(criteriaQuery.select(root)).thenReturn(criteriaQuery);
        when(em.createQuery(criteriaQuery)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(expected);

        List<Order> result = orderService.getOrders();
        assertEquals(2, result.size());
    }

    @Test
    public void testGetOrderById() {
        Order order = new Order();
        order.setCustomerName("Found");
        when(em.find(Order.class, 1L)).thenReturn(order);

        Order result = orderService.getOrderById(1L);
        assertNotNull(result);
        assertEquals("Found", result.getCustomerName());
    }
}
