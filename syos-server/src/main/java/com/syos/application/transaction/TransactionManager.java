package com.syos.application.transaction;

import java.util.function.Supplier;

public interface TransactionManager {
  <T> T executeInTransaction(Supplier<T> action);
}
