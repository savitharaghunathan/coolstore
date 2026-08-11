package com.redhat.coolstore.utils;

import com.redhat.coolstore.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;;

public class TransformersTest {

    @Test
    public void testToProductMapsAllFields() {
        InventoryEntity inv = new InventoryEntity();
        inv.setItemId("329299");
        inv.setLocation("Raleigh");
        inv.setQuantity(35);
        inv.setLink("http://example.com");

        CatalogItemEntity entity = new CatalogItemEntity();
        entity.setItemId("329299");
        entity.setName("Red Fedora");
        entity.setDesc("Official Red Hat Fedora");
        entity.setPrice(34.99);
        entity.setInventory(inv);

        Product p = Transformers.toProduct(entity);

        assertEquals("329299", p.getItemId());
        assertEquals("Red Fedora", p.getName());
        assertEquals("Official Red Hat Fedora", p.getDesc());
        assertEquals(34.99, p.getPrice(), 0.001);
        assertEquals("Raleigh", p.getLocation());
        assertEquals(35, p.getQuantity());
        assertEquals("http://example.com", p.getLink());
    }

    @Test
    public void testToProductWithNullInventory() {
        CatalogItemEntity entity = new CatalogItemEntity();
        entity.setItemId("999999");
        entity.setName("No Stock");
        entity.setDesc("Out of stock item");
        entity.setPrice(9.99);

        Product p = Transformers.toProduct(entity);

        assertEquals("999999", p.getItemId());
        assertEquals("No Stock", p.getName());
        assertEquals(9.99, p.getPrice(), 0.001);
        assertNull(p.getLocation());
        assertNull(p.getLink());
        assertEquals(0, p.getQuantity());
    }

    @Test
    public void testShoppingCartToJsonContainsRequiredFields() {
        Product p = new Product();
        p.setItemId("329299");
        p.setPrice(34.99);

        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(p);
        sci.setQuantity(2);
        sci.setPrice(34.99);

        ShoppingCart cart = new ShoppingCart();
        cart.addShoppingCartItem(sci);
        cart.setCartTotal(72.97);
        cart.setCartItemPromoSavings(-5.0);
        cart.setShippingTotal(4.99);
        cart.setShippingPromoSavings(0.0);

        String json = Transformers.shoppingCartToJson(cart);

        assertTrue(json.contains("\"orderValue\""));
        assertTrue(json.contains("\"customerName\""));
        assertTrue(json.contains("\"customerEmail\""));
        assertTrue(json.contains("\"retailPrice\""));
        assertTrue(json.contains("\"discount\""));
        assertTrue(json.contains("\"shippingFee\""));
        assertTrue(json.contains("\"shippingDiscount\""));
        assertTrue(json.contains("\"items\""));
        assertTrue(json.contains("\"productSku\":\"329299\""));
        assertTrue(json.contains("\"quantity\":2"));
    }

    @Test
    public void testJsonToOrderParsesCorrectly() {
        String json = "{\"orderValue\":100.0," +
                      "\"customerName\":\"Sven Karlsson\"," +
                      "\"customerEmail\":\"sven@gmail.com\"," +
                      "\"retailPrice\":110.0," +
                      "\"discount\":-10.0," +
                      "\"shippingFee\":4.99," +
                      "\"shippingDiscount\":-4.99," +
                      "\"items\":[{\"productSku\":\"329299\",\"quantity\":3}]}";

        Order order = Transformers.jsonToOrder(json);

        assertEquals("Sven Karlsson", order.getCustomerName());
        assertEquals("sven@gmail.com", order.getCustomerEmail());
        assertEquals(100.0, order.getOrderValue(), 0.01);
        assertEquals(110.0, order.getRetailPrice(), 0.01);
        assertEquals(-10.0, order.getDiscount(), 0.01);
        assertEquals(4.99, order.getShippingFee(), 0.01);
        assertEquals(-4.99, order.getShippingDiscount(), 0.01);
        assertEquals(1, order.getItemList().size());
        assertEquals("329299", order.getItemList().get(0).getProductId());
        assertEquals(3, order.getItemList().get(0).getQuantity());
    }

    @Test
    public void testJsonToOrderMultipleItems() {
        String json = "{\"orderValue\":50.0," +
                      "\"customerName\":\"Johan\"," +
                      "\"customerEmail\":\"johan@gmail.com\"," +
                      "\"retailPrice\":50.0," +
                      "\"discount\":0.0," +
                      "\"shippingFee\":2.99," +
                      "\"shippingDiscount\":0.0," +
                      "\"items\":[" +
                      "{\"productSku\":\"A\",\"quantity\":1}," +
                      "{\"productSku\":\"B\",\"quantity\":2}" +
                      "]}";

        Order order = Transformers.jsonToOrder(json);
        assertEquals(2, order.getItemList().size());
        assertEquals("A", order.getItemList().get(0).getProductId());
        assertEquals("B", order.getItemList().get(1).getProductId());
    }
}
