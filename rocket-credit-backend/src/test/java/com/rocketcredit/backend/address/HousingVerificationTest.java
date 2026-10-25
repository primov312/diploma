package com.rocketcredit.backend.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rocketcredit.backend.AbstractIntegrationTest;
import com.rocketcredit.backend.analysis.AnalysisClient;
import com.rocketcredit.backend.analysis.AnalysisUnavailableException;
import com.rocketcredit.backend.users.FinancialInputsService;
import com.rocketcredit.backend.users.SaveFinancialInputsRequest.HousingSituation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class HousingVerificationTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired HousingService housing;
    @Autowired FinancialInputsService finances;
    @MockBean AnalysisClient analysis;

    private static final Path ASSETS = Path.of("../demo-repository/src/assets");

    private long riley(String email) throws Exception {
        register(email, "password-123");
        long user = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        jdbc.update("UPDATE users SET display_name='Riley Review' WHERE id=?", user);
        finances.current(user);
        // Inserted directly: saving through AddressService needs the postcode dataset, which is irrelevant here.
        jdbc.update("""
                INSERT INTO user_address_revisions (user_id, revision, country_code, city, district_id, postal_code, street, building, unit)
                VALUES (?, 1, 'HU', 'Budapest', 'budapest-v', '1051', 'Minta utca', '12', NULL)""", user);
        jdbc.update("INSERT INTO user_address_state (user_id, current_revision) VALUES (?, 1)", user);
        return user;
    }

    private static MockMultipartFile sample(String name) throws Exception {
        return new MockMultipartFile("image", name + ".png", "image/png", Files.readAllBytes(ASSETS.resolve("sample-housing-" + name + ".png")));
    }

    private ObjectNode extraction(String documentType) throws Exception {
        return (ObjectNode) mapper.readTree("""
                {"status":"EXTRACTED","analysisMode":"AI","provider":"TYPESAFE","documentType":"%s","confidence":0.98,
                 "holder":"Riley Review","postalCode":"1051","street":"Minta utca","building":"12","evidenceQuotes":["Riley Review"],
                 "fieldChecks":{"holder":0.97,"postalCode":0.99,"street":0.98,"building":0.96}}
                """.formatted(documentType));
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifiedDocumentChangesTheSituationThroughTheEndpoint() throws Exception {
        String email = uniqueEmail();
        long user = riley(email);
        int revision = finances.current(user).revision();
        var claim = org.mockito.ArgumentCaptor.forClass(Map.class);
        when(analysis.extractHousingDocument(any(), any(), claim.capture())).thenReturn(extraction("OWNERSHIP_RECORD"));
        var session = login(email, "password-123");
        var csrf = fetchCsrf();

        mvc.perform(withCsrf(multipart("/api/me/housing/verifications").file(sample("owner"))
                        .param("expectedRevision", String.valueOf(revision)).param("housingSituation", "OWNER"), csrf).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED_DEMO"))
                .andExpect(jsonPath("$.housingSituation").value("OWNER"))
                .andExpect(jsonPath("$.financialRevision").value(revision + 1))
                .andExpect(jsonPath("$.dataSource").value("SYNTHETIC"));

        assertThat(claim.getValue()).containsEntry("housingSituation", "OWNER").containsEntry("holder", "Riley Review")
                .containsEntry("postalCode", "1051").containsEntry("street", "Minta utca").containsEntry("building", "12");
        assertThat(finances.current(user).housingSituation()).isEqualTo("OWNER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM affordability_jobs WHERE user_id=? AND financial_revision=?",
                Integer.class, user, revision + 1)).isEqualTo(1);
        mvc.perform(get("/api/me/housing").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.housingSituation").value("OWNER"))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.lastAttempt.status").value("VERIFIED_DEMO"));
    }

    @Test
    void theWrongDocumentTypeOrAnUnavailableModelLeavesTheSituationUnchanged() throws Exception {
        long user = riley(uniqueEmail());
        int revision = finances.current(user).revision();

        when(analysis.extractHousingDocument(any(), any(), any())).thenReturn(extraction("RENTAL_AGREEMENT"));
        var wrongType = housing.change(user, revision, HousingSituation.OWNER, sample("renting"));
        assertThat(wrongType.status()).isEqualTo("MISMATCH");
        assertThat(wrongType.checks()).anyMatch(c -> c.contains("a rental agreement, which does not support \"Owner\""));

        when(analysis.extractHousingDocument(any(), any(), any())).thenReturn(mapper.readTree("""
                {"status":"AI_UNAVAILABLE","analysisMode":"AI","documentType":null,"evidenceQuotes":[]}"""));
        assertThat(housing.change(user, revision, HousingSituation.OWNER, sample("owner")).status()).isEqualTo("NEEDS_REVIEW");
        when(analysis.extractHousingDocument(any(), any(), any())).thenThrow(new AnalysisUnavailableException("down"));
        var unreachable = housing.change(user, revision, HousingSituation.OWNER, sample("owner"));
        assertThat(unreachable.status()).isEqualTo("NEEDS_REVIEW");
        assertThat(unreachable.checks()).anyMatch(c -> c.contains("could not be checked automatically right now"));

        assertThat(finances.current(user).revision()).isEqualTo(revision);
        assertThat(finances.current(user).housingSituation()).isEqualTo("OTHER");
        assertThat(housing.current(user).verified()).isFalse();
        assertThat(housing.current(user).lastAttempt().status()).isEqualTo("NEEDS_REVIEW");
    }

    @Test
    void anyUploadedDocumentIsCheckedAndCanChangeTheSituation() throws Exception {
        long user = riley(uniqueEmail());
        byte[] scan = "\u00ff\u00d8\u00ff a customer's own photo of a lease".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        var photo = new MockMultipartFile("image", "lease.jpg", "image/jpeg", scan);
        when(analysis.extractHousingDocument(any(), any(), any())).thenReturn(extraction("RENTAL_AGREEMENT"));

        var result = housing.change(user, finances.current(user).revision(), HousingSituation.RENTING, photo);

        assertThat(result.status()).isEqualTo("VERIFIED_DEMO");
        assertThat(result.dataSource()).isEqualTo("USER_UPLOAD");
        assertThat(result.scenarioId()).isEqualTo("user-upload");
        assertThat(result.housingSituation()).isEqualTo("RENTING");
        verify(analysis).extractHousingDocument(org.mockito.ArgumentMatchers.eq(java.util.Base64.getEncoder().encodeToString(scan)),
                org.mockito.ArgumentMatchers.eq("image/jpeg"), any());
    }

    @Test
    void fixtureModeLeavesOtherUploadsForAManualReview() throws Exception {
        long user = riley(uniqueEmail());
        when(analysis.extractHousingDocument(any(), any(), any())).thenReturn(mapper.readTree("""
                {"status":"AI_UNAVAILABLE","analysisMode":"FIXTURE","dataSource":"USER_UPLOAD","evidenceQuotes":[]}"""));
        var result = housing.change(user, finances.current(user).revision(), HousingSituation.RENTING, sample("renting"));
        assertThat(result.status()).isEqualTo("NEEDS_REVIEW");
        assertThat(result.checks()).anyMatch(c -> c.contains("Automatic document checks are off"));
    }

    @Test
    void jevProbabilitiesConfirmOrContradictEachDetail() throws Exception {
        ObjectNode jev = extraction("FAMILY_DECLARATION");
        assertThat(HousingService.evaluate(HousingSituation.FAMILY, jev).status()).isEqualTo("VERIFIED_DEMO");
        ((ObjectNode) jev.path("fieldChecks")).put("building", 0.5);
        assertThat(HousingService.evaluate(HousingSituation.FAMILY, jev).status()).isEqualTo("NEEDS_REVIEW");
        ((ObjectNode) jev.path("fieldChecks")).put("building", 0.1);
        assertThat(HousingService.evaluate(HousingSituation.FAMILY, jev).status()).isEqualTo("MISMATCH");
        ((ObjectNode) jev.path("fieldChecks")).remove("building");  // a detail missing from the report is unknown
        assertThat(HousingService.evaluate(HousingSituation.FAMILY, jev).status()).isEqualTo("NEEDS_REVIEW");

        ObjectNode someoneElse = extraction("ACCOMMODATION_CONFIRMATION");
        ((ObjectNode) someoneElse.path("fieldChecks")).put("holder", 0.02);
        assertThat(HousingService.evaluate(HousingSituation.OTHER, someoneElse).status()).isEqualTo("MISMATCH");
        someoneElse.put("confidence", 0.6);
        assertThat(HousingService.evaluate(HousingSituation.OTHER, someoneElse).status()).isEqualTo("NEEDS_REVIEW");
    }

    @Test
    void housingCannotChangeWithoutADocumentOrASavedAddress() throws Exception {
        String email = uniqueEmail();
        var session = registerAndLogin(email);
        long user = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        int revision = finances.current(user).revision();

        mvc.perform(withCsrf(put("/api/me/financial-inputs").contentType(MediaType.APPLICATION_JSON).content("""
                        {"expectedRevision":%d,"housingSituation":"OWNER","expenseMode":"AUTOMATIC"}""".formatted(revision)),
                        fetchCsrf()).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("HOUSING_EVIDENCE_REQUIRED"));
        assertThatThrownBy(() -> housing.change(user, revision, HousingSituation.OWNER, sample("owner")))
                .hasMessageContaining("Save your address first");
        assertThatThrownBy(() -> housing.change(user, revision, HousingSituation.OTHER, sample("other")))
                .hasMessageContaining("already your housing situation");
        assertThat(finances.current(user).revision()).isEqualTo(revision);
    }
}
