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
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;;
import static org.mockito.Mockito.*;

public class CatalogServiceTest {

    @Mock
    private EntityManager em;

    @Mock
    private Logger log;

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

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
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
}
