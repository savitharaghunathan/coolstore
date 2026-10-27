package com.redhat.coolstore.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.AuditLogger;
import com.redhat.coolstore.utils.Transformers;

/**
 * Kafka consumer for the `orders` topic (consumer group coolstore-orders).
 * Persists the order and decrements catalog inventory.
 *
 * @Transactional is REQUIRED on this @Incoming method (RULEBOOK Amendment 1,
 * TG5.4): SmallRye Reactive Messaging does not provide container-managed
 * transactions like EJB MDBs — without it, the JPA writes below would fail
 * with TransactionRequiredException.
 */
@ApplicationScoped
public class OrderServiceMDB {

	@Inject
	OrderService orderService;

	@Inject
	CatalogService catalogService;

	@Inject
	AuditLogger auditLogger;

	@Incoming("orders")
	@Transactional
	public void onMessage(String orderStr) {
		if (orderStr != null && !orderStr.isEmpty()) {
			Log.infof("Received order message: %s", orderStr);
			Order order = Transformers.jsonToOrder(orderStr);
			orderService.save(order);
			order.getItemList().forEach(orderItem -> {
				catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
				auditLogger.inventoryUpdated(orderItem.getProductId(), orderItem.getQuantity());
			});
		}
	}

}
