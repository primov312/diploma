package com.rocketcredit.backend.payments;

import com.rocketcredit.backend.auth.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PaymentController {
    private final PaymentService payments;private final PaymentQueries queries;private final CreditQuotes quotes;
    public PaymentController(PaymentService payments,PaymentQueries queries,CreditQuotes quotes){this.payments=payments;this.queries=queries;this.quotes=quotes;}
    public record Amount(BigDecimal amount){} public record Settlement(long quoteId){}
    @GetMapping("/wallet") public Object wallet(@AuthenticationPrincipal AuthenticatedUser u){return queries.wallet(u.getId());}
    @GetMapping("/wallet/chart") public Object chart(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam(defaultValue="30d") String range){return queries.chart(u.getId(),range);}
    @PostMapping("/wallet/top-ups") public Object topUp(@AuthenticationPrincipal AuthenticatedUser u,@RequestHeader("Idempotency-Key") String key,@RequestBody Amount body){return payments.topUp(u.getId(),key,body.amount());}
    @PostMapping("/purchases") public Object purchase(@AuthenticationPrincipal AuthenticatedUser u,@RequestHeader("Idempotency-Key") String key,@RequestBody PaymentService.Purchase body){return payments.purchase(u.getId(),key,body);}
    @PostMapping("/credit-quotes") public Object quote(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody CreditQuotes.Request body){return quotes.create(u.getId(),body);}
    @PostMapping("/applications/{id}/accept") public Object accept(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id,@RequestHeader("Idempotency-Key") String key){return payments.accept(u.getId(),id,key);}
    @GetMapping("/repayments") public Object repayments(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam(required=false) Long loanId,@RequestParam(required=false) String partner,@RequestParam(defaultValue="UNPAID") String status,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return queries.repayments(u.getId(),loanId,partner,status,from,to,page,size);}
    @PostMapping("/repayments/{id}/pay") public Object pay(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id,@RequestHeader("Idempotency-Key") String key){return payments.pay(u.getId(),id,key,false);}
    @GetMapping("/loans/{id}") public Object loan(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id){return queries.loan(u.getId(),id);}
    @PostMapping("/loans/{id}/settlement-quotes") public Object settlementQuote(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id){return payments.settlementQuote(u.getId(),id);}
    @PostMapping("/loans/{id}/settle") public Object settle(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@RequestBody Settlement body){return payments.settle(u.getId(),id,body.quoteId(),key);}
    @GetMapping("/history") public Object history(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam(required=false) String partner,@RequestParam(required=false) String kind,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return queries.history(u.getId(),partner,kind,from,to,page,size);}
}
