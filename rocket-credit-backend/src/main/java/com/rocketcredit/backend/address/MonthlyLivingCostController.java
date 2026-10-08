package com.rocketcredit.backend.address;

import com.fasterxml.jackson.databind.JsonNode;
import com.rocketcredit.backend.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class MonthlyLivingCostController {
    private final MonthlyLivingCostService service;
    private final MonthlyCostResolver costs;
    public MonthlyLivingCostController(MonthlyLivingCostService service, MonthlyCostResolver costs) {
        this.service = service; this.costs = costs;
    }

    @GetMapping("/api/address/postal-code")
    public JsonNode postal(@RequestParam String postalCode) { return costs.postalCode(postalCode); }

    @GetMapping("/api/me/monthly-living-costs")
    public JsonNode current(@AuthenticationPrincipal AuthenticatedUser user) { return service.current(user.getId()); }

    @PostMapping("/api/me/monthly-living-costs/preview")
    public JsonNode preview(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody JsonNode request) {
        return service.preview(user.getId(), request);
    }

    @PutMapping("/api/me/monthly-living-costs")
    public JsonNode save(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody JsonNode request) {
        return service.save(user.getId(), request);
    }
}
