package com.rocketcredit.backend.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rocketcredit.backend.AbstractIntegrationTest;
import com.rocketcredit.backend.partners.PartnerRepository;
import com.rocketcredit.backend.partners.ProductRepository;
import com.rocketcredit.backend.transactions.TransactionRepository;
import com.rocketcredit.backend.users.DemoFinancialProfileRepository;
import com.rocketcredit.backend.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

class DemoDataTest extends AbstractIntegrationTest {

    @Autowired DemoDataLoader loader;
    @Autowired UserRepository users;
    @Autowired PartnerRepository partners;
    @Autowired ProductRepository products;
    @Autowired TransactionRepository transactions;
    @Autowired DemoFinancialProfileRepository profiles;

    private static final String DEMO_PASSWORD = "rocket-demo-123";

    @Test
    void catalogAndPersonasAreSeeded() {
        assertThat(partners.findAll()).extracting("slug").contains("streambox", "markethub", "threadly");
        assertThat(products.count()).isGreaterThanOrEqualTo(15);
        for (String who : new String[] {"avery", "riley", "drew", "casey"}) {
            var u = users.findByEmail(who + "@demo.rocket.local").orElseThrow();
            assertThat(profiles.findById(u.getId())).isPresent();
            assertThat(transactions.findByUserIdOrderByOccurredOnDesc(u.getId())).isNotEmpty();
        }
        // Avery has 12 + 8 + 5 purchases, all with stable fixture ids
        var avery = users.findByEmail("avery@demo.rocket.local").orElseThrow();
        assertThat(transactions.findByUserIdOrderByOccurredOnDesc(avery.getId())).hasSize(25)
                .allMatch(t -> t.getFixtureId().startsWith("avery-"));
    }

    @Test
    void repeatedLoadingInsertsNothing() {
        long usersBefore = users.count();
        long txBefore = transactions.count();

        var second = loader.load();

        assertThat(second.usersCreated()).isZero();
        assertThat(second.transactionsInserted()).isZero();
        assertThat(users.count()).isEqualTo(usersBefore);
        assertThat(transactions.count()).isEqualTo(txBefore);
    }

    @Test
    void demoCustomersSeeTheirOwnThreePartnerHistory() throws Exception {
        MockHttpSession avery = login("avery@demo.rocket.local", DEMO_PASSWORD);
        MockHttpSession casey = login("casey@demo.rocket.local", DEMO_PASSWORD);

        mvc.perform(get("/api/transactions").session(avery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(25))
                .andExpect(jsonPath("$[?(@.partnerSlug == 'streambox')]", hasSize(12)))
                .andExpect(jsonPath("$[?(@.partnerSlug == 'markethub')]", hasSize(8)))
                .andExpect(jsonPath("$[?(@.partnerSlug == 'threadly')]", hasSize(5)))
                .andExpect(jsonPath("$[?(@.status == 'REFUNDED')]", hasSize(1)))
                .andExpect(jsonPath("$[0].occurredOn").value("2026-09-01"));   // newest first

        mvc.perform(get("/api/transactions").param("partner", "markethub").session(avery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[*].partnerSlug", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("markethub"))));

        mvc.perform(get("/api/transactions").param("partner", "no-such-store").session(avery))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/transactions").session(casey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(17))
                .andExpect(jsonPath("$[?(@.partnerSlug == 'streambox')]", hasSize(10)));

        mvc.perform(get("/api/me/profile").session(avery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyIncome").value(4500.00))
                .andExpect(jsonPath("$.syntheticSource").value("FIXTURE"))
                .andExpect(jsonPath("$.accountAgeMonths").value(org.hamcrest.Matchers.greaterThanOrEqualTo(29)));
    }

    @Test
    void newRegistrationGetsALabelledStarterDatasetNotSomeoneElsesHistory() throws Exception {
        String email = uniqueEmail();
        MockHttpSession session = registerAndLogin(email);
        Long id = users.findByEmail(email).orElseThrow().getId();

        mvc.perform(get("/api/me/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syntheticSource").value("STARTER"))
                .andExpect(jsonPath("$.profileComplete").value(false))
                .andExpect(jsonPath("$.accountAgeMonths").value(0));

        var history = transactions.findByUserIdOrderByOccurredOnDesc(id);
        assertThat(history).hasSize((int) partners.count())
                .allMatch(t -> t.getFixtureId().startsWith(StarterDataService.STARTER_PREFIX + id + "-"))
                .allMatch(t -> t.getDescription().startsWith("[Synthetic starter data]"));

        mvc.perform(get("/api/transactions").session(session))
                .andExpect(jsonPath("$.length()").value(history.size()))
                .andExpect(jsonPath("$[?(@.description =~ /^\\[Synthetic starter data\\].*/)]", hasSize(history.size())));
    }

    @Test
    void catalogIsPublicAndReadOnly() throws Exception {
        mvc.perform(get("/api/partners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$[?(@.slug == 'markethub')].amountCap").value(1500.00));

        mvc.perform(get("/api/partners/threadly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Threadly"))
                .andExpect(jsonPath("$.products.length()").value(5))
                .andExpect(jsonPath("$.products[?(@.fixtureId == 'threadly-wool-coat')].price").value(189.00));

        mvc.perform(get("/api/partners/nope")).andExpect(status().isNotFound());

        // nothing under /api writes fixture values: there is no such route at all
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/me/profile"))
                .andExpect(status().isForbidden()); // CSRF first; even with a token there is no handler
    }
}
