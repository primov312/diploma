package com.rocketcredit.backend.auth;

import com.rocketcredit.backend.users.UserDto;
import com.rocketcredit.backend.users.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/**
 * Session authentication for the React client.
 *
 * <pre>
 *   POST /api/auth/register  {email,password,displayName} -> 201 UserDto (no session yet)
 *   POST /api/auth/login     {email,password}             -> 200 UserDto + RCSESSION cookie (new session id)
 *   POST /api/auth/logout                                  -> 204, session invalidated
 *   GET  /api/me                                           -> 200 UserDto | 401
 *   GET  /api/csrf                                         -> 200 {headerName, token} (also sets XSRF-TOKEN cookie)
 * </pre>
 * All POSTs need the X-XSRF-TOKEN header matching the XSRF-TOKEN cookie.
 */
@RestController
@RequestMapping("/api")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy contextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AuthController(UserService userService, AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<UserDto> register(@Valid @RequestBody AuthRequests.Register body) {
        var user = userService.register(body.email(), body.password(), body.displayName());
        log.info("user registered id={}", user.getId());
        return ResponseEntity.created(URI.create("/api/me")).body(UserDto.from(user));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<UserDto> login(@Valid @RequestBody AuthRequests.Login body,
                                         HttpServletRequest request, HttpServletResponse response) {
        // Spring Security verifies the BCrypt hash; failures surface as AuthenticationException (-> 401 generic).
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.email(), body.password()));

        // Session fixation protection: any pre-login session (e.g. one holding the CSRF token)
        // gets a fresh identifier before it becomes an authenticated session.
        var existing = request.getSession(false);
        if (existing != null) {
            request.changeSessionId();
        }

        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(auth);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        var principal = (AuthenticatedUser) auth.getPrincipal();
        log.info("user logged in id={}", principal.getId());
        return ResponseEntity.ok(UserDto.from(userService.requireById(principal.getId())));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal != null) log.info("user logged out id={}", principal.getId());
        logoutHandler.logout(request, response, contextHolderStrategy.getContext().getAuthentication());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserDto.from(userService.requireById(principal.getId()));
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
}
