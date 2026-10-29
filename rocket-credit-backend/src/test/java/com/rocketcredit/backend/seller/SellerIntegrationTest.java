// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.rocketcredit.backend.AbstractIntegrationTest;
import com.rocketcredit.backend.analysis.AnalysisClient;
import com.rocketcredit.backend.analysis.AnalysisDecision;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

/** Seller isolation, data minimisation and the catalogue rules. Each test uses its own partners. */
class SellerIntegrationTest extends AbstractIntegrationTest {
    @MockBean AnalysisClient analysis;

    private MockHttpSession admin;
    private long partnerA, partnerB;
    private String slugA, slugB;
    private MockHttpSession sellerA, sellerB;

    @BeforeEach
    void setUp() throws Exception {
        when(analysis.score(any())).thenReturn(new AnalysisDecision("APPROVED", new BigDecimal("0.95"), new BigDecimal("500"), "USD",
                List.of(), Map.of(), "rules-v4", false, "NOT_REQUESTED", null, Map.of()));
        admin = loginDemo("admin");
        partnerA = createPartner("500");
        partnerB = createPartner("500");
        slugA = slug(partnerA);
        slugB = slug(partnerB);
        sellerA = createSeller(admin, partnerA, uniqueEmail());
        sellerB = createSeller(admin, partnerB, uniqueEmail());
    }

    private String slug(long partner) {
        return testJdbc.queryForObject("SELECT slug FROM partners WHERE id=?", String.class, partner);
    }

    private static String product(String name, String price) {
        return "{\"name\":\"%s\",\"description\":\"A test product.\",\"category\":\"Plans\",\"imagePath\":\"/catalog/streambox-basic-monthly.jpg\",\"price\":%s}"
                .formatted(name, price);
    }

