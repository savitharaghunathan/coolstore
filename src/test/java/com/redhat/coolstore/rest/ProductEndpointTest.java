package com.redhat.coolstore.rest;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.service.ProductService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ProductEndpointTest {

    @Mock
    private ProductService pm;

    @InjectMocks
    private ProductEndpoint endpoint;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    private Product createProduct(String id, String name, double price) {
        Product p = new Product();
        p.setItemId(id);
        p.setName(name);
        p.setPrice(price);
        return p;
    }

    @Test
    public void testListAllReturnsProducts() {
        List<Product> products = Arrays.asList(
            createProduct("A", "Product A", 10.0),
            createProduct("B", "Product B", 20.0)
        );
        when(pm.getProducts()).thenReturn(products);

        List<Product> result = endpoint.listAll();
        assertEquals(2, result.size());
        assertEquals("A", result.get(0).getItemId());
        assertEquals("B", result.get(1).getItemId());
    }

    @Test
    public void testListAllEmptyCatalog() {
        when(pm.getProducts()).thenReturn(Collections.<Product>emptyList());

        List<Product> result = endpoint.listAll();
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetProductByItemId() {
        Product p = createProduct("329299", "Red Fedora", 34.99);
        when(pm.getProductByItemId("329299")).thenReturn(p);

        Product result = endpoint.getProduct("329299");
        assertNotNull(result);
        assertEquals("329299", result.getItemId());
        assertEquals(34.99, result.getPrice(), 0.001);
    }
}
