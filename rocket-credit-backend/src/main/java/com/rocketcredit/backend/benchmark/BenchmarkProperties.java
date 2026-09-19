package com.rocketcredit.backend.benchmark;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rocket.benchmark")
public record BenchmarkProperties(String userEmail, String partnerSlug, int warmup, int iterations,
                                  List<Integer> delaysMs, List<Integer> poolSizes, String out) {
    public BenchmarkProperties {
        if (userEmail == null) userEmail = "avery@demo.rocket.local";
        if (partnerSlug == null) partnerSlug = "markethub";
        if (warmup <= 0) warmup = 20;
        if (iterations <= 0) iterations = 200;
        if (delaysMs == null || delaysMs.isEmpty()) delaysMs = List.of(0, 10, 30);
        if (poolSizes == null || poolSizes.isEmpty()) poolSizes = List.of(3);
        if (out == null) out = "../research/benchmarks/results.csv";
    }
}
