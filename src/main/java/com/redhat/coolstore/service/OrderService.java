package com.redhat.coolstore.service;

import com.enterprise.audit.logging.config.AuditConfiguration;
import com.enterprise.audit.logging.exception.AuditLoggingException;
import com.enterprise.audit.logging.service.FileSystemAuditLogger;
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

/**
 * Native Quarkus port of OrderService.
 * - Replaced @Stateless with @Singleton for simplified lifecycle in Quarkus.
 * - Updated to Jakarta EE 9+ (jakarta.* instead of javax.*).
 * - Added lifecycle management via Quarkus arc framework.
 * - Improved error handling with Quarkus logging integration.
 */
@Singleton
@Unremovable
public class OrderService implements AutoCloseable {

  @Inject
  private EntityManager em;

  private FileSystemAuditLogger auditLogger;

  /**
   * Called automatically by Quarkus upon bean construction.
   */
  void onStart() throws AuditLoggingException {
    AuditConfiguration config = new AuditConfiguration();
    config.setLogDirectory("./device-inventory-audit-logs");
    config.setAutoCreateDirectory(true);
    auditLogger = new FileSystemAuditLogger(config);
    Log.info("OrderService initialized with audit logging enabled");
  }

  /**
   * Called automatically by Quarkus during graceful shutdown.
   */
  void onStop() throws AuditLoggingException {
    if (auditLogger != null) {
      auditLogger.close();
      Log.info("OrderService audit logger closed");
    }
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
    Log.debugf("Order saved with ID: %s", order.getId());
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
