package com.rocketcredit.backend.applications.features;

import com.rocketcredit.backend.analysis.FeatureBundle;
import com.rocketcredit.backend.transactions.TransactionEntity;
import com.rocketcredit.backend.transactions.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Partner-history features from the seeded purchases. Everything is computed relative to
 * {@code observedAt}, so the same inputs always give the same features (Step 7 asserts this).
 */
@Component
public class DbHistoryFeatureProvider implements FeatureProviders.HistoryFeatureProvider {
    private final TransactionRepository transactions;

    public DbHistoryFeatureProvider(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public FeatureBundle.History history(FeatureProviders.Context ctx) {
        LocalDate asOf = ctx.observedAt().toLocalDate();
        List<TransactionEntity> all = transactions.findByUserIdOrderByOccurredOnDesc(ctx.userId());
        List<TransactionEntity> atPartner = all.stream().filter(t -> t.getPartnerId().equals(ctx.partnerId())).toList();
        return compute(all, atPartner, asOf);
    }

    /** Pure function over materialised rows; unit-testable without a database. */
    static FeatureBundle.History compute(List<TransactionEntity> all, List<TransactionEntity> atPartner, LocalDate asOf) {
        LocalDate cutoff = asOf.minusMonths(12);
        var recentAll = all.stream().filter(t -> !t.getOccurredOn().isBefore(cutoff) && !t.getOccurredOn().isAfter(asOf)).toList();
        var recent = atPartner.stream().filter(t -> !t.getOccurredOn().isBefore(cutoff) && !t.getOccurredOn().isAfter(asOf)).toList();

        var completed = recent.stream().filter(t -> t.getStatus() == TransactionEntity.Status.COMPLETED).toList();
        int orders = completed.size();
        BigDecimal avg = orders == 0 ? BigDecimal.ZERO
                : completed.stream().map(TransactionEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);
        double refundRate = recent.isEmpty() ? 0.0
                : (double) recent.stream().filter(t -> t.getStatus() == TransactionEntity.Status.REFUNDED).count() / recent.size();
        double onTime = recent.isEmpty() ? 0.0
                : (double) recent.stream().filter(TransactionEntity::isPaidOnTime).count() / recent.size();
        int tenure = atPartner.stream().map(TransactionEntity::getOccurredOn).filter(d -> !d.isAfter(asOf))
                .min(LocalDate::compareTo)
                .map(first -> (int) ChronoUnit.MONTHS.between(first, asOf)).orElse(0);

        return new FeatureBundle.History(orders, avg, round4(refundRate), round4(onTime), tenure,
                (int) recentAll.stream().filter(t -> t.getStatus() == TransactionEntity.Status.COMPLETED).count());
    }

    private static double round4(double v) {
        return Math.round(v * 10_000d) / 10_000d;
    }
}
