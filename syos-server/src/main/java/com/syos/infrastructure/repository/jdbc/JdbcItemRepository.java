package com.syos.infrastructure.repository.jdbc;

import com.syos.domain.model.Item;
import com.syos.domain.model.ItemCode;
import com.syos.domain.model.Money;
import com.syos.domain.repository.ItemRepository;
import com.syos.infrastructure.config.DatabaseManager;
import com.syos.infrastructure.config.JdbcTransactionContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcItemRepository implements ItemRepository {
  private final DatabaseManager databaseManager;

  public JdbcItemRepository(DatabaseManager databaseManager) {
    if (databaseManager == null)
      throw new IllegalArgumentException("Database manager cannot be null");
    this.databaseManager = databaseManager;
  }

  @Override
  public void save(Item item) {
    if (item == null) throw new IllegalArgumentException("Item cannot be null");
    String sql =
        "INSERT INTO items (item_code, name, unit_price) VALUES (?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE name = VALUES(name), unit_price = VALUES(unit_price)";
    Connection connection = JdbcTransactionContext.getConnection();
    boolean managedConnection = false;

    try {
      if (connection == null) {
        connection = databaseManager.getConnection();
        managedConnection = true;
      }
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, item.getCode().getValue());
        statement.setString(2, item.getName());
        statement.setBigDecimal(3, item.getUnitPrice().getAmount());
        statement.executeUpdate();
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to save item", ex);
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
  public Optional<Item> findByCode(ItemCode code) {
    if (code == null) throw new IllegalArgumentException("Item code cannot be null");
    String sql = "SELECT item_code, name, unit_price FROM items WHERE item_code = ?";
    Connection connection = JdbcTransactionContext.getConnection();
    boolean managedConnection = false;

    try {
      if (connection == null) {
        connection = databaseManager.getConnection();
        managedConnection = true;
      }
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, code.getValue());
        try (ResultSet resultSet = statement.executeQuery()) {
          if (resultSet.next()) {
            return Optional.of(mapItem(resultSet));
          }
          return Optional.empty();
        }
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to load item", ex);
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
  public List<Item> findAll() {
    String sql = "SELECT item_code, name, unit_price FROM items";
    List<Item> items = new ArrayList<>();
    Connection connection = JdbcTransactionContext.getConnection();
    boolean managedConnection = false;

    try {
      if (connection == null) {
        connection = databaseManager.getConnection();
        managedConnection = true;
      }
      try (PreparedStatement statement = connection.prepareStatement(sql);
          ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          items.add(mapItem(resultSet));
        }
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to load items", ex);
    } finally {
      if (managedConnection && connection != null) {
        try {
          connection.close();
        } catch (SQLException ignored) {
          // ignore close failures
        }
      }
    }
    return items;
  }

  @Override
  public boolean exists(ItemCode code) {
    if (code == null) throw new IllegalArgumentException("Item code cannot be null");
    String sql = "SELECT 1 FROM items WHERE item_code = ?";
    Connection connection = JdbcTransactionContext.getConnection();
    boolean managedConnection = false;

    try {
      if (connection == null) {
        connection = databaseManager.getConnection();
        managedConnection = true;
      }
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, code.getValue());
        try (ResultSet resultSet = statement.executeQuery()) {
          return resultSet.next();
        }
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to check item existence", ex);
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

  private Item mapItem(ResultSet resultSet) throws SQLException {
    String code = resultSet.getString("item_code");
    String name = resultSet.getString("name");
    BigDecimal unitPrice = resultSet.getBigDecimal("unit_price");
    return new Item(ItemCode.of(code), name, Money.of(unitPrice));
  }
}
