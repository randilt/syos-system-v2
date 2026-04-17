package com.syos.infrastructure.config;

import com.syos.application.transaction.TransactionManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

public class JdbcTransactionManager implements TransactionManager {
  private final DatabaseManager databaseManager;

  public JdbcTransactionManager(DatabaseManager databaseManager) {
    if (databaseManager == null) {
      throw new IllegalArgumentException("Database manager cannot be null");
    }
    this.databaseManager = databaseManager;
  }

  @Override
  public <T> T executeInTransaction(Supplier<T> action) {
    if (action == null) {
      throw new IllegalArgumentException("Action cannot be null");
    }

    try (Connection connection = databaseManager.getConnection()) {
      connection.setAutoCommit(false);
      JdbcTransactionContext.setConnection(connection);
      try {
        T result = action.get();
        connection.commit();
        return result;
      } catch (Exception ex) {
        connection.rollback();
        throw ex;
      } finally {
        JdbcTransactionContext.clear();
        connection.setAutoCommit(true);
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to execute transactional operation", ex);
    }
  }
}
