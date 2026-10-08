package com.rocketcredit.backend.signals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Reference estimates only: no calls into finance, affordability or decision features. */
@Service
public class LocationPricingService {
    private static final List<String> ROMANS = List.of("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x",
            "xi", "xii", "xiii", "xiv", "xv", "xvi", "xvii", "xviii", "xix", "xx", "xxi", "xxii", "xxiii");
    private static final Pattern DISTRICT = Pattern.compile("^(?:budapest[- ](?:district )?|district )([ivx]+|[0-9]{1,2})(?:\\.?(?: kerulet)?)?$");
    private static final String METHOD = "District mean apartment asking rent / unweighted mean of all 23 district means; benchmark × coff";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public LocationPricingService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Capture once before analysis; an import during a run cannot change this snapshot. */
    public Dataset activeDataset() { return loadDataset(null); }

    public Dataset dataset(String version) { return loadDataset(version); }

    private Dataset loadDataset(String version) {
        var datasets = jdbc.query("""
                SELECT dataset_version, housing_observed_at, housing_retrieved_at, district_average, source_url
                FROM location_price_datasets WHERE (CAST(? AS text) IS NULL AND active) OR dataset_version=?
                """, (rs, row) -> new Dataset(rs.getString("dataset_version"), rs.getString("housing_observed_at"),
                rs.getString("housing_retrieved_at"), rs.getBigDecimal("district_average"), rs.getString("source_url"), Map.of()), version, version);
        if (datasets.isEmpty()) return null;
        Dataset dataset = datasets.getFirst();
        Map<String, Benchmark> prices = new HashMap<>();
        jdbc.query("""
                SELECT p.district_id, p.category, p.estimated_price, d.coff, b.baseline_price,
                       b.unit, b.currency, b.benchmark_description, b.source_url, b.observed_at,
                       b.retrieved_at, b.evidence::text
                FROM location_venue_prices p
                JOIN location_district_prices d USING (dataset_version, district_id)
                JOIN location_venue_baselines b USING (dataset_version, category)
                WHERE p.dataset_version=?
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    var benchmark = new Benchmark(rs.getString("district_id"), rs.getString("category"),
                            rs.getBigDecimal("coff"), rs.getBigDecimal("baseline_price"), rs.getBigDecimal("estimated_price"),
                            rs.getString("currency"), rs.getString("unit"), rs.getString("benchmark_description"),
                            rs.getString("source_url"), rs.getString("observed_at"), rs.getString("retrieved_at"),
                            readEvidence(rs.getString("evidence")));
                    prices.put(key(benchmark.districtId(), benchmark.category()), benchmark);
                }, dataset.version());
        return new Dataset(dataset.version(), dataset.observedAt(), dataset.retrievedAt(), dataset.districtAverage(),
                dataset.sourceUrl(), Map.copyOf(prices));
    }

    public JsonNode enrich(JsonNode raw, Dataset dataset) {
        if (!(raw instanceof ObjectNode)) throw new IllegalArgumentException("Location report must be an object");
        ObjectNode report = raw.deepCopy();
        ObjectNode metadata = report.putObject("pricing");
        metadata.put("available", dataset != null);
        metadata.put("method", METHOD);
        if (dataset != null) {
            metadata.put("datasetVersion", dataset.version());
            metadata.put("housingObservedAt", dataset.observedAt());
            metadata.put("housingRetrievedAt", dataset.retrievedAt());
            metadata.put("districtAverage", dataset.districtAverage());
            metadata.put("sourceUrl", dataset.sourceUrl());
            metadata.put("currency", "HUF");
        }
        for (JsonNode item : report.path("visits")) {
            if (!(item instanceof ObjectNode visit)) continue;
            String districtId = normalizeDistrict(visit.path("district").asText());
            String category = normalizePlace(visit.path("place").asText());
            visit.putNull("pricing");
            if (category == null) {
                visit.put("pricingStatus", "UNSUPPORTED_PLACE");
            } else if (districtId == null) {
                visit.put("pricingStatus", "UNKNOWN_DISTRICT");
            } else if (dataset == null) {
                visit.put("pricingStatus", "NO_DATASET");
            } else {
                Benchmark price = dataset.prices().get(key(districtId, category));
                if (price == null) {
                    visit.put("pricingStatus", "PRICE_UNAVAILABLE");
                    continue;
                }
                visit.put("pricingStatus", "PRICED");
                ObjectNode pricing = visit.putObject("pricing");
                pricing.put("districtId", districtId);
                pricing.put("category", category);
                pricing.put("coff", price.coff());
                pricing.put("baselinePrice", price.baselinePrice());
                pricing.put("estimatedPrice", price.estimatedPrice());
                pricing.put("currency", price.currency());
                pricing.put("unit", price.unit());
                pricing.put("description", price.description());
                pricing.put("sourceUrl", price.sourceUrl());
                pricing.put("observedAt", price.observedAt());
                pricing.put("retrievedAt", price.retrievedAt());
                pricing.set("evidence", price.evidence().deepCopy());
            }
        }
        return report;
    }

    static String normalizeDistrict(String value) {
        var match = DISTRICT.matcher(normalize(value));
        if (!match.matches()) return null;
        String district = match.group(1);
        if (district.matches("[0-9]+")) {
            int number = Integer.parseInt(district);
            if (number < 1 || number > 23) return null;
            district = ROMANS.get(number - 1);
        }
        return ROMANS.contains(district) ? "budapest-" + district : null;
    }

    static String normalizePlace(String value) {
        return switch (normalize(value)) {
            case "grocery", "grocery shop", "grocery store", "supermarket", "market" -> "GROCERY";
            case "library" -> "LIBRARY";
            case "gym", "fitness", "fitness centre", "fitness center" -> "GYM";
            case "cafe", "coffee shop" -> "CAFE";
            case "starbucks" -> "STARBUCKS";
            default -> null;
        };
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private static String key(String districtId, String category) { return districtId + ":" + category; }

    private JsonNode readEvidence(String json) {
        try { return mapper.readTree(json); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Invalid price evidence", e); }
    }

    public record Dataset(String version, String observedAt, String retrievedAt, BigDecimal districtAverage,
                          String sourceUrl, Map<String, Benchmark> prices) {}
    public record Benchmark(String districtId, String category, BigDecimal coff, BigDecimal baselinePrice,
                            BigDecimal estimatedPrice, String currency, String unit, String description,
                            String sourceUrl, String observedAt, String retrievedAt, JsonNode evidence) {}
}
