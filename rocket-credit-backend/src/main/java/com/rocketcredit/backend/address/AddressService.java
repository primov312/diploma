package com.rocketcredit.backend.address;

import com.rocketcredit.backend.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.rocketcredit.backend.analysis.AnalysisClient;
import com.rocketcredit.backend.users.FinancialInputsService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final FinancialInputsService financialInputs;
    private final AnalysisClient analysis;
    private final Path evidenceDirectory;
    private final TransactionTemplate transactions;
    private static final String PREPARED_CARD_SHA256 = "1f4e3b44df56c1d17fee529e0f0592c1fd234537acccee5ca05c4d7b7e3918ed";
    public AddressService(JdbcTemplate jdbc, ObjectMapper mapper, FinancialInputsService financialInputs,
                          AnalysisClient analysis,
                          @Value("${rocket.address.evidence-directory:${java.io.tmpdir}/rocket-credit-private}") Path evidenceDirectory,
                          TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.financialInputs = financialInputs;
        this.analysis = analysis;
        this.evidenceDirectory = evidenceDirectory;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public List<AddressDtos.District> districts() {
        return jdbc.query("""
                SELECT d.district_id, d.display_name, d.city, d.country_code,
                       c.dataset_version, c.monthly_rent, c.monthly_groceries, c.city_salary_monthly,
                       c.salary_basis, c.currency, c.source_label, c.observed_at, c.synthetic
                FROM demo_districts d JOIN local_cost_references c ON c.district_id=d.district_id
                WHERE c.dataset_version=(SELECT max(dataset_version) FROM local_cost_references)
                ORDER BY d.sort_order
                """, AddressService::district);
    }

    public JsonNode researchLocalCosts(String districtId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM demo_districts WHERE district_id=?", Integer.class, districtId);
        if (count == null || count != 1) throw ApiException.badRequest("UNSUPPORTED_DISTRICT", "Choose a district from the supported catalog.");
        return analysis.extractLocalCosts(districtId);
    }

    @Transactional(readOnly = true)
    public AddressDtos.Current current(long userId) {
        List<AddressDtos.Current> found = jdbc.query("""
                SELECT r.*, COALESCE(v.status, 'UNVERIFIED') AS verification_status, v.created_at AS verified_at
                FROM user_address_state s JOIN user_address_revisions r
                  ON r.user_id=s.user_id AND r.revision=s.current_revision
                LEFT JOIN LATERAL (
                    SELECT status, created_at FROM address_verifications
                    WHERE user_id=r.user_id AND address_revision=r.revision
                    ORDER BY created_at DESC LIMIT 1
                ) v ON TRUE WHERE s.user_id=?
                """, (rs, row) -> new AddressDtos.Current(true, rs.getInt("revision"),
                rs.getString("country_code"), rs.getString("city"), rs.getString("district_id"),
                rs.getString("postal_code"), rs.getString("street"), rs.getString("building"),
                rs.getString("unit"), rs.getString("verification_status"),
                rs.getObject("verified_at", OffsetDateTime.class)), userId);
        return found.isEmpty() ? new AddressDtos.Current(false, 0, null, null, null, null, null, null, null, "UNVERIFIED", null) : found.getFirst();
    }

    @Transactional
    public AddressDtos.SaveResult save(long userId, AddressDtos.SaveRequest request) {
        financialInputs.current(userId);
        jdbc.queryForObject("SELECT current_revision FROM financial_input_state WHERE user_id=? FOR UPDATE", Integer.class, userId);
        Integer current = jdbc.query("SELECT current_revision FROM user_address_state WHERE user_id=? FOR UPDATE",
                rs -> rs.next() ? rs.getInt(1) : null, userId);
        int currentRevision = current == null ? 0 : current;
        if (currentRevision != request.expectedRevision()) {
            throw ApiException.conflict("REVISION_CONFLICT", "Address changed in another session. Reload before saving.");
        }
        var district = jdbc.query("SELECT city, country_code FROM demo_districts WHERE district_id=?",
                rs -> rs.next() ? new String[]{rs.getString(1), rs.getString(2)} : null, request.districtId());
        if (district == null || !district[0].equals(request.city()) || !district[1].equalsIgnoreCase(request.countryCode())) {
            throw ApiException.badRequest("UNSUPPORTED_DISTRICT", "Choose a district from the supported Budapest catalog.");
        }
        int revision = currentRevision + 1;
        jdbc.update("""
                INSERT INTO user_address_revisions (user_id, revision, country_code, city, district_id,
                    postal_code, street, building, unit) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, revision, request.countryCode().toUpperCase(), request.city(), request.districtId(),
                request.postalCode(), request.street().trim(), request.building().trim(), blankToNull(request.unit()));
        jdbc.update("""
                INSERT INTO user_address_state (user_id, current_revision) VALUES (?, ?)
                ON CONFLICT (user_id) DO UPDATE SET current_revision=EXCLUDED.current_revision,
                    generation=user_address_state.generation+1
                """, userId, revision);
        long generation = scheduleRecalculation(userId, revision);
        long jobId = jdbc.queryForObject("SELECT id FROM affordability_jobs WHERE user_id=? AND generation=?",
                Long.class, userId, generation);
        return new AddressDtos.SaveResult(current(userId), generation, jobId);
    }

    public AddressDtos.Verification verifyUpload(long userId, int addressRevision, MultipartFile image) {
        if (image == null || image.isEmpty() || image.getSize() > 5L * 1024 * 1024) {
            throw ApiException.badRequest("INVALID_EVIDENCE", "Upload a PNG or JPEG image up to 5 MB.");
        }
        byte[] bytes;
        try { bytes = image.getBytes(); }
        catch (IOException e) { throw ApiException.badRequest("INVALID_EVIDENCE", "The uploaded image could not be read."); }
        String mime = detectMime(bytes);
        if (mime == null) throw ApiException.badRequest("INVALID_EVIDENCE", "Only PNG and JPEG images are supported.");

        Path temporary = null;
        String fileId = UUID.randomUUID().toString();
        try {
            Files.createDirectories(evidenceDirectory);
            temporary = Files.createTempFile(evidenceDirectory, "address-", ".processing");
            Files.write(temporary, bytes);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            JsonNode extraction = null;
            if (PREPARED_CARD_SHA256.equals(digest)) {
                extraction = analysis.extractDemoAddress(Base64.getEncoder().encodeToString(bytes), mime);
            }
            return recordUploadVerification(userId, addressRevision, fileId, digest, extraction);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Address verification could not be completed", e);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    private AddressDtos.Verification recordUploadVerification(long userId, int revision, String fileId,
                                                               String digest, JsonNode extraction) throws Exception {
        return transactions.execute(status -> recordUploadVerificationLocked(userId, revision, fileId, digest, extraction));
    }

    private AddressDtos.Verification recordUploadVerificationLocked(long userId, int revision, String fileId,
                                                                     String digest, JsonNode extraction) {
        var addresses = jdbc.query("""
                SELECT r.* FROM user_address_state s JOIN user_address_revisions r
                  ON r.user_id=s.user_id AND r.revision=s.current_revision WHERE s.user_id=? FOR UPDATE OF s
                """, (rs, row) -> new AddressValue(rs.getInt("revision"), rs.getString("country_code"),
                rs.getString("city"), rs.getString("district_id"), rs.getString("postal_code"),
                rs.getString("street"), rs.getString("building"), rs.getString("unit")), userId);
        if (addresses.isEmpty()) throw ApiException.badRequest("ADDRESS_REQUIRED", "Save a living address before verification.");
        AddressValue address = addresses.getFirst();
        if (address.revision() != revision) throw ApiException.conflict("REVISION_CONFLICT", "The address changed while the document was reviewed.");
        String name = jdbc.queryForObject("SELECT display_name FROM users WHERE id=?", String.class, userId);
        String districtName = jdbc.queryForObject("SELECT display_name FROM demo_districts WHERE district_id=?", String.class, address.districtId());
        boolean aiUnavailable = extraction != null && "AI_UNAVAILABLE".equals(extraction.path("status").asText());
        boolean recognized = extraction != null && "EXTRACTED".equals(extraction.path("status").asText());
        boolean complete = recognized && extraction.path("documentType").asText().equals("ADDRESS_CARD")
                && extraction.path("confidence").asDouble(0) >= 0.85
                && !extraction.path("evidenceQuotes").isEmpty();
        boolean matches = complete && name.equalsIgnoreCase(extraction.path("addressee").asText())
                && address.countryCode().equalsIgnoreCase(extraction.path("countryCode").asText())
                && address.city().equalsIgnoreCase(extraction.path("city").asText())
                && districtName.equalsIgnoreCase(extraction.path("districtName").asText())
                && address.postalCode().equalsIgnoreCase(extraction.path("postalCode").asText())
                && address.street().equalsIgnoreCase(extraction.path("street").asText())
                && address.building().equalsIgnoreCase(extraction.path("building").asText())
                && same(address.unit(), extraction.path("unit").asText(null));
        String outcome = matches ? "VERIFIED_DEMO" : aiUnavailable || !complete ? "NEEDS_REVIEW" : "MISMATCH";
        List<String> checks = List.of(
                "PNG or JPEG file signature validated; temporary copy removed after processing",
                matches ? "Addressee and submitted address fields matched" : "The extracted content did not pass all deterministic checks",
                "A sample document is not proof of authenticity or physical residence");
        String scenario = PREPARED_CARD_SHA256.equals(digest) ? "prepared-address-card-v1" : "unrecognized-upload";
        String dataSource = PREPARED_CARD_SHA256.equals(digest) ? "SYNTHETIC" : "USER_UPLOAD";
        String evidenceJson = toJson(Map.of(
                "fileId", fileId, "sha256", digest, "temporaryCopyDeleted", true,
                "extraction", extraction == null ? mapper.createObjectNode() : extraction,
                "checks", checks));
        Long verificationId = jdbc.queryForObject("""
                INSERT INTO address_verifications (user_id, address_revision, status, scenario_id, checks, evidence, data_source)
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?) RETURNING id
        """, Long.class, userId, revision, outcome, scenario, toJson(checks), evidenceJson, dataSource);
        long jobId = 0;
        if (matches) {
            long generation = scheduleRecalculation(userId, revision);
            jobId = jdbc.queryForObject("SELECT id FROM affordability_jobs WHERE user_id=? AND generation=?", Long.class, userId, generation);
        }
        return new AddressDtos.Verification(verificationId, revision, outcome, scenario, dataSource, checks, OffsetDateTime.now(), jobId);
    }

    private static boolean same(String left, String right) {
        return (left == null || left.isBlank()) ? right == null || right.isBlank() : right != null && left.equalsIgnoreCase(right);
    }

    private static String detectMime(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) return "image/png";
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) return "image/jpeg";
        return null;
    }


    private long scheduleRecalculation(long userId, int addressRevision) {
        Long generation = jdbc.queryForObject("UPDATE financial_input_state SET generation=generation+1 WHERE user_id=? RETURNING generation",
                Long.class, userId);
        if (generation == null) throw ApiException.notFound("Financial inputs");
        Integer financeRevision = jdbc.queryForObject("SELECT current_revision FROM financial_input_state WHERE user_id=?", Integer.class, userId);
        jdbc.update("""
                INSERT INTO affordability_jobs (user_id, generation, financial_revision)
                VALUES (?, ?, ?) ON CONFLICT (user_id, generation) DO NOTHING
                """, userId, generation, financeRevision);
        return generation;
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private String toJson(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    private record AddressValue(int revision, String countryCode, String city, String districtId,
                                String postalCode, String street, String building, String unit) {}

    private static AddressDtos.District district(ResultSet rs, int row) throws SQLException {
        return new AddressDtos.District(rs.getString("district_id"), rs.getString("display_name"),
                rs.getString("city"), rs.getString("country_code"), rs.getString("dataset_version"),
                rs.getBigDecimal("monthly_rent"), rs.getBigDecimal("monthly_groceries"),
                rs.getBigDecimal("city_salary_monthly"), rs.getString("salary_basis"),
                rs.getString("currency"), rs.getString("source_label"), rs.getDate("observed_at").toLocalDate(),
                rs.getBoolean("synthetic"));
    }
}
