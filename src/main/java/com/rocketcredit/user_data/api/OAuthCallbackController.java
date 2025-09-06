// package com.rocketcredit.user_data.api;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.http.HttpStatus;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.bind.annotation.PatchMapping;
// import org.springframework.web.bind.annotation.PathVariable;
// import org.springframework.web.bind.annotation.RequestBody;
// import org.springframework.web.bind.annotation.RequestParam;
// import org.springframework.web.bind.annotation.RestController;

// import com.rocketcredit.user_data.api.UserApiController.ConsentUpdate;
// import com.rocketcredit.user_data.repo.UserRepository;

// @RestController
// public class OAuthCallbackController {

//     private static final Logger logger = LoggerFactory.getLogger(OAuthCallbackController.class);

//     private final UserRepository userRepo;

//     public OAuthCallbackController(UserRepository userRepo) {
//         this.userRepo = userRepo;
//     }

//     // --- OAuth callback endpoints (GET) ---
//     @GetMapping("/oauth/vk/callback")
//     public ResponseEntity<String> vkCallback(
//             @RequestParam(name = "code", required = false) String code,
//             @RequestParam(name = "state", required = false) String state,
//             @RequestParam(name = "error", required = false) String error,
//             @RequestParam(name = "error_description", required = false) String errorDescription) {
//         if (error != null) {
//             logger.warn("VK OAuth error: {} - {}", error, errorDescription);
//             return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("VK auth error");
//         }
//         logger.info("VK OAuth callback received, code={}, state={}", code, state);
//         return ResponseEntity.ok("VK callback received");
//     }

//     @GetMapping("/oauth/meta/callback") // or /oauth/facebook/callback if you prefer
//     public ResponseEntity<String> metaCallback(
//             @RequestParam(name = "code", required = false) String code,
//             @RequestParam(name = "state", required = false) String state,
//             @RequestParam(name = "error", required = false) String error,
//             @RequestParam(name = "error_description", required = false) String errorDescription) {
//         if (error != null) {
//             logger.warn("Meta OAuth error: {} - {}", error, errorDescription);
//             return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Meta auth error");
//         }
//         logger.info("Meta OAuth callback received, code={}, state={}", code, state);
//         return ResponseEntity.ok("Meta callback received");
//     }

//     @PatchMapping("/users/{id}/consent")
//     public ResponseEntity<Void> updateConsent(@PathVariable Long id,
//             @RequestBody(required = false) ConsentUpdate body) {
//         boolean value = body != null && Boolean.TRUE.equals(body.getConsent());
//         return userRepo.findById(id).map(u -> {
//             u.setSocialConsent(value);
//             userRepo.save(u);
//             return ResponseEntity.noContent().<Void>build();   // Force <Void>
//         }).orElseGet(() -> ResponseEntity.<Void>notFound().build()); // Force <Void>
//     }
// }
