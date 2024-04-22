package com.redhat.coolstore.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.ejb.Singleton;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.POST;

import com.redhat.coolstore.model.ShoppingCart;

@Path("/shipping")
@ApplicationScoped
public class ShippingService {

    @POST
    @Path("/calculateShipping")
    public double calculateShipping(ShoppingCart sc) {
        // method implementation remains the same
    }

    @POST
    @Path("/calculateShippingInsurance")
    public double calculateShippingInsurance(ShoppingCart sc) {
        // method implementation remains the same
    }

    private static double getPercentOfTotal(double value, double percentOfTotal) {
        return BigDecimal.valueOf(value * percentOfTotal)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
