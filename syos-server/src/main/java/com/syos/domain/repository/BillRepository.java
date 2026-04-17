package com.syos.domain.repository;

import com.syos.domain.model.Bill;
import com.syos.domain.model.TransactionType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BillRepository {
  void save(Bill bill);

  Optional<Bill> findBySerialNumber(int serialNumber);

  List<Bill> findAll();

  List<Bill> findByDate(LocalDate date);

  List<Bill> findByType(TransactionType type);

  List<Bill> findByDateAndType(LocalDate date, TransactionType type);

  int getNextSerialNumber();
}
