package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import java.util.Optional;

/**
 * Quarkus reactive messaging consumer for inventory notifications.
 *
 * Changes from EJB MDB to Quarkus Reactive Messaging:
 * - Replaced @MessageDriven with @ApplicationScoped for reactive context.
 * - Removed JMS MessageListener interface; uses SmallRye Reactive Messaging @Incoming.
 * - Uses @Transactional to wrap message processing for database consistency.
 * - Uses Quarkus ConfigProperty for external configuration (threshold, logging).
 * - Integrated Quarkus logging (Log) instead of System.out/err.
 *
 * Configuration required in application.properties:
 *   quarkus.kafka.bootstrap.servers=localhost:9092
 *   mp.messaging.incoming.inventory-updates.connector=smallrye-kafka
 *   inventory.notification.threshold=50
 */
@ApplicationScoped
@Startup
public class InventoryNotificationMDB {

  @Inject
  private CatalogService catalogService;

  @ConfigProperty(name = "inventory.notification.threshold", defaultValue = "50")
  private int lowThreshold;

  /**
   * Processes incoming messages from the inventory-updates Kafka topic.
   * Deserializes Order from message payload and updates inventory quantities.
   *
   * @param orderStr the order JSON string from Kafka message
   */
  @Incoming("inventory-updates")
  @Transactional
  public void processUpdate(String orderStr) {
    try {
      Log.info("Received inventory notification message");

      Order order = Transformers.jsonToOrder(orderStr);

      order.getItemList().forEach(orderItem -> {
        try {
          processOrderItem(orderItem);
        } catch (Exception e) {
          Log.errorf("Error processing order item: %s", e.getMessage());
        }
      });

    } catch (Exception e) {
      Log.errorf("Unexpected error processing inventory notification: %s", e.getMessage());
    }
  }

  /**
   * Processes a single order item, updating inventory and alerting if below threshold.
   *
   * @param orderItem the order item to process
   */
  private void processOrderItem(Object orderItem) {
    // Assuming orderItem has getProductId() and getQuantity() methods
    String productId = getProductId(orderItem);
    int requestedQuantity = getQuantity(orderItem);

    Optional.ofNullable(catalogService.getCatalogItemById(productId))
        .ifPresentOrElse(
            catalogItem -> {
              int oldQuantity = catalogItem.getInventory().getQuantity();
              int newQuantity = oldQuantity - requestedQuantity;

              if (newQuantity < lowThreshold) {
                Log.warnf(
                    "Inventory for item %s is below threshold (%d). Current: %d, threshold: %d",
                    productId, lowThreshold, newQuantity, lowThreshold);
                // Optionally trigger alert/notification here
              } else {
                setQuantity(orderItem, newQuantity);
                Log.debugf(
                    "Updated inventory for item %s: %d -> %d", productId, oldQuantity, newQuantity);
              }
            },
            () -> Log.warnf("Catalog item not found: %s", productId));
  }

  /**
   * Extracts product ID from order item (reflection-based or interface-based).
   */
  private String getProductId(Object orderItem) {
    try {
      return (String) orderItem.getClass().getMethod("getProductId").invoke(orderItem);
    } catch (Exception e) {
      Log.warnf("Failed to extract productId: %s", e.getMessage());
      return null;
    }
  }

  /**
   * Extracts quantity from order item.
   */
  private int getQuantity(Object orderItem) {
    try {
      return (int) orderItem.getClass().getMethod("getQuantity").invoke(orderItem);
    } catch (Exception e) {
      Log.warnf("Failed to extract quantity: %s", e.getMessage());
      return 0;
    }
  }

  /**
   * Sets quantity on order item.
   */
  private void setQuantity(Object orderItem, int quantity) {
    try {
      orderItem.getClass().getMethod("setQuantity", int.class).invoke(orderItem, quantity);
    } catch (Exception e) {
      Log.warnf("Failed to set quantity: %s", e.getMessage());
    }
  }
}
