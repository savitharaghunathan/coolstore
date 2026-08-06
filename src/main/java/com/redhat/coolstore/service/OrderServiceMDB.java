package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

/**
 * Message-Driven Bean for processing orders from JMS topic
 * Note: Requires Artemis broker configuration in application.properties
 */
@ApplicationScoped
public class OrderServiceMDB { 

	@Inject
	OrderService orderService;

	@Inject
	CatalogService catalogService;

	// TODO: Configure JMS consumer via application.properties
	// quarkus.artemis.jms.consumers.orders.destination=orders
	public void onMessage(Message rcvMessage) {
		System.out.println("\nMessage recd !");
		TextMessage msg = null;
		try {
				if (rcvMessage instanceof TextMessage) {
						msg = (TextMessage) rcvMessage;
						String orderStr = msg.getBody(String.class);
						System.out.println("Received order: " + orderStr);
						Order order = Transformers.jsonToOrder(orderStr);
						System.out.println("Order object is " + order);
						orderService.save(order);
						order.getItemList().forEach(orderItem -> {
							catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
						});
				}
		} catch (JMSException e) {
			throw new RuntimeException(e);
		}
	}

}