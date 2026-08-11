package com.redhat.coolstore.service;

import com.redhat.coolstore.model.CatalogItemEntity;
import com.redhat.coolstore.model.InventoryEntity;
import com.redhat.coolstore.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ProductServiceTest {

    @Mock
    private CatalogService cm;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    private CatalogItemEntity createEntity(String id, String name, double price) {
        CatalogItemEntity e = new CatalogItemEntity();
        e.setItemId(id);
        e.setName(name);
        e.setPrice(price);
        InventoryEntity inv = new InventoryEntity();
        inv.setItemId(id);
        inv.setLocation("Warehouse");
        inv.setQuantity(10);
        e.setInventory(inv);
        return e;
    }

    @Test
    public void testGetProducts() {
        when(cm.getCatalogItems()).thenReturn(Arrays.asList(
            createEntity("A", "ProductA", 10.0),
            createEntity("B", "ProductB", 20.0)
        ));

        List<Product> products = productService.getProducts();
        assertEquals(2, products.size());
        assertEquals("A", products.get(0).getItemId());
        assertEquals("ProductA", products.get(0).getName());
        assertEquals(10.0, products.get(0).getPrice(), 0.001);
        assertEquals("Warehouse", products.get(0).getLocation());
    }

    @Test
    public void testGetProductByItemId() {
        CatalogItemEntity entity = createEntity("329299", "Red Fedora", 34.99);
        when(cm.getCatalogItemById("329299")).thenReturn(entity);

        Product result = productService.getProductByItemId("329299");
        assertNotNull(result);
        assertEquals("329299", result.getItemId());
        assertEquals("Red Fedora", result.getName());
    }

    @Test
    public void testGetProductByItemIdNotFound() {
        when(cm.getCatalogItemById("UNKNOWN")).thenReturn(null);

        Product result = productService.getProductByItemId("UNKNOWN");
        assertNull(result);
    }
}
