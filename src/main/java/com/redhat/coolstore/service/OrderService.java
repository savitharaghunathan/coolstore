package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;

import io.quarkus.arc.Unremovable;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;

import com.redhat.coolstore.utils.AuditLogger;

/**
 * Native Quarkus port of OrderService.
 * - Replaced @Stateless with @Singleton for simplified lifecycle in Quarkus.
 * - Updated to Jakarta EE 9+ (jakarta.* instead of javax.*).
 * - Audit logging restored (TG6): the private com.enterprise.audit.logging
 *   library was replaced by the SLF4J-based AuditLogger (see utils/AuditLogger.java);
 *   events are captured as structured JSON by quarkus-logging-json.
 */
@Singleton
@Unremovable
public class OrderService implements AutoCloseable {

  @Inject
  private EntityManager em;

  @Inject
  private AuditLogger auditLogger;

  /**
   * Called automatically by Quarkus upon bean construction.
   */
  void onStart() {
    Log.info("OrderService initialized");
  }

  /**
   * Called automatically by Quarkus during graceful shutdown.
   */
  void onStop() {
    Log.info("OrderService shutdown");
  }

  @Override
  public void close() throws Exception {
    onStop();
  }

  /**
   * Persists an Order entity to the database.
   *
   * @param order the order to persist
   */
  public void save(Order order) {
    em.persist(order);
    auditLogger.orderPersisted(order);
    Log.debugf("Order saved with ID: %s", order.getOrderId());
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
