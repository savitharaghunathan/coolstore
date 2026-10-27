package com.redhat.coolstore.utils;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import com.redhat.coolstore.model.Order;

/**
 * Structured audit-event logger (migration TG6).
 *
 * Replaces the private com.enterprise.audit.logging library
 * (lib/audit-logging-library-1.0.0.jar / 2.0.0.jar — not available in Maven
 * Central). Integration decision (option c, recorded in migration notes):
 * SLF4J-based structured audit events. Events are emitted on the dedicated
 * "AUDIT" logger category with an AUDIT marker and JSON payload, so the
 * existing quarkus-logging-json output captures them as structured records
 * and log collectors can route them by marker/category.
 *
 * Event schema (TG6.7):
 * - ORDER_PERSISTED: orderId, orderValue, itemCount, customerName
 * - INVENTORY_UPDATED: productId, quantity
 */
@ApplicationScoped
public class AuditLogger {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT.coolstore");
    private static final Marker AUDIT = MarkerFactory.getMarker("AUDIT");

    /** Compliance event: an order was persisted (TG6.2 — order persisted). */
    public void orderPersisted(Order order) {
        if (order == null) {
            return;
        }
        int itemCount = order.getItemList() == null ? 0 : order.getItemList().size();
        audit.info(AUDIT, String.format(
                "{\"event\":\"ORDER_PERSISTED\",\"orderId\":%d,\"orderValue\":%.2f,\"itemCount\":%d,\"customerName\":\"%s\"}",
                order.getOrderId(), order.getOrderValue(), itemCount,
                order.getCustomerName() == null ? "" : order.getCustomerName()));
    }

    /** Compliance event: catalog inventory was decremented (TG6.2 — inventory updated). */
    public void inventoryUpdated(String productId, int quantity) {
        audit.info(AUDIT, String.format(
                "{\"event\":\"INVENTORY_UPDATED\",\"productId\":\"%s\",\"quantity\":%d}",
                productId, quantity));
    }
}
