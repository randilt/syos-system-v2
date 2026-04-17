package com.syos.infrastructure.config;

import java.sql.Connection;

public final class JdbcTransactionContext {
  private static final ThreadLocal<Connection> CURRENT = new ThreadLocal<>();

  private JdbcTransactionContext() {}

  public static Connection getConnection() {
    return CURRENT.get();
  }

  public static void setConnection(Connection connection) {
    CURRENT.set(connection);
  }

  public static void clear() {
    CURRENT.remove();
  }
}
