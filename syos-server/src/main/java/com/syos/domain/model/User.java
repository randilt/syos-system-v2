package com.syos.domain.model;

import java.util.Objects;

public class User {
  private final String userId;
  private final String username;
  private final String email;

  public User(String userId, String username, String email) {
    if (userId == null || userId.trim().isEmpty())
      throw new IllegalArgumentException("User ID cannot be null or empty");
    if (username == null || username.trim().isEmpty())
      throw new IllegalArgumentException("Username cannot be null or empty");
    if (email == null || email.trim().isEmpty())
      throw new IllegalArgumentException("Email cannot be null or empty");

    this.userId = userId;
    this.username = username.trim();
    this.email = email.trim();
  }

  public String getUserId() {
    return userId;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    User user = (User) o;
    return Objects.equals(userId, user.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId);
  }
}
