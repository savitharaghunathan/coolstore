package com.redhat.coolstore.rest.client;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.redhat.coolstore.model.ShoppingCart;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.POST;

@Path("/shipping")
@RegisterRestClient(configKey="shipping-service-api")
public interface ShippingServiceClient {

    @POST
    @Path("/calculateShipping")
    public double calculateShipping(ShoppingCart sc);

    @POST
    @Path("/calculateShippingInsurance")
    public double calculateShippingInsurance(ShoppingCart sc);

}
