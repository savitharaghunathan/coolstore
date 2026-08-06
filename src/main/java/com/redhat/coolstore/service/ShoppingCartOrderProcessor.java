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

    // TODO: Configure JMS producer for Quarkus Artemis
    // Requires proper Artemis broker configuration in application.properties
  
    public void process(ShoppingCart cart) {
        log.info("Order processed (JMS messaging to be configured): " + Transformers.shoppingCartToJson(cart));
        // TODO: Implement JMS producer when Artemis broker is configured
    }

}
