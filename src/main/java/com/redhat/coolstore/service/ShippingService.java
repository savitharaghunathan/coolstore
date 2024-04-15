package com.redhat.coolstore.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.QueryParam;
import jakarta.enterprise.context.ApplicationScoped;

import com.redhat.coolstore.model.ShoppingCart;

@ApplicationScoped
@Path("/shipping")
public class ShippingService {

    @POST
    @Path("/calculate-shipping")
    public double calculateShipping(@QueryParam("cartItemTotal") double cartItemTotal) {

        if (cartItemTotal >= 0 && cartItemTotal < 25) {

            return 2.99;

        } else if (cartItemTotal >= 25 && cartItemTotal < 50) {

            return 4.99;

        } else if (cartItemTotal >= 50 && cartItemTotal < 75) {

            return 6.99;

        } else if (cartItemTotal >= 75 && cartItemTotal < 100) {

            return 8.99;

        } else if (cartItemTotal >= 100 && cartItemTotal < 10000) {

            return 10.99;

        }

        return 0;

    }

    @POST
    @Path("/calculate-shipping-insurance")
    public double calculateShippingInsurance(@QueryParam("cartItemTotal") double cartItemTotal) {

        if (cartItemTotal >= 25 && cartItemTotal < 100) {

            return getPercentOfTotal(cartItemTotal, 0.02);

        } else if (cartItemTotal >= 100 && cartItemTotal < 500) {

            return getPercentOfTotal(cartItemTotal, 0.015);

        } else if (cartItemTotal >= 500 && cartItemTotal < 10000) {

            return getPercentOfTotal(cartItemTotal, 0.01);

        }

        return 0;
    }

    private static double getPercentOfTotal(double value, double percentOfTotal) {
        return BigDecimal.valueOf(value * percentOfTotal)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

}
