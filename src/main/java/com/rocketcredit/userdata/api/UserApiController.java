package com.rocketcredit.userdata.api;

import com.rocketcredit.userdata.api.UsersApi;
import com.rocketcredit.userdata.model.CreditProfile;
import com.rocketcredit.userdata.model.NewUser;
import com.rocketcredit.userdata.model.User;
import com.rocketcredit.userdata.model.Transaction;
import com.rocketcredit.userdata.model.PaymentMethod;
import com.rocketcredit.userdata.model.SocialInsights;
import com.rocketcredit.userdata.entity.UserEntity;
import com.rocketcredit.userdata.entity.TransactionEntity;
import com.rocketcredit.userdata.entity.PaymentMethodEntity;
import com.rocketcredit.userdata.repo.UserRepository;
import com.rocketcredit.userdata.repo.TransactionRepository;
import com.rocketcredit.userdata.repo.PaymentMethodRepository;
import com.rocketcredit.userdata.entity.SocialAuthEntity;
import com.rocketcredit.userdata.model.SocialAuthRequest;
import com.rocketcredit.userdata.repo.SocialAuthRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import com.fasterxml.jackson.core.type.TypeReference;

import javax.validation.Valid;
import javax.persistence.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;




@RestController
public class UserApiController implements UsersApi {

    private static final Logger logger = LoggerFactory.getLogger(UserApiController.class);

    private final UserRepository userRepo;
    private final TransactionRepository transactionRepo;
    private final PaymentMethodRepository paymentMethodRepo;
    //private final SocialAuthRepository socialAuthRepo;

    private final ConcurrentHashMap<Long, Map<String, Object>> featureOverrides = new ConcurrentHashMap<>();


    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BCryptPasswordEncoder encoder;
    private final WebClient webClient;

