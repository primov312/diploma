package com.rocketcredit.user_data.api;

import com.rocketcredit.user_data.entity.PaymentMethodEntity;
import com.rocketcredit.user_data.entity.TransactionEntity;
import com.rocketcredit.user_data.entity.UserEntity;
import com.rocketcredit.user_data.repo.PaymentMethodRepository;
import com.rocketcredit.user_data.repo.TransactionRepository;
import com.rocketcredit.user_data.repo.UserRepository;
import com.rocketcredit.user_data.model.CreditProfile;
import com.rocketcredit.user_data.model.NewUser;
import com.rocketcredit.user_data.model.PaymentMethod;
import com.rocketcredit.user_data.model.Transaction;
import com.rocketcredit.user_data.model.User;
import com.rocketcredit.user_data.api.UsersApi;

import com.rocketcredit.claimcheck.ClaimRef;
import com.rocketcredit.claimcheck.storage.ClaimStorage;
import com.rocketcredit.claimcheck.storage.impl.S3ObjectKeyBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
public class UserApiController implements UsersApi {

    private static final Logger logger = LoggerFactory.getLogger(UserApiController.class);

    private final UserRepository userRepo;
    private final TransactionRepository transactionRepo;
    private final PaymentMethodRepository paymentMethodRepo;
    private final ClaimStorage claimStorage;

    // in-memory overrides (not persisted; OK for MVP)
    private final ConcurrentHashMap<Long, Map<String, Object>> featureOverrides = new ConcurrentHashMap<>();

    public UserApiController(
            UserRepository userRepo,
            TransactionRepository transactionRepo,
            PaymentMethodRepository paymentMethodRepo,
            ClaimStorage claimStorage
    ) {
        this.userRepo = userRepo;
        this.transactionRepo = transactionRepo;
        this.paymentMethodRepo = paymentMethodRepo;
        this.claimStorage = claimStorage;
    }

    // ------------ CRUD + simple reads ------------

