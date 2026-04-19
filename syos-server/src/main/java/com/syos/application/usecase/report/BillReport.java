package com.syos.application.usecase.report;

import com.syos.domain.model.Bill;
import com.syos.domain.model.TransactionType;
import com.syos.domain.repository.BillRepository;
import java.util.HashMap;
import java.util.Map;

/**
 * Report implementation listing all bills. Created by {ReportFactory} (type 9).
 */
public class BillReport extends AbstractReport {
  private final BillRepository billRepository;
  private final TransactionType type;

  public BillReport(BillRepository billRepository, TransactionType type) {
    if (billRepository == null)
      throw new IllegalArgumentException("Bill repository cannot be null");
    this.billRepository = billRepository;
    this.type = type;
  }

  @Override
  /** Returns the human-readable report title. */
  public String getTitle() {
    String typeStr = type == null ? "All Transactions" : type.name() + " Transactions";
    return "Bill Report - " + typeStr;
  }

  @Override
  protected void collectData() {
    java.util.List<Bill> bills =
        type == null ? billRepository.findAll() : billRepository.findByType(type);

    bills.stream()
        .sorted(
            (b1, b2) -> {
              int dateCompare = b1.getDate().compareTo(b2.getDate());
              if (dateCompare != 0) return dateCompare;
              return Integer.compare(b1.getSerialNumber(), b2.getSerialNumber());
            })
        .forEach(
            bill -> {
              Map<String, Object> row = new HashMap<>();
              row.put("serialNumber", bill.getSerialNumber());
              row.put("date", bill.getDate());
              row.put("type", bill.getType().name());
              row.put("userId", bill.getUserId());
              row.put("itemCount", bill.getItems().size());
              row.put("fullPrice", bill.getFullPrice().getAmount());
              row.put("discount", bill.getDiscount().getAmount());
              row.put("finalAmount", bill.getFinalAmount().getAmount());
              if (bill.getType() == TransactionType.IN_STORE) {
                row.put("cashTendered", bill.getCashTendered().getAmount());
                row.put("change", bill.getChange().getAmount());
              }
              addRow(row);
            });
  }
}
