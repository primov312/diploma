package com.rocketcredit.backend.payments;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfig {
    @Bean public org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer integralTerms() {
        return builder -> builder.featuresToDisable(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
    @Bean public Clock paymentClock() { return Clock.systemUTC(); }
}
