package com.rocketcredit.backend.applications.features;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One bounded executor for the whole application; never a pool per request.
 * AbortPolicy: when the queue is full the submit fails fast and the application
 * reports a technical error rather than queueing without limit.
 */
@Configuration
class FeatureExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    ExecutorService featureExecutor(FeatureProperties props) {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger();
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "features-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        var executor = new ThreadPoolExecutor(props.poolSize(), props.poolSize(), 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(props.queueCapacity()), factory, new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
