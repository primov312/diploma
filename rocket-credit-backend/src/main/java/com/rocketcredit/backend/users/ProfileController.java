package com.rocketcredit.backend.users;

import com.rocketcredit.backend.auth.AuthenticatedUser;
import com.rocketcredit.backend.common.ApiException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileController {
    private final UserService users;
    private final DemoFinancialProfileRepository profiles;
    private final FinancialInputsService financialInputs;

    public ProfileController(UserService users, DemoFinancialProfileRepository profiles,
                             FinancialInputsService financialInputs) {
        this.users = users;
        this.profiles = profiles;
        this.financialInputs = financialInputs;
    }

    @GetMapping("/api/me/profile")
    public FinancialProfileDto profile(@AuthenticationPrincipal AuthenticatedUser principal) {
        var user = users.requireById(principal.getId());
        var p = profiles.findById(user.getId()).orElseThrow(() -> ApiException.notFound("Financial profile"));
        int ageMonths = (int) ChronoUnit.MONTHS.between(user.getCreatedAt(), OffsetDateTime.now());
        return new FinancialProfileDto(p.getMonthlyIncome(), p.getMonthlyExpenses(), p.getMonthlyObligations(),
                p.isProfileComplete(), p.isEmailVerified(), p.getSyntheticSource().name(), Math.max(0, ageMonths));
    }

    @GetMapping("/api/me/financial-inputs")
    public FinancialInputsDto financialInputs(@AuthenticationPrincipal AuthenticatedUser principal) {
        return financialInputs.current(principal.getId());
    }

    @PutMapping("/api/me/financial-inputs")
    public FinancialInputsSaveResult saveFinancialInputs(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @Valid @RequestBody SaveFinancialInputsRequest request) {
        return financialInputs.save(principal.getId(), request);
    }
}
