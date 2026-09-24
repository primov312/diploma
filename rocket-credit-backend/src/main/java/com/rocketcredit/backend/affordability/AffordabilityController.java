package com.rocketcredit.backend.affordability;

import com.rocketcredit.backend.auth.AuthenticatedUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
public class AffordabilityController {
    private final AffordabilityQueryService queries;

    public AffordabilityController(AffordabilityQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/api/me/affordability")
    public AffordabilityDtos.Estimate current(@AuthenticationPrincipal AuthenticatedUser principal) {
        return queries.current(principal.getId());
    }

    @GetMapping("/api/me/affordability/history")
    public List<AffordabilityDtos.HistoryPoint> history(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @RequestParam(defaultValue = "12") int months) {
        return queries.history(principal.getId(), months);
    }

    @PostMapping("/api/me/affordability/recalculate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobAccepted recalculate(@AuthenticationPrincipal AuthenticatedUser principal) {
        return new JobAccepted(202, queries.recalculate(principal.getId()));
    }

    @GetMapping("/api/me/analysis-jobs/{id}")
    public AffordabilityDtos.Job job(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable long id) {
        return queries.job(principal.getId(), id);
    }

    public record JobAccepted(int status, long jobId) {}
}
