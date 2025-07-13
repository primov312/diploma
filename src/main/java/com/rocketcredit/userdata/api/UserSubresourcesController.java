// package com.rocketcredit.userdata.api;

// import com.rocketcredit.userdata.model.Transaction;
// import com.rocketcredit.userdata.model.PaymentMethod;
// import com.rocketcredit.userdata.api.UsersApi;
// import com.rocketcredit.userdata.entity.TransactionEntity;
// import com.rocketcredit.userdata.entity.PaymentMethodEntity;
// import com.rocketcredit.userdata.repo.TransactionRepository;
// import com.rocketcredit.userdata.repo.PaymentMethodRepository;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.RestController;

// import java.util.List;
// import java.util.stream.Collectors;

// @RestController
// public class UserSubresourcesController implements UsersApi {

//     private final TransactionRepository transactionRepository;
//     private final PaymentMethodRepository paymentMethodRepository;

//     public UserSubresourcesController(
//             TransactionRepository transactionRepository,
//             PaymentMethodRepository paymentMethodRepository
//     ) {
//         this.transactionRepository = transactionRepository;
//         this.paymentMethodRepository = paymentMethodRepository;
//     }

//     @Override
//     public ResponseEntity<List<Transaction>> getUserTransactions(Long id) {
//         List<Transaction> transactions = transactionRepository.findByUserId(id)
//                 .stream()
//                 .map(this::toDto)
//                 .collect(Collectors.toList());
//         return ResponseEntity.ok(transactions);
//     }

//     @Override
//     public ResponseEntity<List<PaymentMethod>> getUserPaymentMethods(Long id) {
//         List<PaymentMethod> methods = paymentMethodRepository.findByUserId(id)
//                 .stream()
//                 .map(this::toDto)
//                 .collect(Collectors.toList());
//         return ResponseEntity.ok(methods);
//     }


//     private Transaction toDto(TransactionEntity entity) {
//         Transaction t = new Transaction();
//         t.setDate(entity.getDate());
//         t.setAmount(entity.getAmount());
//         t.setMethod(entity.getMethod());
//         return t;
//     }

//     private PaymentMethod toDto(PaymentMethodEntity entity) {
//         PaymentMethod pm = new PaymentMethod();
//         pm.setId(entity.getId().toString());
//         pm.setType(entity.getType());
//         pm.setLast4(entity.getLast4());
//         return pm;
//     }
// }
