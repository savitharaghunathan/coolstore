# Task Group 2 — JUnit 4 → JUnit 5 Conversion Report

**Scope**: 7 test files + deletion of legacy test persistence unit.
**Constraint**: mechanical conversion — test logic, method names and assertion values preserved wherever the test type did not change. No compilation possible (`java`/`mvn` unavailable) — validation is static (grep + source cross-checks).

## Files converted

### 1. `src/test/java/com/redhat/coolstore/model/ProductTest.java`
- **State on arrival**: already JUnit 5 (`org.junit.jupiter.api.Test`, `static org.junit.jupiter.api.Assertions.*`). No code changes needed.
- **Change**: comment-only — reworded 2 javadoc migration-note lines that contained the literal strings `org.junit.Test` / `org.junit.Assert` (kept tripping the zero-match grep gate). Assertions/logic untouched.

### 2. `src/test/java/com/redhat/coolstore/model/PromotionTest.java`
- **State on arrival**: already JUnit 5. No code changes needed.
- **Change**: comment-only — reworded line 4 (`// Changed imports from org.junit.Test and org.junit.Assert...`) → "JUnit 4 imports (Test and Assert)".

### 3. `src/test/java/com/redhat/coolstore/service/ProductServiceTest.java` — **converted**
- **Imports replaced**: `org.junit.Before`, `org.junit.Test` → `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.extension.ExtendWith`, `org.mockito.junit.jupiter.MockitoExtension`; `static org.junit.Assert.*` → `static org.junit.jupiter.api.Assertions.*`.
- **Annotations added**: `@ExtendWith(MockitoExtension.class)` on the class.
- **Structural fix**: removed `@Before setUp()` containing `MockitoAnnotations.initMocks(this)` (deprecated pattern; the extension now performs mock creation + `@InjectMocks` injection).
- **Kept identical**: `@Mock CatalogService cm` / `@InjectMocks ProductService productService` (field name `cm` matches `ProductService.cm` → name+type injection), `createEntity` helper, all 3 test methods and assertion values. Unused `java.util.Collections` import from the original retained (harmless, mechanical fidelity).

### 4. `src/test/java/com/redhat/coolstore/service/PromoServiceTest.java`
- **State on arrival**: already JUnit 5 (`@BeforeEach`, jupiter `Test`/`Assertions`). No code changes needed.
- **Change**: comment-only — reworded 2 javadoc lines containing literal `org.junit.Test` / `org.junit.Assert`.

### 5. `src/test/java/com/redhat/coolstore/service/ShoppingCartServiceTest.java` — **converted**
- **Imports replaced / annotations added**: same pattern as ProductServiceTest (jupiter `Test`, `Assertions.*`, `@ExtendWith(MockitoExtension.class)`, `MockitoExtension` import).
- **Structural fix**: removed `@Before setUp()` / `MockitoAnnotations.initMocks(this)`.
- **Kept identical**: all 4 `@Mock` fields (incl. vestigial `java.util.logging.Logger log` — see Risks), `@InjectMocks ShoppingCartService` (injected fields `productServices`, `ps`, `shoppingCartOrderProcessor` match main-class field names), all 4 test methods incl. `assertSame` (maps 1:1 in jupiter).

### 6. `src/test/java/com/redhat/coolstore/service/ShoppingCartOrderProcessorTest.java` — **converted (CRITICAL)**
- **Imports replaced / annotations added**: jupiter `Test` + `Assertions.*`, `@ExtendWith(MockitoExtension.class)`, `MockitoExtension`.
- **Messaging-port alignment** (SUT ported from JMS → SmallRye Reactive Messaging):
  - Removed `javax.jms.JMSContext`, `javax.jms.JMSProducer`, `javax.jms.Topic` imports and their `@Mock` fields → **zero `javax.*` references remain**.
  - Added `@Mock private Emitter<String> ordersEmitter;` (`org.eclipse.microprofile.reactive.messaging.Emitter`) + `@InjectMocks ShoppingCartOrderProcessor processor`. Mockito injects the only `Emitter`-typed mock into the processor's `@Inject @Channel("orders") Emitter<String> orders` field by type (single-candidate type injection; name mismatch is irrelevant).
  - Removed `@Before setUp()` — its only content was the obsolete JMS stub `when(context.createProducer()).thenReturn(producer)`.
  - Removed the `java.util.logging.Logger` mock: ported processor uses `private static final Logger` (SLF4J) — not injectable, never exercised.
  - Verification updated to the exact Emitter API used by `ShoppingCartOrderProcessor.process()` (`orders.send(Transformers.shoppingCartToJson(cart))`):
    - `verify(context).createProducer()` + `verify(producer).send(eq(ordersTopic), anyString())` → `verify(ordersEmitter).send(anyString())`
    - `ArgumentCaptor<String>` now captures on `ordersEmitter.send(...)` in `testProcessSendsValidJson`.
- **Kept identical**: test method names, cart fixtures, and all 4 JSON assertion strings — cross-checked against `Transformers.shoppingCartToJson` (jakarta.json, compact output): `"orderValue"`, `"customerName"`, `"items"`, `"productSku":"329299"` all present in the emitted payload.

