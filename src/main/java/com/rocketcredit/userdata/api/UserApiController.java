package com.rocketcredit.userdata.api;

import com.rocketcredit.userdata.model.User;
import com.rocketcredit.userdata.repo.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserApiController implements UsersApi {
  private final UserRepository repo;

  public UserApiController(UserRepository repo) {
    this.repo = repo;
  }

  @Override
  public ResponseEntity<User> getUserById(Long id) {
    return repo.findById(id.longValue())
      .map(entity -> {
        // map entity → DTO
        User dto = new User();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setEmail(entity.getEmail());
        return ResponseEntity.ok(dto);
      })
      .orElse(ResponseEntity.notFound().build());
  }
}

