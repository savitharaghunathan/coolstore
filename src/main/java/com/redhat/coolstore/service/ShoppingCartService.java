package com.redhat.coolstore.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

/**
 * ShoppingCartService - Quarkus port of Java EE 7 @Stateful bean.
 *
 * MIGRATION NOTES (TG 3.5, closes RULEBOOK §2 line 118 [BUG(port)]):
 * - @Stateful/@SessionScoped → @ApplicationScoped with a cartId-keyed cart map.
 *   Quarkus Arc does not implement the CDI session context: @SessionScoped beans
 *   are skipped at discovery (UnsatisfiedResolutionException at deployment —
 *   runtime-only failure mode, surfaced by the @QuarkusTest bootstrap).
 * - The REST contract already passes cartId on every call (front-end managed),
 *   so the cartId-keyed map resolves the documented single-cart-per-session
 *   limitation: multi-cart / tabbed browsing now works as the original
 *   Java EE @Stateful design intended.
 * - @Transactional retained on checkOutShoppingCart()/priceShoppingCart():
 *   CDI provides no automatic transaction boundaries (RULEBOOK Amendment 1 —
 *   EJB container-managed transaction semantics must be preserved explicitly).
 */
@ApplicationScoped
public class ShoppingCartService {

    private static final Logger log = LoggerFactory.getLogger(ShoppingCartService.class);

    @Inject
    ProductService productServices;

    @Inject
    PromoService ps;

    @Inject
    ShoppingCartOrderProcessor shoppingCartOrderProcessor;

    @Inject
    ShippingService shippingService;

    private final Map<String, ShoppingCart> carts = new ConcurrentHashMap<>();

    public ShoppingCartService() {
    }

    public ShoppingCart getShoppingCart(String cartId) {
        return carts.computeIfAbsent(cartId, id -> new ShoppingCart());
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

        // Null safety per RULEBOOK §6 defensive programming.
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
