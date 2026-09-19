package com.rocketcredit.backend.applications.features;

import com.rocketcredit.backend.applications.CreditApplicationEntity;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * rocket.features.mode           SEQUENTIAL | PARALLEL   (which preparation handles applications)
 * rocket.features.pool-size      threads in the one shared executor (default 3: one per provider)
 * rocket.features.queue-capacity bounded queue; a full queue rejects the task instead of growing
 * rocket.features.deadline       total time allowed for all three providers of one application
 */
@ConfigurationProperties(prefix = "rocket.features")
public record FeatureProperties(CreditApplicationEntity.PreparationMode mode, int poolSize, int queueCapacity,
                                Duration deadline) {
    public FeatureProperties {
        if (mode == null) mode = CreditApplicationEntity.PreparationMode.SEQUENTIAL;
        if (poolSize <= 0) poolSize = 3;
        if (queueCapacity <= 0) queueCapacity = 50;
        if (deadline == null) deadline = Duration.ofSeconds(3);
    }
}
