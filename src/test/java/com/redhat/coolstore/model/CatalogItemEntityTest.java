package com.redhat.coolstore.model;

import org.junit.After;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import static org.junit.jupiter.api.Assertions.*;;

public class CatalogItemEntityTest {

    private EntityManagerFactory emf;
    private EntityManager em;

    @BeforeEach
    public void setUp() {
        emf = Persistence.createEntityManagerFactory("primary");
        em = emf.createEntityManager();
    }

    @After
    public void tearDown() {
        if (em != null && em.isOpen()) em.close();
        if (emf != null && emf.isOpen()) emf.close();
    }

    @Test
    public void testFieldMapping() {
        CatalogItemEntity item = new CatalogItemEntity();
        item.setItemId("329299");
        item.setName("Red Fedora");
        item.setDesc("Official Red Hat Fedora");
        item.setPrice(34.99);

        assertEquals("329299", item.getItemId());
        assertEquals("Red Fedora", item.getName());
        assertEquals("Official Red Hat Fedora", item.getDesc());
        assertEquals(34.99, item.getPrice(), 0.001);
    }

    @Test
    public void testJpaRoundTrip() {
        CatalogItemEntity item = new CatalogItemEntity();
        item.setItemId("100001");
        item.setName("Test Item");
        item.setDesc("Description");
        item.setPrice(9.99);

        em.getTransaction().begin();
        em.persist(item);
        em.getTransaction().commit();
        em.clear();

        CatalogItemEntity found = em.find(CatalogItemEntity.class, "100001");
        assertNotNull(found);
        assertEquals("Test Item", found.getName());
        assertEquals("Description", found.getDesc());
        assertEquals(9.99, found.getPrice(), 0.001);
    }

    @Test
    public void testOneToOneWithInventory() {
        InventoryEntity inv = new InventoryEntity();
        inv.setItemId("200001");
        inv.setLocation("Raleigh");
        inv.setQuantity(50);
        inv.setLink("http://example.com");

        CatalogItemEntity item = new CatalogItemEntity();
        item.setItemId("200001");
        item.setName("Item With Inventory");
        item.setDesc("Has inventory");
        item.setPrice(15.00);
        item.setInventory(inv);

        em.getTransaction().begin();
        em.persist(item);
        em.getTransaction().commit();
        em.clear();

        CatalogItemEntity found = em.find(CatalogItemEntity.class, "200001");
        assertNotNull(found.getInventory());
        assertEquals("Raleigh", found.getInventory().getLocation());
        assertEquals(50, found.getInventory().getQuantity());
    }

    @Test
    public void testNullInventory() {
        CatalogItemEntity item = new CatalogItemEntity();
        item.setItemId("300001");
        item.setName("No Inventory");
        item.setDesc("Missing inventory");
        item.setPrice(5.00);

        em.getTransaction().begin();
        em.persist(item);
        em.getTransaction().commit();
        em.clear();

        CatalogItemEntity found = em.find(CatalogItemEntity.class, "300001");
        assertNull(found.getInventory());
    }
}
