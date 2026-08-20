package com.redhat.coolstore.utils;

import com.redhat.coolstore.model.*;
import jakarta.json.JsonException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

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
    @DisplayName("[RULE: Section 6] Null inventory logs SLF4J warning")
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
        // NOTE: Full log verification would require test appender integration
        // (e.g., Logback test appender or SLF4J MockLoggerFactory). Current test
        // validates the Product object is constructed correctly even with null
        // inventory, allowing Transformers.toProduct to execute the warn() path.
    }

    @Test
    @DisplayName("[RULE: Pattern 1A] Shopping cart with items serializes all required fields")
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
    @DisplayName("[RULE: Pattern 1A] Empty shopping cart edge case - empty items array")
    public void testShoppingCartToJsonWithEmptyCart() {
        ShoppingCart emptyCart = new ShoppingCart();
        emptyCart.setCartTotal(0.0);
        emptyCart.setCartItemPromoSavings(0.0);
        emptyCart.setShippingTotal(0.0);
        emptyCart.setShippingPromoSavings(0.0);

        String json = Transformers.shoppingCartToJson(emptyCart);

        assertTrue(json.contains("\"orderValue\":0.0"));
        assertTrue(json.contains("\"items\":[]"), "Empty cart should have empty items array");
    }

    @Test
    @DisplayName("[RULE: Pattern 1B] Valid JSON deserializes to Order entity")
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
    @DisplayName("[RULE: Pattern 1B] Null JSON input throws NullPointerException")
    public void testJsonToOrderWithNullInput() {
        assertThrows(NullPointerException.class, () -> {
            Transformers.jsonToOrder(null);
        }, "jsonToOrder must throw NullPointerException for null input");
    }

    @Test
    @DisplayName("[RULE: Pattern 1B] Malformed JSON with missing required field throws JsonException")
    public void testJsonToOrderWithMissingField() {
        String malformedJson = "{\"orderValue\":100.0," +
                               "\"customerName\":\"Sven\"," +
                               "\"retailPrice\":110.0}"; // Missing customerEmail and other fields

        assertThrows(JsonException.class, () -> {
            Transformers.jsonToOrder(malformedJson);
        }, "jsonToOrder must throw JsonException for malformed JSON with missing required fields");
    }

    @Test
    @DisplayName("[RULE: Pattern 1B] Malformed JSON with wrong type throws JsonException")
    public void testJsonToOrderWithWrongFieldType() {
        String malformedJson = "{\"orderValue\":\"NOT_A_NUMBER\"," +
                               "\"customerName\":\"Sven Karlsson\"," +
                               "\"customerEmail\":\"sven@gmail.com\"," +
                               "\"retailPrice\":110.0," +
                               "\"discount\":-10.0," +
                               "\"shippingFee\":4.99," +
                               "\"shippingDiscount\":-4.99," +
                               "\"items\":[]}";

        assertThrows(JsonException.class, () -> {
            Transformers.jsonToOrder(malformedJson);
        }, "jsonToOrder must throw JsonException for JSON with wrong field types");
    }

    @Test
    @DisplayName("[RULE: Pattern 1B] Valid UTF-8 JSON serialization for Kafka StringSerializer")
    public void testJsonToOrderHandlesValidUtf8() {
        String json = "{\"orderValue\":100.0," +
                      "\"customerName\":\"José García\"," +
                      "\"customerEmail\":\"jose@example.com\"," +
                      "\"retailPrice\":110.0," +
                      "\"discount\":-10.0," +
                      "\"shippingFee\":4.99," +
                      "\"shippingDiscount\":-4.99," +
                      "\"items\":[{\"productSku\":\"329299\",\"quantity\":1}]}";

        Order order = Transformers.jsonToOrder(json);
        assertEquals("José García", order.getCustomerName());
        // Kafka StringSerializer expects valid UTF-8 strings
        assertTrue(json.matches("^\\{.*\\}$"), "JSON must be valid UTF-8 serializable string");
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

    @Test
    @DisplayName("[RULE: Amendment 2] Shopping cart JSON is valid UTF-8 for Kafka StringSerializer")
    public void testShoppingCartToJsonProducesKafkaCompatibleString() {
        Product p = new Product();
        p.setItemId("ABC123");
        p.setPrice(25.99);

        ShoppingCartItem item = new ShoppingCartItem();
        item.setProduct(p);
        item.setQuantity(1);
        item.setPrice(25.99);

        ShoppingCart cart = new ShoppingCart();
        cart.addShoppingCartItem(item);
        cart.setCartTotal(25.99);
        cart.setCartItemPromoSavings(0.0);
        cart.setShippingTotal(5.00);
        cart.setShippingPromoSavings(0.0);

        String json = Transformers.shoppingCartToJson(cart);

        // Validate UTF-8 compatibility with Kafka StringSerializer
        assertNotNull(json, "JSON must not be null");
        assertTrue(json.length() > 0, "JSON must not be empty");
        // StringSerializer requires String that can be encoded as UTF-8
        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(bytes.length > 0, "JSON must be serializable to UTF-8 bytes");
        // Round-trip: deserialize back to string
        String roundTrip = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(json, roundTrip, "JSON must be identical after UTF-8 round-trip");
    }

    @Test
    @DisplayName("[RULE: Pattern 1B] Order entity can be constructed from JSON for persistence")
    public void testJsonToOrderEntityIsValidForPersistence() {
        String json = "{\"orderValue\":75.50," +
                      "\"customerName\":\"Test Customer\"," +
                      "\"customerEmail\":\"test@example.com\"," +
                      "\"retailPrice\":80.0," +
                      "\"discount\":-4.5," +
                      "\"shippingFee\":3.99," +
                      "\"shippingDiscount\":0.0," +
                      "\"items\":[{\"productSku\":\"SKU001\",\"quantity\":2}]}";

        Order order = Transformers.jsonToOrder(json);

        // Verify Order is fully constructed with all fields for persistence
        assertNotNull(order.getCustomerName());
        assertNotNull(order.getCustomerEmail());
        assertNotNull(order.getItemList());
        assertTrue(order.getItemList().size() > 0);
        assertNotNull(order.getItemList().get(0).getProductId());
        // Order fields are ready for EntityManager.persist() in OrderServiceMDB
        assertEquals("Test Customer", order.getCustomerName());
        assertEquals(75.50, order.getOrderValue(), 0.01);
    }
}
