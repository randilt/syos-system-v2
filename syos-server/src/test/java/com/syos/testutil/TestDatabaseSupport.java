package com.syos.testutil;

import com.syos.infrastructure.config.DatabaseManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;

public final class TestDatabaseSupport {
  private static DatabaseManager databaseManager;
  private static Flyway flyway;

  private TestDatabaseSupport() {}

  public static synchronized DatabaseManager databaseManager() {
    if (databaseManager == null) {
      try {
        databaseManager = DatabaseManager.getInstance();
        flyway =
            Flyway.configure()
                .dataSource(databaseManager.getDataSource())
                .locations("classpath:db/migration")
                .load();
      } catch (Exception ex) {
        throw new IllegalStateException("MySQL not available for integration tests", ex);
      }
    }
    return databaseManager;
  }

  public static void migrate() {
    DatabaseManager manager = databaseManager();
    if (flyway == null) {
      try {
        flyway =
            Flyway.configure()
                .dataSource(manager.getDataSource())
                .locations("classpath:db/migration")
                .load();
      } catch (Exception ex) {
        throw new IllegalStateException("MySQL not available for integration tests", ex);
      }
    }
    flyway.repair();
    flyway.migrate();
  }

  public static void resetDatabase() {
    try (Connection connection = databaseManager().getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("SET FOREIGN_KEY_CHECKS = 0");
      statement.execute("TRUNCATE TABLE bill_items");
      statement.execute("TRUNCATE TABLE transactions");
      statement.execute("TRUNCATE TABLE bills");
      statement.execute("TRUNCATE TABLE stock_batches");
      statement.execute("TRUNCATE TABLE users");
      statement.execute("TRUNCATE TABLE items");
        // Reset sequences to initial values matching V3 migration seed
        statement.execute(
          "UPDATE id_sequences SET current_value = 0 WHERE sequence_name = 'BILL_SERIAL'");
        statement.execute(
          "UPDATE id_sequences SET current_value = 12 WHERE sequence_name = 'BATCH_ID'");
        statement.execute(
          "UPDATE id_sequences SET current_value = 1 WHERE sequence_name = 'USER_ID'");
        statement.execute(
          "UPDATE id_sequences SET current_value = 0 WHERE sequence_name = 'TXN_ID'");
      statement.execute("SET FOREIGN_KEY_CHECKS = 1");
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to reset database", ex);
    }
  }
}
