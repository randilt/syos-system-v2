package com.syos.domain.repository;

import com.syos.domain.model.User;
import java.util.Optional;

/**
 * Repository port for {User} persistence.
 */
public interface UserRepository {
  void save(User user);

  Optional<User> findById(String userId);

  boolean exists(String userId);

  String nextUserId();
}
