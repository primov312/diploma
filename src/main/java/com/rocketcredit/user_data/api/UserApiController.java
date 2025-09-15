package com.rocketcredit.user_data.api;

import com.rocketcredit.user_data.entity.PaymentMethodEntity;
import com.rocketcredit.user_data.entity.TransactionEntity;
import com.rocketcredit.user_data.entity.UserEntity;
import com.rocketcredit.user_data.repo.PaymentMethodRepository;
import com.rocketcredit.user_data.repo.TransactionRepository;
import com.rocketcredit.user_data.repo.UserRepository;
import com.rocketcredit.user_data.repo.UserStatsRepository;

import com.rocketcredit.user_data.security.Hasher;

import com.rocketcredit.user_data.model.NewUser;
import com.rocketcredit.user_data.model.PaymentMethod;
import com.rocketcredit.user_data.model.Transaction;
import com.rocketcredit.user_data.model.User;
import com.rocketcredit.user_data.model.UserResolveRequest;


import com.rocketcredit.claimcheck.ClaimRef;
import com.rocketcredit.claimcheck.storage.ClaimStorage;
import com.rocketcredit.claimcheck.storage.impl.S3ObjectKeyBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;


import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;



@RestController
public class UserApiController implements UsersApi {

    private static final Logger logger = LoggerFactory.getLogger(UserApiController.class);
    private static final String CID = "cid";

    private final UserRepository userRepo;
    private final UserStatsRepository userStatsRepo;
    private final TransactionRepository transactionRepo;
    private final PaymentMethodRepository paymentMethodRepo;

    private final ClaimStorage claimStorage;

    private final ConcurrentHashMap<Long, Map<String, Object>> featureOverrides = new ConcurrentHashMap<>();

    public UserApiController(
            UserRepository userRepo,
            UserStatsRepository userStatsRepo,
            TransactionRepository transactionRepo,
            PaymentMethodRepository paymentMethodRepo,
            ClaimStorage claimStorage
    ) {
        this.userRepo = userRepo;
        this.userStatsRepo = userStatsRepo;
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
    @Transactional
    @Override
    public ResponseEntity<User> resolveUser(
            @Valid @RequestBody UserResolveRequest body) {

        final String cid = ensureCorrelationId();
        final boolean hasPartnerId = body.getPartnerUserId() != null && !body.getPartnerUserId().isBlank();
        final boolean hasEmail     = body.getEmail() != null && !body.getEmail().isBlank();
        final String hashedPartnerId = Hasher.hash(body.getPartnerUserId());
        logger.info("[{}] start partnerUserId={} email={}",
                cid, hashedPartnerId, maskEmail(body.getEmail()));

        if (!hasPartnerId && !hasEmail) {
            logger.warn("[{}] resolveUser invalid request: neither partnerUserId nor email provided", cid);
            return ResponseEntity.unprocessableEntity().build();
        }

        Optional<UserEntity> found = Optional.empty();
        if (hasPartnerId) {
            found = userRepo.findByPartnerUserId(body.getPartnerUserId());
            logger.debug("[{}] lookup by partnerUserId={} -> present={}", cid, hashedPartnerId, found.isPresent());
        }
        if (found.isEmpty() && hasEmail) {
            found = userRepo.findByEmail(body.getEmail());
            logger.debug("[{}] lookup by email={} -> present={}", cid, maskEmail(body.getEmail()), found.isPresent());
        }

        UserEntity entity;
        String hashedEntityId;
        String hashedEntityPartnerId;
        if (found.isPresent()) {
            entity = found.get();
            hashedEntityId = Hasher.hash(entity.getId());
            hashedEntityPartnerId = Hasher.hash(entity.getPartnerUserId());
            logger.info("[{}] user found id={} partnerUserId={} email={}",
                    cid, hashedEntityId, hashedEntityPartnerId, maskEmail(entity.getEmail()));
        } else {
            UserEntity u = new UserEntity();
            u.setName(java.util.Optional.ofNullable(body.getName()).orElse(""));
            if (hasEmail)     u.setEmail(body.getEmail());
            if (hasPartnerId) u.setPartnerUserId(body.getPartnerUserId());
            entity = userRepo.save(u);
            hashedEntityId = Hasher.hash(entity.getId());
            hashedEntityPartnerId = Hasher.hash(entity.getPartnerUserId());
            logger.info("[{}] user created id={} partnerUserId={} email={}",
                    cid, hashedEntityId, hashedEntityPartnerId, maskEmail(entity.getEmail()));
        }

        boolean changed = false;
        if (body.getName() != null && !body.getName().isBlank() && !body.getName().equals(entity.getName())) {
            logger.debug("[{}] updating name id={} old='{}' new='{}'", cid, hashedEntityId, entity.getName(), body.getName());
            entity.setName(body.getName()); changed = true;
        }
        if (hasEmail && !body.getEmail().equals(entity.getEmail())) {
            logger.debug("[{}] updating email id={} old={} new={}", cid, hashedEntityId, maskEmail(entity.getEmail()), maskEmail(body.getEmail()));
            entity.setEmail(body.getEmail()); changed = true;
        }
        if (hasPartnerId && (entity.getPartnerUserId() == null || !body.getPartnerUserId().equals(entity.getPartnerUserId()))) {
            logger.debug("[{}] updating partnerUserId id={} old={} new={}", cid, hashedEntityId, hashedEntityPartnerId, Hasher.hash(body.getPartnerUserId()));
            entity.setPartnerUserId(body.getPartnerUserId()); changed = true;
        }
        if (changed) {
            entity = userRepo.save(entity);
            logger.info("[{}] user updated id={}", cid,Hasher.hash(entity.getId()) );
        }

        Long userId = entity.getId();
        String hashedUserId = Hasher.hash(userId);
        if (body.getPaymentMethod() != null && body.getPaymentMethod().getToken() != null) {
            var pm = body.getPaymentMethod();
            var existing = paymentMethodRepo.findByUserIdAndToken(userId, pm.getToken());
            if (existing.isEmpty()) {
                PaymentMethodEntity pme = new PaymentMethodEntity();
                pme.setUserId(userId);
                pme.setToken(pm.getToken());
                paymentMethodRepo.save(pme);
                logger.info("[{}] paymentMethod upserted (new) userId={} tokenHash={}", cid, hashedUserId, Integer.toHexString(pm.getToken().hashCode()));
            } else {
                logger.debug("[{}] paymentMethod exists userId={} tokenHash={}", cid, hashedUserId, Integer.toHexString(pm.getToken().hashCode()));
            }
        }

        if (body.getTransactions() != null && !body.getTransactions().isEmpty()) {
        var valid = body.getTransactions().stream()
            .filter(Objects::nonNull)
            .filter(t -> t.getDate() != null)
            .filter(t -> t.getAmount() != null && t.getAmount() > 0.0)
            .filter(t -> t.getMethod() != null && !t.getMethod().isBlank())
            .collect(Collectors.toMap(
                t -> t.getDate() + "|" + t.getAmount() + "|" + t.getMethod().trim().toLowerCase(),
                t -> t, (a,b) -> a
            ))
            .values();

        var entities = valid.stream().map(t -> {
            var e = new TransactionEntity();
            e.setUserId(userId);
            e.setDate(t.getDate());
            e.setAmount(t.getAmount());
            e.setMethod(t.getMethod().trim());
            return e;
        }).toList();

        if (!entities.isEmpty()) {
            transactionRepo.saveAll(entities);
            logger.info("[{}] ingested {} transactions userId={}", cid, entities.size(), hashedUserId);
            // keep user_stats fresh
            computeFeatures(userId);
        }
    }

        logger.info("[{}] resolveUser success id={}", cid, hashedUserId);
        return ResponseEntity.ok(toDto(entity));
    }

    // ------------ claim-check producer (UDS -> object storage) ------------
    @PostMapping("/feature-claims")
    public ResponseEntity<ClaimRef> createFeatureClaim(@RequestBody Map<String, Object> req) {
        final String cid = ensureCorrelationId();
        Long userId = ((Number) req.get("userId")).longValue();
        String correlationId = java.util.UUID.randomUUID().toString();
        String hashedUserId = Hasher.hash(userId);

        logger.info("[{}] feature-claims start userId={} correlationId={}", cid, hashedUserId, correlationId);

        Map<String, Object> features = getUserInfo(userId).getBody();
        byte[] bytes;
        try {
            bytes = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(features);
        } catch (Exception e) {
            logger.error("[{}] feature-claims serialize failed userId={}", cid, hashedUserId, e);
            return ResponseEntity.internalServerError().build();
        }

        String bucket = System.getenv().getOrDefault("S3_BUCKET", "rc-features");
        String key = S3ObjectKeyBuilder.featuresKey(userId, correlationId);

        ClaimRef ref = claimStorage.put(bucket, key, bytes, "application/json");
        ref.setCorrelationId(correlationId);
        ref.setExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(24)).toEpochMilli());

