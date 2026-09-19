package com.rocketcredit.backend.benchmark;

import com.rocketcredit.backend.applications.features.FeaturePreparation;
import com.rocketcredit.backend.applications.features.FeatureProviders;
import com.rocketcredit.backend.applications.features.ParallelFeaturePreparation;
import com.rocketcredit.backend.applications.features.SequentialFeaturePreparation;
import com.rocketcredit.backend.partners.PartnerRepository;
import com.rocketcredit.backend.transactions.TransactionRepository;
import com.rocketcredit.backend.users.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Step 7 experiment. Activated only with SPRING_PROFILES_ACTIVE=benchmark; measures feature
 * preparation for one seeded customer in SEQUENTIAL and PARALLEL mode, first on the real
 * local data and then with a controlled simulated I/O delay added to every provider call.
 * Writes one CSV row per (mode, pool size, delay) and exits.
 *
 * Both modes use the same provider beans; the delay is a decorator applied outside the
 * providers so production code is untouched. Results are asserted equal to the sequential
 * baseline on every iteration.
 */
@Component
@Profile("benchmark")
public class FeatureBenchmarkRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(FeatureBenchmarkRunner.class);

    private final BenchmarkProperties props;
    private final ConfigurableApplicationContext context;
    private final UserRepository users;
    private final PartnerRepository partners;
    private final TransactionRepository transactions;
    private final FeatureProviders.ProfileFeatureProvider profile;
    private final FeatureProviders.HistoryFeatureProvider history;
    private final FeatureProviders.FinanceFeatureProvider finance;

    public FeatureBenchmarkRunner(BenchmarkProperties props, ConfigurableApplicationContext context,
                                  UserRepository users, PartnerRepository partners, TransactionRepository transactions,
                                  FeatureProviders.ProfileFeatureProvider profile,
                                  FeatureProviders.HistoryFeatureProvider history,
                                  FeatureProviders.FinanceFeatureProvider finance) {
        this.props = props;
        this.context = context;
        this.users = users;
        this.partners = partners;
        this.transactions = transactions;
        this.profile = profile;
        this.history = history;
        this.finance = finance;
    }

    record Row(String mode, int poolSize, int delayMs, int iterations, int datasetRows,
               double medianMs, double p95Ms, double meanMs, double minMs, double maxMs, int errors, boolean equalToSequential) {
        String csv() {
            return String.join(",", mode, String.valueOf(poolSize), String.valueOf(delayMs), String.valueOf(iterations),
                    String.valueOf(datasetRows), fmt(medianMs), fmt(p95Ms), fmt(meanMs), fmt(minMs), fmt(maxMs),
                    String.valueOf(errors), String.valueOf(equalToSequential));
        }
        private static String fmt(double v) { return String.format(java.util.Locale.ROOT, "%.3f", v); }
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Long userId = users.findByEmail(props.userEmail()).orElseThrow().getId();
        Long partnerId = partners.findBySlug(props.partnerSlug()).orElseThrow().getId();
        int datasetRows = transactions.findByUserIdOrderByOccurredOnDesc(userId).size();
        var ctx = new FeatureProviders.Context(userId, partnerId, OffsetDateTime.of(2026, 9, 19, 12, 0, 0, 0, ZoneOffset.UTC));

        var baseline = new SequentialFeaturePreparation(profile, history, finance).prepare(ctx);
        List<Row> rows = new ArrayList<>();

        for (int delay : props.delaysMs()) {
            var p = delayed(profile, delay);
            var h = delayed(history, delay);
            var f = delayed(finance, delay);

            rows.add(measure("SEQUENTIAL", 1, delay, datasetRows, new SequentialFeaturePreparation(p, h, f), ctx, baseline));

            for (int pool : props.poolSizes()) {
                var executor = new ThreadPoolExecutor(pool, pool, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(50),
                        new ThreadPoolExecutor.AbortPolicy());
                try {
                    var parallel = new ParallelFeaturePreparation(executor, Duration.ofSeconds(10), p, h, f);
                    rows.add(measure("PARALLEL", pool, delay, datasetRows, parallel, ctx, baseline));
                } finally {
                    executor.shutdown();
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
                }
            }
        }

        Path out = Path.of(props.out()).toAbsolutePath().normalize();
        write(out, rows);
        log.info("benchmark written to {}", out);
        System.out.println();
        System.out.println("mode        pool delay_ms  n   rows  median_ms   p95_ms   mean_ms  errors equal");
        for (Row r : rows) {
            System.out.printf(java.util.Locale.ROOT, "%-11s %4d %8d %4d %5d %10.3f %8.3f %9.3f %7d %s%n", r.mode(), r.poolSize(), r.delayMs(),
                    r.iterations(), r.datasetRows(), r.medianMs(), r.p95Ms(), r.meanMs(), r.errors(), r.equalToSequential());
        }
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    private Row measure(String mode, int pool, int delay, int datasetRows, FeaturePreparation prep,
                        FeatureProviders.Context ctx, FeaturePreparation.Sections baseline) {
        for (int i = 0; i < props.warmup(); i++) prep.prepare(ctx);
        double[] samples = new double[props.iterations()];
        int errors = 0;
        boolean equal = true;
        for (int i = 0; i < props.iterations(); i++) {
            long t0 = System.nanoTime();
            try {
                var result = prep.prepare(ctx);
                if (!result.equals(baseline)) equal = false;
            } catch (RuntimeException e) {
                errors++;
            }
            samples[i] = (System.nanoTime() - t0) / 1_000_000.0;
        }
        Arrays.sort(samples);
        double mean = Arrays.stream(samples).average().orElse(0);
        return new Row(mode, pool, delay, props.iterations(), datasetRows,
                percentile(samples, 50), percentile(samples, 95), mean, samples[0], samples[samples.length - 1], errors, equal);
    }

    private static double percentile(double[] sorted, int pct) {
        int idx = (int) Math.ceil(pct / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    private static void write(Path out, List<Row> rows) throws IOException {
        Files.createDirectories(out.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("mode,pool_size,delay_ms,iterations,dataset_rows,median_ms,p95_ms,mean_ms,min_ms,max_ms,errors,equal_to_sequential");
        rows.forEach(r -> lines.add(r.csv()));
        Files.write(out, lines);
    }

    // ---- simulated I/O delay decorators -------------------------------------------------------
    private static void sleep(int ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static FeatureProviders.ProfileFeatureProvider delayed(FeatureProviders.ProfileFeatureProvider real, int ms) {
        return c -> { sleep(ms); return real.profile(c); };
    }

    private static FeatureProviders.HistoryFeatureProvider delayed(FeatureProviders.HistoryFeatureProvider real, int ms) {
        return c -> { sleep(ms); return real.history(c); };
    }

    private static FeatureProviders.FinanceFeatureProvider delayed(FeatureProviders.FinanceFeatureProvider real, int ms) {
        return c -> { sleep(ms); return real.finance(c); };
    }
}
