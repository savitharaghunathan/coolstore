package com.redhat.coolstore.model;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class OrderItemTest {

    @Inject
    EntityManager em;

    @Test
    @TestTransaction
    public void testJpaRoundTrip() {
        OrderItem item = new OrderItem();
        item.setProductId("SKU-123");
        item.setQuantity(3);

        em.persist(item);
        em.clear();

        var results = em.createQuery(
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