    private long create(MockHttpSession seller, String name, String price) throws Exception {
        String body = mvc.perform(postJson("/api/seller/products", product(name, price), fetchCsrf()).session(seller))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String catalog() throws Exception {
        return mvc.perform(get("/api/catalog")).andReturn().getResponse().getContentAsString();
    }

    private long apply(MockHttpSession customer, String slug, long product) throws Exception {
        String quote = mvc.perform(postJson("/api/credit-quotes",
                        "{\"partnerSlug\":\"%s\",\"productId\":%d,\"termUnit\":\"MONTH\",\"installmentCount\":3}".formatted(slug, product), fetchCsrf())
                        .session(customer)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String app = mvc.perform(postJson("/api/applications",
                        "{\"partnerSlug\":\"%s\",\"productId\":%d,\"useAi\":false,\"quoteId\":%d}".formatted(slug, product, ((Number) JsonPath.read(quote, "$.id")).longValue()),
                        fetchCsrf()).session(customer).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(app, "$.id")).longValue();
    }

    private long accept(MockHttpSession customer, long application) throws Exception {
        String body = mvc.perform(postJson("/api/applications/" + application + "/accept", "{}", fetchCsrf()).session(customer)
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.loanId")).longValue();
    }

    @Test
    void sellerCannotReadOrChangeAnotherPartnersProducts() throws Exception {
        long foreign = create(sellerB, "B only", "20");
        mvc.perform(withCsrf(put("/api/seller/products/" + foreign).contentType(MediaType.APPLICATION_JSON).content(product("Taken over", "1")), fetchCsrf())
                        .session(sellerA))
                .andExpect(status().isNotFound());
        for (String action : new String[] {"deactivate", "activate"}) {
            mvc.perform(postJson("/api/seller/products/" + foreign + "/" + action, "{}", fetchCsrf()).session(sellerA))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }
        String own = mvc.perform(get("/api/seller/products").session(sellerA)).andReturn().getResponse().getContentAsString();
        assertThat(own).doesNotContain("B only").contains("\"slug\":\"" + slugA + "\"");
        // query parameters cannot switch the partner either
        assertThat(mvc.perform(get("/api/seller/products?partnerId=" + partnerB).session(sellerA)).andReturn().getResponse().getContentAsString())
                .doesNotContain("B only");
        assertThat(testJdbc.queryForObject("SELECT name FROM products WHERE id=?", String.class, foreign)).isEqualTo("B only");
    }

    @Test
    void productRulesAndThePublicCatalogue() throws Exception {
        mvc.perform(postJson("/api/seller/products", product("Too dear", "500.01"), fetchCsrf()).session(sellerA))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("PRICE_ABOVE_CAP"));
        mvc.perform(postJson("/api/seller/products", product("Free", "0"), fetchCsrf()).session(sellerA))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        mvc.perform(postJson("/api/seller/products", product("x".repeat(161), "5"), fetchCsrf()).session(sellerA))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/seller/products", product("Odd", "5").replace("\"Plans\"", "\"Spaceships\""), fetchCsrf()).session(sellerA))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("UNKNOWN_CATEGORY"));
        mvc.perform(postJson("/api/seller/products", product("Odd", "5").replace("/catalog/streambox-basic-monthly.jpg", "https://evil.example/x.jpg"), fetchCsrf())
                        .session(sellerA))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("UNKNOWN_IMAGE"));

        long id = create(sellerA, "Test lamp", "500");
        assertThat(testJdbc.queryForObject("SELECT fixture_id FROM products WHERE id=?", String.class, id)).startsWith("seller-" + slugA + "-");
        assertThat(catalog()).contains("Test lamp");
        MockHttpSession customer = registerAndLogin(uniqueEmail());
        String quote = "{\"partnerSlug\":\"%s\",\"productId\":%d,\"termUnit\":\"MONTH\",\"installmentCount\":3}".formatted(slugA, id);
        mvc.perform(postJson("/api/credit-quotes", quote, fetchCsrf()).session(customer)).andExpect(status().isOk());

        mvc.perform(postJson("/api/seller/products/" + id + "/deactivate", "{}", fetchCsrf()).session(sellerA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        assertThat(catalog()).doesNotContain("Test lamp");
        mvc.perform(get("/api/partners/" + slugA)).andExpect(jsonPath("$.products[?(@.id == %d)]".formatted(id)).isEmpty());
        mvc.perform(postJson("/api/credit-quotes", quote, fetchCsrf()).session(customer))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PRODUCT_UNAVAILABLE"));
        mvc.perform(postJson("/api/purchases", "{\"partnerSlug\":\"%s\",\"productId\":%d}".formatted(slugA, id), fetchCsrf()).session(customer)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PRODUCT_UNAVAILABLE"));

        mvc.perform(postJson("/api/seller/products/" + id + "/activate", "{}", fetchCsrf()).session(sellerA)).andExpect(status().isOk());
        assertThat(catalog()).contains("Test lamp");

        // an inactive partner hides its whole catalogue and cannot be quoted
        mvc.perform(withCsrf(put("/api/admin/partners/" + partnerA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Paused store\",\"amountCap\":500,\"active\":false}"), fetchCsrf()).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        assertThat(catalog()).doesNotContain("Test lamp");
        mvc.perform(get("/api/partners/" + slugA)).andExpect(status().isNotFound());
        mvc.perform(postJson("/api/credit-quotes", quote, fetchCsrf()).session(customer))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PARTNER_UNAVAILABLE"));
    }

    @Test
    void ordersShowOnlyTheOwnPartnerAndNoCustomerData() throws Exception {
        String email = uniqueEmail();
        MockHttpSession customer = registerAndLogin(email);
        long productA = create(sellerA, "Order item A", "120");
        long productB = create(sellerB, "Order item B", "80");
        long loanA = accept(customer, apply(customer, slugA, productA));
        long loanB = accept(customer, apply(customer, slugB, productB));

        String orders = mvc.perform(get("/api/seller/orders").session(sellerA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].orderRef").value(loanA))
                .andExpect(jsonPath("$.items[0].productName").value("Order item A"))
                .andExpect(jsonPath("$.items[0].principal").value(120.0))
                .andExpect(jsonPath("$.items[0].termUnit").value("MONTH"))
                .andExpect(jsonPath("$.items[0].installmentCount").value(3))
                .andExpect(jsonPath("$.items[0].loanStatus").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(orders).doesNotContain("\"orderRef\":" + loanB);
        String ref = JsonPath.read(orders, "$.items[0].customerRef");
        assertThat(ref).matches("C-[0-9A-F]{6}");
        // the same customer gets another reference at another seller, so sellers cannot link their lists
        String refB = JsonPath.read(mvc.perform(get("/api/seller/orders").session(sellerB)).andReturn().getResponse().getContentAsString(), "$.items[0].customerRef");
        assertThat(refB).isNotEqualTo(ref);

        mvc.perform(get("/api/seller/orders?status=PAID").session(sellerA)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/seller/orders?status=nope").session(sellerA)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/seller/overview").session(sellerA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.financedOrders").value(1)).andExpect(jsonPath("$.financedPrincipal").value(120.0))
                .andExpect(jsonPath("$.activeLoans").value(1)).andExpect(jsonPath("$.applicationsByDecision.APPROVED").value(1))
                .andExpect(jsonPath("$.last30Days.length()").value(30));

        long userId = testJdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        for (String path : new String[] {"/api/seller/overview", "/api/seller/orders", "/api/seller/products"}) {
            String json = mvc.perform(get(path).session(sellerA)).andReturn().getResponse().getContentAsString();
            assertThat(json).as(path).doesNotContain(email).doesNotContain("Test User").doesNotContain("\"userId\"")
                    .doesNotContain("\"customerId\":" + userId)
                    .doesNotContainIgnoringCase("income").doesNotContainIgnoringCase("expenses").doesNotContain("score")
                    .doesNotContain("reasons").doesNotContainIgnoringCase("consent").doesNotContainIgnoringCase("address")
                    .doesNotContain("featureSnapshot").doesNotContain("password");
        }
    }

    @Test
    void priceChangesNeverRewriteQuotesApplicationsOrLoans() throws Exception {
        MockHttpSession customer = registerAndLogin(uniqueEmail());
        long product = create(sellerA, "Price test", "100");
        long loan = accept(customer, apply(customer, slugA, product));
        long pending = apply(customer, slugA, product);   // approved, not accepted yet
        long quote = testJdbc.queryForObject("SELECT quote_id FROM credit_applications WHERE id=?", Long.class, pending);

        mvc.perform(withCsrf(put("/api/seller/products/" + product).contentType(MediaType.APPLICATION_JSON).content(product("Price test", "150")), fetchCsrf())
                        .session(sellerA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.price").value(150.0));

        assertThat(testJdbc.queryForObject("SELECT principal FROM loans WHERE id=?", BigDecimal.class, loan)).isEqualByComparingTo("100");
        assertThat(testJdbc.queryForObject("SELECT principal FROM credit_quotes WHERE id=?", BigDecimal.class, quote)).isEqualByComparingTo("100");
        assertThat(testJdbc.queryForObject("SELECT requested_amount FROM credit_applications WHERE id=?", BigDecimal.class, pending)).isEqualByComparingTo("100");
        mvc.perform(get("/api/applications/" + pending).session(customer))
                .andExpect(jsonPath("$.canAccept").value(false))
                .andExpect(jsonPath("$.acceptanceUnavailableReason").value(org.hamcrest.Matchers.containsString("price changed")));
        mvc.perform(postJson("/api/applications/" + pending + "/accept", "{}", fetchCsrf()).session(customer).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("TERMS_CHANGED"));
        mvc.perform(postJson("/api/purchases", "{\"partnerSlug\":\"%s\",\"productId\":%d,\"expectedAmount\":100}".formatted(slugA, product), fetchCsrf())
                        .session(customer).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("TERMS_CHANGED"));
    }

    @Test
    void everySellerWriteIsAuditedOnce() throws Exception {
        long sellerId = testJdbc.queryForObject("""
                SELECT s.user_id FROM seller_accounts s WHERE s.partner_id=?""", Long.class, partnerA);
        long id = create(sellerA, "Audited", "10");
        mvc.perform(withCsrf(put("/api/seller/products/" + id).contentType(MediaType.APPLICATION_JSON).content(product("Audited", "12")), fetchCsrf())
                .session(sellerA)).andExpect(status().isOk());
        mvc.perform(postJson("/api/seller/products/" + id + "/deactivate", "{}", fetchCsrf()).session(sellerA)).andExpect(status().isOk());
        mvc.perform(postJson("/api/seller/products/" + id + "/activate", "{}", fetchCsrf()).session(sellerA)).andExpect(status().isOk());
        // a refused write leaves no row
        mvc.perform(postJson("/api/seller/products", product("Too dear", "9999"), fetchCsrf()).session(sellerA)).andExpect(status().isBadRequest());

        var rows = testJdbc.queryForList("SELECT action, actor_role, target_type, target_id, details::text AS details FROM audit_events WHERE actor_user_id=? ORDER BY id", sellerId);
        assertThat(rows).extracting(r -> r.get("action")).containsExactly(
                "SELLER_PRODUCT_CREATED", "SELLER_PRODUCT_UPDATED", "SELLER_PRODUCT_DEACTIVATED", "SELLER_PRODUCT_ACTIVATED");
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.get("actor_role")).isEqualTo("SELLER");
            assertThat(r.get("target_id")).isEqualTo(String.valueOf(id));
            assertThat((String) r.get("details")).doesNotContain("password").doesNotContain("$2");
        });
        assertThat((String) rows.get(1).get("details")).contains("\"previousPrice\": \"10.00\"");
    }
}
