package com.rocketcredit.userdata.api;

import com.rocketcredit.userdata.api.UsersApi;
import com.rocketcredit.userdata.repo.UserRepository;
import com.rocketcredit.userdata.model.NewUser;
import com.rocketcredit.userdata.model.User;
import com.rocketcredit.userdata.model.UserEntity;

import java.net.URI;

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
    return repo.findById(id).map(this::toDto)
      .map(ResponseEntity::ok)
      .orElse(ResponseEntity.notFound().build());
  }

  @Override
  public ResponseEntity<User> createUser(@RequestBody NewUser newUser) {
    UserEntity e = new UserEntity(newUser.getName(), newUser.getEmail());
    e = repo.save(e);
    User dto = toDto(e);
    return ResponseEntity
      .created(URI.create("/users/" + e.getId()))
      .body(dto);
  }

  @Override
  public ResponseEntity<User> updateUser(Long id, @RequestBody User user) {
    return repo.findById(id).map(existing -> {
      existing.setName(user.getName());
      existing.setEmail(user.getEmail());
      repo.save(existing);
      return ResponseEntity.ok(toDto(existing));
    }).orElse(ResponseEntity.notFound().build());
  }

  @Override
  public ResponseEntity<Void> deleteUser(Long id) {
    if (!repo.existsById(id)) {
      return ResponseEntity.notFound().build();
    }
    repo.deleteById(id);
    return ResponseEntity.noContent().build();
  }

  private User toDto(UserEntity e) {
    User u = new User(e.getId(), e.getName(), e.getEmail());
    return u;
  }
}

