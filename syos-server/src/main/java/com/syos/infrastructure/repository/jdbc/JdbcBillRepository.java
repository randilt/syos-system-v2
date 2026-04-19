package com.syos.infrastructure.repository.jdbc;

import com.syos.domain.model.Bill;
import com.syos.domain.model.BillItem;
import com.syos.domain.model.ItemCode;
import com.syos.domain.model.Money;
import com.syos.domain.model.TransactionType;
import com.syos.domain.repository.BillRepository;
import com.syos.infrastructure.config.DatabaseManager;
import com.syos.infrastructure.config.JdbcTransactionContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC adapter implementing {BillRepository}.
 */
public class JdbcBillRepository implements BillRepository {
  private final DatabaseManager databaseManager;
  private final JdbcSequenceGenerator sequenceGenerator;

  public JdbcBillRepository(DatabaseManager databaseManager) {
    if (databaseManager == null)
      throw new IllegalArgumentException("Database manager cannot be null");
    this.databaseManager = databaseManager;
    this.sequenceGenerator = new JdbcSequenceGenerator(databaseManager);
  }

  @Override
  /** Persists the entity. */
  public void save(Bill bill) {
    if (bill == null) throw new IllegalArgumentException("Bill cannot be null");
    String billSql =
        "INSERT INTO bills (serial_number, bill_date, type, full_price, discount, cash_tendered, change_amount, user_id) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE bill_date = VALUES(bill_date), type = VALUES(type), full_price = VALUES(full_price), "
            + "discount = VALUES(discount), cash_tendered = VALUES(cash_tendered), change_amount = VALUES(change_amount), user_id = VALUES(user_id)";
    String deleteItemsSql = "DELETE FROM bill_items WHERE bill_serial_number = ?";
    String itemSql =
        "INSERT INTO bill_items (bill_serial_number, item_code, item_name, quantity, unit_price, total_price) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
    Connection connection = JdbcTransactionContext.getConnection();
    boolean managedConnection = false;

    try {
      if (connection == null) {
        connection = databaseManager.getConnection();
        connection.setAutoCommit(false);
        managedConnection = true;
      }

      try (PreparedStatement billStatement = connection.prepareStatement(billSql);
          PreparedStatement deleteItemsStatement = connection.prepareStatement(deleteItemsSql);
          PreparedStatement itemStatement = connection.prepareStatement(itemSql)) {
        billStatement.setInt(1, bill.getSerialNumber());
        billStatement.setDate(2, java.sql.Date.valueOf(bill.getDate()));
        billStatement.setString(3, bill.getType().name());
        billStatement.setBigDecimal(4, bill.getFullPrice().getAmount());
        billStatement.setBigDecimal(5, bill.getDiscount().getAmount());
        billStatement.setBigDecimal(
            6, bill.getCashTendered() == null ? null : bill.getCashTendered().getAmount());
        billStatement.setBigDecimal(
            7, bill.getChange() == null ? null : bill.getChange().getAmount());
        billStatement.setString(8, bill.getUserId());
        billStatement.executeUpdate();

        deleteItemsStatement.setInt(1, bill.getSerialNumber());
        deleteItemsStatement.executeUpdate();

        for (BillItem item : bill.getItems()) {
          itemStatement.setInt(1, bill.getSerialNumber());
          itemStatement.setString(2, item.getItemCode().getValue());
          itemStatement.setString(3, item.getItemName());
          itemStatement.setInt(4, item.getQuantity());
          itemStatement.setBigDecimal(5, item.getUnitPrice().getAmount());
          itemStatement.setBigDecimal(6, item.getTotalPrice().getAmount());
          itemStatement.addBatch();
        }
        itemStatement.executeBatch();

        if (managedConnection) {
          connection.commit();
        }
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
      throw new IllegalStateException("Failed to save bill", ex);
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
  /** FindBySerialNumber operation. */
  public Optional<Bill> findBySerialNumber(int serialNumber) {
    String sql =
        "SELECT serial_number, bill_date, type, full_price, discount, cash_tendered, change_amount, user_id "
            + "FROM bills WHERE serial_number = ?";
    Connection connection = JdbcTransactionContext.getConnection();
    boolean managedConnection = false;

    try {
      if (connection == null) {
        connection = databaseManager.getConnection();
        managedConnection = true;
      }
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setInt(1, serialNumber);
        try (ResultSet resultSet = statement.executeQuery()) {
          if (resultSet.next()) {
            return Optional.of(mapBill(resultSet, connection));
          }
        }
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to load bill", ex);
    } finally {
      if (managedConnection && connection != null) {
        try {
          connection.close();
        } catch (SQLException ignored) {
          // ignore close failures
        }
      }
    }
    return Optional.empty();
  }

  @Override
  /** Returns all entities. */
  public List<Bill> findAll() {
    String sql =
        "SELECT serial_number, bill_date, type, full_price, discount, cash_tendered, change_amount, user_id FROM bills";
    return queryBills(sql, statement -> {});
  }

  @Override
  /** FindByDate operation. */
  public List<Bill> findByDate(LocalDate date) {
    if (date == null) throw new IllegalArgumentException("Date cannot be null");
    String sql =
        "SELECT serial_number, bill_date, type, full_price, discount, cash_tendered, change_amount, user_id "
            + "FROM bills WHERE bill_date = ?";
    return queryBills(sql, statement -> statement.setDate(1, java.sql.Date.valueOf(date)));
  }

  @Override
  /** FindByType operation. */
  public List<Bill> findByType(TransactionType type) {
    if (type == null) throw new IllegalArgumentException("Transaction type cannot be null");
    String sql =
        "SELECT serial_number, bill_date, type, full_price, discount, cash_tendered, change_amount, user_id "
            + "FROM bills WHERE type = ?";
    return queryBills(sql, statement -> statement.setString(1, type.name()));
  }

  @Override
  /** FindByDateAndType operation. */
  public List<Bill> findByDateAndType(LocalDate date, TransactionType type) {
    if (date == null) throw new IllegalArgumentException("Date cannot be null");
    if (type == null) throw new IllegalArgumentException("Transaction type cannot be null");
    String sql =
        "SELECT serial_number, bill_date, type, full_price, discount, cash_tendered, change_amount, user_id "
            + "FROM bills WHERE bill_date = ? AND type = ?";
    return queryBills(
        sql,
        statement -> {
          statement.setDate(1, java.sql.Date.valueOf(date));
          statement.setString(2, type.name());
        });
  }

  @Override
  /** GetNextSerialNumber operation. */
  public int getNextSerialNumber() {
    return sequenceGenerator.getNextId("BILL_SERIAL");
  }

  private List<Bill> queryBills(String sql, StatementConfigurer configurer) {
    List<Bill> bills = new ArrayList<>();
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
            bills.add(mapBill(resultSet, connection));
          }
        }
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to load bills", ex);
    } finally {
      if (managedConnection && connection != null) {
        try {
          connection.close();
        } catch (SQLException ignored) {
          // ignore close failures
        }
      }
    }
    return bills;
  }

