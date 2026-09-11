package com.rocketcredit.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import com.rocketcredit.gateway.config.NoRedirectRequestFactory;
import org.springframework.retry.annotation.EnableRetry;


@EnableRetry
@SpringBootApplication(scanBasePackages = "com.rocketcredit.gateway")
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        // Configure connect/read timeouts for all downstream calls
        NoRedirectRequestFactory f = new NoRedirectRequestFactory();
        f.setConnectTimeout(2000); // 2s connect timeout
        f.setReadTimeout(5000);    // 5s read timeout
        return new RestTemplate(f);
    }
}
