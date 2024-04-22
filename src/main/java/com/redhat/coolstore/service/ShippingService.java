package com.redhat.coolstore.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.ejb.Remove;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import com.redhat.coolstore.model.ShoppingCart;

@Path("/shipping")
public class ShippingService {

    @POST
    @Path("/calculate-shipping")
    public Response calculateShipping(@PathParam("cartId") Long cartId) {
        ShoppingCart sc = // get ShoppingCart by cartId;
        if (sc != null) {
            if (sc.getCartItemTotal() >= 0 && sc.getCartItemTotal() < 25) {
                return Response.ok(2.99).build();
            } else if (sc.getCartItemTotal() >= 25 && sc.getCartItemTotal() < 50) {
                return Response.ok(4.99).build();
            } else if (sc.getCartItemTotal() >= 50 && sc.getCartItemTotal() < 75) {
                return Response.ok(6.99).build();
            } else if (sc.getCartItemTotal() >= 75 && sc.getCartItemTotal() < 100) {
                return Response.ok(8.99).build();
            } else if (sc.getCartItemTotal() >= 100 && sc.getCartItemTotal() < 10000) {
                return Response.ok(10.99).build();
            }
        }
        return Response.ok(0).build();
    }

    @POST
    @Path("/calculate-shipping-insurance")
    public Response calculateShippingInsurance(@PathParam("cartId") Long cartId) {
        ShoppingCart sc = // get ShoppingCart by cartId;
        if (sc != null) {
            if (sc.getCartItemTotal() >= 25 && sc.getCartItemTotal() < 100) {
                return Response.ok(getPercentOfTotal(sc.getCartItemTotal(), 0.02)).build();
            } else if (sc.getCartItemTotal() >= 100 && sc.getCartItemTotal() < 500) {
                return Response.ok(getPercentOfTotal(sc.getCartItemTotal(), 0.015)).build();
            } else if (sc.getCartItemTotal() >= 500 && sc.getCartItemTotal() < 10000) {
                return Response.ok(getPercentOfTotal(sc.getCartItemTotal(), 0.01)).build();
            }
        }
        return Response.ok(0).build();
    }

    private static double getPercentOfTotal(double value, double percentOfTotal) {
        return BigDecimal.valueOf(value * percentOfTotal)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

}
