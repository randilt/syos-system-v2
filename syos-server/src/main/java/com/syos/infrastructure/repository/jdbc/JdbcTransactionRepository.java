package com.syos.infrastructure.repository.jdbc;

import com.syos.domain.model.Transaction;
import com.syos.domain.model.TransactionType;
import com.syos.domain.repository.TransactionRepository;
import com.syos.infrastructure.config.DatabaseManager;
import com.syos.infrastructure.config.JdbcTransactionContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC adapter implementing {TransactionRepository}.
 */
public class JdbcTransactionRepository implements TransactionRepository {
  private final DatabaseManager databaseManager;
  private final JdbcSequenceGenerator sequenceGenerator;

  public JdbcTransactionRepository(DatabaseManager databaseManager) {
    if (databaseManager == null)
      throw new IllegalArgumentException("Database manager cannot be null");
    this.databaseManager = databaseManager;
    this.sequenceGenerator = new JdbcSequenceGenerator(databaseManager);
  }

  @Override
  /** Persists the entity. */
  public void save(Transaction transaction) {
    if (transaction == null) throw new IllegalArgumentException("Transaction cannot be null");
    String sql =
        "INSERT INTO transactions (transaction_id, bill_serial_number, txn_date, type, user_id) "
            + "VALUES (?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE bill_serial_number = VALUES(bill_serial_number), "
            + "txn_date = VALUES(txn_date), type = VALUES(type), user_id = VALUES(user_id)";
    Connection connection = JdbcTransactionContext.getConnection();
    boolean managedConnection = false;

    try {
      if (connection == null) {
        connection = databaseManager.getConnection();
        managedConnection = true;
      }
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, transaction.getTransactionId());
        statement.setInt(2, transaction.getBillSerialNumber());
        statement.setDate(3, java.sql.Date.valueOf(transaction.getDate()));
        statement.setString(4, transaction.getType().name());
        statement.setString(5, transaction.getUserId());
        statement.executeUpdate();
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to save transaction", ex);
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
  /** NextTransactionId operation. */
  public String nextTransactionId() {
    int nextValue = sequenceGenerator.getNextId("TXN_ID");
    return String.format("TXN-%06d", nextValue);
  }

  @Override
  /** Returns all entities. */
  public List<Transaction> findAll() {
    String sql =
        "SELECT transaction_id, bill_serial_number, txn_date, type, user_id FROM transactions";
    return queryTransactions(sql, statement -> {});
  }

  @Override
  /** FindByDate operation. */
  public List<Transaction> findByDate(LocalDate date) {
    if (date == null) throw new IllegalArgumentException("Date cannot be null");
    String sql =
        "SELECT transaction_id, bill_serial_number, txn_date, type, user_id FROM transactions WHERE txn_date = ?";
    return queryTransactions(sql, statement -> statement.setDate(1, java.sql.Date.valueOf(date)));
  }

  @Override
  /** FindByType operation. */
  public List<Transaction> findByType(TransactionType type) {
    if (type == null) throw new IllegalArgumentException("Transaction type cannot be null");
    String sql =
        "SELECT transaction_id, bill_serial_number, txn_date, type, user_id FROM transactions WHERE type = ?";
    return queryTransactions(sql, statement -> statement.setString(1, type.name()));
  }

  @Override
  /** FindByDateAndType operation. */
  public List<Transaction> findByDateAndType(LocalDate date, TransactionType type) {
    if (date == null) throw new IllegalArgumentException("Date cannot be null");
    if (type == null) throw new IllegalArgumentException("Transaction type cannot be null");
    String sql =
        "SELECT transaction_id, bill_serial_number, txn_date, type, user_id FROM transactions "
            + "WHERE txn_date = ? AND type = ?";
    return queryTransactions(
        sql,
        statement -> {
          statement.setDate(1, java.sql.Date.valueOf(date));
          statement.setString(2, type.name());
        });
  }

  private List<Transaction> queryTransactions(String sql, StatementConfigurer configurer) {
    List<Transaction> transactions = new ArrayList<>();
    Connection connection = JdbcTransactionContext.getConnection();
    boolean managedConnection = false;

    try {
      if (connection == null) {
        connection = databaseManager.getConnection();
        managedConnection = true;
      }
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        configurer.configure(statement);
        try (ResultSet resultSet = statement.executeQuery()) {
          while (resultSet.next()) {
            transactions.add(mapTransaction(resultSet));
          }
        }
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to load transactions", ex);
    } finally {
      if (managedConnection && connection != null) {
        try {
          connection.close();
        } catch (SQLException ignored) {
          // ignore close failures
        }
      }
    }
    return transactions;
  }

  private Transaction mapTransaction(ResultSet resultSet) throws SQLException {
    String transactionId = resultSet.getString("transaction_id");
    int billSerialNumber = resultSet.getInt("bill_serial_number");
    LocalDate date = resultSet.getDate("txn_date").toLocalDate();
    TransactionType type = TransactionType.valueOf(resultSet.getString("type"));
    String userId = resultSet.getString("user_id");
    return new Transaction(transactionId, billSerialNumber, date, type, userId);
  }

  @FunctionalInterface
  private interface StatementConfigurer {
    void configure(PreparedStatement statement) throws SQLException;
  }
}
