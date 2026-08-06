package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

/**
 * Message-Driven Bean for inventory notifications
 * Note: Requires Artemis broker configuration in application.properties
 */
@ApplicationScoped
public class InventoryNotificationMDB {

    private static final int LOW_THRESHOLD = 50;

    @Inject
    private CatalogService catalogService;

    // TODO: Configure JMS consumer via application.properties
    public void onMessage(Message rcvMessage) {
        TextMessage msg;
        try {
            System.out.println("received message inventory");
            if (rcvMessage instanceof TextMessage) {
                msg = (TextMessage) rcvMessage;
                String orderStr = msg.getBody(String.class);
                Order order = Transformers.jsonToOrder(orderStr);
                order.getItemList().forEach(orderItem -> {
                    int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId()).getInventory().getQuantity();
                    int new_quantity = old_quantity - orderItem.getQuantity();
                    if (new_quantity < LOW_THRESHOLD) {
                        System.out.println("Inventory for item " + orderItem.getProductId() + " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
                    } else {
                        orderItem.setQuantity(new_quantity);
                    }
                });
            }
        } catch (JMSException jmse) {
            System.err.println("An exception occurred: " + jmse.getMessage());
        }
    }
}