package com.rocketcredit.backend.signals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FacebookClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SocialProperties props = new SocialProperties("app-1", "secret-1", "http://localhost:5173", "v23.0", "https://graph.test", "");

    @Test
    void normalizesGraphPostsIntoAnalysisShape() throws Exception {
        var graph = mapper.readTree("""
                {"data":[
                  {"id":"1_1","created_time":"2026-08-02T10:00:00+0000","message":"Walk by the river","place":{"name":"Danube"},
                   "reactions":{"summary":{"total_count":7}},
                   "comments":{"data":[{"id":"c1","message":"Nice"}]}},
                  {"id":"1_2","created_time":"2026-08-03T10:00:00+0000","story":"Primov shared a link"},
                  {"id":"1_3","created_time":"not a date","message":"dropped"}
                ]}""");

        var posts = FacebookClient.normalizePosts(graph, mapper);

        assertThat(posts).hasSize(2);
        assertThat(posts.get(0).path("id").asText()).isEqualTo("post-01");
        assertThat(posts.get(0).path("date").asText()).isEqualTo("2026-08-02T10:00:00Z");
        assertThat(posts.get(0).path("location").asText()).isEqualTo("Danube");
        assertThat(posts.get(0).path("reactions").asInt()).isEqualTo(7);
        assertThat(posts.get(0).path("comments").get(0).path("id").asText()).isEqualTo("comment-01-1");
        assertThat(posts.get(1).path("text").asText()).isEqualTo("Primov shared a link");
        assertThat(posts.get(1).path("location").isNull()).isTrue();
        assertThat(posts.get(1).path("reactions").asInt()).isZero();
        assertThat(posts.toString()).doesNotContain("1_1");   // Graph IDs never leave the backend
    }

    @Test
    void authorizeUrlCarriesStateScopeAndRedirect() {
        var client = new FacebookClient(props, RestClient.builder(), mapper);
        String url = client.authorizeUrl("st4te");
        assertThat(url).startsWith("https://www.facebook.com/v23.0/dialog/oauth?client_id=app-1")
                .contains("state=st4te", "scope=public_profile%2Cuser_posts",
                        "redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fapi%2Fme%2Fsocial-connection%2Ffacebook%2Fcallback");
    }

    @Test
    void fetchPostsSendsEncodedFieldsAndMapsRejectedTokens() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new FacebookClient(props, builder.baseUrl(FacebookClient.graphBase(props)).build(), mapper);

        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://graph.test/v23.0/me/posts?")))
                .andExpect(queryParam("access_token", "tok"))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"1\",\"created_time\":\"2026-08-02T10:00:00+0000\",\"message\":\"Hi\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://graph.test/v23.0/me/posts?")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"OAuthException\",\"code\":190}}"));

        assertThat(client.fetchPosts("tok")).hasSize(1);
        assertThatThrownBy(() -> client.fetchPosts("old")).isInstanceOf(FacebookClient.TokenRejectedException.class);
    }

    @Test
    void tokenCipherRoundTripsAndIsRandomised() {
        var cipher = new TokenCipher(props);
        String a = cipher.encrypt("EAAB-token");
        assertThat(a).isNotEqualTo(cipher.encrypt("EAAB-token")).doesNotContain("EAAB");
        assertThat(cipher.decrypt(a)).isEqualTo("EAAB-token");
        assertThatThrownBy(() -> cipher.decrypt(a.substring(0, a.length() - 4) + "AAAA")).isInstanceOf(IllegalStateException.class);
    }
}
