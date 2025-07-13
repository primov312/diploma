package com.rocketcredit.userdata.api;

import com.rocketcredit.userdata.api.UsersApi;

import com.rocketcredit.userdata.model.NewUser;
import com.rocketcredit.userdata.model.User;
import com.rocketcredit.userdata.model.Transaction;
import com.rocketcredit.userdata.model.PaymentMethod;

import com.rocketcredit.userdata.entity.UserEntity;
import com.rocketcredit.userdata.entity.TransactionEntity;
import com.rocketcredit.userdata.entity.PaymentMethodEntity;

import com.rocketcredit.userdata.repo.UserRepository;
import com.rocketcredit.userdata.repo.TransactionRepository;
import com.rocketcredit.userdata.repo.PaymentMethodRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import javax.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class UserApiController implements UsersApi {

    private final UserRepository userRepo;
    private final TransactionRepository transactionRepo;
    private final PaymentMethodRepository paymentMethodRepo;

    public UserApiController(
        UserRepository userRepo,
        TransactionRepository transactionRepo,
        PaymentMethodRepository paymentMethodRepo
    ) {
        this.userRepo = userRepo;
        this.transactionRepo = transactionRepo;
        this.paymentMethodRepo = paymentMethodRepo;
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
}
