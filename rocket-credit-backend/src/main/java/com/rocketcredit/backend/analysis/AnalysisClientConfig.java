package com.rocketcredit.backend.analysis;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
class AnalysisClientConfig {

    /** Dedicated RestClient with short timeouts and the shared secret attached. */
    @Bean
    RestClient analysisRestClient(AnalysisProperties props, RestClient.Builder builder) {
        return client(props, builder, props.readTimeout());
    }

    @Bean
    @Qualifier("analysisLongRestClient")
    RestClient analysisLongRestClient(AnalysisProperties props, RestClient.Builder builder) {
        return client(props, builder, Duration.ofSeconds(45));
    }

    private RestClient client(AnalysisProperties props, RestClient.Builder builder, Duration readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(readTimeout);
        return builder.clone()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader(AnalysisClient.TOKEN_HEADER, props.sharedSecret())
                .build();
    }
}
