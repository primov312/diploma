package com.rocketcredit.backend.address;

import com.rocketcredit.backend.auth.AuthenticatedUser;
import com.rocketcredit.backend.users.SaveFinancialInputsRequest.HousingSituation;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class HousingController {
    private final HousingService housing;

    public HousingController(HousingService housing) {
        this.housing = housing;
    }

    @GetMapping("/api/me/housing")
    public HousingDtos.Current current(@AuthenticationPrincipal AuthenticatedUser user) {
        return housing.current(user.getId());
    }

    @PostMapping(value = "/api/me/housing/verifications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HousingDtos.Result change(@AuthenticationPrincipal AuthenticatedUser user,
                                     @RequestParam int expectedRevision,
                                     @RequestParam HousingSituation housingSituation,
                                     @RequestPart("image") MultipartFile image) {
        return housing.change(user.getId(), expectedRevision, housingSituation, image);
    }
}
