package com.rocketcredit.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rocketcredit.backend.AbstractIntegrationTest;
import com.rocketcredit.backend.partners.PartnerEntity;
import com.rocketcredit.backend.partners.PartnerRepository;
import com.rocketcredit.backend.transactions.TransactionEntity;
import com.rocketcredit.backend.transactions.TransactionRepository;
import com.rocketcredit.backend.users.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

/** 2.4: customer A cannot read customer B's records, by list or by guessing IDs. */
class OwnershipTest extends AbstractIntegrationTest {

    @Autowired UserRepository users;
    @Autowired PartnerRepository partners;
    @Autowired TransactionRepository transactions;

    @Test
    void customerACannotSeeCustomerBsTransactions() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MockHttpSession a = registerAndLogin("a-" + suffix + "@example.test");
        MockHttpSession b = registerAndLogin("b-" + suffix + "@example.test");
        Long bId = users.findByEmail("b-" + suffix + "@example.test").orElseThrow().getId();

        var partner = partners.findBySlug("test-store-" + suffix)
                .orElseGet(() -> partners.save(new PartnerEntity("test-store-" + suffix, "Test Store", new BigDecimal("1000.00"))));
        var tx = transactions.save(new TransactionEntity(bId, partner.getId(), "tx-" + suffix, new BigDecimal("49.99"),
                LocalDate.of(2026, 5, 1), TransactionEntity.Status.COMPLETED, true, "B's purchase"));

        // B sees it
        mvc.perform(get("/api/transactions").session(b))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].partnerSlug".formatted(tx.getId())).value(partner.getSlug()));
        mvc.perform(get("/api/transactions/" + tx.getId()).session(b)).andExpect(status().isOk());

        // A's list never contains B's record, and B's record ID is a 404 for A (not 403: no existence leak)
        mvc.perform(get("/api/transactions").session(a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(tx.getId())).isEmpty())
                .andExpect(jsonPath("$[?(@.partnerSlug == '%s')]".formatted(partner.getSlug())).isEmpty());
        mvc.perform(get("/api/transactions/" + tx.getId()).session(a)).andExpect(status().isNotFound());

        // and without any session the endpoint is closed
        mvc.perform(get("/api/transactions/" + tx.getId())).andExpect(status().isUnauthorized());
    }
}
