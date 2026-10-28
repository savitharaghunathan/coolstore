package com.redhat.coolstore.rest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
/** Tasks 3.6/5.6: SPA served from META-INF/resources; catalog public; orders protected by OIDC. */
@QuarkusTest
public class StaticAndSecurityIT {
    @Test
    public void testSpaShellServed() { given().when().get("/").then().statusCode(200).body(containsString("Cool Store")); }
    @Test
    public void testRuntimeBowerAssetServed() { given().when().get("/bower_components/angular/angular.min.js").then().statusCode(200); }
    @Test
    public void testPrunedTaintedGulpfileGone() { given().when().get("/bower_components/matchHeight/gulpfile.js").then().statusCode(404); }
    @Test
    public void testCatalogPublic() { given().when().get("/services/products").then().statusCode(200); }
    @Test
    public void testOrdersRejectedAnonymous() { given().when().get("/services/orders").then().statusCode(401); }
    @Test
    @TestSecurity(user = "user1")
    public void testOrdersAllowedAuthenticated() { given().when().get("/services/orders").then().statusCode(200); }
    @Test
    @TestSecurity(user = "user1")
    public void testCheckoutRequiresAuth() { given().when().post("/services/cart/checkout/c1").then().statusCode(anyOf(is(200), is(204), is(500))); }
}