  private Bill mapBill(ResultSet resultSet, Connection connection) throws SQLException {
    int serialNumber = resultSet.getInt("serial_number");
    LocalDate date = resultSet.getDate("bill_date").toLocalDate();
    TransactionType type = TransactionType.valueOf(resultSet.getString("type"));
    Money fullPrice = Money.of(resultSet.getBigDecimal("full_price"));
    Money discount = Money.of(resultSet.getBigDecimal("discount"));
    BigDecimal cashTenderedValue = resultSet.getBigDecimal("cash_tendered");
    BigDecimal changeValue = resultSet.getBigDecimal("change_amount");
    String userId = resultSet.getString("user_id");

    Bill.Builder builder =
        Bill.builder()
            .serialNumber(serialNumber)
            .date(date)
            .type(type)
            .fullPrice(fullPrice)
            .discount(discount)
            .userId(userId);

    if (type == TransactionType.IN_STORE) {
      builder.cashTendered(cashTenderedValue == null ? Money.zero() : Money.of(cashTenderedValue));
      builder.change(changeValue == null ? Money.zero() : Money.of(changeValue));
    }

    for (BillItem item : loadBillItems(serialNumber, connection)) {
      builder.addItem(item);
    }

    return builder.build();
  }

  private List<BillItem> loadBillItems(int serialNumber, Connection connection)
      throws SQLException {
    String sql =
        "SELECT item_code, item_name, quantity, unit_price, total_price FROM bill_items WHERE bill_serial_number = ?";
    List<BillItem> items = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, serialNumber);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          ItemCode itemCode = ItemCode.of(resultSet.getString("item_code"));
          String itemName = resultSet.getString("item_name");
          int quantity = resultSet.getInt("quantity");
          Money unitPrice = Money.of(resultSet.getBigDecimal("unit_price"));
          Money totalPrice = Money.of(resultSet.getBigDecimal("total_price"));
          items.add(new BillItem(itemCode, itemName, quantity, unitPrice, totalPrice));
        }
      }
    }
    return items;
  }

  @FunctionalInterface
  private interface StatementConfigurer {
    void configure(PreparedStatement statement) throws SQLException;
  }
}
