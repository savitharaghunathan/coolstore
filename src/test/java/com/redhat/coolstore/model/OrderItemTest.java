package com.redhat.coolstore.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OrderItemTest {

    private EntityManagerFactory emf;
    private EntityManager em;

    @BeforeEach
    public void setUp() {
        emf = Persistence.createEntityManagerFactory("primary");
        em = emf.createEntityManager();
    }

    @AfterEach
    public void tearDown() {
        if (em != null && em.isOpen()) em.close();
        if (emf != null && emf.isOpen()) emf.close();
    }

    @Test
    public void testJpaRoundTrip() {
        OrderItem item = new OrderItem();
        item.setProductId("SKU-123");
        item.setQuantity(3);

        em.getTransaction().begin();
        em.persist(item);
        em.getTransaction().commit();
        em.clear();

        List<OrderItem> results = em.createQuery(
            "SELECT oi FROM OrderItem oi WHERE oi.productId = :pid", OrderItem.class)
            .setParameter("pid", "SKU-123")
            .getResultList();
        assertEquals(1, results.size());
        assertEquals("SKU-123", results.get(0).getProductId());
        assertEquals(3, results.get(0).getQuantity());
    }

    @Test
    public void testFieldCorrectness() {
        OrderItem item = new OrderItem();
        item.setProductId("ABC-999");
        item.setQuantity(7);

        assertEquals("ABC-999", item.getProductId());
        assertEquals(7, item.getQuantity());
    }
}
