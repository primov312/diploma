package com.rocketcredit.backend.signals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rocketcredit.backend.analysis.AnalysisUnavailableException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Minimal Facebook Login + Graph API client. Only the owner's own profile and posts are read. */
@Component
public class FacebookClient {
    private static final Logger log = LoggerFactory.getLogger(FacebookClient.class);
    static final String SCOPES = "public_profile,user_posts";
    private static final int MAX_POSTS = 50;
    private static final DateTimeFormatter GRAPH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    /** The stored token was rejected (expired, revoked, or permission withdrawn). */
    public static class TokenRejectedException extends RuntimeException {
        public TokenRejectedException() { super("Facebook rejected the access token"); }
    }

    public record TokenResult(String accessToken, Instant expiresAt) {}
    public record Profile(String id, String name) {}

    private final SocialProperties props;
    private final RestClient http;
    private final ObjectMapper mapper;

    @org.springframework.beans.factory.annotation.Autowired
    public FacebookClient(SocialProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        this(props, build(props, builder), mapper);
    }

    private static RestClient build(SocialProperties props, RestClient.Builder builder) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        factory.setReadTimeout(java.time.Duration.ofSeconds(20));
        return builder.clone().baseUrl(graphBase(props)).requestFactory(factory).build();
    }

    FacebookClient(SocialProperties props, RestClient http, ObjectMapper mapper) {
        this.props = props;
        this.http = http;
        this.mapper = mapper;
    }

    static String graphBase(SocialProperties props) {
        return props.graphBaseUrl().replaceAll("/+$", "") + "/" + props.graphVersion();
    }

    public String authorizeUrl(String state) {
        return "https://www.facebook.com/" + props.graphVersion() + "/dialog/oauth"
                + "?client_id=" + enc(props.metaAppId())
                + "&redirect_uri=" + enc(props.redirectUri())
                + "&state=" + enc(state)
                + "&scope=" + enc(SCOPES)
                + "&response_type=code";
    }

    public TokenResult exchangeCode(String code) {
        JsonNode body = get("/oauth/access_token", Map.of("client_id", props.metaAppId(),
                "client_secret", props.metaAppSecret(), "redirect_uri", props.redirectUri(), "code", code));
        String token = body.path("access_token").asText("");
        if (token.isBlank()) throw new AnalysisUnavailableException("Facebook returned no access token");
        long seconds = body.path("expires_in").asLong(0);
        return new TokenResult(token, seconds > 0 ? Instant.now().plusSeconds(seconds) : null);
    }

    public Profile me(String token) {
        JsonNode body = get("/me", Map.of("fields", "id,name", "access_token", token));
        return new Profile(body.path("id").asText(), body.path("name").asText(null));
    }

    public List<ObjectNode> fetchPosts(String token) {
        JsonNode body = get("/me/posts", Map.of("limit", String.valueOf(MAX_POSTS), "access_token", token,
                "fields", "id,created_time,message,story,place{name,location{city}},"
                        + "reactions.limit(0).summary(true),comments.limit(25).summary(true){id,message}"));
        return normalizePosts(body, mapper);
    }

    /** Best effort: withdraw the app's permissions when the user disconnects. */
    public void revoke(String token) {
        try {
            http.delete().uri(uriFor("/me/permissions", Map.of("access_token", token))).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.info("Facebook permission revoke skipped: {}", e.getClass().getSimpleName());
        }
    }

    /** Convert a Graph API {@code /me/posts} page into the posts shape the analysis service expects. */
    static List<ObjectNode> normalizePosts(JsonNode graph, ObjectMapper mapper) {
        List<ObjectNode> posts = new ArrayList<>();
        int n = 0;
        for (JsonNode item : graph.path("data")) {
            String created = isoTime(item.path("created_time").asText(""));
            if (created == null) continue;
            n++;
            String id = String.format("post-%02d", n);
            ObjectNode post = mapper.createObjectNode();
            post.put("id", id);
            post.put("date", created);
            JsonNode place = item.path("place");
            String location = place.path("name").asText(place.path("location").path("city").asText(""));
            if (location.isBlank()) post.putNull("location"); else post.put("location", truncate(location, 120));
            String text = item.path("message").asText(item.path("story").asText(""));
            post.put("text", truncate(text, 2000));
            post.put("reactions", Math.max(0, item.path("reactions").path("summary").path("total_count").asInt(0)));
            ArrayNode comments = post.putArray("comments");
            int c = 0;
            for (JsonNode comment : item.path("comments").path("data")) {
                if (++c > 50) break;
                ObjectNode out = comments.addObject();
                out.put("id", id.replace("post", "comment") + "-" + c);
                out.put("text", truncate(comment.path("message").asText(""), 500));
            }
            posts.add(post);
        }
        return posts;
    }

    private static String isoTime(String value) {
        try {
            return OffsetDateTime.parse(value, GRAPH_TIME).toInstant().toString();
        } catch (DateTimeParseException e) {
            try { return OffsetDateTime.parse(value).toInstant().toString(); }
            catch (DateTimeParseException ignored) { return null; }
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uriFor(String path, Map<String, String> params) {
        return b -> {
            b.path(path);
            params.keySet().forEach(k -> b.queryParam(k, "{" + k + "}"));
            return b.build(params);
        };
    }

    private JsonNode get(String path, Map<String, String> params) {
        try {
            JsonNode body = http.get().uri(uriFor(path, params)).retrieve().body(JsonNode.class);
            if (body == null) throw new AnalysisUnavailableException("Facebook returned an empty body");
            return body;
        } catch (RestClientResponseException e) {
            if (isTokenError(e)) throw new TokenRejectedException();
            log.warn("Facebook Graph call failed: HTTP {}", e.getStatusCode().value());
            throw new AnalysisUnavailableException("Facebook Graph call failed: HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            log.warn("Facebook Graph unreachable: {}", e.getClass().getSimpleName());
            throw new AnalysisUnavailableException("Facebook Graph is unreachable", e);
        }
    }

    private boolean isTokenError(RestClientResponseException e) {
        try {
            JsonNode error = mapper.readTree(e.getResponseBodyAsString()).path("error");
            return error.path("code").asInt() == 190 || error.path("type").asText().equals("OAuthException") && e.getStatusCode().value() == 401;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
