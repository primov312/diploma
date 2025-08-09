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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;

import javax.validation.Valid;
import javax.persistence.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@RestController
public class UserApiController implements UsersApi {

    private static final Logger logger = LoggerFactory.getLogger(UserApiController.class);

    private final UserRepository userRepo;
    private final TransactionRepository transactionRepo;
    private final PaymentMethodRepository paymentMethodRepo;
    private final SocialAuthRepository socialAuthRepo;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BCryptPasswordEncoder encoder;
    private final WebClient webClient;

    @Value("${external.apis.vkontakte.client-id}")
    private String vkClientId;
    @Value("${external.apis.vkontakte.client-secret}")
    private String vkClientSecret;
    @Value("${external.apis.vkontakte.oauth-url}")
    private String vkOauthUrl;
    @Value("${external.apis.vkontakte.base-url}")
    private String vkBaseUrl;

    @Value("${external.apis.meta.client-id}")
    private String metaClientId;
    @Value("${external.apis.meta.client-secret}")
    private String metaClientSecret;
    @Value("${external.apis.meta.base-url}")
    private String metaBaseUrl;

    public UserApiController(
        UserRepository userRepo,
        TransactionRepository transactionRepo,
        PaymentMethodRepository paymentMethodRepo,
        SocialAuthRepository socialAuthRepo,
        RestTemplate restTemplate,
        BCryptPasswordEncoder encoder,
        WebClient.Builder webClientBuilder
    ) {
        this.userRepo = userRepo;
        this.transactionRepo = transactionRepo;
        this.paymentMethodRepo = paymentMethodRepo;
        this.socialAuthRepo = socialAuthRepo;
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

            // Social aggregation (mock; real: call APIs if consent)
            SocialInsights insights = new SocialInsights();
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> handles = user.getSocialHandles();
                if (handles != null) {
                    Object linkedin = handles.get("linkedin");
                    if (linkedin != null) {
                        // Mock API call; real: restTemplate.getForObject(linkedin + "/public-profile", Map.class)
                        insights.setJobStabilityScore(80);  // e.g., from job length
                        insights.setNetworkQuality(300);  // Connections
                        insights.setActivitySentiment(0.85);  // Positive posts
                    }
                }
            } catch (Exception e) {
                // Log error; default to 0
            }
            profile.setSocialInsights(insights);

            return ResponseEntity.ok(profile);
        }).orElse(ResponseEntity.notFound().build());
    }

   @PostMapping("/user/social-auth")
    public ResponseEntity<String> socialAuth(@Valid @RequestBody SocialAuthRequest request) {
        logger.info("Received social auth request for user {} on platform {}", request.getUserId(), request.getPlatform());  // Audit entry
        return userRepo.findById(request.getUserId()).map(user -> {
            // allow when either request consent OR stored consent is true
            if (!(request.isConsentGiven() || Boolean.TRUE.equals(user.getSocialConsent()))) {
                return ResponseEntity.badRequest().body("Explicit consent required for social data access");
            }
            user.setSocialConsent(true);
            userRepo.save(user);

            SocialAuthEntity existing = socialAuthRepo.findByUserIdAndPlatform(request.getUserId(), request.getPlatform());
            if (existing != null) {
                logger.debug("Duplicate auth for user {} on platform {}", request.getUserId(), request.getPlatform());
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Platform already authorized for this user");
            }

            String accessToken = "";
            String profileData = "{}";  // Fallback empty JSON if processing fails
            try {

                switch (request.getPlatform()) {
                    case "vkontakte":
                        // Exchange code
                        Map<String, Object> tokenResponse = webClient.post()
                            .uri(vkOauthUrl + "/access_token?client_id=" + vkClientId + "&client_secret=" + vkClientSecret + "&code=" + request.getAuthCode())
                            .retrieve()
                            .bodyToMono(mapTypeReference())  // Use static method
                            .block();
                        accessToken = (String) tokenResponse.get("access_token");

                        // Fetch profile
                        Map<String, Object> profileResponse = webClient.get()
                            .uri(vkBaseUrl + "/users.get?access_token=" + accessToken + "&v=5.199&fields=verified,connections")
                            .retrieve()
                            .bodyToMono(mapTypeReference())  // Use static method
                            .block();
                        profileData = objectMapper.writeValueAsString(profileResponse);
                        break;
                    case "facebook":
                    case "instagram":
                        // Meta exchange
                        Map<String, Object> metaTokenResponse = webClient.post()
                            .uri(metaBaseUrl + "/oauth/access_token?client_id=" + metaClientId + "&client_secret=" + metaClientSecret + "&code=" + request.getAuthCode())
                            .retrieve()
                            .bodyToMono(mapTypeReference())  // Use static method
                            .block();
                        accessToken = (String) metaTokenResponse.get("access_token");

                        // Fetch profile
                        Map<String, Object> metaProfile = webClient.get()
                            .uri(metaBaseUrl + "/me?access_token=" + accessToken + "&fields=id,verified")
                            .retrieve()
                            .bodyToMono(mapTypeReference())  // Use static method
                            .block();
                        profileData = objectMapper.writeValueAsString(metaProfile);
                        break;
                    default:
                        return ResponseEntity.badRequest().body("Unsupported platform");
                }

                String hashedToken = encoder.encode(accessToken);

                SocialAuthEntity entity = new SocialAuthEntity();
                entity.setUserId(request.getUserId());
                entity.setPlatform(request.getPlatform());
                entity.setTokenHash(hashedToken);
                entity.setFetchedData(profileData);
                socialAuthRepo.save(entity);

                logger.info("Social auth succeeded for user {} on platform {}", request.getUserId(), request.getPlatform());
                return ResponseEntity.ok("Social platform authorized successfully");
            } catch (JsonProcessingException e) {
                logger.error("JSON processing error during social auth for user {} platform {}: {}", request.getUserId(), request.getPlatform(), e.getMessage());
                return ResponseEntity.internalServerError().body("Error processing profile data—please try again");
            } catch (Exception e) {
                logger.error("Unexpected error during social auth: {}", e.getMessage());
                return ResponseEntity.internalServerError().body("Authorization failed—internal error");
            }
        }).orElse(ResponseEntity.notFound().build());
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
