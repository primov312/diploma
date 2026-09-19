package com.rocketcredit.backend.analysis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class AnalysisClientConfig {

    /** Dedicated RestClient with short timeouts and the shared secret attached. */
    @Bean
    RestClient analysisRestClient(AnalysisProperties props, RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return builder.clone()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader(AnalysisClient.TOKEN_HEADER, props.sharedSecret())
                .build();
    }
}
