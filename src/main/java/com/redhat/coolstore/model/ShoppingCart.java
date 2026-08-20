package com.redhat.coolstore.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ShoppingCart - Data Transfer Object (DTO) for shopping cart operations.
 * Plain POJO (not a CDI bean). Contains mutable per-request state.
 *
 * [TODO(port)] Validate JSON serialization works with Kafka once OrderServiceMDB is migrated
 * to SmallRye @Incoming consumer. Verify double fields (NaN/Infinity) and List<ShoppingCartItem>
 * serialize/deserialize correctly through StringSerializer/Jackson pipeline.
 */
public class ShoppingCart implements Serializable {

	private static final long serialVersionUID = -1108043957592113528L;

	private double cartItemTotal;

	private double cartItemPromoSavings;

	private double shippingTotal;

	private double shippingPromoSavings;

	private double cartTotal;

	private List<ShoppingCartItem> shoppingCartItemList = new ArrayList<>();

	public ShoppingCart() {

	}

	public List<ShoppingCartItem> getShoppingCartItemList() {
		return Collections.unmodifiableList(shoppingCartItemList);
	}

	public void setShoppingCartItemList(List<ShoppingCartItem> shoppingCartItemList) {
		this.shoppingCartItemList = (shoppingCartItemList != null) ? shoppingCartItemList : new ArrayList<>();
	}

	public void resetShoppingCartItemList() {
		shoppingCartItemList = new ArrayList<>();
	}

	public void addShoppingCartItem(ShoppingCartItem sci) {

		if ( sci != null ) {

			shoppingCartItemList.add(sci);

		}

	}

	public boolean removeShoppingCartItem(ShoppingCartItem sci) {

		boolean removed = false;

		if ( sci != null ) {

			removed = shoppingCartItemList.remove(sci);

		}

		return removed;

	}

	public double getCartItemTotal() {
		return cartItemTotal;
	}

	public void setCartItemTotal(double cartItemTotal) {
		this.cartItemTotal = cartItemTotal;
	}

	public double getShippingTotal() {
		return shippingTotal;
	}

	public void setShippingTotal(double shippingTotal) {
		this.shippingTotal = shippingTotal;
	}

	public double getCartTotal() {
		return cartTotal;
	}

	public void setCartTotal(double cartTotal) {
		this.cartTotal = cartTotal;
	}

	public double getCartItemPromoSavings() {
		return cartItemPromoSavings;
	}

	public void setCartItemPromoSavings(double cartItemPromoSavings) {
		this.cartItemPromoSavings = cartItemPromoSavings;
	}

	public double getShippingPromoSavings() {
		return shippingPromoSavings;
	}

	public void setShippingPromoSavings(double shippingPromoSavings) {
		this.shippingPromoSavings = shippingPromoSavings;
	}

	@Override
	public String toString() {
		return "ShoppingCart [cartItemTotal=" + cartItemTotal
				+ ", cartItemPromoSavings=" + cartItemPromoSavings
				+ ", shippingTotal=" + shippingTotal
				+ ", shippingPromoSavings=" + shippingPromoSavings
				+ ", cartTotal=" + cartTotal + ", shoppingCartItemList="
				+ shoppingCartItemList + "]";
	}
}
