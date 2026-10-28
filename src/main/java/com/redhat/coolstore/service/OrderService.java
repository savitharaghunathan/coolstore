package com.redhat.coolstore.service;

import com.enterprise.audit.logging.model.AuditEvent;
import com.enterprise.audit.logging.model.AuditResult;
import com.enterprise.audit.logging.service.StreamableAuditLogger;
import com.redhat.coolstore.model.Order;

import io.quarkus.arc.Unremovable;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Native Quarkus port of OrderService with audit-logging-library 2.x integration
 * (tasks 2.3-2.5; rules audit-logging-0002..0005):
 * - AuditEvent.builder() -> direct Java record instantiation (rule 0002)
 * - StreamableAuditLogger over TCP replaces FileSystemAuditLogger (rule 0003)
 * - logEventAsync(event) replaces sync logEvent/logSuccess/logFailure (rules 0004, 0005)
 * TCP endpoint externalized via MicroProfile Config (audit.logging.host/port).
 */
@Singleton
@Unremovable
public class OrderService implements AutoCloseable {

  @Inject
  private EntityManager em;

  @Inject
  @Named("streamableAuditLogger")
  private StreamableAuditLogger auditLogger;

  void onStart() {
    Log.info("OrderService initialized");
  }

  void onStop() {
    Log.info("OrderService shutdown");
  }

  @Override
  public void close() throws Exception {
    onStop();
  }

  /**
   * Persists an Order entity and emits a non-blocking audit event.
   *
   * @param order the order to persist
   */
  public void save(Order order) {
    try {
      em.persist(order);
      Log.debugf("Order saved with ID: %s", order.getOrderId());
      audit(order, AuditResult.SUCCESS, "Order persisted",
          Map.of("orderValue", order.getOrderValue(), "itemCount", order.getItemList().size()));
    } catch (RuntimeException e) {
      audit(order, AuditResult.FAILURE, "Order persist failed: " + e.getMessage(), Map.of());
      throw e;
    }
  }

  /**
   * Builds a full AuditEvent record and emits it asynchronously (task 2.5).
   * Defensive null check keeps plain unit tests (no CDI producer) green.
   */
  private void audit(Order order, AuditResult result, String message, Map<String, Object> details) {
    if (auditLogger == null) {
      return;
    }
    AuditEvent event = new AuditEvent(
        Instant.now(),                                   // timestamp
        "ORDER_PERSISTED",                               // event_type
        null,                                            // user_id (Kafka consumer path has no identity context)
        null,                                            // session_id (session-free design, task 3.4)
        "coolstore",                                     // application
        "OrderService",                                  // component
        "save",                                          // action
        order != null ? "order/" + order.getCustomerEmail() : "order/unknown", // resource
        result,                                          // result
        message,                                         // message
        details,                                         // details
        null, null, null);                               // correlation_id, source_ip, user_agent
    auditLogger.logEventAsync(event);                   // non-blocking (rule 0004)
  }

  /**
   * Retrieves all orders from the database.
   *
   * @return list of all orders
   */
  public List<Order> getOrders() {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Order> criteria = cb.createQuery(Order.class);
    Root<Order> root = criteria.from(Order.class);
    criteria.select(root);
    return em.createQuery(criteria).getResultList();
  }

  /**
   * Retrieves an order by its ID.
   *
   * @param id the order ID
   * @return the order, or null if not found
   */
  public Order getOrderById(long id) {
    return em.find(Order.class, id);
  }
}
