package com.rocketcredit.backend.applications.features;

import com.rocketcredit.backend.analysis.FeatureBundle;
import com.rocketcredit.backend.users.DemoFinancialProfileRepository;
import com.rocketcredit.backend.users.UserRepository;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DbProfileFeatureProvider implements FeatureProviders.ProfileFeatureProvider {
    private final UserRepository users;
    private final DemoFinancialProfileRepository profiles;

    public DbProfileFeatureProvider(UserRepository users, DemoFinancialProfileRepository profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    /** Own short read transaction: safe to call from any thread. */
    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public FeatureBundle.Profile profile(FeatureProviders.Context ctx) {
        var user = users.findById(ctx.userId()).orElse(null);
        var profile = profiles.findById(ctx.userId()).orElse(null);
        if (user == null || profile == null) return null;
        int ageMonths = (int) Math.max(0, ChronoUnit.MONTHS.between(user.getCreatedAt(), ctx.observedAt()));
        return new FeatureBundle.Profile(ageMonths, profile.isProfileComplete(), profile.isEmailVerified());
    }
}
