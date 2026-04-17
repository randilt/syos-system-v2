package com.syos.application.usecase;

import com.syos.domain.model.User;
import com.syos.domain.repository.UserRepository;

public class RegisterUser {
  private final UserRepository userRepository;

  public RegisterUser(UserRepository userRepository) {
    if (userRepository == null)
      throw new IllegalArgumentException("User repository cannot be null");
    this.userRepository = userRepository;
  }

  public User execute(String username, String email) {
    if (username == null || username.trim().isEmpty()) {
      throw new IllegalArgumentException("Username cannot be null or empty");
    }
    if (email == null || email.trim().isEmpty()) {
      throw new IllegalArgumentException("Email cannot be null or empty");
    }

    String userId = userRepository.nextUserId();
    User user = new User(userId, username, email);
    userRepository.save(user);
    return user;
  }
}
