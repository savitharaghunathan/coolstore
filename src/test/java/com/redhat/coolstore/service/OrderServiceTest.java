package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for OrderService using Quarkus test framework.
 *
 * Ported from JUnit 4 + Arquillian to JUnit 5 (Jupiter) + Quarkus.
 * Uses @QuarkusTest annotation to run in Quarkus test context.
 * Uses @InjectMock for Quarkus-managed mocking of dependencies.
 */
@QuarkusTest
public class OrderServiceTest {

    @InjectMock
    private EntityManager em;

    @InjectMock
    private CriteriaBuilder cb;

    @InjectMock
    private CriteriaQuery<Order> criteriaQuery;

    @InjectMock
    private Root<Order> root;

    @InjectMock
    private TypedQuery<Order> typedQuery;

    @InjectMock
    private OrderService orderService;

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
