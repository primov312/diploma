package com.rocketcredit.backend.address;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcredit.backend.analysis.AnalysisClient;
import com.rocketcredit.backend.common.ApiException;
import com.rocketcredit.backend.users.FinancialInputsService;
import com.rocketcredit.backend.users.SaveFinancialInputsRequest.HousingSituation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Changes the housing situation only after a supporting document passes the checks. Any PNG or JPEG is sent to
 * the analysis service, which asks Jev whether it shows the account name and the saved address, and whether the
 * home is rented, owned or being bought, shared with family, or provided accommodation. The upload is deleted
 * after the check; when it cannot be checked, it is left for a manual review and changes nothing.
 */
@Service
public class HousingService {
    /** SHA-256 of demo-repository/src/assets/sample-housing-*.png → scenario id; samples are checked like any other upload. */
    static final Map<String, String> SAMPLE_DOCUMENTS = Map.of(
            "4cde71469d9c692b8978efa44aa01bb434f1bab41981e445b88d6ee5b3d401d1", "prepared-rental-agreement-v1",
            "982edcc525ce25102045cc49e7c7957e8d7387840464134a8e48f147b4cee331", "prepared-ownership-record-v1",
            "4d238ba3891a4a8fe5267ed2fb42a99be8ffa6fd183a9ad7512819ba007a89b5", "prepared-family-declaration-v1",
            "59f711bd81f566f12f2b170c0e0324f3bb2eb27534e1cb4e5302ff52c09e9c70", "prepared-accommodation-v1");
    static final Map<HousingSituation, String> EXPECTED_DOCUMENT = Map.of(
            HousingSituation.RENTING, "RENTAL_AGREEMENT", HousingSituation.OWNER, "OWNERSHIP_RECORD",
            HousingSituation.FAMILY, "FAMILY_DECLARATION", HousingSituation.OTHER, "ACCOMMODATION_CONFIRMATION");
    private static final Map<String, String> DOCUMENT_LABEL = Map.of(
            "RENTAL_AGREEMENT", "a rental agreement", "OWNERSHIP_RECORD", "an ownership record",
            "FAMILY_DECLARATION", "a family residence declaration", "ACCOMMODATION_CONFIRMATION", "an accommodation confirmation");
    private static final Map<HousingSituation, String> SITUATION_LABEL = Map.of(
            HousingSituation.RENTING, "Renting", HousingSituation.OWNER, "Owner",
            HousingSituation.FAMILY, "Living with family", HousingSituation.OTHER, "Other");
    private static final List<String> DETAILS = List.of("holder", "postalCode", "street", "building");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final FinancialInputsService financialInputs;
    private final AddressService addresses;
    private final AnalysisClient analysis;
    private final Path evidenceDirectory;
    private final TransactionTemplate transactions;

    public HousingService(JdbcTemplate jdbc, ObjectMapper mapper, FinancialInputsService financialInputs,
                          AddressService addresses, AnalysisClient analysis,
                          @Value("${rocket.address.evidence-directory:${java.io.tmpdir}/rocket-credit-private}") Path evidenceDirectory,
                          TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.financialInputs = financialInputs;
        this.addresses = addresses;
        this.analysis = analysis;
        this.evidenceDirectory = evidenceDirectory;
        this.transactions = transactions;
    }

    public HousingDtos.Current current(long userId) {
        var inputs = financialInputs.current(userId);
        OffsetDateTime verifiedAt = jdbc.query("SELECT created_at FROM housing_verifications WHERE user_id=? AND financial_revision=?",
                rs -> rs.next() ? rs.getObject(1, OffsetDateTime.class) : null, userId, inputs.revision());
        List<HousingDtos.Attempt> attempts = jdbc.query("""
                SELECT id, claimed_situation, status, checks::text, created_at FROM housing_verifications
                WHERE user_id=? ORDER BY created_at DESC, id DESC LIMIT 1
                """, (rs, row) -> new HousingDtos.Attempt(rs.getLong("id"), rs.getString("claimed_situation"),
                rs.getString("status"), readChecks(rs.getString("checks")), rs.getObject("created_at", OffsetDateTime.class)), userId);
        return new HousingDtos.Current(inputs.housingSituation(), inputs.revision(), verifiedAt != null, verifiedAt,
                attempts.isEmpty() ? null : attempts.getFirst());
    }

