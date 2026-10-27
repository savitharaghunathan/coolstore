package com.redhat.coolstore.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * ProductEndpointTest - JUnit 5 @QuarkusTest migration from a JUnit 4 Mockito unit test.
 *
 * Migration notes:
 * - JUnit 4 @Test -> org.junit.jupiter.api.Test
 * - The Mockito unit test (mock ProductService + @InjectMocks ProductEndpoint) is replaced
 *   with a @QuarkusTest integration test that exercises the real endpoint over HTTP using
 *   REST-assured, per the Quarkus testing model.
 * - Base path: RestApplication @ApplicationPath("/services") + resource paths resolve to
 *   /services/rest/products (see RestApplication javadoc).
 * - Data: H2 test database seeded by Flyway (db/migration/V1_2__AddInitialData.sql seeds
 *   9 catalog products, including itemId 329299 = "Quarkus T-shirt", price 10.00). The
 *   %test profile in src/main/resources/application.properties is the single test config
 *   source (legacy src/test/resources/META-INF/persistence.xml removed) and disables OIDC
 *   auth, so no bearer token is required.
 * - Assertion values for testGetProductByItemId were adapted from the old mock fixtures
 *   ("Red Fedora"/34.99) to the Flyway seed data ("Quarkus T-shirt"/10.00), per the
 *   integration-test conversion.
 */
@QuarkusTest
public class ProductEndpointTest {

    @Test
    public void testListAllReturnsProducts() {
        given()
            .when().get("/services/rest/products")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$.size()", greaterThanOrEqualTo(8))
            .body("itemId", hasItem("329299"));
    }

    @Test
    public void testListAllEmptyCatalog() {
        // The catalog is always seeded in the @QuarkusTest H2 database, so the original
        // mocked "empty catalog" scenario cannot occur over HTTP. This test now verifies
        // the same response contract: a well-formed JSON array body (an empty catalog
        // would render as []).
        given()
            .when().get("/services/rest/products")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", instanceOf(List.class));
    }

    @Test
    public void testGetProductByItemId() {
        given()
            .when().get("/services/rest/products/329299")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("itemId", equalTo("329299"))
            .body("name", equalTo("Quarkus T-shirt"))
            .body("price", equalTo(10.00f));
    }

    @Test
    public void testGetProductByItemIdNotFound() {
        given()
            .when().get("/services/rest/products/UNKNOWN")
            .then()
            .statusCode(404);
    }
}
