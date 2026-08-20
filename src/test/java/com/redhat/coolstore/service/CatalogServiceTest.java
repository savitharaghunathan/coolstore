package com.redhat.coolstore.service;

import com.redhat.coolstore.model.CatalogItemEntity;
import com.redhat.coolstore.model.InventoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterEach;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CatalogServiceTest {

    // TODO: Service integration test required to verify transaction safety
    // [RULE: Section 1B, lines 87-93] CatalogService.updateInventoryItems() calls em.merge()
    // but service implementation lacks @Transactional annotation. Unit tests with mocked
    // EntityManager cannot verify active transaction context. Convert to @QuarkusTest
    // integration test or add @Transactional to updateInventoryItems() in CatalogService.java
    // to avoid TransactionRequiredException at runtime.

    // TODO: Service CDI scope verification required
    // [RULE: Section 2, lines 111-114] RULEBOOK mandates @Stateless → @ApplicationScoped,
    // but actual service uses @Singleton. Unit tests with @InjectMocks bypass CDI entirely.
    // Convert to @QuarkusTest integration test with @Inject CatalogService to verify
    // correct CDI scope annotation per RULEBOOK specification.

    @Mock
    private EntityManager em;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private CriteriaQuery<CatalogItemEntity> criteriaQuery;

    @Mock
    private Root<CatalogItemEntity> root;

    @Mock
    private TypedQuery<CatalogItemEntity> typedQuery;

    @InjectMocks
    private CatalogService catalogService;

    private AutoCloseable closeable;

    @BeforeEach
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    public void testGetCatalogItems() {
        List<CatalogItemEntity> expected = Arrays.asList(new CatalogItemEntity(), new CatalogItemEntity());
        when(em.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(CatalogItemEntity.class)).thenReturn(criteriaQuery);
        when(criteriaQuery.from(CatalogItemEntity.class)).thenReturn(root);
        when(criteriaQuery.select(root)).thenReturn(criteriaQuery);
        when(em.createQuery(criteriaQuery)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(expected);

        List<CatalogItemEntity> result = catalogService.getCatalogItems();
        assertEquals(2, result.size());
    }

    @Test
    public void testGetCatalogItemById() {
        CatalogItemEntity item = new CatalogItemEntity();
        item.setItemId("329299");
        when(em.find(CatalogItemEntity.class, "329299")).thenReturn(item);

        CatalogItemEntity result = catalogService.getCatalogItemById("329299");
        assertNotNull(result);
        assertEquals("329299", result.getItemId());
    }

    @Test
    public void testGetCatalogItemByIdNotFound() {
        when(em.find(CatalogItemEntity.class, "UNKNOWN")).thenReturn(null);

        CatalogItemEntity result = catalogService.getCatalogItemById("UNKNOWN");
        assertNull(result);
    }

    @Test
    public void testUpdateInventoryItems() {
        InventoryEntity inv = new InventoryEntity();
        inv.setItemId("329299");
        inv.setQuantity(50);

        CatalogItemEntity item = new CatalogItemEntity();
        item.setItemId("329299");
        item.setInventory(inv);

        when(em.find(CatalogItemEntity.class, "329299")).thenReturn(item);

        catalogService.updateInventoryItems("329299", 3);

        assertEquals(47, inv.getQuantity());
        verify(em).merge(inv);
    }

    @Test
    public void testUpdateInventoryItemsNotFound() {
        when(em.find(CatalogItemEntity.class, "UNKNOWN")).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> {
            catalogService.updateInventoryItems("UNKNOWN", 3);
        });
    }

    @Test
    public void testUpdateInventoryItemsInventoryNotFound() {
        CatalogItemEntity item = new CatalogItemEntity();
        item.setItemId("329299");
        item.setInventory(null);

        when(em.find(CatalogItemEntity.class, "329299")).thenReturn(item);

        assertThrows(IllegalStateException.class, () -> {
            catalogService.updateInventoryItems("329299", 3);
        });
    }
}
