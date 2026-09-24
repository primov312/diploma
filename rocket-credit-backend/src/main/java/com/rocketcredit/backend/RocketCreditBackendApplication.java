package com.rocketcredit.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * One application with modules (packages) for auth, users, partners,
 * transactions, applications and the analysis client. It owns the single
 * PostgreSQL database; the Python analysis service is stateless.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class RocketCreditBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(RocketCreditBackendApplication.class, args);
    }
}
