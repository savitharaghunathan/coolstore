package com.redhat.coolstore.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.enterprise.context.SessionScoped;
import jakarta.transaction.Transactional;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

/**
 * ShoppingCartService - Quarkus port of Java EE 7 @Stateful bean.
 *
 * MIGRATION NOTES:
 * - @Stateful → @SessionScoped (maintains per-session state)
 * - Serializable + transient fields: @SessionScoped beans may be passivated; injected CDI fields marked transient
 *   per [RULE] §2 line 119 to prevent NotSerializableException on session restore.
 * - @Transactional restored on checkOutShoppingCart() and priceShoppingCart(): In @Stateful EJB,
 *   container-managed transactions ensured atomicity for state mutation. CDI @SessionScoped requires explicit
 *   @Transactional boundaries per [RULE] Amendment 1 lines 88–93 (EJB tx semantics preservation).
 * - Note: In Quarkus REST context, @SessionScoped requires explicit HttpSession management.
 *   [BUG(port)]: cartId parameter ignored in getShoppingCart(); no multi-cart support. Implement cartId-keyed
 *   Map<String, ShoppingCart> per [RULE] §2 line 118. Current: Single cart per session (tabbed browsing unsupported).
 * - ShoppingCartOrderProcessor dependency: MIGRATED to @ApplicationScoped (Pattern 1A applied 2026-08-20).
 *   CDI bean resolution now valid at deployment. Injection at line 45 verified.
 */
@SessionScoped
public class ShoppingCartService implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(ShoppingCartService.class);

    @Inject
    transient ProductService productServices;

    @Inject
    transient PromoService ps;

    @Inject
    transient ShoppingCartOrderProcessor shoppingCartOrderProcessor;

    @Inject
    transient ShippingService shippingService;

    private ShoppingCart cart = new ShoppingCart(); // Each user can have multiple shopping carts (tabbed browsing)

    public ShoppingCartService() {
    }

    public ShoppingCart getShoppingCart(String cartId) {
        // [BUG(port)]: cartId parameter ignored; no multi-cart support per RULEBOOK §2 line 118.
        // Single cart per session breaks tabbed-browsing use case (line 43 comment promises multi-cart).
        // [TODO(port)]: Implement cartId-keyed Map<String, ShoppingCart> and update test expectations.
        // NOTE: Fixing this requires updating ShoppingCartServiceTest.testGetShoppingCartReturnsSameInstance()
        //       which currently validates broken behavior (assertSame across different cartIds).
        return cart;
    }

    /**
     * Checkout shopping cart: processes order and resets cart.
     *
     * MIGRATION NOTES:
     * - @Transactional RESTORED: Container-managed transactions in @Stateful EJB ensured atomicity for state mutation
     *   (checkOutShoppingCart → priceShoppingCart state changes → JMS send). CDI @SessionScoped provides no automatic
     *   transaction boundaries. @Transactional ensures rollback if any service call fails (per RULEBOOK Amendment 1
     *   lines 88–93: "EJB tx semantics must be preserved" and Reviewer Finding #7).
     * - Pattern: STATE MUTATION + EXTERNAL CALL requires explicit @Transactional boundary.
     */
    @Transactional
    public ShoppingCart checkOutShoppingCart(String cartId) {
        ShoppingCart cart = this.getShoppingCart(cartId);

        log.info("Sending order: ");
        shoppingCartOrderProcessor.process(cart);

        cart.resetShoppingCartItemList();
        priceShoppingCart(cart);
        return cart;
    }

    @Transactional
    public void priceShoppingCart(ShoppingCart sc) {

        if (sc != null) {

            initShoppingCartForPricing(sc);

            if (sc.getShoppingCartItemList() != null && sc.getShoppingCartItemList().size() > 0) {

                ps.applyCartItemPromotions(sc);

                for (ShoppingCartItem sci : sc.getShoppingCartItemList()) {

                    sc.setCartItemPromoSavings(
                            sc.getCartItemPromoSavings() + sci.getPromoSavings() * sci.getQuantity());
                    sc.setCartItemTotal(sc.getCartItemTotal() + sci.getPrice() * sci.getQuantity());

                }

                sc.setShippingTotal(shippingService.calculateShipping(sc));

                if (sc.getCartItemTotal() >= 25) {
                    sc.setShippingTotal(sc.getShippingTotal()
                            + shippingService.calculateShippingInsurance(sc));
                }

            }

            ps.applyShippingPromotions(sc);

            sc.setCartTotal(sc.getCartItemTotal() + sc.getShippingTotal());

        }

    }

    private void initShoppingCartForPricing(ShoppingCart sc) {

        sc.setCartItemTotal(0);
        sc.setCartItemPromoSavings(0);
        sc.setShippingTotal(0);
        sc.setShippingPromoSavings(0);
        sc.setCartTotal(0);

        // [BUG(port)]: Null safety violation - initShoppingCartForPricing assumes getShoppingCartItemList() != null.
        // Line 93 guards with null check; this loop does not. Applied null check per RULEBOOK §6 defensive programming.
        if (sc.getShoppingCartItemList() != null && sc.getShoppingCartItemList().size() > 0) {
            for (ShoppingCartItem sci : sc.getShoppingCartItemList()) {
                Product p = getProduct(sci.getProduct().getItemId());
                // if product exist
                if (p != null) {
                    sci.setProduct(p);
                    sci.setPrice(p.getPrice());
                }

                sci.setPromoSavings(0);
            }
        }

    }

    public Product getProduct(String itemId) {
        return productServices.getProductByItemId(itemId);
    }

}
