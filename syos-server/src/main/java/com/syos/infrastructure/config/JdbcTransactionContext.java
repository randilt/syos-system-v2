package com.syos.infrastructure.config;

import java.sql.Connection;

/**
 * Thread-local holder for the active JDBC connection during a transaction.
 */
public final class JdbcTransactionContext {
  private static final ThreadLocal<Connection> CURRENT = new ThreadLocal<>();

  private JdbcTransactionContext() {}

  /** GetConnection operation. */

  public static Connection getConnection() {
    return CURRENT.get();
  }

  /** SetConnection operation. */

  public static void setConnection(Connection connection) {
    CURRENT.set(connection);
  }

  /** Clear operation. */

  public static void clear() {
    CURRENT.remove();
  }
}
