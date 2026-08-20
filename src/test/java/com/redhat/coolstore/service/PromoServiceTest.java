package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.Promotion;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PromoServiceTest - JUnit 5 migration from JUnit 4.
 *
 * Migration notes:
 * - @Before → @BeforeEach
 * - org.junit.Test → org.junit.jupiter.api.Test
 * - org.junit.Assert → org.junit.jupiter.api.Assertions
 *
 * DEPENDENCY ORDERING (Reviewer Finding #3): Promotion.java is still status=TODO in manifest.
 * This test hard-asserts on Promotion's exact field shape (getItemId(), getPercentOff()).
 * When Promotion is translated, verify its field types match these assertions.
 *
 * HARDCODED SEED DATA COUPLING (Reviewer Finding #4): testGetPromotionsContainsKnownPromo()
 * hard-asserts promos.size()==1 against PromoService's constructor-hardcoded seed.
 * If/when PromoService's seed moves to application.properties or a datasource,
 * this test will break. Flag: Consider externalizing test fixtures per RULEBOOK Section 7.
 */
public class PromoServiceTest {

    private PromoService promoService;

    @BeforeEach
    public void setUp() {
        promoService = new PromoService();
    }

    @Test
    public void testGetPromotionsContainsKnownPromo() {
        Set<Promotion> promos = promoService.getPromotions();
        assertEquals(1, promos.size());
        Promotion promo = promos.iterator().next();
        assertEquals("329299", promo.getItemId());
        assertEquals(0.25, promo.getPercentOff(), 0.001);
    }

    @Test
    public void testApplyCartItemPromotionsForPromotedItem() {
        Product p = new Product();
        p.setItemId("329299");
        p.setPrice(34.99);

        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(p);
        sci.setQuantity(1);
        sci.setPrice(34.99);

        ShoppingCart cart = new ShoppingCart();
        cart.addShoppingCartItem(sci);

        promoService.applyCartItemPromotions(cart);

        assertEquals(34.99 * 0.75, sci.getPrice(), 0.01);
        assertEquals(34.99 * 0.25 * -1, sci.getPromoSavings(), 0.01);
    }

    @Test
    public void testApplyCartItemPromotionsNoEffectForUnknownItem() {
        Product p = new Product();
        p.setItemId("UNKNOWN");
        p.setPrice(20.00);

        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(p);
        sci.setQuantity(1);
        sci.setPrice(20.00);

        ShoppingCart cart = new ShoppingCart();
        cart.addShoppingCartItem(sci);

        promoService.applyCartItemPromotions(cart);

        assertEquals(20.00, sci.getPrice(), 0.001);
        assertEquals(0.0, sci.getPromoSavings(), 0.001);
    }

    @Test
    public void testApplyShippingPromotionsFreeShippingOver75() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartItemTotal(80.00);
        cart.setShippingTotal(8.99);

        promoService.applyShippingPromotions(cart);

        assertEquals(0.0, cart.getShippingTotal(), 0.001);
        assertEquals(-8.99, cart.getShippingPromoSavings(), 0.001);
    }

    @Test
    public void testApplyShippingPromotionsNoFreeShippingUnder75() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartItemTotal(50.00);
        cart.setShippingTotal(6.99);

        promoService.applyShippingPromotions(cart);

        assertEquals(6.99, cart.getShippingTotal(), 0.001);
        assertEquals(0.0, cart.getShippingPromoSavings(), 0.001);
    }
}
