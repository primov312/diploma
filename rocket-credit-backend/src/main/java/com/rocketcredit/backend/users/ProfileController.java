package com.rocketcredit.backend.users;

import com.rocketcredit.backend.auth.AuthenticatedUser;
import com.rocketcredit.backend.common.ApiException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileController {
    private final UserService users;
    private final DemoFinancialProfileRepository profiles;

    public ProfileController(UserService users, DemoFinancialProfileRepository profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    @GetMapping("/api/me/profile")
    public FinancialProfileDto profile(@AuthenticationPrincipal AuthenticatedUser principal) {
        var user = users.requireById(principal.getId());
        var p = profiles.findById(user.getId()).orElseThrow(() -> ApiException.notFound("Financial profile"));
        int ageMonths = (int) ChronoUnit.MONTHS.between(user.getCreatedAt(), OffsetDateTime.now());
        return new FinancialProfileDto(p.getMonthlyIncome(), p.getMonthlyExpenses(), p.getMonthlyObligations(),
                p.isProfileComplete(), p.isEmailVerified(), p.getSyntheticSource().name(), Math.max(0, ageMonths));
    }
}
