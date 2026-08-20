package com.redhat.coolstore.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

/**
 * ShoppingCartOrderProcessor - Quarkus port of Java EE 7 @Stateless bean.
 *
 * MIGRATION NOTES:
 * - @Stateless → @ApplicationScoped (CDI equivalent for singleton, application-scoped services)
 * - JMSContext + Topic → SmallRye Reactive Messaging @Channel Emitter<String> per RULEBOOK Pattern 1A (lines 29–50)
 * - Messaging connector: Kafka (via application.properties mp.messaging.outgoing.orders.connector=smallrye-kafka)
 * - java.util.logging.Logger → SLF4J per RULEBOOK §6 (logging modernization)
 */
@ApplicationScoped
public class ShoppingCartOrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(ShoppingCartOrderProcessor.class);

    @Inject
    @Channel("orders")
    Emitter<String> orders;

    public void process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        orders.send(Transformers.shoppingCartToJson(cart));
    }

}
