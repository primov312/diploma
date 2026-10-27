// AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
package com.rocketcredit.backend.signals;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class CollectorClientConfig {
    @Bean
    RestClient collectorRestClient(CollectorProperties props, RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return builder.clone()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader(CollectorClient.TOKEN_HEADER, props.sharedSecret())
                .build();
    }
}
