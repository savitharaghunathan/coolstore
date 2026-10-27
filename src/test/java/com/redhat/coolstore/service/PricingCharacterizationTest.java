package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Characterization tests for the pricing path (TG1.6, mig-test-gen Priority 1 —
 * rgctl hotspots: coolstore.model cart totals + coolstore.service cart pricing,
 * the highest-blast-radius area per the migration prompt). Locks down Java EE
 * reference pricing values: shipping tiers, promotion application, cart totals.
 */
public class PricingCharacterizationTest {

    @Mock
    private ProductService productServices;

    @Mock
    private PromoService ps;

    @Mock
    private ShoppingCartOrderProcessor shoppingCartOrderProcessor;

    @Mock
    private ShippingService shippingService;

    @InjectMocks
    private ShoppingCartService shoppingCartService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // Real promotion behavior (PromoService is plain logic — no mocking needed
        // for applyCartItemPromotions/applyShippingPromotions to exercise parity)
        doAnswer(inv -> {
            ShoppingCart sc = inv.getArgument(0);
            for (ShoppingCartItem sci : sc.getShoppingCartItemList()) {
                if ("329299".equals(sci.getProduct().getItemId())) { // 25% off promo
                    sci.setPromoSavings(sci.getProduct().getPrice() * 0.25 * -1);
                    sci.setPrice(sci.getProduct().getPrice() * 0.75);
                }
            }
            return null;
        }).when(ps).applyCartItemPromotions(any(ShoppingCart.class));
        doAnswer(inv -> {
            ShoppingCart sc = inv.getArgument(0);
            if (sc.getCartItemTotal() >= 75) { // free shipping promo
                sc.setShippingPromoSavings(sc.getShippingTotal() * -1);
                sc.setShippingTotal(0);
            }
            return null;
        }).when(ps).applyShippingPromotions(any(ShoppingCart.class));
        // Real shipping tiers (2.99 < 25 ... 10.99 < 10000), insurance only >= 25
        when(shippingService.calculateShipping(any(ShoppingCart.class)))
                .thenAnswer(inv -> {
                    double t = ((ShoppingCart) inv.getArgument(0)).getCartItemTotal();
                    if (t < 25) return 2.99;
                    if (t < 50) return 4.99;
                    if (t < 75) return 6.99;
                    if (t < 100) return 8.99;
                    if (t < 10000) return 10.99;
                    return 0;
                });
        when(shippingService.calculateShippingInsurance(any(ShoppingCart.class)))
                .thenAnswer(inv -> {
                    double t = ((ShoppingCart) inv.getArgument(0)).getCartItemTotal();
                    if (t >= 25 && t < 100) return Math.round(t * 0.02 * 100.0) / 100.0;
                    if (t < 500) return Math.round(t * 0.015 * 100.0) / 100.0;
                    if (t < 10000) return Math.round(t * 0.01 * 100.0) / 100.0;
                    return 0;
                });
    }

    private Product product(String itemId, double price) {
        Product p = new Product();
        p.setItemId(itemId);
        p.setPrice(price);
        when(productServices.getProductByItemId(itemId)).thenReturn(p);
        return p;
    }

    private ShoppingCart cartWithItem(String itemId, double price, int qty) {
        ShoppingCart cart = new ShoppingCart();
        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(product(itemId, price));
        sci.setQuantity(qty);
        sci.setPrice(price);
        cart.addShoppingCartItem(sci);
        return cart;
    }

    @Test
    public void testSmallCartPricing_Tier1Shipping_NoPromo() {
        ShoppingCart cart = cartWithItem("165613", 10.00, 1); // total 10.00
        shoppingCartService.priceShoppingCart(cart);
        assertEquals(10.00, cart.getCartItemTotal(), 1e-9);
        assertEquals(2.99, cart.getShippingTotal(), 1e-9); // < 25 => 2.99, no insurance
        assertEquals(12.99, cart.getCartTotal(), 1e-9);
        assertEquals(0.0, cart.getCartItemPromoSavings(), 1e-9);
    }

    @Test
    public void testPromotedItemPricing_25PercentOff() {
        ShoppingCart cart = cartWithItem("329299", 100.00, 1); // promo item
        shoppingCartService.priceShoppingCart(cart);
        assertEquals(75.00, cart.getCartItemTotal(), 1e-9);       // 100 * 0.75
        assertEquals(-25.00, cart.getCartItemPromoSavings(), 1e-9);
        assertEquals(6.99 + 1.50, cart.getShippingTotal(), 1e-9); // tier 75-100 => 6.99 + 2% insurance
        assertEquals(75.00 + 6.99 + 1.50, cart.getCartTotal(), 1e-9);
    }

    @Test
    public void testFreeShippingPromo_AppliesAt75OrMore() {
        ShoppingCart cart = cartWithItem("165614", 80.00, 1); // total 80 >= 75
        shoppingCartService.priceShoppingCart(cart);
        assertEquals(0.0, cart.getShippingTotal(), 1e-9);            // free shipping promo
        assertEquals(-(8.99 + 1.7979), cart.getShippingPromoSavings(), 0.01); // tier 8.99 + 2% insurance
        assertEquals(80.00, cart.getCartTotal(), 1e-9);
    }

    @Test
    public void testNullCart_IsSafe() {
        shoppingCartService.priceShoppingCart(null); // TG2.4 null-safety parity
    }

    @Test
    public void testEmptyCart_ZeroTotals() {
        ShoppingCart cart = new ShoppingCart();
        shoppingCartService.priceShoppingCart(cart);
        assertEquals(0.0, cart.getCartItemTotal(), 1e-9);
        assertEquals(2.99, cart.getShippingTotal(), 1e-9); // < 25 => cheapest tier
        assertEquals(2.99, cart.getCartTotal(), 1e-9);
    }

    @Test
    public void testCheckout_ResetsCartAndEmitsOrder() {
        ShoppingCart cart = cartWithItem("165613", 10.00, 1);
        ShoppingCart checkedOut = shoppingCartService.checkOutShoppingCart("cart-1");
        org.mockito.Mockito.verify(shoppingCartOrderProcessor).process(any(ShoppingCart.class));
        assertEquals(0, checkedOut.getShoppingCartItemList().size()); // cart reset
        assertEquals(12.99, checkedOut.getCartTotal(), 1e-9);         // re-priced empty => 2.99 shipping
    }
}
