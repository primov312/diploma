package com.rocketcredit.userdata.api;

import com.rocketcredit.userdata.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserApiController implements UsersApi {

    @Override
    public ResponseEntity<User> getUserById(Integer id) {
        // Return a dummy User
        User u = new User();
        u.setId(id);
        u.setName("Alice Example");
        u.setEmail("alice@example.com");
        return ResponseEntity.ok(u);
    }
}
