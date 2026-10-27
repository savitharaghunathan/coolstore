package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * OrderServiceMDB - Quarkus port of the Java EE 7 OrderServiceMDB (@MessageDriven).
 *
 * MIGRATION NOTES:
 * - @MessageDriven JMS MDB -> SmallRye Reactive Messaging @Incoming("orders") Kafka
 *   consumer (RULEBOOK Pattern 1B), consumer group `coolstore-orders`
 *   (mp.messaging.incoming.orders.group.id in application.properties).
 * - @Transactional is REQUIRED on @Incoming methods (RULEBOOK Amendment 1, TG1.4):
 *   unlike EJB MDBs, SmallRye Reactive Messaging does NOT start container-managed
 *   transactions. Without it, em.persist()/em.merge() in OrderService/CatalogService
 *   would run outside a transaction and order persistence would fail.
 * - System.out.println -> Quarkus Log (RULEBOOK §6 logging modernization).
 */
@ApplicationScoped
public class OrderServiceMDB {

	@Inject
	OrderService orderService;

	@Inject
	CatalogService catalogService;

	@Incoming("orders")
	@Transactional
	public void onMessage(String orderStr) {
		Log.info("Order message received");
		if (orderStr != null && !orderStr.isEmpty()) {
			Log.debugf("Received order: %s", orderStr);
			Order order = Transformers.jsonToOrder(orderStr);
			orderService.save(order);
			order.getItemList().forEach(orderItem -> {
				catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
			});
		}
	}

}
