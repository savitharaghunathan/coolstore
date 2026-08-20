package com.redhat.coolstore.model;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "PRODUCT_CATALOG")
public class CatalogItemEntity implements Serializable {

	private static final long serialVersionUID = -7304814269819778382L;

	@Id
	private String itemId;

    @Column(length = 80)
    private String name;

	@Column(name="description",columnDefinition = "text")
	private String desc;

    @Column
	private double price;

	// TODO: Verify EAGER fetch doesn't break existing test mocks for InventoryEntity [RULE: Section 8 - Testing]
	@OneToOne(cascade = CascadeType.ALL,fetch=FetchType.EAGER)
    @PrimaryKeyJoinColumn
	private InventoryEntity inventory;

	// TODO: Confirm InventoryEntity doesn't need back-reference to CatalogItemEntity for bidirectional navigation

	public CatalogItemEntity() {
	}

	public String getItemId() {
		return itemId;
	}

	public void setItemId(String itemId) {
		this.itemId = itemId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	public double getPrice() {
		return price;
	}

	public void setPrice(double price) {
		this.price = price;
	}

    public InventoryEntity getInventory() {
        return inventory;
    }

    public void setInventory(InventoryEntity inventory) {
        this.inventory = inventory;
    }

    @Override
	public String toString() {
		return "CatalogItemEntity [itemId=" + itemId + ", name=" + name + ", desc="
				+ desc + ", price=" + price + "]";
    }

}
