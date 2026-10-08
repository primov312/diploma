package com.rocketcredit.backend.address;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rocketcredit.backend.AbstractIntegrationTest;
import com.rocketcredit.backend.affordability.AffordabilityJobs;
import com.rocketcredit.backend.affordability.AffordabilityJobWorker;
import com.rocketcredit.backend.analysis.AnalysisClient;
import com.rocketcredit.backend.analysis.AffordabilityResult;
import com.rocketcredit.backend.applications.features.DbFinanceFeatureProvider;
import com.rocketcredit.backend.applications.features.FeatureProviders;
import com.rocketcredit.backend.users.FinancialInputsService;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"rocket.monthly-costs.rollout-delay=3600000", "rocket.affordability.poll-delay=3600000"})
class MonthlyLivingCostsIntegrationTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MonthlyCostResolver costs;
    @Autowired MonthlyLivingCostService service;
    @Autowired AddressService addresses;
    @Autowired AffordabilityJobs jobs;
    @Autowired AffordabilityJobWorker worker;
    @Autowired FinancialInputsService finances;
    @Autowired DbFinanceFeatureProvider provider;
    @Autowired com.rocketcredit.backend.signals.DemoSignalService signals;
    @MockBean AnalysisClient analysis;
    JsonNode snapshot;
    String version;

    @BeforeEach
    void setup() throws Exception {
        snapshot = mapper.readTree(Path.of("../scripts/data/budapest-monthly-living-costs-2026-10-03.json").toFile());
        String housingVersion = snapshot.path("housingDatasetVersion").asText();
        jdbc.update("""
                INSERT INTO location_price_datasets(dataset_version,housing_observed_at,housing_retrieved_at,district_average,content_checksum,source_url)
                VALUES(?,'2026-09-30',now(),5341.304348,repeat('e',64),'https://example.test/housing') ON CONFLICT DO NOTHING
                """, housingVersion);
        JsonNode location = mapper.readTree(Path.of("../scripts/data/budapest-location-prices-2026-09-30.json").toFile());
        var mean = new BigDecimal("5341.304347826086956521739130434782608696");
        for (JsonNode d : snapshot.path("housing").path("districts")) {
            BigDecimal coefficient=new BigDecimal(d.path("rentPerM2").asText()).divide(mean,java.math.MathContext.DECIMAL128);
            jdbc.update("INSERT INTO location_district_prices VALUES(?,?,?,?,?,10,?,?) ON CONFLICT DO NOTHING",housingVersion,
                    d.path("districtId").asText(),d.path("districtNumber").asInt(),d.path("displayName").asText(),
                    new BigDecimal(d.path("rentPerM2").asText()),coefficient,"https://example.test/housing");
            for(JsonNode b:location.path("baselines")) {
                jdbc.update("""
                        INSERT INTO location_venue_baselines(dataset_version,category,benchmark_description,unit,baseline_price,source_url,retrieved_at,evidence)
                        VALUES(?,?,?,?,?, ?,now(),'{}'::jsonb) ON CONFLICT DO NOTHING
                        """,housingVersion,b.path("category").asText(),b.path("description").asText(),b.path("unit").asText(),
                        new BigDecimal(b.path("price").asText()),b.path("sourceUrl").asText());
                jdbc.update("INSERT INTO location_venue_prices VALUES(?,?,?,?) ON CONFLICT DO NOTHING",housingVersion,d.path("districtId").asText(),
                        b.path("category").asText(),new BigDecimal(b.path("price").asText()).multiply(coefficient).setScale(2,java.math.RoundingMode.HALF_UP));
            }
        }
        jdbc.update("UPDATE location_price_datasets SET active=FALSE WHERE active");
        jdbc.update("UPDATE location_price_datasets SET active=TRUE WHERE dataset_version=?",housingVersion);
        version = publish(snapshot);
        when(analysis.affordability(any())).thenReturn(new AffordabilityResult("affordability-v3", "rules-v3", "USD", 6,
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("16.66"), Map.of(), List.of()));
    }

    @AfterEach
    void cleanup() { jdbc.update("UPDATE monthly_living_cost_datasets SET active=FALSE WHERE active"); }

    String publish(JsonNode snapshot) {
        String version = "monthly-test-" + UUID.randomUUID();
        jdbc.update("INSERT INTO monthly_living_cost_datasets(dataset_version,housing_dataset_version,content_checksum,snapshot) VALUES(?,?,?,?::jsonb)",
                version, snapshot.path("housingDatasetVersion").asText(), UUID.randomUUID().toString().replace("-", "").repeat(2), snapshot.toString());
        BigDecimal basket=BigDecimal.ZERO,sum=BigDecimal.ZERO;
        for(JsonNode product:snapshot.path("groceries")) basket=basket.add(new BigDecimal(product.path("packagePrice").asText())
                .divide(new BigDecimal(product.path("packageQuantity").asText()),java.math.MathContext.DECIMAL128)
                .multiply(new BigDecimal(snapshot.path("defaults").path("groceryQuantities").path(product.path("id").asText()).asText())));
        for(JsonNode d:snapshot.path("housing").path("districts")) sum=sum.add(new BigDecimal(d.path("rentPerM2").asText()));
        BigDecimal mean=sum.divide(new BigDecimal("23"),java.math.MathContext.DECIMAL128);
        for (JsonNode d : snapshot.path("housing").path("districts")) {
            BigDecimal rent=new BigDecimal(d.path("rentPerM2").asText()).multiply(new BigDecimal("50"));
            BigDecimal groceries=basket.multiply(new BigDecimal(d.path("rentPerM2").asText()).divide(mean,java.math.MathContext.DECIMAL128))
                    .setScale(2,java.math.RoundingMode.HALF_UP);
            jdbc.update("INSERT INTO monthly_living_costs(dataset_version,district_id,monthly_rent,monthly_groceries,monthly_total) VALUES(?,?,?,?,?)",
                    version,d.path("districtId").asText(),rent,groceries,rent.add(groceries));
        }
        for (JsonNode m : snapshot.path("postal").path("mappings")) {
            jdbc.update("INSERT INTO postal_code_districts VALUES(?,?,?,'https://example.test/postcodes',now())", version,
                    m.path("postalCode").asText(), m.path("districtId").asText());
        }
        jdbc.update("UPDATE monthly_living_cost_datasets SET active=FALSE WHERE active");
        jdbc.update("UPDATE monthly_living_cost_datasets SET active=TRUE WHERE dataset_version=?", version);
        return version;
    }

    long user(boolean renting) throws Exception {
        String email = uniqueEmail(); register(email, "password-123");
        long user = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        finances.current(user);
        jdbc.update("UPDATE financial_input_revisions SET housing_situation=?,monthly_net_income=3000 WHERE user_id=?",
                renting ? "RENTING" : "OWNER", user);
        addresses.save(user, new AddressDtos.SaveRequest(0, "HU", "Budapest", "budapest-v", "1051", "Minta utca", "12", null));
        return user;
    }

    void verified(long user) {
        jdbc.update("""
                INSERT INTO address_verifications(user_id,address_revision,status,scenario_id,checks,evidence,data_source)
                VALUES(?,1,'VERIFIED_DEMO','test','[]'::jsonb,'{}'::jsonb,'SYNTHETIC')
                """, user);
        jobs.recalculate(user);
    }

    ObjectNode draft(long user) {
        ObjectNode request = costs.profileJson(costs.savedProfile(user));
        request.put("expectedRevision", request.path("revision").asInt());
        return request;
    }

    long generation(long user) { return jdbc.queryForObject("SELECT generation FROM financial_input_state WHERE user_id=?", Long.class, user); }

    @Test
    void postcodeCatalogCoversEveryDistrictAndSaveRejectsContradictions() throws Exception {
        assertThat(costs.postalCode("1021").path("districtId").asText()).isEqualTo("budapest-ii");
        assertThat(costs.postalCode("1051").path("districtId").asText()).isEqualTo("budapest-v");
        assertThat(costs.postalCode("1239").path("districtId").asText()).isEqualTo("budapest-xxiii");
        assertThat(snapshot.path("postal").path("mappings").findValuesAsText("districtId").stream().distinct().count()).isEqualTo(23);
        for (JsonNode mapping : snapshot.path("postal").path("mappings")) {
            assertThat(costs.postalCode(mapping.path("postalCode").asText()).path("districtId")).isEqualTo(mapping.path("districtId"));
        }
        for (String code : new String[]{"1007", "1019", "9999", "105", "abcd"}) assertThatThrownBy(() -> costs.postalCode(code)).isInstanceOf(RuntimeException.class);
        long user = user(true);
        assertThatThrownBy(() -> addresses.save(user, new AddressDtos.SaveRequest(1, "HU", "Budapest", "budapest-ii", "1051", "Minta utca", "12", null)))
                .hasMessageContaining("District must match");
        assertThat(addresses.current(user).revision()).isEqualTo(1);
    }

    @Test
    void verificationProfilesSharingAndApplicationInputsUseTheSameCapturedReferences() throws Exception {
        long user = user(true);
        var initial = service.current(user);
        assertThat(initial.path("monthlyRent").decimalValue()).isEqualByComparingTo("350000.00");
        assertThat(initial.path("eligible").asBoolean()).isFalse();
        assertThat(costs.references(costs.currentContext(user, generation(user))).eligible()).isFalse();
        verified(user);
        ObjectNode request = draft(user);
        request.put("apartmentSize", "40").put("rentSharers", 2);
        ObjectNode result = service.save(user, request);
        assertThat(result.path("monthlyRent").decimalValue()).isEqualByComparingTo("140000");
        assertThat(result.path("monthlyOther").decimalValue()).isPositive();
        assertThat(result.path("eligible").asBoolean()).isTrue();
        assertThat(addresses.current(user).verificationStatus()).isEqualTo("VERIFIED_DEMO");
        var reference = costs.references(costs.currentContext(user, generation(user)));
        assertThat(reference.rent()).isEqualByComparingTo("388.89");
        assertThat(reference.other()).isEqualByComparingTo(result.path("monthlyOther").decimalValue().divide(new BigDecimal("360"),2,java.math.RoundingMode.HALF_UP));
        var features = provider.financeBundle(new FeatureProviders.Context(user, 1L, OffsetDateTime.now()));
        assertThat(features.affordability().districtRentReference()).isEqualByComparingTo(reference.rent());
        assertThat(features.affordability().districtGroceryReference()).isEqualByComparingTo(reference.groceries());
        assertThat(features.affordability().districtOtherReference()).isEqualByComparingTo(reference.other());
        assertThat(features.affordability().referencesEligible()).isTrue();
        assertThatThrownBy(() -> service.save(user, request)).hasMessageContaining("changed in another session");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_input_revisions WHERE user_id=?", Integer.class, user)).isEqualTo(1);
    }

    @Test
    void refreshPreservesProfilesQueuedContextsAndSavedSnapshotUntilExplicitAdoption() throws Exception {
        long user = user(true); verified(user);
        long oldGeneration = generation(user);
        JsonNode oldContext = costs.currentContext(user, oldGeneration);
        JsonNode changed = snapshot.deepCopy();
        ((ObjectNode) changed.path("groceries").get(0)).put("packagePrice", "630").put("unitPrice", "630.000000");
        String newVersion = publish(changed);
        assertThat(service.current(user).path("datasetVersion").asText()).isEqualTo(version);
        assertThat(costs.currentContext(user, oldGeneration)).isEqualTo(oldContext);
        for (int i = 0; i < 25 && jdbc.queryForObject("SELECT count(*) FROM affordability_snapshots WHERE user_id=? AND generation=?", Integer.class, user, oldGeneration) == 0; i++) worker.poll();
        JsonNode persisted = costs.read(jdbc.queryForObject("SELECT local_cost_context::text FROM affordability_snapshots WHERE user_id=? AND generation=?", String.class, user, oldGeneration));
        assertThat(persisted).isEqualTo(oldContext);
        ObjectNode request = draft(user); request.put("refreshDataset", true);
        ObjectNode adopted = service.save(user, request);
        assertThat(adopted.path("datasetVersion").asText()).isEqualTo(newVersion);
        assertThat(adopted.path("monthlyGroceries").decimalValue()).isGreaterThan(oldContext.path("monthlyGroceries").decimalValue());
        assertThat(costs.currentContext(user, oldGeneration)).isEqualTo(persisted);
        assertThat(generation(user)).isGreaterThan(oldGeneration);
    }

    @Test
    void previewDoesNotWriteAndOwnerTotalExcludesRentalBenchmark() throws Exception {
        long user = user(false); verified(user);
        long generation = generation(user);
        ObjectNode request = draft(user); request.put("apartmentSize", "100").put("postalCode", "1021");
        var preview = service.preview(user, request);
        assertThat(preview.path("monthlyRent").decimalValue()).isEqualByComparingTo("623000");
        assertThat(preview.path("rentApplies").asBoolean()).isFalse();
        assertThat(preview.path("monthlyTotal").decimalValue()).isEqualByComparingTo(preview.path("monthlyGroceries").decimalValue().add(preview.path("monthlyOther").decimalValue()));
        assertThat(preview.path("eligible").asBoolean()).isFalse();
        assertThat(generation(user)).isEqualTo(generation);
        assertThat(costs.savedProfile(user).apartmentSize()).isEqualByComparingTo("50");
        ObjectNode bad = draft(user); bad.put("apartmentSize", "0");
        assertThatThrownBy(() -> service.save(user, bad)).hasMessageContaining("supported range");
    }

    @Test
    void preparedSampleMatchesDistrictAliasAndFailedReverificationRemovesFloors() throws Exception {
        long user = user(true);
        jdbc.update("UPDATE users SET display_name='Riley Review' WHERE id=?", user);
        ObjectNode extraction = (ObjectNode) mapper.readTree("""
                {"status":"EXTRACTED","documentType":"ADDRESS_CARD","confidence":1,
                 "addressee":"Riley Review","countryCode":"HU","city":"Budapest","districtName":"District V",
                 "postalCode":"1051","street":"Minta utca","building":"12","unit":null,"evidenceQuotes":["Sample"]}
                """);
        when(analysis.extractDemoAddress(any(), any())).thenReturn(extraction);
        byte[] image = java.nio.file.Files.readAllBytes(Path.of("../demo-repository/src/assets/sample-address-card-riley.png"));
        var file = new org.springframework.mock.web.MockMultipartFile("file", "sample.png", "image/png", image);
        assertThat(addresses.verifyUpload(user, 1, file).status()).isEqualTo("VERIFIED_DEMO");
        assertThat(costs.references(costs.currentContext(user, generation(user))).eligible()).isTrue();
        extraction.put("postalCode", "1239");
        long verifiedGeneration = generation(user);
        assertThat(addresses.verifyUpload(user, 1, file).status()).isEqualTo("MISMATCH");
        assertThat(generation(user)).isGreaterThan(verifiedGeneration);
        assertThat(costs.references(costs.currentContext(user, generation(user))).eligible()).isFalse();
    }

    @Test
    void automaticAmountsCannotBeOverriddenButSizeAndSharingCan() throws Exception {
        long user=user(true); verified(user);
        ObjectNode request=draft(user); request.put("otherSpending","1");
        assertThatThrownBy(()->service.save(user,request)).hasMessageContaining("calculated from venue");
        request.put("otherSpending","0"); ((ObjectNode)request.path("groceryQuantities")).put("milk","0");
        assertThatThrownBy(()->service.save(user,request)).hasMessageContaining("fixed per-person");
        int revision=finances.current(user).revision();
        var manual=new com.rocketcredit.backend.users.SaveFinancialInputsRequest(revision,BigDecimal.ONE,
                com.rocketcredit.backend.users.SaveFinancialInputsRequest.HousingSituation.RENTING,
                com.rocketcredit.backend.users.SaveFinancialInputsRequest.ExpenseMode.AUTOMATIC,
                null,null,null,null,null,null,null);
        assertThatThrownBy(()->finances.save(user,manual)).hasMessageContaining("supplied by the demo");
        assertThat(finances.current(user).revision()).isEqualTo(revision);
        assertThat(finances.current(user).expenseMode()).isEqualTo("AUTOMATIC");
    }

    @Test
    void forecastBlendsSufficientHistoryAndFallsBackForSparseHistoryWithoutCountingGroceries() throws Exception {
        long user=user(true); verified(user);
        JsonNode baseline=service.current(user).path("activityForecast");
        assertThat(baseline.path("method").asText()).isEqualTo("BENCHMARK_FALLBACK");
        JsonNode raw=mapper.readTree("""
            {"status":"COMPLETE","dataSource":"SYNTHETIC","observedFrom":"2026-09-01T12:00:00Z",
             "observedTo":"2026-09-07T12:00:00Z","visits":[
              {"id":"gym1","district":"Budapest V","place":"GYM"},
              {"id":"gym2","district":"Budapest V","place":"GYM"},
              {"id":"cafe","district":"Budapest V","place":"Cafe"},
              {"id":"food","district":"Budapest V","place":"Grocery store"},
              {"id":"library1","district":"Budapest V","place":"Library"},
              {"id":"library2","district":"Budapest V","place":"Library"}]}
            """);
        when(analysis.runDemoAnalysis(any(),any(),any())).thenReturn(raw);
        long oldGeneration=generation(user);
        JsonNode oldContext=costs.currentContext(user,oldGeneration);
        signals.run(user,"LOCATION","test-week");
        JsonNode quote=service.current(user),forecast=quote.path("activityForecast");
        assertThat(forecast.path("historySufficient").asBoolean()).isTrue();
        assertThat(forecast.path("pricedVisits").asInt()).isEqualTo(3);
        assertThat(forecast.path("items")).hasSize(4);
        JsonNode gym=forecast.path("items").get(0),library=forecast.path("items").get(3);
        BigDecimal projection=gym.path("unitPrice").decimalValue().multiply(new BigDecimal("60"))
                .divide(new BigDecimal("7"),40,java.math.RoundingMode.HALF_UP);
        assertThat(gym.path("monthlyAmount").decimalValue()).isEqualByComparingTo(gym.path("baselineMonthly").decimalValue()
                .add(projection).divide(new BigDecimal("2"),2,java.math.RoundingMode.HALF_UP));
        assertThat(library.path("monthlyAmount").decimalValue()).isEqualByComparingTo(library.path("baselineMonthly").decimalValue());
        assertThat(generation(user)).isGreaterThan(oldGeneration);
        assertThat(costs.currentContext(user,oldGeneration)).isEqualTo(oldContext);
        assertThat(costs.currentContext(user,generation(user)).path("activityForecast")).isEqualTo(costs.read(forecast.toString()));
        var features=provider.financeBundle(new FeatureProviders.Context(user,1L,OffsetDateTime.now()));
        assertThat(features.affordability().expenseMode()).isEqualTo("AUTOMATIC");
        assertThat(features.finance().monthlyExpenses()).isEqualByComparingTo(features.affordability().districtRentReference()
                .add(features.affordability().districtGroceryReference()).add(features.affordability().districtOtherReference()).max(features.affordability().legacyLivingExpenses()));
        var settings = signals.update(user,new com.rocketcredit.backend.signals.DemoSignalDtos.UpdateRequest(false,false));
        assertThat(settings.locationEnabled()).isTrue();
        assertThat(settings.socialEnabled()).isTrue();
        assertThat(service.current(user).path("activityForecast").path("monthlyAmount")).isEqualTo(forecast.path("monthlyAmount"));
        ((ObjectNode)raw).put("observedTo","2026-09-02T12:00:00Z");
        signals.run(user,"LOCATION","test-sparse");
        assertThat(service.current(user).path("activityForecast").path("method").asText()).isEqualTo("BENCHMARK_FALLBACK");
    }

    @Test
    void missingDataDoesNotBecomeZeroCostsOrSyntheticFloors() throws Exception {
        long user = user(true);
        jdbc.update("DELETE FROM user_living_cost_profiles WHERE user_id=?", user);
        jdbc.update("UPDATE monthly_living_cost_datasets SET active=FALSE WHERE active");
        var unavailable = service.current(user);
        assertThat(unavailable.path("available").asBoolean()).isFalse();
        assertThat(unavailable.path("unavailableReason").asText()).isEqualTo("NO_DATASET");
        assertThat(unavailable.has("monthlyTotal")).isFalse();
        jobs.recalculate(user);
        assertThat(costs.references(costs.currentContext(user, generation(user))).eligible()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM local_cost_references", Integer.class)).isEqualTo(3);
    }
}
