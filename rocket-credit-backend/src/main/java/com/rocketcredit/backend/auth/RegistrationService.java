package com.rocketcredit.backend.auth;

import com.rocketcredit.backend.fixtures.StarterDataService;
import com.rocketcredit.backend.users.UserEntity;
import com.rocketcredit.backend.users.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account + its synthetic starter dataset are created in one transaction. */
@Service
public class RegistrationService {
    private final UserService userService;
    private final StarterDataService starterData;

    public RegistrationService(UserService userService, StarterDataService starterData) {
        this.userService = userService;
        this.starterData = starterData;
    }

    @Transactional
    public UserEntity register(String email, String password, String displayName) {
        UserEntity user = userService.register(email, password, displayName);
        starterData.createFor(user);
        return user;
    }
}
