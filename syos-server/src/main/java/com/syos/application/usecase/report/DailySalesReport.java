package com.syos.application.usecase.report;

import com.syos.domain.model.*;
import com.syos.domain.repository.BillRepository;
import com.syos.domain.repository.ItemRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Report implementation summarising sales for a date, optionally filtered by {TransactionType}.
 */
public class DailySalesReport extends AbstractReport {
  private final BillRepository billRepository;
  private final ItemRepository itemRepository;
  private final LocalDate date;
  private final TransactionType type;

  public DailySalesReport(
      BillRepository billRepository,
      ItemRepository itemRepository,
      LocalDate date,
      TransactionType type) {
    if (billRepository == null)
      throw new IllegalArgumentException("Bill repository cannot be null");
    if (itemRepository == null)
      throw new IllegalArgumentException("Item repository cannot be null");
    if (date == null) throw new IllegalArgumentException("Date cannot be null");

    this.billRepository = billRepository;
    this.itemRepository = itemRepository;
    this.date = date;
    this.type = type;
  }

  @Override
  /** Returns the human-readable report title. */
  public String getTitle() {
    String typeStr = type == null ? "All" : type.name();
    return "Daily Sales Report - " + date + " (" + typeStr + ")";
  }

  @Override
  protected void collectData() {
    List<Bill> bills =
        type == null
            ? billRepository.findByDate(date)
            : billRepository.findByDateAndType(date, type);

    Map<ItemCode, SalesSummary> summaries = new HashMap<>();

    for (Bill bill : bills) {
      for (BillItem item : bill.getItems()) {
        SalesSummary summary =
            summaries.computeIfAbsent(
                item.getItemCode(), k -> new SalesSummary(item.getItemCode(), item.getItemName()));
        summary.addQuantity(item.getQuantity());
        summary.addRevenue(item.getTotalPrice());
      }
    }

    summaries.values().stream()
        .sorted((s1, s2) -> s1.itemCode.getValue().compareTo(s2.itemCode.getValue()))
        .forEach(
            summary -> {
              Map<String, Object> row = new HashMap<>();
              row.put("itemName", summary.itemName);
              row.put("itemCode", summary.itemCode.getValue());
              row.put("totalQuantity", summary.totalQuantity);
              row.put("totalRevenue", summary.totalRevenue);
              addRow(row);
            });
  }

  private static class SalesSummary {
    final ItemCode itemCode;
    final String itemName;
    int totalQuantity = 0;
    Money totalRevenue = Money.zero();

    SalesSummary(ItemCode itemCode, String itemName) {
      this.itemCode = itemCode;
      this.itemName = itemName;
    }

    void addQuantity(int quantity) {
      this.totalQuantity += quantity;
    }

    void addRevenue(Money revenue) {
      this.totalRevenue = this.totalRevenue.add(revenue);
    }
  }
}
