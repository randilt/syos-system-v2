package com.syos.domain.repository;

import com.syos.domain.model.Transaction;
import com.syos.domain.model.TransactionType;
import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository {
  void save(Transaction transaction);

  String nextTransactionId();

  List<Transaction> findAll();

  List<Transaction> findByDate(LocalDate date);

  List<Transaction> findByType(TransactionType type);

  List<Transaction> findByDateAndType(LocalDate date, TransactionType type);
}
