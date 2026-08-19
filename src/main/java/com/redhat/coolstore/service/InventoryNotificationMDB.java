package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Optional;

/**
 * Native Quarkus port of InventoryNotificationMDB.
 *
 * Changes from EJB MDB to Quarkus-native approach:
 * - Removed WebLogic-specific JNDI/TopicConnection setup; uses Quarkus JMS configuration.
 * - Replaced @Stateless + manual init() with @Singleton for managed lifecycle.
 * - Uses Quarkus ConfigProperty for external configuration (threshold, logging).
 * - Removed boilerplate JNDI context creation; configured via application.properties.
 * - Integrated Quarkus logging instead of System.out/err.
 *
 * Configuration required in application.properties:
 *   quarkus.jms.url=tcp://localhost:61616
 *   inventory.notification.threshold=50
 */
@Singleton
@Startup
public class InventoryNotificationMDB implements MessageListener {

  @Inject
  private CatalogService catalogService;

  @ConfigProperty(name = "inventory.notification.threshold", defaultValue = "50")
  private int lowThreshold;

  /**
   * Processes incoming JMS messages from the inventory notification topic.
   * Deserializes Order from message and updates inventory quantities.
   *
   * @param rcvMessage the received JMS message
   */
  @Override
  public void onMessage(Message rcvMessage) {
    try {
      Log.info("Received inventory notification message");

      if (rcvMessage instanceof TextMessage) {
        TextMessage msg = (TextMessage) rcvMessage;
        String orderStr = msg.getBody(String.class);
        Order order = Transformers.jsonToOrder(orderStr);

        order.getItemList().forEach(orderItem -> {
          try {
            processOrderItem(orderItem);
          } catch (Exception e) {
            Log.errorf("Error processing order item %s: %s", orderItem.getProductId(), e.getMessage());
          }
        });
      } else {
        Log.warn("Received non-TextMessage, skipping");
      }

    } catch (JMSException jmse) {
      Log.errorf("JMS exception during message processing: %s", jmse.getMessage());
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