    public UserApiController(
        UserRepository userRepo,
        TransactionRepository transactionRepo,
        PaymentMethodRepository paymentMethodRepo,
        //SocialAuthRepository socialAuthRepo,
        RestTemplate restTemplate,
        BCryptPasswordEncoder encoder,
        WebClient.Builder webClientBuilder
    ) {
        this.userRepo = userRepo;
        this.transactionRepo = transactionRepo;
        this.paymentMethodRepo = paymentMethodRepo;
        //this.socialAuthRepo = socialAuthRepo;
        this.restTemplate = restTemplate;
        this.encoder = encoder;
        this.webClient = webClientBuilder.build();
    }

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
        User dto = toDto(entity);
        return ResponseEntity
                .created(URI.create("/users/" + entity.getId()))
                .body(dto);
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
        if (!userRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<Transaction>> getUserTransactions(Long id) {
        List<Transaction> transactions = transactionRepo.findByUserId(id)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(transactions);
    }

    @Override
    public ResponseEntity<List<PaymentMethod>> getUserPaymentMethods(Long id) {
        List<PaymentMethod> methods = paymentMethodRepo.findByUserId(id)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(methods);
    }

    @Override
    public ResponseEntity<CreditProfile> getUserCreditProfile(Long id) {
        return userRepo.findById(id).map(user -> {
            if (!user.getSocialConsent()) {
                // Fallback: No social data if no consent
            }

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

            profile.setOnTimePaymentRate(txns.size() > 5 ? 0.95 : 0.7);  // Proxy; enhance with status later
            profile.setNumPaymentMethods(methods.size());

            long activeBnpl = txns.stream()
                .filter(t -> t.getDate().isAfter(LocalDate.now().minusMonths(6)))
                .count();
            profile.setNumActiveBnpl((int) activeBnpl);
            return ResponseEntity.ok(profile);
        }).orElse(ResponseEntity.notFound().build());
    }

   
    /**
     * Upload/Upsert a user's features for credit-analysis.
     * Accepts a flat JSON object; unknown keys are ignored.
     */
    @PostMapping("/users/{id}/features")
    public ResponseEntity<Void> upsertUserFeatures(
            @PathVariable("id") Long userId,
            @RequestBody Map<String, Object> payload
    ) {
        if (payload == null) return ResponseEntity.badRequest().build();

        Set<String> allowed = Set.of(
            "kyc_passed",
            // "social_consent",             // disabled for now
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
            // Social fields intentionally commented out:
            // "social_account_age_months","social_verified",
            // "social_activity_days_since","social_network_volatility"
        );

        Map<String, Object> filtered = payload.entrySet().stream()
            .filter(e -> allowed.contains(e.getKey()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (a,b) -> b,
                LinkedHashMap::new
            ));

        featureOverrides.merge(userId, filtered, (oldMap, newMap) -> { oldMap.putAll(newMap); return oldMap; });
        return ResponseEntity.accepted().build();
    }

    /**
     * Return the feature bundle expected by credit-analysis.
     * Both /user-info and /user-data are supported for compatibility.
     */
    @GetMapping({"/user-info", "/user-data"})
    public ResponseEntity<Map<String, Object>> getUserInfo(@RequestParam("id") Long userId) {
        // 1) Start from computed defaults based on existing data
        Map<String, Object> computed = computeFeatures(userId);

        // 2) Overlay explicit overrides uploaded via POST /users/{id}/features
        Map<String, Object> overrides = featureOverrides.getOrDefault(userId, Collections.emptyMap());
        if (!overrides.isEmpty()) {
            computed.putAll(overrides);
        }

        // 3) Strip any social_* keys (if someone uploaded them by mistake)
        computed.keySet().removeIf(k -> k.startsWith("social_"));

        return ResponseEntity.ok(computed);
    }

    private Map<String, Object> computeFeatures(Long userId) {
        Map<String, Object> m = new LinkedHashMap<>();

        Optional<UserEntity> maybeUser = userRepo.findById(userId);
        List<TransactionEntity> txns = transactionRepo.findByUserId(userId);

        // KYC flag – if you have a real field, use it; otherwise default true for now
        boolean kyc = maybeUser.map(UserEntity::getKycPassed).orElse(Boolean.TRUE);
        m.put("kyc_passed", kyc);

        // Partner stats (based on last 12 months transactions)
        LocalDate cutoff12m = LocalDate.now().minusMonths(12);
        List<TransactionEntity> last12m = txns.stream()
            .filter(t -> t.getDate() != null && !t.getDate().isBefore(cutoff12m))
            .collect(Collectors.toList());

        int orders12m = last12m.size();
        double avgOrder = last12m.isEmpty() ? 0.0 :
            last12m.stream().mapToDouble(TransactionEntity::getAmount).average().orElse(0.0);

        // We don't have explicit refund/repayment status here, so use safe defaults
        double refundRate = 0.0;
        double onTimeRatio = txns.size() > 5 ? 0.95 : 0.70;

        int tenureMonths = txns.isEmpty()
            ? 0
            : Math.max(0, monthsBetween(
                txns.stream().map(TransactionEntity::getDate).min(LocalDate::compareTo).orElse(LocalDate.now()),
                LocalDate.now()
              ));

        m.put("partner_orders_12m", orders12m);
        m.put("partner_avg_order_value", avgOrder);
        m.put("partner_refund_rate", refundRate);
        m.put("partner_ontime_ratio", onTimeRatio);
        m.put("partner_tenure_months", tenureMonths);

        // Rocket stats – proxy from same data until you centralize cross-partner history
        m.put("rocket_ontime_ratio", onTimeRatio);
        m.put("rocket_dpd30_12m", 0);  // unknown → assume 0
        int activePlans = (int) txns.stream()
            .filter(t -> t.getDate() != null && !t.getDate().isBefore(LocalDate.now().minusMonths(6)))
            .count();
        m.put("rocket_active_plans", activePlans);
        m.put("rocket_tenure_months", tenureMonths);

        // Capacity
        double annualIncome = maybeUser.map(UserEntity::getAnnualIncome).orElse(0.0);
        double monthlyIncome = annualIncome / 12.0;
        m.put("income", monthlyIncome);

        // If you have a real credit limit field, use it; else approximate from income (30%)
        Double creditLimit = maybeUser.map(UserEntity::getCreditLimit).orElse(null);
        if (creditLimit == null) {
            creditLimit = monthlyIncome * 0.30;
        }
        m.put("credit_limit", creditLimit);

        return m;
    }

    private static int monthsBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null) return 0;
        Period p = Period.between(start, end);
        return p.getYears() * 12 + p.getMonths();
    }

    static class ConsentUpdate {
        public Boolean consent;
        public Boolean getConsent() { return consent; }
        public void setConsent(Boolean consent) { this.consent = consent; }
    }

    //---- toDto helpers ----
    private User toDto(UserEntity e) {
        User u = new User();
        u.setId(e.getId());
        u.setName(e.getName());
        u.setEmail(e.getEmail());
        
        return u;
    }

    private Transaction toDto(TransactionEntity entity) {
        Transaction t = new Transaction();
        t.setDate(entity.getDate());
        t.setAmount(entity.getAmount());
        t.setMethod(entity.getMethod());
        return t;
    }

    private PaymentMethod toDto(PaymentMethodEntity entity) {
        PaymentMethod pm = new PaymentMethod();
        pm.setId(entity.getId().toString());
        pm.setType(entity.getType());
        pm.setLast4(entity.getLast4());
        return pm;
    }
    private static ParameterizedTypeReference<Map<String, Object>> mapTypeReference() {
        return new ParameterizedTypeReference<Map<String, Object>>() {};
    }
}
