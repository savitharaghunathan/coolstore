package com.redhat.coolstore.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.redhat.coolstore.model.ShoppingCart;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Shipping calculation service.
 *
 * Migrated from Java EE 7 (@Stateless, @Remote) to Quarkus (@ApplicationScoped).
 * - Removed @Remote interface (ShippingServiceRemote) - service is now local-only per Quarkus CDI patterns
 * - Pure business logic with no persistence, messaging, or security concerns
 * - Calculations are thread-safe when ShoppingCart instances are not shared across concurrent threads
 *   (Assumes stable ShoppingCart state during calculation)
 *
 * TODO(MIGRATION): Removed 'implements ShippingServiceRemote' and @Remote annotation.
 * Verify no clients depend on this interface for remote lookups or type safety.
 * See RULEBOOK.md - no guidance on @Remote EJB handling in migration rules.
 */
@ApplicationScoped
public class ShippingService {

    public double calculateShipping(ShoppingCart sc) {

        if (sc != null) {

            if (sc.getCartItemTotal() >= 0 && sc.getCartItemTotal() < 25) {

                return 2.99;

            } else if (sc.getCartItemTotal() >= 25 && sc.getCartItemTotal() < 50) {

                return 4.99;

            } else if (sc.getCartItemTotal() >= 50 && sc.getCartItemTotal() < 75) {

                return 6.99;

            } else if (sc.getCartItemTotal() >= 75 && sc.getCartItemTotal() < 100) {

                return 8.99;

            } else if (sc.getCartItemTotal() >= 100 && sc.getCartItemTotal() < 10000) {

                return 10.99;

            }
            // TODO: Verify business rule - orders >= $10,000 get free shipping (return 0)?
            // This edge case should be reviewed with business stakeholders.

        }

        return 0;

    }

    public double calculateShippingInsurance(ShoppingCart sc) {

        if (sc != null) {

            if (sc.getCartItemTotal() >= 25 && sc.getCartItemTotal() < 100) {

                return getPercentOfTotal(sc.getCartItemTotal(), 0.02);

            } else if (sc.getCartItemTotal() >= 100 && sc.getCartItemTotal() < 500) {

                return getPercentOfTotal(sc.getCartItemTotal(), 0.015);

            } else if (sc.getCartItemTotal() >= 500 && sc.getCartItemTotal() < 10000) {

                return getPercentOfTotal(sc.getCartItemTotal(), 0.01);

            }

        }

        return 0;
    }

    // Migration verified: No instance state, safe to share singleton across tests (@ApplicationScoped)

    private static double getPercentOfTotal(double value, double percentOfTotal) {
        return BigDecimal.valueOf(value * percentOfTotal)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    // TODO: Consider externalizing shipping rules to application.properties if multi-region
    // deployment needs different rates. Hardcoded thresholds (25, 50, 75, 100, 10000) and
    // costs (2.99, 4.99, 6.99, 8.99, 10.99) are currently in-memory. Similarly for insurance
    // thresholds (25, 100, 500, 10000) and percentages (0.02, 0.015, 0.01).
    // See RULEBOOK.md Section 7 Line 161: runtime config → application.properties

    // TODO: Validate input - should negative cart totals throw IllegalArgumentException?
    // Currently, negative totals fall through and return 0 shipping/insurance.
    // Pre-existing behavior from EE7 codebase, but worth reviewing for correctness.

}
