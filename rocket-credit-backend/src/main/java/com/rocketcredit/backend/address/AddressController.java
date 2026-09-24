package com.rocketcredit.backend.address;

import com.rocketcredit.backend.auth.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AddressController {
    private final AddressService addresses;

    public AddressController(AddressService addresses) {
        this.addresses = addresses;
    }

    @GetMapping("/api/me/address")
    public AddressDtos.Current current(@AuthenticationPrincipal AuthenticatedUser user) {
        return addresses.current(user.getId());
    }

    @PutMapping("/api/me/address")
    public AddressDtos.SaveResult save(@AuthenticationPrincipal AuthenticatedUser user,
                                       @Valid @RequestBody AddressDtos.SaveRequest request) {
        return addresses.save(user.getId(), request);
    }

    @GetMapping("/api/local-costs")
    public List<AddressDtos.District> districts() {
        return addresses.districts();
    }

    @PostMapping("/api/me/local-costs/research")
    public JsonNode researchCosts(@AuthenticationPrincipal AuthenticatedUser user,
                                  @RequestParam String districtId) {
        return addresses.researchLocalCosts(districtId);
    }

    @PostMapping(value = "/api/me/address/verifications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AddressDtos.Verification verifyUpload(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @RequestParam int addressRevision,
                                                 @RequestPart("image") MultipartFile image) {
        return addresses.verifyUpload(user.getId(), addressRevision, image);
    }
}
