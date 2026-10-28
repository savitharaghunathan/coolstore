package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

/**
 * Quarkus port of the Java EE 7 OrderServiceMDB (@MessageDriven on topic/orders).
 *
 * MIGRATION NOTES (RULEBOOK Pattern 1B + Amendment 1):
 * - @MessageDriven → @ApplicationScoped with @Incoming("orders")
 * - @Transactional is REQUIRED: SmallRye @Incoming methods do NOT auto-run in
 *   transactions (unlike EJB MDBs with container-managed transactions). Without it,
 *   em.persist() below fails with TransactionRequiredException at runtime.
 * - System.out.println → SLF4J per RULEBOOK §6.
 * - Order persistence + inventory updates run atomically in one JTA transaction:
 *   any failure rolls back both and nacks the message (dead-letter-queue strategy
 *   configured in application.properties routes it to orders-dlq).
 */
@ApplicationScoped
public class OrderServiceMDB {

	private static final Logger log = LoggerFactory.getLogger(OrderServiceMDB.class);

	@Inject
	OrderService orderService;

	@Inject
	CatalogService catalogService;

	@Incoming("orders")
	@Transactional
	public void onMessage(String orderStr) {
		log.info("Message received on orders channel");
		if (orderStr != null && !orderStr.isEmpty()) {
			log.info("Received order: {}", orderStr);
			Order order = Transformers.jsonToOrder(orderStr);
			log.info("Order object is {}", order);
			orderService.save(order);
			order.getItemList().forEach(orderItem -> {
				catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
			});
		}
	}

}
