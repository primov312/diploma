package com.rocketcredit.backend.signals;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LocationPricingServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LocationPricingService pricing = new LocationPricingService(new JdbcTemplate(), mapper);

    @Test
    void normalizesCanonicalRomanNumericAndAccentedDistrictLabels() {
        for (String value : new String[]{"Budapest V", "budapest-v", "Budapest District V", "Budapest V. kerület", "Budapest 5"})
            assertThat(LocationPricingService.normalizeDistrict(value)).isEqualTo("budapest-v");
        assertThat(LocationPricingService.normalizeDistrict("District XIII")).isEqualTo("budapest-xiii");
        assertThat(LocationPricingService.normalizeDistrict("Budapest XXIII")).isEqualTo("budapest-xxiii");
        assertThat(LocationPricingService.normalizeDistrict("Budapest 24")).isNull();
        assertThat(LocationPricingService.normalizeDistrict("Vienna V")).isNull();
        assertThat(LocationPricingService.normalizeDistrict("Budapest IIII")).isNull();
    }

    @Test
    void supportsKnownAliasesWithoutPricingUnrelatedPlaces() {
        assertThat(LocationPricingService.normalizePlace("Café")).isEqualTo("CAFE");
        assertThat(LocationPricingService.normalizePlace(" GYM ")).isEqualTo("GYM");
        assertThat(LocationPricingService.normalizePlace("Grocery shop")).isEqualTo("GROCERY");
        assertThat(LocationPricingService.normalizePlace("Starbucks")).isEqualTo("STARBUCKS");
        for (String place : new String[]{"Park", "Station", "Office", "unknown"})
            assertThat(LocationPricingService.normalizePlace(place)).isNull();
    }

    @Test
    void persistsCapturedPricesAndUnitsWithoutMutatingRawReport() throws Exception {
        var raw = mapper.readTree("""
                {"dataSource":"SYNTHETIC","visitCount":3,"visits":[
                  {"id":"1","district":"Budapest V","place":"Library"},
                  {"id":"2","district":"Budapest V","place":"Park"},
                  {"id":"3","district":"Vienna","place":"Cafe"}]}
                """);
        var benchmark = new LocationPricingService.Benchmark("budapest-v", "LIBRARY", new BigDecimal("1.310541"),
                new BigDecimal("5800"), new BigDecimal("7601.14"), "HUF", "YEAR", "Adult annual membership",
                "https://fszek.hu", "2026-01-01", "2026-10-03T10:00:00Z", mapper.readTree("{\"channel\":\"published_tariff\"}"));
        var captured = new LocationPricingService.Dataset("old-version", "2026-09-30", "2026-10-03T10:00:00Z",
                new BigDecimal("5341.304348"), "https://negyzetmeterarak.hu", Map.of("budapest-v:LIBRARY", benchmark));
        var report = pricing.enrich(raw, captured);
        assertThat(report.at("/pricing/datasetVersion").asText()).isEqualTo("old-version");
        assertThat(report.at("/visits/0/pricing/estimatedPrice").decimalValue()).isEqualByComparingTo("7601.14");
        assertThat(report.at("/visits/0/pricing/unit").asText()).isEqualTo("YEAR");
        assertThat(report.at("/visits/1/pricingStatus").asText()).isEqualTo("UNSUPPORTED_PLACE");
        assertThat(report.at("/visits/2/pricingStatus").asText()).isEqualTo("UNKNOWN_DISTRICT");
        assertThat(report.path("dataSource").asText()).isEqualTo("SYNTHETIC");
        assertThat(raw.has("pricing")).isFalse();
        assertThat(raw.at("/visits/0").has("pricing")).isFalse();
    }

    @Test
    void missingDatasetStillProducesReadableCompleteReport() throws Exception {
        var report = pricing.enrich(mapper.readTree("{\"status\":\"COMPLETE\",\"visits\":[{\"district\":\"Budapest XI\",\"place\":\"Cafe\"}]}"), null);
        assertThat(report.path("status").asText()).isEqualTo("COMPLETE");
        assertThat(report.at("/pricing/available").asBoolean()).isFalse();
        assertThat(report.at("/visits/0/pricingStatus").asText()).isEqualTo("NO_DATASET");
        assertThat(report.at("/visits/0/pricing").isNull()).isTrue();
    }
}
