package com.redhat.coolstore.model;

import java.io.Serializable;

// TODO(MIGRATION): RULEBOOK.md provides no guidance on Serializable for model POJOs.
// Verify if java.io.Serializable is needed - class used only in JSON serialization
// via Transformers.shoppingCartToJson (no JMS object messaging, no remote EJB calls).
// Quarkus best practice: remove Serializable from non-passivation, non-persistence contexts.
public class Promotion implements Serializable {

	private static final long serialVersionUID = 1L;

	private String itemId;

	private double percentOff;

	public Promotion() {

	}

	public Promotion(String itemId, double percentOff) {
		super();
		this.itemId = itemId;
		this.percentOff = percentOff;
	}

	public String getItemId() {
		return itemId;
	}

	public void setItemId(String itemId) {
		this.itemId = itemId;
	}

	public double getPercentOff() {
		return percentOff;
	}

	public void setPercentOff(double percentOff) {
		this.percentOff = percentOff;
	}

	@Override
	public String toString() {
		return "Promotion [itemId=" + itemId + ", percentOff=" + percentOff
				+ "]";
	}

}
