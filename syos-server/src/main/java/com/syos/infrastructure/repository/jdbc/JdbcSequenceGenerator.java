package com.syos.infrastructure.repository.jdbc;

import com.syos.infrastructure.config.DatabaseManager;
import com.syos.infrastructure.config.JdbcTransactionContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class JdbcSequenceGenerator {
  private final DatabaseManager databaseManager;

  public JdbcSequenceGenerator(DatabaseManager databaseManager) {
    if (databaseManager == null)
      throw new IllegalArgumentException("Database manager cannot be null");
    this.databaseManager = databaseManager;
  }

  public int getNextId(String sequenceName) {
    if (sequenceName == null || sequenceName.isBlank()) {
      throw new IllegalArgumentException("Sequence name cannot be null or empty");
    }

    String selectSql = "SELECT current_value FROM id_sequences WHERE sequence_name = ? FOR UPDATE";
    String updateSql = "UPDATE id_sequences SET current_value = ? WHERE sequence_name = ?";

    Connection connection = JdbcTransactionContext.getConnection();
    boolean managedConnection = false;
    try {
      if (connection == null) {
        connection = databaseManager.getConnection();
        connection.setAutoCommit(false);
        managedConnection = true;
      }

      try (PreparedStatement selectStatement = connection.prepareStatement(selectSql);
          PreparedStatement updateStatement = connection.prepareStatement(updateSql)) {
        selectStatement.setString(1, sequenceName);
        int nextValue;

        try (ResultSet resultSet = selectStatement.executeQuery()) {
          if (!resultSet.next()) {
            throw new IllegalStateException("Sequence not found: " + sequenceName);
          }
          int currentValue = resultSet.getInt("current_value");
          nextValue = currentValue + 1;
        }

        updateStatement.setInt(1, nextValue);
        updateStatement.setString(2, sequenceName);
        updateStatement.executeUpdate();

        if (managedConnection) {
          connection.commit();
        }
        return nextValue;
      } catch (SQLException ex) {
        if (managedConnection) {
          connection.rollback();
        }
        throw ex;
      } finally {
        if (managedConnection) {
          connection.setAutoCommit(true);
        }
      }
    } catch (SQLException ex) {
      throw new IllegalStateException(
          "Failed to generate next id for sequence: " + sequenceName, ex);
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
}
