package com.redhat.coolstore.rest;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.service.OrderService;

@ApplicationScoped
@Path("/orders")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class OrderEndpoint {

    private static final Logger log = LoggerFactory.getLogger(OrderEndpoint.class);

    private final OrderService os;

    @Inject
    public OrderEndpoint(OrderService os) {
        this.os = os;
    }


    @GET
    @Transactional
    public List<Order> listAll() {
        log.info("Retrieving all orders");
        List<Order> orders = os.getOrders();
        log.info("Retrieved {} orders", orders.size());
        return orders;
    }

    @GET
    @Path("/{orderId}")
    @Transactional
    public Response getOrder(@PathParam("orderId") long orderId) {
        log.info("Retrieving order with id: {}", orderId);
        Order order = os.getOrderById(orderId);
        if (order == null) {
            log.warn("Order not found with id: {}", orderId);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        log.info("Order found with id: {}", orderId);
        return Response.ok(order).build();
    }

}
