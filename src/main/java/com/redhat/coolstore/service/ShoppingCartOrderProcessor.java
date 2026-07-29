package com.redhat.coolstore.service;

import java.util.logging.Logger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    public void  process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        // TODO: Replace with Reactive Messaging Emitter
        // For now, just log the order
        log.info("Order JSON: " + Transformers.shoppingCartToJson(cart));
    }



}
