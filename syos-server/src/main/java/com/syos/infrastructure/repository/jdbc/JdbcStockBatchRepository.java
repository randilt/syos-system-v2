package com.syos.infrastructure.repository.jdbc;

import com.syos.domain.model.ItemCode;
import com.syos.domain.model.StockBatch;
import com.syos.domain.repository.StockBatchRepository;
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
 * JDBC adapter implementing {StockBatchRepository} for one stock location.
 */
public class JdbcStockBatchRepository implements StockBatchRepository {
  private final DatabaseManager databaseManager;
  private final String stockType;
  private final JdbcSequenceGenerator sequenceGenerator;

  public JdbcStockBatchRepository(DatabaseManager databaseManager, String stockType) {
    if (databaseManager == null)
      throw new IllegalArgumentException("Database manager cannot be null");
    if (stockType == null || stockType.isBlank())
      throw new IllegalArgumentException("Stock type cannot be null or empty");
    this.databaseManager = databaseManager;
    this.stockType = stockType.trim().toUpperCase();
    this.sequenceGenerator = new JdbcSequenceGenerator(databaseManager);
  }

  @Override
  /** Persists the entity. */
  public void save(StockBatch batch) {
    if (batch == null) throw new IllegalArgumentException("Batch cannot be null");
    String sql =
        "INSERT INTO stock_batches (batch_id, item_code, purchase_date, expiry_date, quantity, stock_type) "
            + "VALUES (?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE item_code = VALUES(item_code), purchase_date = VALUES(purchase_date), "
            + "expiry_date = VALUES(expiry_date), quantity = VALUES(quantity), stock_type = VALUES(stock_type)";
    Connection connection = JdbcTransactionContext.getConnection();
    boolean managedConnection = false;

    try {
      if (connection == null) {
        connection = databaseManager.getConnection();
        managedConnection = true;
      }
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, batch.getBatchId());
        statement.setString(2, batch.getItemCode().getValue());
        statement.setDate(3, java.sql.Date.valueOf(batch.getPurchaseDate()));
        statement.setDate(4, java.sql.Date.valueOf(batch.getExpiryDate()));
        statement.setInt(5, batch.getQuantity());
        statement.setString(6, stockType);
        statement.executeUpdate();
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to save stock batch", ex);
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
  /** FindByItemCode operation. */
  public List<StockBatch> findByItemCode(ItemCode itemCode) {
    if (itemCode == null) throw new IllegalArgumentException("Item code cannot be null");
    String sql =
        "SELECT batch_id, item_code, purchase_date, expiry_date, quantity FROM stock_batches "
            + "WHERE item_code = ? AND stock_type = ?"
            + (JdbcTransactionContext.getConnection() != null ? " FOR UPDATE" : "");
    return queryBatches(
        sql,
        statement -> {
          statement.setString(1, itemCode.getValue());
          statement.setString(2, stockType);
        });
  }

  @Override
  /** Returns all entities. */
  public List<StockBatch> findAll() {
    String sql =
        "SELECT batch_id, item_code, purchase_date, expiry_date, quantity FROM stock_batches WHERE stock_type = ?";
    return queryBatches(sql, statement -> statement.setString(1, stockType));
  }

  @Override
  /** GetTotalStock operation. */
  public int getTotalStock(ItemCode itemCode) {
    if (itemCode == null) throw new IllegalArgumentException("Item code cannot be null");
    String sql =
        "SELECT COALESCE(SUM(quantity), 0) AS total_quantity FROM stock_batches "
            + "WHERE item_code = ? AND stock_type = ?";
    Connection connection = JdbcTransactionContext.getConnection();
    boolean managedConnection = false;

    try {
      if (connection == null) {
        connection = databaseManager.getConnection();
        managedConnection = true;
      }
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, itemCode.getValue());
        statement.setString(2, stockType);
        try (ResultSet resultSet = statement.executeQuery()) {
          if (resultSet.next()) {
            return resultSet.getInt("total_quantity");
          }
        }
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to get total stock", ex);
    } finally {
      if (managedConnection && connection != null) {
        try {
          connection.close();
        } catch (SQLException ignored) {
          // ignore close failures
        }
      }
    }
    return 0;
  }

  @Override
  /** Returns the next available batch identifier. */
  public String nextBatchId() {
    int nextValue = sequenceGenerator.getNextId("BATCH_ID");
    return String.format("BATCH-%06d", nextValue);
  }

  private List<StockBatch> queryBatches(String sql, StatementConfigurer configurer) {
    List<StockBatch> batches = new ArrayList<>();
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
            batches.add(mapBatch(resultSet));
          }
        }
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to load stock batches", ex);
    } finally {
      if (managedConnection && connection != null) {
        try {
          connection.close();
        } catch (SQLException ignored) {
          // ignore close failures
        }
      }
    }
    return batches;
  }

  private StockBatch mapBatch(ResultSet resultSet) throws SQLException {
    String batchId = resultSet.getString("batch_id");
    ItemCode itemCode = ItemCode.of(resultSet.getString("item_code"));
    LocalDate purchaseDate = resultSet.getDate("purchase_date").toLocalDate();
    LocalDate expiryDate = resultSet.getDate("expiry_date").toLocalDate();
    int quantity = resultSet.getInt("quantity");
    return new StockBatch(batchId, itemCode, purchaseDate, expiryDate, quantity);
  }

  @FunctionalInterface
  private interface StatementConfigurer {
    void configure(PreparedStatement statement) throws SQLException;
  }
}
