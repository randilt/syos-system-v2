package com.syos.infrastructure.repository.jdbc;

import com.syos.domain.model.User;
import com.syos.domain.repository.UserRepository;
import com.syos.infrastructure.config.DatabaseManager;
import com.syos.infrastructure.config.JdbcTransactionContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class JdbcUserRepository implements UserRepository {
  private final DatabaseManager databaseManager;
  private final JdbcSequenceGenerator sequenceGenerator;

  public JdbcUserRepository(DatabaseManager databaseManager) {
    if (databaseManager == null)
      throw new IllegalArgumentException("Database manager cannot be null");
    this.databaseManager = databaseManager;
    this.sequenceGenerator = new JdbcSequenceGenerator(databaseManager);
  }

  @Override
  public void save(User user) {
    if (user == null) throw new IllegalArgumentException("User cannot be null");
    String sql =
        "INSERT INTO users (user_id, username, email) VALUES (?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE username = VALUES(username), email = VALUES(email)";
    Connection connection = JdbcTransactionContext.getConnection();
    boolean managedConnection = false;

    try {
      if (connection == null) {
        connection = databaseManager.getConnection();
        managedConnection = true;
      }
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, user.getUserId());
        statement.setString(2, user.getUsername());
        statement.setString(3, user.getEmail());
        statement.executeUpdate();
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to save user", ex);
    } finally {
      if (managedConnection && connection != null) {
        try {
          connection.close();
        } catch (SQLException ignored) {
          // ignore close failures
        }
      }
    }
  }

  @Override
  public Optional<User> findById(String userId) {
    if (userId == null) throw new IllegalArgumentException("User ID cannot be null");
    String sql = "SELECT user_id, username, email FROM users WHERE user_id = ?";
    Connection connection = JdbcTransactionContext.getConnection();
    boolean managedConnection = false;

    try {
      if (connection == null) {
        connection = databaseManager.getConnection();
        managedConnection = true;
      }
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, userId);
        try (ResultSet resultSet = statement.executeQuery()) {
          if (resultSet.next()) {
            return Optional.of(mapUser(resultSet));
          }
          return Optional.empty();
        }
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to load user", ex);
    } finally {
      if (managedConnection && connection != null) {
        try {
          connection.close();
        } catch (SQLException ignored) {
          // ignore close failures
        }
      }
    }
  }

  @Override
  public boolean exists(String userId) {
    if (userId == null) throw new IllegalArgumentException("User ID cannot be null");
    String sql = "SELECT 1 FROM users WHERE user_id = ?";
    Connection connection = JdbcTransactionContext.getConnection();
    boolean managedConnection = false;

    try {
      if (connection == null) {
        connection = databaseManager.getConnection();
        managedConnection = true;
      }
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, userId);
        try (ResultSet resultSet = statement.executeQuery()) {
          return resultSet.next();
        }
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to check user existence", ex);
    } finally {
      if (managedConnection && connection != null) {
        try {
          connection.close();
        } catch (SQLException ignored) {
          // ignore close failures
        }
      }
    }
  }

  @Override
  public String nextUserId() {
    int nextValue = sequenceGenerator.getNextId("USER_ID");
    return String.format("USER-%06d", nextValue);
  }

  private User mapUser(ResultSet resultSet) throws SQLException {
    String userId = resultSet.getString("user_id");
    String username = resultSet.getString("username");
    String email = resultSet.getString("email");
    return new User(userId, username, email);
  }
}
