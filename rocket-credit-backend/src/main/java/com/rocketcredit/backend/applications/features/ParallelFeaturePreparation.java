package com.rocketcredit.backend.applications.features;

import com.rocketcredit.backend.analysis.FeatureBundle;
import com.rocketcredit.backend.applications.CreditApplicationEntity;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The three providers run as independent tasks on the shared executor and are joined
 * before scoring. Each task opens its own short read transaction (REQUIRES_NEW on the
 * providers) and returns an immutable record, so nothing is shared between threads
 * except the final join. One deadline covers all three; on timeout or failure every
 * outstanding task is cancelled and a technical error is raised.
 */
@Component
public class ParallelFeaturePreparation implements FeaturePreparation {
    private final ExecutorService executor;
    private final Duration deadline;
    private final FeatureProviders.ProfileFeatureProvider profile;
    private final FeatureProviders.HistoryFeatureProvider history;
    private final FeatureProviders.FinanceFeatureProvider finance;

    @Autowired
    public ParallelFeaturePreparation(ExecutorService featureExecutor, FeatureProperties props,
                                      FeatureProviders.ProfileFeatureProvider profile,
                                      FeatureProviders.HistoryFeatureProvider history,
                                      FeatureProviders.FinanceFeatureProvider finance) {
        this(featureExecutor, props.deadline(), profile, history, finance);
    }

    /** Explicit executor and deadline: used by tests and the benchmark runner. */
    public ParallelFeaturePreparation(ExecutorService executor, Duration deadline,
                               FeatureProviders.ProfileFeatureProvider profile,
                               FeatureProviders.HistoryFeatureProvider history,
                               FeatureProviders.FinanceFeatureProvider finance) {
        this.executor = executor;
        this.deadline = deadline;
        this.profile = profile;
        this.history = history;
        this.finance = finance;
    }

    @Override
    public Sections prepare(FeatureProviders.Context ctx) {
        long deadlineNanos = System.nanoTime() + deadline.toNanos();
        Future<FeatureBundle.Profile> p;
        Future<FeatureBundle.History> h;
        Future<FeatureProviders.FinanceFeatures> f;
        try {
            p = executor.submit(() -> profile.profile(ctx));
            h = executor.submit(() -> history.history(ctx));
            f = executor.submit(() -> finance.financeBundle(ctx));
        } catch (RejectedExecutionException e) {
            throw new FeaturePreparationException("feature executor saturated", e);
        }
        List<Future<?>> all = List.of(p, h, f);
        try {
            return new Sections(await(p, deadlineNanos, "profile"), await(h, deadlineNanos, "history"),
                    await(f, deadlineNanos, "finance").finance(),
                    await(f, deadlineNanos, "finance").affordability());
        } catch (FeaturePreparationException e) {
            all.forEach(x -> x.cancel(true));
            throw e;
        }
    }

    private static <T> T await(Future<T> future, long deadlineNanos, String name) {
        long remaining = deadlineNanos - System.nanoTime();
        try {
            return future.get(Math.max(1, remaining), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            throw new FeaturePreparationException(name + " features exceeded the deadline", e);
        } catch (ExecutionException e) {
            throw new FeaturePreparationException(name + " features failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FeaturePreparationException("interrupted while preparing " + name + " features", e);
        }
    }

    @Override
    public CreditApplicationEntity.PreparationMode mode() {
        return CreditApplicationEntity.PreparationMode.PARALLEL;
    }
}
