package com.redhat.coolstore.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Kafka consumer for order persistence (rulebook Pattern 1B, channel 'orders', group coolstore-orders).
 *
 * MIGRATION NOTES (task 4.5):
 * - @Transactional REQUIRED: SmallRye @Incoming methods do not auto-run in transactions
 *   (unlike EJB MDBs); em.persist() would throw TransactionRequiredException (rulebook Amendment 1).
 * - Idempotency guard: Kafka is at-least-once, so redelivered payloads are fingerprinted and
 *   skipped to prevent duplicate order persistence.
 * - System.out.println -> SLF4J (rulebook §6).
 */
@ApplicationScoped
public class OrderServiceMDB {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceMDB.class);
    private static final int DEDUP_CAPACITY = 10_000;

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    /** Bounded LRU of processed payload fingerprints (duplicate-order protection). */
    private final Map<String, Boolean> seenPayloads = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > DEDUP_CAPACITY;
                }
            });

    @Incoming("orders")
    @Transactional
    public void onMessage(String orderStr) {
        log.info("Message received on orders channel");
        if (orderStr == null || orderStr.isEmpty()) {
            return;
        }
        if (isDuplicate(orderStr)) {
            log.warn("Duplicate order payload detected (at-least-once delivery); skipping");
            return;
        }
        log.info("Received order: {}", orderStr);
        Order order = Transformers.jsonToOrder(orderStr);
        orderService.save(order);
        order.getItemList().forEach(orderItem ->
                catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity()));
    }

    /** Visible for idempotency tests. */
    boolean isDuplicate(String orderStr) {
        String fingerprint = sha256(orderStr);
        synchronized (seenPayloads) {
            if (seenPayloads.containsKey(fingerprint)) {
                return true;
            }
            seenPayloads.put(fingerprint, Boolean.TRUE);
            return false;
        }
    }

    private static String sha256(String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // Digest unavailable: fail open (process the message) rather than drop orders
            return "noid-" + Integer.toHexString(payload.hashCode());
        }
    }
}
