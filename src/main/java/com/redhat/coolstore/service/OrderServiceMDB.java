package com.redhat.coolstore.service;

import jakarta.inject.Inject;
import jakarta.jms.TextMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class OrderServiceMDB implements MessageListener { 

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Incoming("topic/orders")
    @Override
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
