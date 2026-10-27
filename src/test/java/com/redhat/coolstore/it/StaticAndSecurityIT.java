package com.redhat.coolstore.it;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;

/**
 * Static serving + security smoke tests (TG3.6/TG4.6, mig-test-gen Priority 2 public APIs):
 * storefront/index.html and vendored assets from META-INF/resources, SmallRye Health
 * endpoints (replaced health.jsp), @PermitAll products, @RolesAllowed("user") cart/order.
 */
@QuarkusTest
public class StaticAndSecurityIT {

    @Test
    public void indexHtmlServed() {
        RestAssured.get("/").then().statusCode(200).body(containsString("Cool Store"));
    }

    @Test
    public void vendoredAssetsServed() {
        RestAssured.get("/bower_components/jquery/dist/jquery.min.js").then().statusCode(200);
        RestAssured.get("/partials/cart.html").then().statusCode(200);
    }

    @Test
    public void healthEndpointsUp() {
        RestAssured.get("/q/health/live").then().statusCode(200);
        RestAssured.get("/q/health/ready").then().statusCode(200);
    }

    @Test
    public void productsPublic() { // @PermitAll browse (TG4.1)
        RestAssured.get("/services/products").then().statusCode(200);
    }

    @Test
    public void cartRequiresAuth() { // 401 without token (TG4.1)
        RestAssured.get("/services/cart/0000").then().statusCode(401);
    }

    @Test
    public void ordersRequireAuth() {
        RestAssured.get("/services/orders").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "test", roles = "user")
    public void cartAllowedWithUserRole() { // 200 with realm role `user`
        RestAssured.get("/services/cart/0000").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "test", roles = "wrong-role")
    public void cartForbiddenWithoutUserRole() { // 403 wrong role (TG4.1)
        RestAssured.get("/services/cart/0000").then().statusCode(403);
    }
}
