package com.syos.application.transaction;

import java.util.function.Supplier;

/**
 * Application-layer port for executing work inside a database transaction. Implemented by {JdbcTransactionManager}.
 */
public interface TransactionManager {
  <T> T executeInTransaction(Supplier<T> action);
}