    public HousingDtos.Result change(long userId, int expectedRevision, HousingSituation claimed, MultipartFile image) {
        if (image == null || image.isEmpty() || image.getSize() > 5L * 1024 * 1024) {
            throw ApiException.badRequest("INVALID_EVIDENCE", "Upload a PNG or JPEG image up to 5 MB.");
        }
        byte[] bytes;
        try { bytes = image.getBytes(); }
        catch (IOException e) { throw ApiException.badRequest("INVALID_EVIDENCE", "The uploaded image could not be read."); }
        String mime = AddressService.detectMime(bytes);
        if (mime == null) throw ApiException.badRequest("INVALID_EVIDENCE", "Only PNG and JPEG images are supported.");

        var inputs = financialInputs.current(userId);
        if (inputs.revision() != expectedRevision) {
            throw ApiException.conflict("REVISION_CONFLICT", "Financial information changed in another session. Reload and try again.");
        }
        if (claimed.name().equals(inputs.housingSituation())) {
            throw ApiException.badRequest("HOUSING_UNCHANGED", "This is already your housing situation.");
        }
        AddressDtos.Current address = addresses.current(userId);
        if (!address.available()) {
            throw ApiException.badRequest("ADDRESS_REQUIRED", "Save your address first; the document must show it.");
        }
        String holder = jdbc.queryForObject("SELECT display_name FROM users WHERE id=?", String.class, userId);

        Path temporary = null;
        String fileId = UUID.randomUUID().toString();
        try {
            Files.createDirectories(evidenceDirectory);
            temporary = Files.createTempFile(evidenceDirectory, "housing-", ".processing");
            Files.write(temporary, bytes);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            JsonNode extraction = DocumentChecks.extract(() -> analysis.extractHousingDocument(
                    Base64.getEncoder().encodeToString(bytes), mime, Map.of(
                    "housingSituation", claimed.name(), "holder", holder, "postalCode", address.postalCode(),
                    "street", address.street(), "building", address.building())));
            Outcome outcome = evaluate(claimed, extraction);
            JsonNode found = extraction;
            return transactions.execute(status -> record(userId, expectedRevision, address.revision(), inputs.housingSituation(),
                    claimed, outcome, fileId, digest, found));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Housing document check could not be completed", e);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    record Outcome(String status, String finding) {
        boolean verified() { return "VERIFIED_DEMO".equals(status); }
    }

    /**
     * The policy, kept out of the analysis service: the document must show the way of living that supports the
     * requested situation, and must name the account holder at the saved address (see {@link DocumentChecks}).
     */
    static Outcome evaluate(HousingSituation claimed, JsonNode extraction) {
        if (!DocumentChecks.extracted(extraction)) {
            return new Outcome("NEEDS_REVIEW", DocumentChecks.uncheckedReason(extraction));
        }
        String type = extraction.path("documentType").asText("");
        if (!DOCUMENT_LABEL.containsKey(type) || !DocumentChecks.recognized(extraction, type)) {
            return new Outcome("NEEDS_REVIEW", "The document type could not be recognized with confidence");
        }
        if (!type.equals(EXPECTED_DOCUMENT.get(claimed))) {
            return new Outcome("MISMATCH", "The document is " + DOCUMENT_LABEL.get(type) + ", which does not support \""
                    + SITUATION_LABEL.get(claimed) + "\"");
        }
        DocumentChecks checked = DocumentChecks.of(extraction, DETAILS);
        if (checked.allShown()) return new Outcome("VERIFIED_DEMO", "Document type, holder and address match the requested housing situation");
        return checked.anyContradicted()
                ? new Outcome("MISMATCH", "The name or address on the document does not match your account")
                : new Outcome("NEEDS_REVIEW", "Not every detail could be confirmed on the document");
    }

    private HousingDtos.Result record(long userId, int expectedRevision, int addressRevision, String previous,
                                      HousingSituation claimed, Outcome outcome, String fileId, String digest, JsonNode extraction) {
        Integer locked = jdbc.queryForObject("SELECT current_revision FROM financial_input_state WHERE user_id=? FOR UPDATE",
                Integer.class, userId);
        Integer currentAddress = jdbc.queryForObject("SELECT current_revision FROM user_address_state WHERE user_id=?",
                Integer.class, userId);
        if (locked == null || locked != expectedRevision || currentAddress == null || currentAddress != addressRevision) {
            throw ApiException.conflict("REVISION_CONFLICT", "Your details changed while the document was checked. Reload and try again.");
        }
        Integer financialRevision = outcome.verified()
                ? financialInputs.applyVerifiedHousing(userId, expectedRevision, claimed) : null;
        List<String> checks = new ArrayList<>(List.of("PNG or JPEG file signature validated; temporary copy removed after processing",
                outcome.finding()));
        checks.add(outcome.verified() ? "Housing situation changed to " + SITUATION_LABEL.get(claimed) : "Housing situation not changed");
        checks.add("An automatic document check is not proof of authenticity");
        boolean sample = SAMPLE_DOCUMENTS.containsKey(digest);
        String scenario = sample ? SAMPLE_DOCUMENTS.get(digest) : "user-upload";
        String dataSource = sample ? "SYNTHETIC" : "USER_UPLOAD";
        String evidence = json(Map.of("fileId", fileId, "sha256", digest, "temporaryCopyDeleted", true,
                "extraction", extraction == null ? mapper.createObjectNode() : extraction));
        return jdbc.queryForObject("""
                INSERT INTO housing_verifications (user_id, previous_situation, claimed_situation, address_revision, status,
                    financial_revision, scenario_id, checks, evidence, data_source)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?) RETURNING id, created_at
                """, (rs, row) -> new HousingDtos.Result(rs.getLong("id"), outcome.status(), claimed.name(),
                        outcome.verified() ? claimed.name() : previous,
                        financialRevision == null ? expectedRevision : financialRevision, scenario, dataSource, checks,
                        rs.getObject("created_at", OffsetDateTime.class)),
                userId, previous, claimed.name(), addressRevision, outcome.status(), financialRevision, scenario,
                json(checks), evidence, dataSource);
    }

    private List<String> readChecks(String value) {
        try { return List.of(mapper.readValue(value, String[].class)); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
