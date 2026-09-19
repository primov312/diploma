package com.rocketcredit.backend.applications.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rocketcredit.backend.analysis.FeatureBundle;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Failure handling with fake providers; no database. */
class ParallelFeaturePreparationTest {
    private final ExecutorService executor = new ThreadPoolExecutor(3, 3, 1, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy());
    private final FeatureProviders.Context ctx = new FeatureProviders.Context(1L, 1L, OffsetDateTime.now());

    private static final FeatureBundle.Profile PROFILE = new FeatureBundle.Profile(12, true, true);
    private static final FeatureBundle.History HISTORY = new FeatureBundle.History(3, new BigDecimal("50.00"), 0.0, 1.0, 6, 5);
    private static final FeatureBundle.Finance FINANCE = new FeatureBundle.Finance(new BigDecimal("3000"), new BigDecimal("2000"), new BigDecimal("100"));

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private ParallelFeaturePreparation prep(Duration deadline, FeatureProviders.ProfileFeatureProvider p,
                                            FeatureProviders.HistoryFeatureProvider h, FeatureProviders.FinanceFeatureProvider f) {
        return new ParallelFeaturePreparation(executor, deadline, p, h, f);
    }

    @Test
    void joinsAllThreeSections() {
        var prep = prep(Duration.ofSeconds(2), c -> PROFILE, c -> HISTORY, c -> FINANCE);
        assertThat(prep.prepare(ctx)).isEqualTo(new FeaturePreparation.Sections(PROFILE, HISTORY, FINANCE));
    }

    @Test
    void aFailingTaskIsATechnicalErrorNotADecision() {
        var prep = prep(Duration.ofSeconds(2), c -> PROFILE, c -> { throw new IllegalStateException("db down"); }, c -> FINANCE);
        assertThatThrownBy(() -> prep.prepare(ctx))
                .isInstanceOf(FeaturePreparationException.class)
                .hasMessageContaining("history features failed")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void theDeadlineCoversAllTasksAndCancelsTheSlowOne() {
        var prep = prep(Duration.ofMillis(200), c -> PROFILE, c -> HISTORY, c -> {
            try { Thread.sleep(5_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return FINANCE;
        });
        long start = System.nanoTime();
        assertThatThrownBy(() -> prep.prepare(ctx))
                .isInstanceOf(FeaturePreparationException.class)
                .hasMessageContaining("deadline");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void aSaturatedExecutorFailsFast() throws Exception {
        // occupy all 3 threads + the 1 queue slot
        for (int i = 0; i < 4; i++) executor.submit(() -> { Thread.sleep(2_000); return null; });
        var prep = prep(Duration.ofSeconds(1), c -> PROFILE, c -> HISTORY, c -> FINANCE);
        assertThatThrownBy(() -> prep.prepare(ctx))
                .isInstanceOf(FeaturePreparationException.class)
                .hasMessageContaining("saturated");
    }
}
