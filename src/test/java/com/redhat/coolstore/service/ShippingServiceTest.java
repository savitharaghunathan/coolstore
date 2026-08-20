package com.redhat.coolstore.service;

import com.redhat.coolstore.model.ShoppingCart;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class ShippingServiceTest {

    @Inject
    private ShippingService shippingService;

    private ShoppingCart cartWithTotal(double total) {
        ShoppingCart sc = new ShoppingCart();
        sc.setCartItemTotal(total);
        return sc;
    }

    @Test
    public void testShippingTier0To25() {
        assertEquals(2.99, shippingService.calculateShipping(cartWithTotal(10.00)), 0.001);
    }

    @Test
    public void testShippingTier25To50() {
        assertEquals(4.99, shippingService.calculateShipping(cartWithTotal(30.00)), 0.001);
    }

    @Test
    public void testShippingTier50To75() {
        assertEquals(6.99, shippingService.calculateShipping(cartWithTotal(60.00)), 0.001);
    }

    @Test
    public void testShippingTier75To100() {
        assertEquals(8.99, shippingService.calculateShipping(cartWithTotal(80.00)), 0.001);
    }

    @Test
    public void testShippingTier100Plus() {
        assertEquals(10.99, shippingService.calculateShipping(cartWithTotal(150.00)), 0.001);
    }

    @Test
    public void testShippingBoundaryAt25() {
        assertEquals(4.99, shippingService.calculateShipping(cartWithTotal(25.00)), 0.001);
    }

    @Test
    public void testShippingNullCart() {
        assertEquals(0.0, shippingService.calculateShipping(null), 0.001);
    }

    @Test
    public void testInsuranceUnder25() {
        assertEquals(0.0, shippingService.calculateShippingInsurance(cartWithTotal(20.00)), 0.001);
    }

    @Test
    public void testInsurance25To100() {
        assertEquals(0.60, shippingService.calculateShippingInsurance(cartWithTotal(30.00)), 0.001);
    }

    @Test
    public void testInsurance100To500() {
        double expected = 150.0 * 0.015;
        assertEquals(expected, shippingService.calculateShippingInsurance(cartWithTotal(150.00)), 0.01);
    }

    @Test
    public void testInsurance500Plus() {
        double expected = 600.0 * 0.01;
        assertEquals(expected, shippingService.calculateShippingInsurance(cartWithTotal(600.00)), 0.01);
    }
}
