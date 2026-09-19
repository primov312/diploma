package com.rocketcredit.backend.applications.features;

import com.rocketcredit.backend.applications.CreditApplicationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Chooses the implementation from rocket.features.mode; both stay available for tests and benchmarks. */
@Component
@Primary
public class ConfiguredFeaturePreparation implements FeaturePreparation {
    private static final Logger log = LoggerFactory.getLogger(ConfiguredFeaturePreparation.class);
    private final FeaturePreparation delegate;

    public ConfiguredFeaturePreparation(FeatureProperties props, SequentialFeaturePreparation sequential,
                                        ParallelFeaturePreparation parallel) {
        this.delegate = props.mode() == CreditApplicationEntity.PreparationMode.PARALLEL ? parallel : sequential;
        log.info("feature preparation mode={} poolSize={} queueCapacity={} deadline={}",
                props.mode(), props.poolSize(), props.queueCapacity(), props.deadline());
    }

    @Override
    public Sections prepare(FeatureProviders.Context ctx) {
        return delegate.prepare(ctx);
    }

    @Override
    public CreditApplicationEntity.PreparationMode mode() {
        return delegate.mode();
    }
}
