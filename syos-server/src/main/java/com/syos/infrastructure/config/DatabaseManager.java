package com.syos.infrastructure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/** Singleton Pattern: ensures a single, shared connection pool across the application. */
public final class DatabaseManager {
  private static volatile DatabaseManager instance;
  private final HikariDataSource dataSource;

  private DatabaseManager() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(
        getEnvOrDefault(
            "SYOS_DB_URL",
            "jdbc:mysql://localhost:3306/syos_billing?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"));
    config.setUsername(getEnvOrDefault("SYOS_DB_USER", "syos"));
    config.setPassword(getEnvOrDefault("SYOS_DB_PASSWORD", "syos"));
    config.setMaximumPoolSize(10);
    config.setMinimumIdle(2);
    config.setPoolName("SyosBillingPool");
    this.dataSource = new HikariDataSource(config);
  }

  public static DatabaseManager getInstance() {
    if (instance == null) {
      synchronized (DatabaseManager.class) {
        if (instance == null) {
          instance = new DatabaseManager();
        }
      }
    }
    return instance;
  }

  public Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }

  public DataSource getDataSource() {
    return dataSource;
  }

  private String getEnvOrDefault(String key, String defaultValue) {
    String value = System.getenv(key);
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
