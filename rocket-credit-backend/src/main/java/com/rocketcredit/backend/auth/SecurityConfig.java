// AI-assisted (Claude), 2026: role-based route rules, 403 body and the account state filter added; reviewed by the author.
package com.rocketcredit.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcredit.backend.common.ApiError;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Session-based security for a same-origin React client.
 *
 * <ul>
 *   <li>Passwords: BCrypt via {@link PasswordEncoder}; verified by {@link DaoAuthenticationProvider}.</li>
 *   <li>Session: RCSESSION cookie (HttpOnly, SameSite=Lax, Secure when COOKIE_SECURE=true),
 *       stored in the Java process; a restart logs everyone out. {@link AccountStateFilter} ends a session
 *       on its next request when an administrator disables the account.</li>
 *   <li>CSRF: double-submit cookie. XSRF-TOKEN (readable by JS) must be echoed in X-XSRF-TOKEN on
 *       every POST/PUT/DELETE, including login and logout.</li>
 *   <li>Authorization by role, deny by default: every /api route is listed below with the role it needs;
 *       an unlisted /api route is refused. No session gives 401; a session with the wrong role gives 403
 *       with error FORBIDDEN_ROLE. Everything outside /api is the React app and its static files.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final String CUSTOMER = "CUSTOMER";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(encoder);
        provider.setUserDetailsService(userDetailsService);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
                                            JdbcTemplate jdbc, ObjectMapper mapper) throws Exception {
        var csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null); // resolve eagerly so the XSRF-TOKEN cookie is issued on any response

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler))
            .securityContext(sc -> sc.securityContextRepository(contextRepository))
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(sf -> sf.changeSessionId()))
            .addFilterBefore(new AccountStateFilter(jdbc), AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // public
                .requestMatchers("/actuator/health", "/actuator/health/**", "/api/health", "/api/csrf").permitAll()
                .requestMatchers("/api/partners/**", "/api/catalog").permitAll()
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()
                // Meta redirects the browser here; the controller itself sends anonymous callers back to log in.
                .requestMatchers(HttpMethod.GET, "/api/me/social-connection/facebook/callback").permitAll()
                // every signed-in user: who am I
                .requestMatchers("/api/me").authenticated()
                // one area per role
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/seller/**").hasRole("SELLER")
                .requestMatchers("/api/me/**", "/api/applications/**", "/api/transactions/**",
                        "/api/wallet/**", "/api/purchases", "/api/credit-quotes", "/api/repayments/**",
                        "/api/loans/**", "/api/history", "/api/address/**", "/api/local-costs").hasRole(CUSTOMER)
                // deny by default: a new /api prefix must be added above with its role
                .requestMatchers("/api/**").denyAll()
                .anyRequest().permitAll())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler(accessDenied(mapper)))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable()); // AuthController provides POST /api/auth/logout explicitly

        return http.build();
    }

    /** 403 with the standard ApiError body. A missing CSRF token is also an access denial, with its own code. */
    private static AccessDeniedHandler accessDenied(ObjectMapper mapper) {
        return (request, response, e) -> {
            ApiError body = e instanceof CsrfException
                    ? ApiError.of("CSRF_INVALID", "The security token is missing or invalid. Reload the page and try again.")
                    : ApiError.of("FORBIDDEN_ROLE", "Your account type cannot use this feature.");
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), body);
        };
    }
}