        logger.info("[{}] feature-claims stored userId={} bucket={} key={} expiresAt={}",
                cid, hashedUserId, Hasher.hash(bucket), Hasher.hash(key), ref.getExpiresAt());
        return ResponseEntity.ok(ref);
    }
    // ------------ internals ------------

    private Map<String, Object> computeFeatures(Long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        var maybeUser = userRepo.findById(userId);
        var txns = transactionRepo.findByUserId(userId);

        boolean kyc = maybeUser.map(UserEntity::isKysPassed).orElse(Boolean.TRUE);
        m.put("kyc_passed", kyc);

        LocalDate cutoff12m = LocalDate.now().minusMonths(12);
        var last12m = txns.stream()
            .filter(t -> t.getDate() != null && !t.getDate().isBefore(cutoff12m))
            .toList();

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

        double total12m = last12m.stream()
            .mapToDouble(TransactionEntity::getAmount)
            .sum();
        double minimalIncome = total12m / 12.0;
        m.put("income", minimalIncome);

        Double creditLimit = minimalIncome;
        m.put("credit_limit", creditLimit);

        // ---- Persist (UPSERT) ----
        userStatsRepo.upsert(
            userId,
            kyc,
            orders12m,
            avgOrder,
            refundRate,
            onTimeRatio,
            tenureMonths,
            onTimeRatio,
            0,
            activePlans,
            tenureMonths,
            minimalIncome,
            creditLimit
        );

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

    private PaymentMethod toDto(PaymentMethodEntity entity) {
        PaymentMethod pm = new PaymentMethod();
        pm.setId(String.valueOf(entity.getId()));
        try { pm.getClass().getMethod("setUserId", Long.class).invoke(pm, entity.getUserId()); } catch (Exception ignore) {}
        try { pm.getClass().getMethod("setToken", String.class).invoke(pm, entity.getToken()); } catch (Exception ignore) {}
        return pm;
    }

    
    private String ensureCorrelationId() {
        String cid = MDC.get(CID);
        if (cid == null) {
            cid = java.util.UUID.randomUUID().toString();
            MDC.put(CID, cid);
        }
        return cid;
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) return null;
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}