package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.model.OrderItem;
import com.redhat.coolstore.utils.Transformers;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Quarkus reactive messaging consumer for inventory notifications.
 *
 * MIGRATION NOTES (RULEBOOK "Messaging Connector Decision" + Amendments 1–2):
 * - Replaced JMS @MessageDriven MessageListener with SmallRye @Incoming.
 * - Channel renamed inventory-updates → "inventory" to match the configured
 *   mp.messaging.incoming.inventory.* channel (Kafka topic `orders`, consumer
 *   group `coolstore-inventory`). The previous channel name caused application
 *   startup failure (channel without connector configuration).
 * - @Transactional per RULEBOOK Amendment 1: SmallRye @Incoming methods do NOT
 *   auto-run in transactions; JPA writes require an explicit jakarta.transaction
 *   boundary (TransactionRequiredException risk otherwise).
 * - Inventory is updated via CatalogService.updateInventoryItems (the same
 *   mechanism as OrderServiceMDB), replacing the prior reflection-based
 *   setQuantity(orderItem, ...) which wrote the new quantity onto the ORDER
 *   ITEM (a no-op for the database) instead of the catalog inventory.
 * - Deserialization/processing failures propagate so the dead-letter-queue
 *   failure-strategy (inventory-dlq) can quarantine poison messages; the prior
 *   catch-all swallowed errors, silently disabling the DLQ.
 *
 * Configuration (application.properties):
 *   mp.messaging.connector.smallrye-kafka.bootstrap.servers=...
 *   mp.messaging.incoming.inventory.connector=smallrye-kafka
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
   * Processes incoming order messages from the `inventory` channel (Kafka topic
   * `orders`, consumer group `coolstore-inventory`). Deserializes the Order and
   * decrements catalog inventory for each ordered item, warning when stock
   * falls below the configured low-inventory threshold.
   *
   * @param orderStr the order JSON string from the Kafka message
   */
  @Incoming("inventory")
  @Transactional
  public void processUpdate(String orderStr) {
    Log.info("Received inventory notification message");

    Order order = Transformers.jsonToOrder(orderStr);

    order.getItemList().forEach(this::processOrderItem);
  }

  /**
   * Processes a single order item, updating inventory and alerting if below threshold.
   *
   * @param orderItem the order item to process
   */
  private void processOrderItem(OrderItem orderItem) {
    String productId = orderItem.getProductId();
    int requestedQuantity = orderItem.getQuantity();

    try {
      catalogService.updateInventoryItems(productId, requestedQuantity);
    } catch (IllegalStateException e) {
      // Unknown catalog item / missing inventory: warn but keep processing the
      // remaining items (parity with Java EE MDB behavior of non-fatal per-item misses).
      Log.warnf("Inventory update skipped for item %s: %s", productId, e.getMessage());
      return;
    }

    Log.debugf("Updated inventory for item %s (-%d)", productId, requestedQuantity);

    int newQuantity = currentQuantity(productId);
    if (newQuantity >= 0 && newQuantity < lowThreshold) {
      Log.warnf(
          "Inventory for item %s is below threshold. Current: %d, threshold: %d",
          productId, newQuantity, lowThreshold);
    }
  }

  private int currentQuantity(String productId) {
    try {
      return catalogService.getCatalogItemById(productId).getInventory().getQuantity();
    } catch (Exception e) {
      return -1;
    }
  }
}
