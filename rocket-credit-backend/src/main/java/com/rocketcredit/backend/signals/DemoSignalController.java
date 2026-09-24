package com.rocketcredit.backend.signals;

import com.fasterxml.jackson.databind.JsonNode;
import com.rocketcredit.backend.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoSignalController {
    private final DemoSignalService signals;

    public DemoSignalController(DemoSignalService signals) {
        this.signals = signals;
    }

    @GetMapping("/api/me/demo-signals/settings")
    public DemoSignalDtos.Settings settings(@AuthenticationPrincipal AuthenticatedUser user) {
        return signals.settings(user.getId());
    }

    @PutMapping("/api/me/demo-signals/settings")
    public DemoSignalDtos.Settings update(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Valid @RequestBody DemoSignalDtos.UpdateRequest request) {
        return signals.update(user.getId(), request);
    }

    @PostMapping("/api/me/demo-signals/location-runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DemoSignalDtos.RunAccepted location(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody DemoSignalDtos.RunRequest request) {
        return signals.run(user.getId(), "LOCATION", request.scenarioId());
    }

    @PostMapping("/api/me/demo-signals/social-runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DemoSignalDtos.RunAccepted social(@AuthenticationPrincipal AuthenticatedUser user,
                                             @Valid @RequestBody DemoSignalDtos.RunRequest request) {
        return signals.run(user.getId(), "SOCIAL", request.scenarioId());
    }

    @GetMapping("/api/me/demo-signals/reports")
    public List<JsonNode> reports(@AuthenticationPrincipal AuthenticatedUser user,
                                  @RequestParam String kind) {
        return signals.reports(user.getId(), kind.toUpperCase());
    }
}
