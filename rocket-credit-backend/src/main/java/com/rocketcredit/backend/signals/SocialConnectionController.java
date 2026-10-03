package com.rocketcredit.backend.signals;

import com.rocketcredit.backend.auth.AuthenticatedUser;
import com.rocketcredit.backend.common.ApiException;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Connect / disconnect the owner's Facebook account (OAuth authorization-code flow). */
@RestController
public class SocialConnectionController {
    private static final Logger log = LoggerFactory.getLogger(SocialConnectionController.class);
    private static final String STATE_ATTRIBUTE = "facebook.oauth.state";
    private static final String RETURN_PATH = "/account-dashboard";

    private final SocialConnectionService connections;
    private final SecureRandom random = new SecureRandom();

    public SocialConnectionController(SocialConnectionService connections) {
        this.connections = connections;
    }

    @GetMapping("/api/me/social-connection")
    public SocialConnectionDtos.Status status(@AuthenticationPrincipal AuthenticatedUser user) {
        return connections.status(user.getId());
    }

    @PostMapping("/api/me/social-connection/facebook/start")
    public SocialConnectionDtos.StartResponse start(HttpSession session) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String url = connections.authorizeUrl(state);
        session.setAttribute(STATE_ATTRIBUTE, state);
        return new SocialConnectionDtos.StartResponse(url);
    }

    /** Browser redirect target registered with Meta. Always answers with a redirect back to the dashboard. */
    @GetMapping("/api/me/social-connection/facebook/callback")
    public ResponseEntity<Void> callback(@AuthenticationPrincipal AuthenticatedUser user, HttpSession session,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String expected = (String) session.getAttribute(STATE_ATTRIBUTE);
        session.removeAttribute(STATE_ATTRIBUTE);
        if (error != null) return back("denied");
        if (expected == null || state == null || code == null
                || !MessageDigest.isEqual(expected.getBytes(), state.getBytes())) {
            return back("error");
        }
        try {
            connections.connect(user.getId(), code);
            return back("connected");
        } catch (ApiException e) {
            return back(e.getCode().equals("SOCIAL_ACCOUNT_ALREADY_LINKED") ? "linked" : "error");
        } catch (RuntimeException e) {
            log.warn("Facebook connect failed: {}", e.getClass().getSimpleName());
            return back("error");
        }
    }

    @DeleteMapping("/api/me/social-connection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal AuthenticatedUser user) {
        connections.disconnect(user.getId());
    }

    private static ResponseEntity<Void> back(String outcome) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(RETURN_PATH + "?social=" + outcome).toString()).build();
    }
}
