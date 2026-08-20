package com.redhat.coolstore.model;

import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class InventoryEntityTest {

    @Inject
    private EntityManager em;

    @Test
    public void testFieldMapping() {
        InventoryEntity inv = new InventoryEntity();
        inv.setItemId("INV-1");
        inv.setLocation("Boston");
        inv.setQuantity(100);
        inv.setLink("http://example.com/inv");

        assertEquals("INV-1", inv.getItemId());
        assertEquals("Boston", inv.getLocation());
        assertEquals(100, inv.getQuantity());
        assertEquals("http://example.com/inv", inv.getLink());
    }

    @Test
    @TestTransaction
    public void testJpaRoundTrip() {
        InventoryEntity inv = new InventoryEntity();
        inv.setItemId("INV-2");
        inv.setLocation("Atlanta");
        inv.setQuantity(25);
        inv.setLink("http://example.com/atlanta");

        em.persist(inv);
        em.flush();
        em.clear();

        InventoryEntity found = em.find(InventoryEntity.class, "INV-2");
        assertNotNull(found);
        assertEquals("Atlanta", found.getLocation());
        assertEquals(25, found.getQuantity());
        assertEquals("http://example.com/atlanta", found.getLink());
    }

    @Test
    @TestTransaction
    public void testQuantityUpdate() {
        InventoryEntity inv = new InventoryEntity();
        inv.setItemId("INV-3");
        inv.setLocation("Denver");
        inv.setQuantity(50);

        em.persist(inv);
        em.flush();

        inv.setQuantity(45);
        em.merge(inv);
        em.flush();
        em.clear();

        InventoryEntity found = em.find(InventoryEntity.class, "INV-3");
        assertEquals(45, found.getQuantity());
    }
}
