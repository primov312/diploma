// AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
package com.rocketcredit.backend.consents;

import com.rocketcredit.backend.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConsentController {
    private final ConsentService consents;

    public ConsentController(ConsentService consents) {
        this.consents = consents;
    }

    @GetMapping("/api/me/consents")
    public ConsentDtos.View view(@AuthenticationPrincipal AuthenticatedUser user) {
        return consents.view(user.getId());
    }

    @PutMapping("/api/me/consents/{purpose}")
    public ConsentDtos.View update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable ConsentPurpose purpose,
                                   @Valid @RequestBody ConsentDtos.UpdateRequest body) {
        return body.granted() ? consents.grant(user.getId(), purpose) : consents.withdraw(user.getId(), purpose);
    }
}