    @Override
    public ResponseEntity<User> getUserById(Long id) {
        return userRepo.findById(id)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<User> createUser(@Valid @RequestBody NewUser newUser) {
        UserEntity entity = new UserEntity(newUser.getName(), newUser.getEmail());
        entity.setSocialHandles(new HashMap<>());
        entity = userRepo.save(entity);
        return ResponseEntity.created(URI.create("/users/" + entity.getId()))
                .body(toDto(entity));
    }

    @Override
    public ResponseEntity<User> updateUser(Long id, @Valid @RequestBody User user) {
        return userRepo.findById(id).map(existing -> {
            existing.setName(user.getName());
            existing.setEmail(user.getEmail());
            userRepo.save(existing);
            return ResponseEntity.ok(toDto(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<Void> deleteUser(Long id) {
        if (!userRepo.existsById(id)) return ResponseEntity.notFound().build();
        userRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<PaymentMethod>> getUserPaymentMethods(Long id) {
        List<PaymentMethod> methods = paymentMethodRepo.findByUserId(id)
                .stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(methods);
    }

    @Override
    public ResponseEntity<CreditProfile> getUserCreditProfile(Long id) {
        return userRepo.findById(id).map(user -> {
            List<TransactionEntity> txns = transactionRepo.findByUserId(id);
            List<PaymentMethodEntity> methods = paymentMethodRepo.findByUserId(id);

            CreditProfile profile = new CreditProfile();
            profile.setUserId(id);
            profile.setAnnualIncome(user.getAnnualIncome());
            profile.setCreditBureauScore(user.getCreditBureauScore());
            profile.setNumTransactions(txns.size());

            double totalSpent = txns.stream().mapToDouble(TransactionEntity::getAmount).sum();
            profile.setTotalSpent(totalSpent);
            profile.setAvgTransactionAmount(txns.isEmpty() ? 0.0 : totalSpent / txns.size());
            profile.setOnTimePaymentRate(txns.size() > 5 ? 0.95 : 0.7); // proxy for now
            profile.setNumPaymentMethods(methods.size());

            long activeBnpl = txns.stream()
                    .filter(t -> t.getDate().isAfter(LocalDate.now().minusMonths(6)))
                    .count();
            profile.setNumActiveBnpl((int) activeBnpl);
            return ResponseEntity.ok(profile);
        }).orElse(ResponseEntity.notFound().build());
    }

    // ------------ feature overrides + feature bundle ------------

    @PostMapping("/users/{id}/features")
    public ResponseEntity<Void> upsertUserFeatures(
            @PathVariable("id") Long userId,
            @RequestBody Map<String, Object> payload
    ) {
        if (payload == null) return ResponseEntity.badRequest().build();

        Set<String> allowed = Set.of(
                "kyc_passed",
                "partner_orders_12m",
                "partner_avg_order_value",
                "partner_refund_rate",
                "partner_ontime_ratio",
                "partner_tenure_months",
                "rocket_ontime_ratio",
                "rocket_dpd30_12m",
                "rocket_active_plans",
                "rocket_tenure_months",
                "credit_limit",
                "income"
        );

        Map<String, Object> filtered = payload.entrySet().stream()
                .filter(e -> allowed.contains(e.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> b,
                        LinkedHashMap::new
                ));

        featureOverrides.merge(userId, filtered, (oldMap, newMap) -> {
            oldMap.putAll(newMap);
            return oldMap;
        });
        return ResponseEntity.accepted().build();
    }

    @GetMapping({"/user-info", "/user-data"})
    public ResponseEntity<Map<String, Object>> getUserInfo(@RequestParam("id") Long userId) {
        Map<String, Object> computed = computeFeatures(userId);
        Map<String, Object> overrides = featureOverrides.getOrDefault(userId, Collections.emptyMap());
        if (!overrides.isEmpty()) computed.putAll(overrides);
        computed.keySet().removeIf(k -> k.startsWith("social_")); // sanitize
        return ResponseEntity.ok(computed);
    }

    // ------------ resolve (find-or-create) + payment method upsert ------------

    @Override
    public ResponseEntity<User> resolveUser(@Valid @RequestBody com.rocketcredit.user_data.model.UserResolveRequest body) {
        boolean hasPartnerId = body.getPartnerUserId() != null && !body.getPartnerUserId().isBlank();
        boolean hasEmail     = body.getEmail() != null && !body.getEmail().isBlank();
        if (!hasPartnerId && !hasEmail) return ResponseEntity.unprocessableEntity().build();

        Optional<UserEntity> found = Optional.empty();
        if (hasPartnerId) found = userRepo.findByPartnerUserId(body.getPartnerUserId());
        if (found.isEmpty() && hasEmail) found = userRepo.findByEmail(body.getEmail());

        UserEntity entity;
        if (found.isPresent()) {
            entity = found.get();
        } else {
            UserEntity u = new UserEntity();
            u.setName(Optional.ofNullable(body.getName()).orElse(""));
            if (hasEmail)     u.setEmail(body.getEmail());
            if (hasPartnerId) u.setPartnerUserId(body.getPartnerUserId());
            entity = userRepo.save(u);
        }

        boolean changed = false;
        if (body.getName() != null && !body.getName().isBlank() && !body.getName().equals(entity.getName())) {
            entity.setName(body.getName()); changed = true;
        }
        if (hasEmail && !body.getEmail().equals(entity.getEmail())) {
            entity.setEmail(body.getEmail()); changed = true;
        }
        if (hasPartnerId && (entity.getPartnerUserId() == null || !body.getPartnerUserId().equals(entity.getPartnerUserId()))) {
            entity.setPartnerUserId(body.getPartnerUserId()); changed = true;
        }
        if (changed) entity = userRepo.save(entity);

        Long userId = entity.getId();
        if (body.getPaymentMethod() != null && body.getPaymentMethod().getToken() != null) {
            PaymentMethod pm = body.getPaymentMethod();

            Optional<PaymentMethodEntity> existing = paymentMethodRepo.findByUserIdAndToken(userId, pm.getToken());
            if (existing.isEmpty()) {
                PaymentMethodEntity pme = new PaymentMethodEntity();
                pme.setUserId(userId);
                pme.setToken(pm.getToken());
                paymentMethodRepo.save(pme);
            }
        }

        return ResponseEntity.ok(toDto(entity));
    }

    // ------------ claim-check producer (UDS -> object storage) ------------

    @PostMapping("/feature-claims")
    public ResponseEntity<ClaimRef> createFeatureClaim(@RequestBody Map<String, Object> req) {
        Long userId = ((Number) req.get("userId")).longValue();
        String correlationId = UUID.randomUUID().toString();

        Map<String, Object> features = getUserInfo(userId).getBody();
        byte[] bytes;
        try {
            bytes = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(features);
        } catch (Exception e) {
            logger.error("Failed to serialize features for user {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }

        String bucket = System.getenv().getOrDefault("S3_BUCKET", "rc-features");
        String key = S3ObjectKeyBuilder.featuresKey(userId, correlationId);

        ClaimRef ref = claimStorage.put(bucket, key, bytes, "application/json");
        ref.setCorrelationId(correlationId);
        ref.setExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(24)).toEpochMilli());
        return ResponseEntity.ok(ref);
        }

    // ------------ internals ------------

    private Map<String, Object> computeFeatures(Long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        Optional<UserEntity> maybeUser = userRepo.findById(userId);
        List<TransactionEntity> txns = transactionRepo.findByUserId(userId);

        boolean kyc = maybeUser.map(UserEntity::getKycPassed).orElse(Boolean.TRUE);
        m.put("kyc_passed", kyc);

        LocalDate cutoff12m = LocalDate.now().minusMonths(12);
        List<TransactionEntity> last12m = txns.stream()
                .filter(t -> t.getDate() != null && !t.getDate().isBefore(cutoff12m))
                .collect(Collectors.toList());

        int orders12m = last12m.size();
        double avgOrder = last12m.isEmpty() ? 0.0 :
                last12m.stream().mapToDouble(TransactionEntity::getAmount).average().orElse(0.0);

        double refundRate = 0.0;
        double onTimeRatio = txns.size() > 5 ? 0.95 : 0.70;

        int tenureMonths = txns.isEmpty() ? 0
                : Math.max(0, monthsBetween(
                        txns.stream().map(TransactionEntity::getDate).min(LocalDate::compareTo).orElse(LocalDate.now()),
                        LocalDate.now()));

        m.put("partner_orders_12m", orders12m);
        m.put("partner_avg_order_value", avgOrder);
        m.put("partner_refund_rate", refundRate);
        m.put("partner_ontime_ratio", onTimeRatio);
        m.put("partner_tenure_months", tenureMonths);

        m.put("rocket_ontime_ratio", onTimeRatio);
        m.put("rocket_dpd30_12m", 0);
        int activePlans = (int) txns.stream()
                .filter(t -> t.getDate() != null && !t.getDate().isBefore(LocalDate.now().minusMonths(6)))
                .count();
        m.put("rocket_active_plans", activePlans);
        m.put("rocket_tenure_months", tenureMonths);

        double annualIncome = maybeUser.map(UserEntity::getAnnualIncome).orElse(0.0);
        double monthlyIncome = annualIncome / 12.0;
        m.put("income", monthlyIncome);

        Double creditLimit = maybeUser.map(UserEntity::getCreditLimit).orElse(null);
        if (creditLimit == null) creditLimit = monthlyIncome * 0.30;
        m.put("credit_limit", creditLimit);

        return m;
    }

    private static int monthsBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null) return 0;
        Period p = Period.between(start, end);
        return p.getYears() * 12 + p.getMonths();
    }

    // ---- DTO mappers ----

    private User toDto(UserEntity e) {
        User u = new User();
        u.setId(e.getId());
        u.setName(e.getName());
        u.setEmail(e.getEmail());
        try {
            User.class.getMethod("setPartnerUserId", String.class).invoke(u, e.getPartnerUserId());
        } catch (Exception ignore) {}
        return u;
    }

    private Transaction toDto(TransactionEntity entity) {
        Transaction t = new Transaction();
        if (entity.getId() != null) t.setId(entity.getId().toString());
        try { Transaction.class.getMethod("setUserId", Long.class).invoke(t, entity.getUserId()); } catch (Exception ignore) {}
        t.setDate(entity.getDate());
        t.setAmount(entity.getAmount());
        t.setMethod(entity.getMethod());
        return t;
    }

    private PaymentMethod toDto(PaymentMethodEntity entity) {
        PaymentMethod pm = new PaymentMethod();
        pm.setId(String.valueOf(entity.getId()));
        try { pm.getClass().getMethod("setUserId", Long.class).invoke(pm, entity.getUserId()); } catch (Exception ignore) {}
        try { pm.getClass().getMethod("setToken", String.class).invoke(pm, entity.getToken()); } catch (Exception ignore) {}
        return pm;
    }
}