### 7. `src/test/java/com/redhat/coolstore/rest/ProductEndpointTest.java` — **converted (Mockito unit test → `@QuarkusTest`)**
- **Imports**: `io.quarkus.test.junit.QuarkusTest`, `io.restassured.http.ContentType`, `static io.restassured.RestAssured.given`, `static org.hamcrest.Matchers.*`, jupiter `Test`. Removed all Mockito imports, `@Mock`/`@InjectMocks`/setUp and the `createProduct` mock fixture helper.
- **Annotations added**: `@QuarkusTest` on the class. No `@TestHttpResource`, no security annotations, no bearer token (OIDC disabled in `%test` profile: `%test.quarkus.oidc.tenant.enabled=false`).
- **Tests** (all 3 original method names kept; 1 method added per task spec):
  - `testListAllReturnsProducts`: `given().when().get("/services/rest/products").then().statusCode(200)` + `contentType(JSON)` + `body("$.size()", greaterThanOrEqualTo(8))` + `body("itemId", hasItem("329299"))` — Flyway `V1_2__AddInitialData.sql` seeds 9 catalog products.
  - `testListAllEmptyCatalog`: the mocked empty-catalog scenario cannot occur over HTTP against the seeded H2 DB; now asserts the same response contract (200, JSON, array body — an empty catalog would render `[]`). Method name kept, documented in-code.
  - `testGetProductByItemId`: `GET /services/rest/products/329299` → 200, `itemId` "329299", `name` "Quarkus T-shirt", `price` 10.00f — assertion values adapted from the old mock fixtures ("Red Fedora"/34.99) to the Flyway seed values per task rule 4 (integration test against seed data).
  - `testGetProductByItemIdNotFound` (new): `GET /services/rest/products/UNKNOWN` → 404. Path verified: `CatalogService.getCatalogItemById` → `em.find` → null → `ProductEndpoint.getProduct` returns `Response.Status.NOT_FOUND`.

### 8. `src/test/resources/META-INF/persistence.xml` — **DELETED**
- Legacy javax persistence unit removed; empty `META-INF` directory removed (`src/test/resources` is now empty). The `%test` profile in `src/main/resources/application.properties` is the single test config source.

## Grep verification (must be empty — PASS)

```
$ grep -rn "org.junit.Test\|org.junit.Before\|org.junit.Assert\|import javax\." \
    src/test/java/com/redhat/coolstore/model/ProductTest.java \
    src/test/java/com/redhat/coolstore/model/PromotionTest.java \
    src/test/java/com/redhat/coolstore/service/ProductServiceTest.java \
    src/test/java/com/redhat/coolstore/service/PromoServiceTest.java \
    src/test/java/com/redhat/coolstore/service/ShoppingCartServiceTest.java \
    src/test/java/com/redhat/coolstore/service/ShoppingCartOrderProcessorTest.java \
    src/test/java/com/redhat/coolstore/rest/ProductEndpointTest.java
(no output — exit code 1 = zero matches)
```
Positive check: all 7 files contain `org.junit.jupiter` imports; the 4 Mockito files carry `@ExtendWith(MockitoExtension.class)`; `ProductEndpointTest` carries `@QuarkusTest`.

## Dependency availability (verified read-only in pom.xml)
`quarkus-junit5`, `rest-assured`, `mockito-core`, `mockito-junit-jupiter`, `quarkus-flyway`, `quarkus-test-h2` all present. `org.eclipse.microprofile.reactive.messaging.Emitter` is on the compile classpath (imported by `ShoppingCartOrderProcessor`).

## Risks / cross-task dependencies
1. **No compilation possible** (`java`/`mvn` unavailable in environment). Validation was static: full-file reads, main-source cross-checks, grep gates. A `mvn test` run by the orchestrator is the real gate.
2. **`/services/rest` path prefix** (owned by main-sources task group): `ProductEndpoint` is `@Path("/products")` and `RestApplication` pins `@ApplicationPath("/services")`. RestApplication's javadoc states resources resolve under `/services/rest/*` and explicitly cites this test — the main-sources agent must ensure the effective URL is `/services/rest/products` (e.g. `@Path("/rest/products")` on resources). If the final prefix differs, the 4 HTTP tests will 404.
3. **Flyway seed vs `%test` schema generation** (owned by config task group): `application.properties` currently has `quarkus.flyway.migrate-at-start=true` **and** `%test.quarkus.hibernate-orm.database.generation=drop-and-create`. If Hibernate's drop-and-create executes after Flyway seeding in the test profile, the seeded rows are wiped and `testListAllReturnsProducts` (≥ 8) / `testGetProductByItemId` will fail. Recommendation to TG1: `%test.quarkus.hibernate-orm.database.generation=none` so Flyway owns the test schema. Flagged, not fixed (outside my file scope).
4. **Deliberate assertion-value change** in `ProductEndpointTest.testGetProductByItemId` ("Red Fedora"/34.99 → "Quarkus T-shirt"/10.00): required because the mock-based values do not exist in the Flyway seed; mandated by task rule 4. `body("price", equalTo(10.00f))` relies on REST-assured JsonPath parsing `10.0` as Float (CatalogItemEntity.price is `double`) — low risk.
5. **Vestigial `java.util.logging.Logger` mock** retained in `ShoppingCartServiceTest` for mechanical fidelity: `ShoppingCartService` now uses a static final SLF4J logger, so the mock is never injected. Harmless — an unused `@Mock` does not trigger `UnnecessaryStubbingException` (strict stubs only flag unused *stubbings*).
6. **`@InjectMocks` field-name mismatch** in `ShoppingCartOrderProcessorTest` (`ordersEmitter` mock vs `orders` field): safe — with a single `Emitter`-typed mock, Mockito injects by type regardless of name.
7. **`ShoppingCartServiceTest.testGetShoppingCartReturnsSameInstance`** still validates current single-cart behavior (`assertSame` across different cartIds). Kept identical per "no logic changes" rule; the main source carries a `[TODO(port)]` noting multi-cart support would change this expectation.
8. **Already-converted files**: `ProductTest`, `PromotionTest`, `PromoServiceTest` arrived already on JUnit 5 (prior conversion pass). Only comment rewording was applied (grep hygiene); no code/assertion changes.
