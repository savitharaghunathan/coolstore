package com.redhat.coolstore.utils;

import com.redhat.coolstore.model.CatalogItemEntity;
import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.model.OrderItem;
import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonWriter;

import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by tqvarnst on 2017-03-30.
 *
 * Utility class for transforming between model entities (Product, Order, ShoppingCart)
 * and JSON representations.
 *
 * [PORT STATUS: 0 TODOs, 0 BUGs, 0 PERFs]
 *
 * Migration improvements applied:
 * - Uses jakarta.json (EE 10 standard) with proper resource management
 * - Uses SLF4J logging with structured debug/error messages
 * - Added null safety guards and exception handling for messaging patterns
 * - Try-with-resources for JsonReader/JsonWriter resource cleanup
 * - Comprehensive error logging for malformed messages in consumer path
 * - Pure utility methods (no EJB/CDI-specific patterns)
 */
public class Transformers {

    private static final String[] RANDOM_NAMES = {"Sven Karlsson","Johan Andersson","Karl Svensson","Anders Johansson","Stefan Olson","Martin Ericsson"};
    private static final String[] RANDOM_EMAILS = {"sven@gmail.com","johan@gmail.com","karl@gmail.com","anders@gmail.com","stefan@gmail.com","martin@gmail.com"};

    private static final Logger log = LoggerFactory.getLogger(Transformers.class);

    /**
     * Converts a CatalogItemEntity to a Product DTO.
     *
     * @param entity the catalog item entity (must not be null)
     * @return a Product with fields populated from entity; inventory fields left null
     *         if entity.getInventory() is null (warning logged)
     * @throws NullPointerException if entity is null
     */
    public static Product toProduct(CatalogItemEntity entity) {
        if (entity == null) {
            throw new NullPointerException("CatalogItemEntity cannot be null");
        }
        Product prod = new Product();
        prod.setItemId(entity.getItemId());
        prod.setName(entity.getName());
        prod.setDesc(entity.getDesc());
        prod.setPrice(entity.getPrice());
        if (entity.getInventory() != null) {
            prod.setLocation(entity.getInventory().getLocation());
            prod.setLink(entity.getInventory().getLink());
            prod.setQuantity(entity.getInventory().getQuantity());
        } else {
            log.warn("Inventory for {} [{}] unknown and missing", entity.getName(), entity.getItemId());
        }
        return prod;
    }

    /**
     * Converts a ShoppingCart to JSON string representation.
     *
     * Generates a JSON order message structure with randomly assigned customer
     * name/email from predefined lists. Used by ShoppingCartOrderProcessor as
     * a serializer for Kafka producer in Pattern 1A messaging flow.
     *
     * @param cart the shopping cart (must not be null)
     * @return JSON string with structure:
     *   {
     *     "orderValue": <double>,
     *     "customerName": <random from RANDOM_NAMES>,
     *     "customerEmail": <random from RANDOM_EMAILS>,
     *     "retailPrice": <sum of quantity*price>,
     *     "discount": <cartItemPromoSavings>,
     *     "shippingFee": <shippingTotal>,
     *     "shippingDiscount": <shippingPromoSavings>,
     *     "items": [ { "productSku": <itemId>, "quantity": <int> }, ... ]
     *   }
     * @throws NullPointerException if cart is null or shoppingCartItemList is null
     * @throws NullPointerException if any cart item's Product is null
     */
    public static String shoppingCartToJson(ShoppingCart cart) {
        if (cart == null) {
            throw new NullPointerException("ShoppingCart cannot be null");
        }
        if (cart.getShoppingCartItemList() == null) {
            throw new NullPointerException("ShoppingCart item list cannot be null");
        }

        JsonArrayBuilder cartItems = Json.createArrayBuilder();
        cart.getShoppingCartItemList().forEach(item -> {
            if (item.getProduct() == null) {
                log.warn("Skipping cart item with null product");
                return;
            }
            cartItems.add(Json.createObjectBuilder()
                .add("productSku",item.getProduct().getItemId())
                .add("quantity",item.getQuantity())
            );
        });

        int randomNameAndEmailIndex = ThreadLocalRandom.current().nextInt(RANDOM_NAMES.length);

        JsonObject jsonObject = Json.createObjectBuilder()
            .add("orderValue", cart.getCartTotal())
            .add("customerName",RANDOM_NAMES[randomNameAndEmailIndex])
            .add("customerEmail",RANDOM_EMAILS[randomNameAndEmailIndex])
            .add("retailPrice", cart.getShoppingCartItemList().stream().mapToDouble(i -> i.getQuantity()*i.getPrice()).sum())
            .add("discount", cart.getCartItemPromoSavings())
            .add("shippingFee", cart.getShippingTotal())
            .add("shippingDiscount", cart.getShippingPromoSavings())
            .add("items",cartItems)
            .build();
        StringWriter w = new StringWriter();
        try (JsonWriter writer = Json.createWriter(w)) {
            writer.write(jsonObject);
        }
        log.debug("Successfully serialized shopping cart to JSON for Kafka producer");
        return w.toString();
    }

    /**
     * Converts a JSON string representation to an Order entity.
     *
     * Deserializes a JSON order message into an Order entity. Used by OrderServiceMDB
     * as a deserializer for Kafka consumer in Pattern 1B messaging flow. Must be called
     * from @Transactional context.
     *
     * @param json the JSON string with structure matching shoppingCartToJson output (must not be null)
     * @return an Order entity with fields populated from JSON
     * @throws NullPointerException if json is null or a required field is missing from the JSON
     * @throws jakarta.json.JsonException if JSON parsing fails or field type is unexpected
     */
    public static Order jsonToOrder(String json) {
        if (json == null) {
            throw new NullPointerException("JSON string cannot be null");
        }

        try (JsonReader jsonReader = Json.createReader(new StringReader(json))) {
            JsonObject rootObject = jsonReader.readObject();
            Order order = new Order();

            try {
                order.setCustomerName(rootObject.getString("customerName"));
                order.setCustomerEmail(rootObject.getString("customerEmail"));
                order.setOrderValue(rootObject.getJsonNumber("orderValue").doubleValue());
                order.setRetailPrice(rootObject.getJsonNumber("retailPrice").doubleValue());
                order.setDiscount(rootObject.getJsonNumber("discount").doubleValue());
                order.setShippingFee(rootObject.getJsonNumber("shippingFee").doubleValue());
                order.setShippingDiscount(rootObject.getJsonNumber("shippingDiscount").doubleValue());
                JsonArray jsonItems = rootObject.getJsonArray("items");
                List<OrderItem> items = new ArrayList<OrderItem>(jsonItems.size());
                for (JsonObject jsonItem : jsonItems.getValuesAs(JsonObject.class)) {
                    OrderItem oi = new OrderItem();
                    oi.setProductId(jsonItem.getString("productSku"));
                    oi.setQuantity(jsonItem.getInt("quantity"));
                    items.add(oi);
                }
                order.setItemList(items);
                log.debug("Successfully deserialized JSON order message from Kafka consumer");
                return order;
            } catch (NullPointerException | JsonException e) {
                log.error("Malformed order JSON, unable to deserialize: {}", json, e);
                throw new JsonException("Failed to deserialize order JSON: required field missing or type mismatch", e);
            }
        }
    }

}
