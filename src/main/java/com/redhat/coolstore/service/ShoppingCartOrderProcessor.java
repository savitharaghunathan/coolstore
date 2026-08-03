package com.redhat.coolstore.service;

import java.util.logging.Logger;
import javax.inject.Inject;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    @Inject
    @Channel("orders")
    Emitter<String> ordersEmitter;
  
    public void  process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersEmitter.send(Transformers.shoppingCartToJson(cart));
    }



}
