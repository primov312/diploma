package com.rocketcredit.user_data.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .csrf(csrf -> csrf.disable())
      .authorizeHttpRequests(auth -> auth
        // allow your endpoints (tune as you like)
        .requestMatchers("/", "/swagger-ui/**", "/v3/api-docs/**", "/users/**", "/user-data", "/feature-claims").permitAll()
        .anyRequest().permitAll()
      )
      .httpBasic(Customizer.withDefaults());

    return http.build();
  }
}