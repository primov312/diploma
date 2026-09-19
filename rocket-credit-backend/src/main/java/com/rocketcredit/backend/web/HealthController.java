package com.rocketcredit.backend.web;

import com.rocketcredit.backend.analysis.AnalysisClient;
import com.rocketcredit.backend.analysis.AnalysisUnavailableException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Small public health summary for the run instructions; details live in /actuator/health. */
@RestController
public class HealthController {
    private final AnalysisClient analysis;

    public HealthController(AnalysisClient analysis) {
        this.analysis = analysis;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("backend", "ok");
        try {
            var h = analysis.health();
            body.put("analysis", h.status());
            body.put("policyVersion", h.policyVersion());
            return ResponseEntity.ok(body);
        } catch (AnalysisUnavailableException e) {
            body.put("analysis", "unavailable");
            return ResponseEntity.status(503).body(body);
        }
    }
}
