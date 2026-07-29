package com.redhat.coolstore.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import static org.junit.jupiter.api.Assertions.*;

public class InventoryEntityTest {

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
    public void testJpaRoundTrip() {
        InventoryEntity inv = new InventoryEntity();
        inv.setItemId("INV-2");
        inv.setLocation("Atlanta");
        inv.setQuantity(25);
        inv.setLink("http://example.com/atlanta");

        em.getTransaction().begin();
        em.persist(inv);
        em.getTransaction().commit();
        em.clear();

        InventoryEntity found = em.find(InventoryEntity.class, "INV-2");
        assertNotNull(found);
        assertEquals("Atlanta", found.getLocation());
        assertEquals(25, found.getQuantity());
        assertEquals("http://example.com/atlanta", found.getLink());
    }

    @Test
    public void testQuantityUpdate() {
        InventoryEntity inv = new InventoryEntity();
        inv.setItemId("INV-3");
        inv.setLocation("Denver");
        inv.setQuantity(50);

        em.getTransaction().begin();
        em.persist(inv);
        em.getTransaction().commit();

        em.getTransaction().begin();
        inv.setQuantity(45);
        em.merge(inv);
        em.getTransaction().commit();
        em.clear();

        InventoryEntity found = em.find(InventoryEntity.class, "INV-3");
        assertEquals(45, found.getQuantity());
    }
}
