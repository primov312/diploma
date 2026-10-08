package com.rocketcredit.backend.address;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rocketcredit.backend.common.ApiException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import com.rocketcredit.backend.signals.LocationPricingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** One resolver for the address preview, queued estimates and application features. */
@Service
public class MonthlyCostResolver {
    public static final BigDecimal HUF_PER_USD = new BigDecimal("360");
    private static final MathContext PRECISION = new MathContext(40, RoundingMode.HALF_UP);
    private static final BigDecimal MAX = new BigDecimal("9999999999.99");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LocationPricingService locationPrices;
    public static final String METHOD_VERSION = "automatic-living-costs-v1";
    // Explicit demo frequencies, not researched consumption averages.
    private static final Map<String,Integer> MONTHLY_VISITS = Map.of("GYM",4,"CAFE",8,"STARBUCKS",4);

    public MonthlyCostResolver(JdbcTemplate jdbc, ObjectMapper mapper, LocationPricingService locationPrices) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.locationPrices = locationPrices;
    }

    public record Dataset(String version, JsonNode snapshot) {}
    public record Profile(int revision, String datasetVersion, BigDecimal apartmentSize, int rentSharers,
                          JsonNode groceryQuantities, BigDecimal otherSpending, String source) {}
    public record References(BigDecimal rent, BigDecimal groceries, BigDecimal other, boolean eligible, JsonNode context) {}

    public Dataset activeDataset() {
        return jdbc.query("SELECT dataset_version,snapshot::text FROM monthly_living_cost_datasets WHERE active",
                rs -> rs.next() ? new Dataset(rs.getString(1), read(rs.getString(2))) : null);
    }

    public Dataset dataset(String version) {
        return jdbc.query("SELECT dataset_version,snapshot::text FROM monthly_living_cost_datasets WHERE dataset_version=?",
                rs -> rs.next() ? new Dataset(rs.getString(1), read(rs.getString(2))) : null, version);
    }

    public ObjectNode postalCode(String code) {
        if (code == null || !code.matches("[0-9]{4}")) {
            throw ApiException.badRequest("INVALID_POSTAL_CODE", "Enter a four-digit Budapest postal code.");
        }
        Dataset active = activeDataset();
        if (active == null) throw ApiException.badRequest("POSTCODE_MAPPING_UNAVAILABLE", "The Budapest postcode dataset is unavailable.");
        ObjectNode mapping = mapping(active, code);
        if (mapping == null) throw ApiException.badRequest("UNSUPPORTED_POSTAL_CODE", "This code is not a supported residential Budapest postcode.");
        return mapping;
    }

    private ObjectNode mapping(Dataset dataset, String code) {
        return jdbc.query("""
                SELECT p.district_id,d.display_name,p.source_url,p.retrieved_at::text
                FROM postal_code_districts p JOIN demo_districts d USING(district_id)
                WHERE p.dataset_version=? AND p.postal_code=?
                """, rs -> {
            if (!rs.next()) return null;
            ObjectNode result = mapper.createObjectNode();
            result.put("postalCode", code).put("districtId", rs.getString(1)).put("displayName", rs.getString(2))
                    .put("city", "Budapest").put("countryCode", "HU").put("sourceUrl", rs.getString(3))
                    .put("retrievedAt", rs.getString(4)).put("datasetVersion", dataset.version());
            return result;
        }, dataset.version(), code);
    }

    public Profile savedProfile(long userId) {
        return jdbc.query("""
                SELECT revision,dataset_version,apartment_size,rent_sharers,grocery_quantities::text,other_spending,source
                FROM user_living_cost_profiles WHERE user_id=? ORDER BY revision DESC LIMIT 1
                """, rs -> rs.next() ? new Profile(rs.getInt(1), rs.getString(2), rs.getBigDecimal(3), rs.getInt(4),
                read(rs.getString(5)), rs.getBigDecimal(6), rs.getString(7)) : null, userId);
    }

    public Profile defaults(Dataset dataset) {
        JsonNode defaults = dataset.snapshot().path("defaults");
        return new Profile(0, dataset.version(), new BigDecimal(defaults.path("apartmentSize").asText()),
                defaults.path("rentSharers").asInt(), defaults.path("groceryQuantities"),
                new BigDecimal(defaults.path("otherSpending").asText()), "DEMO_DEFAULT");
    }

    public Profile validateProfile(JsonNode body, Dataset dataset, int revision) {
        try {
            BigDecimal area = amount(body.get("apartmentSize"), "Apartment size", true, new BigDecimal("1000"));
            JsonNode sharers = body.get("rentSharers");
            if (sharers == null || !sharers.isIntegralNumber() || !sharers.canConvertToInt() || sharers.asInt() < 1 || sharers.asInt() > 20) {
                throw ApiException.badRequest("INVALID_PROFILE", "Rent sharers must be an integer between 1 and 20.");
            }
            BigDecimal other = BigDecimal.ZERO;
            if (body.has("otherSpending") && amount(body.get("otherSpending"), "Other spending", false, MAX).signum() != 0) {
                throw ApiException.badRequest("MANAGED_COST_ASSUMPTIONS", "Other spending is calculated from venue benchmarks and location history.");
            }
            JsonNode quantities = dataset.snapshot().path("defaults").path("groceryQuantities");
            if (body.has("groceryQuantities")) {
                JsonNode supplied = body.path("groceryQuantities");
                if (!supplied.isObject() || supplied.size()!=quantities.size()) {
                    throw ApiException.badRequest("MANAGED_COST_ASSUMPTIONS", "Grocery quantities use the fixed per-person demo basket.");
                }
                for (JsonNode product : dataset.snapshot().path("groceries")) {
                    String id = product.path("id").asText();
                    if (amount(supplied.get(id),id,false,new BigDecimal("1000")).compareTo(new BigDecimal(quantities.path(id).asText()))!=0) {
                        throw ApiException.badRequest("MANAGED_COST_ASSUMPTIONS", "Grocery quantities use the fixed per-person demo basket.");
                    }
                }
            }
            return new Profile(revision, dataset.version(), area, sharers.asInt(), quantities, other, "USER_DECLARED");
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("INVALID_PROFILE", "Enter finite, non-negative quantities with at most two decimals.");
        }
    }

    private BigDecimal amount(JsonNode node, String label, boolean positive, BigDecimal max) {
        if (node == null || (!node.isNumber() && !node.isTextual()) || !node.asText().matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            throw ApiException.badRequest("INVALID_PROFILE", label + " must be a number with at most two decimals.");
        }
        BigDecimal value = new BigDecimal(node.asText());
        if (value.signum() < 0 || positive && value.signum() == 0 || value.compareTo(max) > 0) {
            throw ApiException.badRequest("INVALID_PROFILE", label + " is outside the supported range.");
        }
        return value;
    }

    public ObjectNode profileJson(Profile profile) {
        ObjectNode value = mapper.createObjectNode();
        value.put("revision", profile.revision()).put("datasetVersion", profile.datasetVersion())
                .put("apartmentSize", profile.apartmentSize()).put("rentSharers", profile.rentSharers())
                .put("otherSpending", profile.otherSpending()).put("source", profile.source());
        value.set("groceryQuantities", profile.groceryQuantities());
        return value;
    }

    public void insertProfile(long userId, Profile profile) {
        jdbc.update("""
                INSERT INTO user_living_cost_profiles(user_id,revision,dataset_version,apartment_size,rent_sharers,
                    grocery_quantities,other_spending,source) VALUES(?,?,?,?,?,?::jsonb,?,?)
                """, userId, profile.revision(), profile.datasetVersion(), profile.apartmentSize(), profile.rentSharers(),
                profile.groceryQuantities().toString(), profile.otherSpending(), profile.source());
    }

    public void ensureDefaultProfile(long userId) {
        if (savedProfile(userId) != null) return;
        Dataset active = activeDataset();
        if (active == null) return;
        Integer count = jdbc.queryForObject("SELECT count(*) FROM user_address_state WHERE user_id=?", Integer.class, userId);
        if (count == null || count == 0) return;
        Profile p = defaults(active);
        insertProfile(userId, new Profile(1, p.datasetVersion(), p.apartmentSize(), p.rentSharers(),
                p.groceryQuantities(), p.otherSpending(), p.source()));
    }

    /** Pure preview: reads saved facts and does not create profiles, revisions or jobs. */
    public ObjectNode quote(long userId, String postcodeOverride, JsonNode draft, boolean refresh) {
        Profile profile = savedProfile(userId);
        Dataset selected = profile == null || refresh ? activeDataset() : dataset(profile.datasetVersion());
        ObjectNode result = mapper.createObjectNode();
        result.put("available", false).put("currency", "HUF").put("hufPerUsd", HUF_PER_USD).put("eligible", false);
        BigDecimal suppliedFloor=jdbc.query("SELECT COALESCE(r.legacy_living_expenses,p.monthly_expenses) FROM demo_financial_profiles p LEFT JOIN financial_input_state s ON s.user_id=p.user_id LEFT JOIN financial_input_revisions r ON r.user_id=s.user_id AND r.revision=s.current_revision WHERE p.user_id=?",rs->rs.next()?rs.getBigDecimal(1):null,userId);
        if(suppliedFloor!=null) result.put("suppliedExpenseFloorHuf",money(suppliedFloor.multiply(HUF_PER_USD)));
        if (selected == null) return result.put("unavailableReason", "NO_DATASET");
        if (profile == null) profile = defaults(selected);
        if (draft != null) profile = validateProfile(draft, selected, profile.revision());
        else if (refresh) profile = new Profile(profile.revision(), selected.version(), profile.apartmentSize(), profile.rentSharers(),
                selected.snapshot().path("defaults").path("groceryQuantities"), BigDecimal.ZERO, profile.source());
        profile = new Profile(profile.revision(), selected.version(), profile.apartmentSize(), profile.rentSharers(),
                selected.snapshot().path("defaults").path("groceryQuantities"), BigDecimal.ZERO, profile.source());
        result.put("methodVersion", METHOD_VERSION);
        result.set("profile", profileJson(profile));
        result.set("groceries", selected.snapshot().path("groceries"));
        result.set("housing", selected.snapshot().path("housing"));
        result.put("datasetVersion", selected.version()).put("housingDatasetVersion", selected.snapshot().path("housingDatasetVersion").asText());
        List<ObjectNode> addresses = jdbc.query("""
                SELECT a.revision,a.district_id,a.postal_code,COALESCE(v.status,'UNVERIFIED') AS status
                FROM user_address_state s JOIN user_address_revisions a ON a.user_id=s.user_id AND a.revision=s.current_revision
                LEFT JOIN LATERAL(SELECT status FROM address_verifications WHERE user_id=a.user_id AND address_revision=a.revision
                    ORDER BY created_at DESC,id DESC LIMIT 1) v ON TRUE WHERE s.user_id=?
                """, (rs, row) -> mapper.createObjectNode().put("revision", rs.getInt(1)).put("districtId", rs.getString(2))
                .put("postalCode", rs.getString(3)).put("status", rs.getString(4)), userId);
        ObjectNode address = addresses.isEmpty() ? null : addresses.getFirst();
        String code = postcodeOverride != null ? postcodeOverride : address == null ? null : address.path("postalCode").asText();
        if (code == null || code.isBlank()) return result.put("unavailableReason", "NO_ADDRESS");
        ObjectNode resolved = mapping(selected, code);
        if (resolved == null) return result.put("unavailableReason", "UNSUPPORTED_POSTAL_CODE");
        result.set("district", resolved);
        var districtRows=jdbc.query("SELECT district_id,rent_per_m2 FROM location_district_prices WHERE dataset_version=?",
                (rs,n)->new DistrictRent(rs.getString(1),rs.getBigDecimal(2)),selected.snapshot().path("housingDatasetVersion").asText());
        if(districtRows.size()!=23) return result.put("unavailableReason","MISSING_DISTRICT_PRICE");
        BigDecimal sum=BigDecimal.ZERO,rentPerM2=null;
        for(var district:districtRows) {
            sum=sum.add(district.rent());
            if(district.id().equals(resolved.path("districtId").asText())) rentPerM2=district.rent();
        }
        if(rentPerM2==null) return result.put("unavailableReason","MISSING_DISTRICT_PRICE");
        BigDecimal mean=sum.divide(new BigDecimal("23"),PRECISION);
        BigDecimal coff=rentPerM2.divide(mean,PRECISION);
        BigDecimal rent=money(rentPerM2.multiply(profile.apartmentSize()).divide(new BigDecimal(profile.rentSharers()),PRECISION));
        BigDecimal groceries=jdbc.query("SELECT monthly_groceries FROM monthly_living_costs WHERE dataset_version=? AND district_id=?",
                rs->rs.next()?rs.getBigDecimal(1):null, selected.version(),resolved.path("districtId").asText());
        if(groceries==null) return result.put("unavailableReason","MISSING_DISTRICT_PRICE");
        ObjectNode activity = activityForecast(userId, selected, resolved.path("districtId").asText());
        result.set("activityForecast", activity);
        if (!activity.path("available").asBoolean()) return result.put("unavailableReason", "NO_VENUE_DATASET");
        BigDecimal other = activity.path("monthlyAmount").decimalValue();
        String housingSituation = jdbc.query("""
                SELECT r.housing_situation FROM financial_input_state s JOIN financial_input_revisions r
                ON r.user_id=s.user_id AND r.revision=s.current_revision WHERE s.user_id=?
                """, rs -> rs.next() ? rs.getString(1) : "OTHER", userId);
        boolean renting = "RENTING".equals(housingSituation);
        boolean matches = address != null && code.equals(address.path("postalCode").asText())
                && resolved.path("districtId").asText().equals(address.path("districtId").asText());
        boolean eligible = matches && "VERIFIED_DEMO".equals(address.path("status").asText()) && draft == null && postcodeOverride == null;
        BigDecimal total = money((renting ? rent : BigDecimal.ZERO).add(groceries).add(other));
        if (total.compareTo(MAX) > 0) throw ApiException.badRequest("INVALID_PROFILE", "Monthly reference total is too large.");
        result.put("available", true).put("eligible", eligible).put("rentApplies", renting)
                .put("housingSituation", housingSituation).put("coff", coff.setScale(6, RoundingMode.HALF_UP))
                .put("rentPerM2", rentPerM2).put("monthlyRent", rent).put("monthlyGroceries", groceries)
                .put("monthlyOther", other).put("monthlyTotal", total)
                .put("addressRevision", address == null ? 0 : address.path("revision").asInt())
                .put("verificationStatus", address == null ? "UNVERIFIED" : address.path("status").asText());
        return result;
    }

    /** Reuses the existing 115 venue prices and the persisted priced report; no extra pricing tables. */
    private ObjectNode activityForecast(long userId, Dataset dataset, String districtId) {
        ObjectNode result = mapper.createObjectNode().put("available", false).put("dataSource", "SYNTHETIC_DEMO_FORECAST")
                .put("forecastDays",30).put("minimumObservationDays",7).put("minimumPricedVisits",3);
        var benchmarks = locationPrices.dataset(dataset.snapshot().path("housingDatasetVersion").asText());
        if (benchmarks == null) return result.put("reason","NO_VENUE_DATASET");
        result.put("baselineDatasetVersion",benchmarks.version());
        List<ObjectNode> rows = jdbc.query("""
                SELECT r.id,r.report::text FROM demo_signal_reports r JOIN demo_signal_settings s USING(user_id)
                WHERE r.user_id=? AND r.kind='LOCATION' AND s.location_enabled
                ORDER BY r.created_at DESC,r.id DESC LIMIT 1
                """, (rs,n) -> mapper.createObjectNode().put("id",rs.getLong(1)).set("report",read(rs.getString(2))),userId);
        JsonNode report = rows.isEmpty() ? null : rows.getFirst().path("report");
        int days = 0;
        if (report!=null) {
            result.put("reportId", rows.getFirst().path("id").asLong());
            result.put("reportDatasetVersion", report.path("pricing").path("datasetVersion").asText());
            result.set("observedFrom",report.path("observedFrom")); result.set("observedTo",report.path("observedTo"));
            try {
                var from=OffsetDateTime.parse(report.path("observedFrom").asText()).toLocalDate();
                var to=OffsetDateTime.parse(report.path("observedTo").asText()).toLocalDate();
                long duration=ChronoUnit.DAYS.between(from,to)+1;
                if(duration>0 && duration<=366) days=(int)duration;
            } catch (RuntimeException ignored) { }
        }
        Map<String,BigDecimal> sums=new java.util.HashMap<>();
        Map<String,Integer> counts=new java.util.HashMap<>();
        java.util.Set<String> seen=new java.util.HashSet<>();
        BigDecimal libraryAnnual=null;
        int paidVisits=0;
        if(report!=null) for(JsonNode visit:report.path("visits")) {
            if(!seen.add(visit.path("id").asText())) continue;
            JsonNode p=visit.path("pricing"); String category=p.path("category").asText();
            if(!"HUF".equals(p.path("currency").asText()) || !p.path("estimatedPrice").isNumber()) continue;
            BigDecimal price=p.path("estimatedPrice").decimalValue();
            if(price.signum()<=0) continue;
            if("LIBRARY".equals(category) && "YEAR".equals(p.path("unit").asText())) {
                libraryAnnual=libraryAnnual==null?price:libraryAnnual.max(price);
            } else if (MONTHLY_VISITS.containsKey(category) && ("GYM".equals(category)?"ENTRY":"DRINK").equals(p.path("unit").asText())) {
                sums.merge(category,price,BigDecimal::add); counts.merge(category,1,Integer::sum); paidVisits++;
            }
        }
        boolean enough=days>=7 && paidVisits>=3 && report!=null && report.path("pricing").path("available").asBoolean();
        result.put("observationDays",days).put("pricedVisits",paidVisits).put("historySufficient",enough)
                .put("method", enough?"MEAN_OF_BASELINE_AND_HISTORY":"BENCHMARK_FALLBACK")
                .put("reason", enough?"SUFFICIENT_HISTORY":report==null?"NO_ENABLED_HISTORY":"INSUFFICIENT_HISTORY");
        var items=result.putArray("items");
        BigDecimal baselineTotal=BigDecimal.ZERO, forecastTotal=BigDecimal.ZERO;
        for(String category:List.of("GYM","CAFE","STARBUCKS","LIBRARY")) {
            var price=benchmarks.prices().get(districtId+":"+category);
            if(price==null) return result.put("reason","MISSING_VENUE_PRICE");
            BigDecimal baseline="LIBRARY".equals(category)?price.estimatedPrice().divide(new BigDecimal("12"),PRECISION)
                    :price.estimatedPrice().multiply(new BigDecimal(MONTHLY_VISITS.get(category)));
            BigDecimal observed=null;
            if(enough) {
                if("LIBRARY".equals(category) && libraryAnnual!=null) observed=libraryAnnual.divide(new BigDecimal("12"),PRECISION);
                else if(sums.containsKey(category)) observed=sums.get(category).multiply(new BigDecimal("30")).divide(new BigDecimal(days),PRECISION);
            }
            BigDecimal forecast=observed==null?baseline:baseline.add(observed).divide(new BigDecimal("2"),PRECISION);
            baselineTotal=baselineTotal.add(baseline); forecastTotal=forecastTotal.add(forecast);
            ObjectNode item=items.addObject().put("category",category).put("baselineMonthly",money(baseline))
                    .put("monthlyAmount",money(forecast)).put("historyVisitCount",counts.getOrDefault(category,0))
                    .put("sourceUrl",price.sourceUrl()).put("unit",price.unit()).put("unitPrice",price.estimatedPrice())
                    .put("retrievedAt",price.retrievedAt()).put("method",observed==null?"BENCHMARK_FALLBACK":"AVERAGE");
            if(observed!=null) item.put("historyMonthly",money(observed));
            if(!"LIBRARY".equals(category)) item.put("defaultMonthlyVisits",MONTHLY_VISITS.get(category));
        }
        result.put("available",true).put("baselineMonthly",money(baselineTotal)).put("monthlyAmount",money(forecastTotal));
        return result;
    }

    public ObjectNode capture(long userId) {
        ensureDefaultProfile(userId);
        ObjectNode context = quote(userId, null, null, false);
        context.put("methodVersion", METHOD_VERSION);
        if(!context.has("addressRevision")) context.put("addressRevision",jdbc.query("SELECT current_revision FROM user_address_state WHERE user_id=?",rs->rs.next()?rs.getInt(1):0,userId));
        if (context.path("available").asBoolean()) {
            context.put("rentUsd", usd(context.path("monthlyRent").decimalValue()))
                    .put("groceriesUsd", usd(context.path("monthlyGroceries").decimalValue()))
                    .put("otherUsd", usd(context.path("monthlyOther").decimalValue()));
        }
        return context;
    }

    public References references(JsonNode context) {
        boolean eligible = context != null && context.path("available").asBoolean() && context.path("eligible").asBoolean();
        return new References(eligible ? context.path("rentUsd").decimalValue() : null,
                eligible ? context.path("groceriesUsd").decimalValue() : null,
                eligible ? context.path("otherUsd").decimalValue() : null, eligible, context);
    }

    public JsonNode currentContext(long userId, long generation) {
        JsonNode context = jdbc.query("SELECT local_cost_context::text FROM affordability_jobs WHERE user_id=? AND generation=?",
                rs -> rs.next() && rs.getString(1) != null ? read(rs.getString(1)) : null, userId, generation);
        // Pre-migration jobs retain no researched reference; rollout enqueues a new captured generation.
        return context == null ? mapper.createObjectNode().put("available", false).put("eligible", false) : context;
    }

    public JsonNode read(String text) {
        try { return mapper.readTree(text); }
        catch (Exception e) { throw new IllegalStateException("Invalid saved monthly cost data", e); }
    }

    private record DistrictRent(String id,BigDecimal rent) {}

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal usd(BigDecimal value) { return value.divide(HUF_PER_USD, 2, RoundingMode.HALF_UP); }
}
