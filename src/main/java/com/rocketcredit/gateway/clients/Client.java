package com.rocketcredit.gateway.clients;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import com.rocketcredit.gateway.config.ServiceEndpoints;

import java.net.ConnectException;
import java.time.Duration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;


public class Client {
    protected final RestTemplate http;
    protected final ServiceEndpoints ep;

    public Client(RestTemplate http, ServiceEndpoints ep) {
        this.http = http; this.ep = ep;
    }

    protected <T> T post(String url, Object body, Class<T> type) {
        return withGuards(url, () -> {
            HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<T> r = http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), type);
            if (!r.getStatusCode().is2xxSuccessful()) {
                // Propagate non-2xx decisions as IllegalStateException with status for coarse classification
                throw new IllegalStateException("POST " + url + " -> " + r.getStatusCode());
            }
            return r.getBody();
        });
    }

    protected <T> T get(String url, Class<T> type) {
        return withGuards(url, () -> {
            ResponseEntity<T> r = http.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, type);
            if (!r.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("GET " + url + " -> " + r.getStatusCode());
            }
            return r.getBody();
        });
    }

    // ===== Bulkhead + Circuit Breaker =====
    private static final ConcurrentHashMap<String, Guard> GUARDS = new ConcurrentHashMap<>();

    private String serviceKeyForUrl(String url) {
        if (url == null) return "default";
        if (ep.uds != null && url.startsWith(ep.uds)) return "uds";
        if (ep.cas != null && url.startsWith(ep.cas)) return "cas";
        if (ep.pay != null && url.startsWith(ep.pay)) return "pay";
        if (ep.rep != null && url.startsWith(ep.rep)) return "rep";
        if (ep.notif != null && url.startsWith(ep.notif)) return "notif";
        return "default";
    }

    private Guard guard(String url) {
        String key = serviceKeyForUrl(url);
        return GUARDS.computeIfAbsent(key, k -> Guard.defaultForService(k));
    }

    private <T> T withGuards(String url, SupplierWithException<T> supplier) {
        Guard g = guard(url);
        Guard.Token token = g.enter();
        try {
            T out = supplier.get();
            g.onSuccess(token);
            return out;
        } catch (RuntimeException e) {
            g.onFailure(token, e);
            throw e;
        } catch (Throwable t) {
            g.onFailure(token, t);
            throw new RuntimeException(t);
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> { T get() throws Exception; }

    private static class Guard {
        enum State { CLOSED, OPEN, HALF_OPEN }

        static Guard defaultForService(String key) {
            int permits;
            switch (key) {
                case "uds": permits = 50; break;
                case "cas": permits = 30; break;
                case "pay": permits = 20; break;
                case "rep": permits = 20; break;
                case "notif": permits = 10; break;
                default: permits = 20;
            }
            return new Guard(permits, 5, Duration.ofSeconds(30));
        }

        private final Semaphore bulkhead;
        private final int failureThreshold;
        private final long openMillis;
        private final AtomicInteger failures = new AtomicInteger(0);
        private final Semaphore halfOpenGate = new Semaphore(1);

        private volatile State state = State.CLOSED;
        private volatile long openUntil = 0L;

        Guard(int permits, int failureThreshold, Duration openDuration) {
            this.bulkhead = new Semaphore(permits, true);
            this.failureThreshold = Math.max(1, failureThreshold);
            this.openMillis = Math.max(1000L, openDuration.toMillis());
        }

        static class Token { boolean bulk = false; boolean half = false; }

        Token enter() {
            long now = System.currentTimeMillis();
            if (state == State.OPEN) {
                if (now < openUntil) throw new CircuitOpenException();
                // Move to half-open
                state = State.HALF_OPEN;
            }
            Token t = new Token();

            if (state == State.HALF_OPEN) {
                if (!halfOpenGate.tryAcquire()) throw new CircuitOpenException();
                t.half = true;
            }

            if (!bulkhead.tryAcquire()) {
                // Fail fast when bulkhead full
                if (t.half) halfOpenGate.release();
                throw new BulkheadFullException();
            }
            t.bulk = true;
            return t;
        }

        void onSuccess(Token t) {
            failures.set(0);
            if (state == State.HALF_OPEN) {
                state = State.CLOSED;
            }
            release(t);
        }

        void onFailure(Token t, Throwable ex) {
            boolean countAsFailure = classifyFailure(ex);
            if (countAsFailure) {
                if (state == State.HALF_OPEN) {
                    openNow();
                } else {
                    int f = failures.incrementAndGet();
                    if (f >= failureThreshold) openNow();
                }
            }
            release(t);
        }

        private void openNow() {
            state = State.OPEN;
            openUntil = System.currentTimeMillis() + openMillis;
            // Ensure half-open gate is available for the probe later
            if (halfOpenGate.availablePermits() == 0) {
                halfOpenGate.release();
            }
        }

        private void release(Token t) {
            if (t == null) return;
            if (t.half) {
                halfOpenGate.release();
                t.half = false;
            }
            if (t.bulk) {
                bulkhead.release();
                t.bulk = false;
            }
        }

        private boolean classifyFailure(Throwable ex) {
            if (ex instanceof BulkheadFullException || ex instanceof CircuitOpenException) return false; // not a downstream failure
            if (ex instanceof ResourceAccessException) return true; // timeouts/connectivity
            if (ex instanceof ConnectException || ex instanceof java.net.SocketTimeoutException) return true;
            if (ex instanceof HttpStatusCodeException) {
                try {
                    return ((HttpStatusCodeException) ex).getStatusCode().is5xxServerError();
                } catch (Throwable ignore) { return true; }
            }
            if (ex instanceof IllegalStateException) {
                // Heuristic: treat server errors as tripping
                String m = ex.getMessage();
                return m != null && (m.contains(" 5") || m.toUpperCase().contains(" 5XX") || m.contains("-> 5"));
            }
            return false;
        }
    }
}
