package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import java.util.List;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

@ApplicationScoped
public class OrderService {

  @Inject
  Logger log;

  @Inject
  private EntityManager em;

  public void save(Order order) {
    em.persist(order);
    log.info("Order saved: " + order.getOrderId());
  }

  public List<Order> getOrders() {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Order> criteria = cb.createQuery(Order.class);
    Root<Order> member = criteria.from(Order.class);
    criteria.select(member);
    return em.createQuery(criteria).getResultList();
  }

  public Order getOrderById(long id) {
    return em.find(Order.class, id);
  }

  // TODO: Re-integrate audit logging library once compatible version is available
  // The audit-logging-library needs to be updated to work with Quarkus

}