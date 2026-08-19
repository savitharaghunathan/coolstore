package com.redhat.coolstore.service;

import com.redhat.coolstore.model.CatalogItemEntity;
import com.redhat.coolstore.model.InventoryEntity;
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
 * Native Quarkus port of CatalogService.
 * - Replaced @Stateless with @Singleton for simplified lifecycle in Quarkus.
 * - Updated to Jakarta EE 9+ (jakarta.* instead of javax.*).
 * - Removed redundant empty constructor (Quarkus handles injection).
 * - Integrated Quarkus logging (Log) instead of java.util.logging.Logger.
 * - Added comprehensive JavaDoc.
 */
@Singleton
@Unremovable
public class CatalogService {

  @Inject
  private EntityManager em;

  /**
   * Retrieves all catalog items from the database.
   *
   * @return list of all catalog items
   */
  public List<CatalogItemEntity> getCatalogItems() {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<CatalogItemEntity> criteria = cb.createQuery(CatalogItemEntity.class);
    Root<CatalogItemEntity> root = criteria.from(CatalogItemEntity.class);
    criteria.select(root);
    return em.createQuery(criteria).getResultList();
  }

  /**
   * Retrieves a catalog item by its ID.
   *
   * @param itemId the item ID
   * @return the catalog item entity, or null if not found
   */
  public CatalogItemEntity getCatalogItemById(String itemId) {
    return em.find(CatalogItemEntity.class, itemId);
  }

  /**
   * Updates inventory quantity for a catalog item.
   * Deducts the specified amount from the current inventory.
   *
   * @param itemId the catalog item ID
   * @param deducts the quantity to deduct
   * @throws IllegalStateException if catalog item or inventory not found
   */
  public void updateInventoryItems(String itemId, int deducts) {
    CatalogItemEntity catalogItem = getCatalogItemById(itemId);
    if (catalogItem == null) {
      Log.warnf("Catalog item not found: %s", itemId);
      throw new IllegalStateException("Catalog item not found: " + itemId);
    }

    InventoryEntity inventoryEntity = catalogItem.getInventory();
    if (inventoryEntity == null) {
      Log.warnf("Inventory not found for catalog item: %s", itemId);
      throw new IllegalStateException("Inventory not found for item: " + itemId);
    }

    int currentQuantity = inventoryEntity.getQuantity();
    int newQuantity = currentQuantity - deducts;
    inventoryEntity.setQuantity(newQuantity);
    em.merge(inventoryEntity);

    Log.debugf("Updated inventory for item %s: %d -> %d", itemId, currentQuantity, newQuantity);
  }
}
