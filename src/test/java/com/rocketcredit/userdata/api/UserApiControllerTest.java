// package com.rocketcredit.userdata.api;

// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.rocketcredit.userdata.entity.UserEntity;
// import com.rocketcredit.userdata.entity.TransactionEntity;
// import com.rocketcredit.userdata.entity.PaymentMethodEntity;
// import com.rocketcredit.userdata.model.CreditProfile;
// import com.rocketcredit.userdata.repo.UserRepository;
// import com.rocketcredit.userdata.repo.TransactionRepository;
// import com.rocketcredit.userdata.repo.PaymentMethodRepository;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.mockito.Mock;
// import org.mockito.MockitoAnnotations;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.client.RestTemplate;

// import java.time.LocalDate;
// import java.util.List;
// import java.util.Optional;

// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.Mockito.when;

// class UserApiControllerTest {

//     @Mock private UserRepository userRepo;
//     @Mock private TransactionRepository txnRepo;
//     @Mock private PaymentMethodRepository pmRepo;
//     @Mock private RestTemplate restTemplate;

//     private UserApiController controller;

//     @BeforeEach
//     void setUp() {
//         MockitoAnnotations.openMocks(this);
//         controller = new UserApiController(userRepo, txnRepo, pmRepo, restTemplate);
//     }

//     @Test
//     void testGetCreditProfile_WithSocialConsent() {
//         // Mock user with consent and handles
//         UserEntity user = new UserEntity("Test", "test@email.com");
//         user.setId(1L);
//         user.setAnnualIncome(60000.0);
//         user.setCreditBureauScore(720);
//         user.setSocialConsent(true);
//         user.setSocialHandles("{\"linkedin\":\"mock-url\"}");
//         when(userRepo.findById(1L)).thenReturn(Optional.of(user));

//         // Mock txns and methods
//         TransactionEntity txn = new TransactionEntity();
//         txn.setAmount(100.0);
//         txn.setDate(LocalDate.now().minusDays(1));
//         when(txnRepo.findByUserId(1L)).thenReturn(List.of(txn));

//         PaymentMethodEntity pm = new PaymentMethodEntity();
//         when(pmRepo.findByUserId(1L)).thenReturn(List.of(pm));

//         // Mock external call (if real; here assume mock in controller)
//         // For real: when(restTemplate.getForObject(any(), any())).thenReturn(mockResponse);

//         ResponseEntity<CreditProfile> response = controller.getCreditProfile(1L);
//         assertTrue(response.getStatusCode().is2xxSuccessful());
//         CreditProfile profile = response.getBody();
//         assertEquals(1L, profile.getUserId());
//         assertEquals(60000.0, profile.getAnnualIncome());
//         assertEquals(1, profile.getNumTransactions());
//         assertEquals(1, profile.getNumPaymentMethods());
//         assertEquals(80, profile.getSocialInsights().getJobStabilityScore());  // Mock assertion
//     }

//     @Test
//     void testGetCreditProfile_NoConsent_NoSocial() {
//         UserEntity user = new UserEntity("Test", "test@email.com");
//         user.setId(1L);
//         user.setSocialConsent(false);
//         when(userRepo.findById(1L)).thenReturn(Optional.of(user));
//         when(txnRepo.findByUserId(1L)).thenReturn(List.of());
//         when(pmRepo.findByUserId(1L)).thenReturn(List.of());

//         ResponseEntity<CreditProfile> response = controller.getCreditProfile(1L);
//         CreditProfile profile = response.getBody();
//         assertEquals(0.5, profile.getOnTimePaymentRate());  // Low txns proxy
//         assertEquals(0, profile.getSocialInsights().getJobStabilityScore());  // Default neutral
//     }

//     @Test
//     void testGetCreditProfile_UserNotFound() {
//         when(userRepo.findById(999L)).thenReturn(Optional.empty());
//         ResponseEntity<CreditProfile> response = controller.getCreditProfile(999L);
//         assertTrue(response.getStatusCode().is4xxClientError());
//     }
// }