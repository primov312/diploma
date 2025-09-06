// src/main/java/com/rocketcredit/gateway/clients/CreditAnalysisClient.java
package com.rocketcredit.gateway.clients;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import com.rocketcredit.gateway.config.ServiceEndpoints;


public class Client {
    protected final RestTemplate http;
    protected final ServiceEndpoints ep;

    public Client(RestTemplate http, ServiceEndpoints ep) {
        this.http = http; this.ep = ep;
    }

    protected <T> T post(String url, Object body, Class<T> type) {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<T> r = http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), type);
        if (!r.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("POST " + url + " -> " + r.getStatusCode());
        return r.getBody();
    }

    protected <T> T get(String url, Class<T> type) {
        ResponseEntity<T> r = http.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, type);
        if (!r.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("GET " + url + " -> " + r.getStatusCode());
        return r.getBody();
    }
}