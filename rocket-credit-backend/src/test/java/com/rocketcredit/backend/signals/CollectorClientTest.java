// AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
package com.rocketcredit.backend.signals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.rocketcredit.backend.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CollectorClientTest {
    private MockRestServiceServer server;
    private CollectorClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CollectorClient(builder.baseUrl("http://collector:8100")
                .defaultHeader(CollectorClient.TOKEN_HEADER, "s3cret").build());
    }

    private ApiException failure(String address) {
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, () -> client.collect(address));
        server.verify();
        return thrown;
    }

    @Test
    void sendsTheAddressWithTheTokenAndReturnsThePosts() {
        server.expect(requestTo("http://collector:8100/collect"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Collector-Token", "s3cret"))
                .andExpect(jsonPath("$.url").value("http://collector:8100/demo-source/ordinary"))
                .andRespond(withSuccess("{\"source\":{\"host\":\"collector\",\"robots\":\"ALLOWED\"},\"posts\":[{\"id\":\"post-01\"}],\"skipped\":0}", MediaType.APPLICATION_JSON));

        var result = client.collect("http://collector:8100/demo-source/ordinary");

        assertThat(result.path("posts")).hasSize(1);
        assertThat(result.path("source").path("robots").asText()).isEqualTo("ALLOWED");
        server.verify();
    }

    @Test
    void refusedSourcesBecomeUnprocessableWithTheCollectorsCode() {
        server.expect(requestTo("http://collector:8100/collect")).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"SOURCE_NOT_ALLOWED\",\"message\":\"This website is not on the list of allowed sources.\"}"));

        var e = failure("http://example.com/me");

        assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(e.getCode()).isEqualTo("SOURCE_NOT_ALLOWED");
    }

    @Test
    void robotsRefusalIsReportedAsSuch() {
        server.expect(requestTo("http://collector:8100/collect")).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"ROBOTS_DISALLOWED\",\"message\":\"no\"}"));

        assertThat(failure("http://collector:8100/private/x").getCode()).isEqualTo("ROBOTS_DISALLOWED");
    }

    @Test
    void badAddressIsABadRequest() {
        server.expect(requestTo("http://collector:8100/collect")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"INVALID_URL\",\"message\":\"Use a full http or https address.\"}"));

        assertThat(failure("nonsense").getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void pageFetchFailureIsABadGateway() {
        server.expect(requestTo("http://collector:8100/collect")).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"FETCH_FAILED\",\"message\":\"The page could not be fetched.\"}"));

        assertThat(failure("http://collector:8100/demo-source/x").getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void rejectedTokenOrCollectorErrorIsReportedAsUnavailableWithoutLeakingDetails() {
        server.expect(requestTo("http://collector:8100/collect")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        var e = failure("http://collector:8100/demo-source/x");

        assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(e.getCode()).isEqualTo("COLLECTOR_UNAVAILABLE");
    }
}
