package com.redhat.coolstore.service;

import java.util.logging.Logger;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.Emitter;
import io.smallrye.mutiny.Multi;
import io.smallrye.reactive.messaging.Channel;
import io.smallrye.reactive.messaging.EmitterWrapper;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    @Inject
    @Channel("orders")
    EmitterWrapper<String> ordersEmitter;

    @PostConstruct
    public void init() {
        // Any initialization code here
    }

    public void  process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersEmitter.send(Multi.createFrom().item(Transformers.shoppingCartToJson(cart)));
    }



}
