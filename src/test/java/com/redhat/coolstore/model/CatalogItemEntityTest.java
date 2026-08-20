package com.redhat.coolstore.model;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CatalogItemEntity test - Quarkus port of Java EE 7 test.
 *
 * CHANGES FROM ORIGINAL:
 * - JUnit 4 (@Before/@After/@Test) → JUnit 5 (@Test)
 * - Removed manual EntityManagerFactory/Persistence.createEntityManagerFactory()
 *   Quarkus auto-injects EntityManager via @PersistenceContext
 * - Transactions: Test methods use @Transactional for demarcation
 * - Schema initialization handled by Quarkus Hibernate ORM auto-configuration
 * - javax.persistence → jakarta.persistence
 */
@QuarkusTest
public class CatalogItemEntityTest {

    @PersistenceContext
    private EntityManager em;

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
    @Transactional
    public void testJpaRoundTrip() {
        CatalogItemEntity item = new CatalogItemEntity();
        item.setItemId("100001");
        item.setName("Test Item");
        item.setDesc("Description");
        item.setPrice(9.99);

        // @Transactional (jakarta.transaction.Transactional) wraps entire test method; no need for em.getTransaction().begin()/commit()
        em.persist(item);
        em.flush();  // Explicit flush to ensure persistence before clear
        em.clear();

        CatalogItemEntity found = em.find(CatalogItemEntity.class, "100001");
        assertNotNull(found);
        assertEquals("Test Item", found.getName());
        assertEquals("Description", found.getDesc());
        assertEquals(9.99, found.getPrice(), 0.001);
    }

    @Test
    @Transactional
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

        // @Transactional (jakarta.transaction.Transactional) wraps entire test method
        em.persist(item);
        em.flush();  // Explicit flush to ensure persistence before clear
        em.clear();

        CatalogItemEntity found = em.find(CatalogItemEntity.class, "200001");
        assertNotNull(found.getInventory());
        assertEquals("Raleigh", found.getInventory().getLocation());
        assertEquals(50, found.getInventory().getQuantity());
    }

    @Test
    @Transactional
    public void testNullInventory() {
        CatalogItemEntity item = new CatalogItemEntity();
        item.setItemId("300001");
        item.setName("No Inventory");
        item.setDesc("Missing inventory");
        item.setPrice(5.00);

        // @Transactional (jakarta.transaction.Transactional) wraps entire test method
        em.persist(item);
        em.flush();  // Explicit flush to ensure persistence before clear
        em.clear();

        CatalogItemEntity found = em.find(CatalogItemEntity.class, "300001");
        assertNull(found.getInventory());
    }
}
