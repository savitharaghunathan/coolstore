package com.redhat.coolstore.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.service.ShippingService;

@Path("/shipping")
public class ShippingEndpoint {

    @Inject
    ShippingService shippingService;

    @POST
    @Path("/calculate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public double calculateShipping(ShoppingCart sc) {
        return shippingService.calculateShipping(sc);
    }

    @POST
    @Path("/insurance")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public double calculateShippingInsurance(ShoppingCart sc) {
        return shippingService.calculateShippingInsurance(sc);
    }
}
