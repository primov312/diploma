package com.rocketcredit.backend.applications;

import com.rocketcredit.backend.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {
    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApplicationDtos.ApplicationDto> submit(@AuthenticationPrincipal AuthenticatedUser user,
                                                                 @Valid @RequestBody ApplicationDtos.SubmitRequest body) {
        var dto = service.submit(user.getId(), body);
        return ResponseEntity.created(URI.create("/api/applications/" + dto.id())).body(dto);
    }

    @GetMapping
    public List<ApplicationDtos.ApplicationDto> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.listForUser(user.getId());
    }

    @GetMapping("/{id}")
    public ApplicationDtos.ApplicationDto get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return service.getForUser(user.getId(), id);
    }
}
