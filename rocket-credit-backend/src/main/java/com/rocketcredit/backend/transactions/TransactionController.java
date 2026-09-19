package com.rocketcredit.backend.transactions;

import com.rocketcredit.backend.auth.AuthenticatedUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** The user ID always comes from the session principal, never from the request. */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping
    public List<TransactionDto> list(@AuthenticationPrincipal AuthenticatedUser user,
                                     @RequestParam(name = "partner", required = false) String partnerSlug) {
        return service.listForUser(user.getId(), partnerSlug);
    }

    @GetMapping("/{id}")
    public TransactionDto get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return service.getForUser(user.getId(), id);
    }
}
