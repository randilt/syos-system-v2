package com.syos.domain.factory;

import com.syos.application.usecase.report.BillReport;
import com.syos.application.usecase.report.DailySalesReport;
import com.syos.application.usecase.report.ReorderReport;
import com.syos.application.usecase.report.Report;
import com.syos.application.usecase.report.ReshelvingReport;
import com.syos.application.usecase.report.StockReport;
import com.syos.domain.model.TransactionType;
import com.syos.domain.repository.BillRepository;
import com.syos.domain.repository.ItemRepository;
import com.syos.domain.repository.StockBatchRepository;
import com.syos.domain.service.ReshelvingCalculator;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Factory (creational pattern) that builds report instances by type key. Used by {RequestRouter}.
 */
public class ReportFactory {
  private final BillRepository billRepository;
  private final ItemRepository itemRepository;
  private final StockBatchRepository storeStockRepository;
  private final StockBatchRepository shelfStockRepository;
  private final StockBatchRepository onlineStockRepository;
  private final ReshelvingCalculator reshelvingCalculator;
  private final int reorderThreshold;
  private final Map<String, Supplier<Report>> reportSuppliers;
  private LocalDate reportDate;

  public ReportFactory(
      BillRepository billRepository,
      ItemRepository itemRepository,
      StockBatchRepository storeStockRepository,
      StockBatchRepository shelfStockRepository,
      StockBatchRepository onlineStockRepository,
      ReshelvingCalculator reshelvingCalculator,
      int reorderThreshold) {
    if (billRepository == null || itemRepository == null) {
      throw new IllegalArgumentException("Repositories cannot be null");
    }
    if (storeStockRepository == null
        || shelfStockRepository == null
        || onlineStockRepository == null) {
      throw new IllegalArgumentException("Stock repositories cannot be null");
    }
    if (reshelvingCalculator == null) {
      throw new IllegalArgumentException("Reshelving calculator cannot be null");
    }
    this.billRepository = billRepository;
    this.itemRepository = itemRepository;
    this.storeStockRepository = storeStockRepository;
    this.shelfStockRepository = shelfStockRepository;
    this.onlineStockRepository = onlineStockRepository;
    this.reshelvingCalculator = reshelvingCalculator;
    this.reorderThreshold = reorderThreshold;
    this.reportSuppliers = new HashMap<>();
    initializeSuppliers();
  }

  /** Creates a report instance for the given type key and date. */

  public Report createReport(String reportType, LocalDate date) {
    if (reportType == null || date == null) {
      throw new IllegalArgumentException("Report type and date cannot be null");
    }

    reportDate = date;
    Supplier<Report> supplier = reportSuppliers.get(reportType);
    return supplier == null ? null : supplier.get();
  }

  private void initializeSuppliers() {
    reportSuppliers.put(
        "1", () -> new DailySalesReport(billRepository, itemRepository, reportDate, null));
    reportSuppliers.put(
        "2",
        () ->
            new DailySalesReport(
                billRepository, itemRepository, reportDate, TransactionType.IN_STORE));
    reportSuppliers.put(
        "3",
        () ->
            new DailySalesReport(
                billRepository, itemRepository, reportDate, TransactionType.ONLINE));
    reportSuppliers.put(
        "4", () -> new ReshelvingReport(reshelvingCalculator, itemRepository, reportDate));
    reportSuppliers.put(
        "5", () -> new ReorderReport(storeStockRepository, itemRepository, reorderThreshold));
    reportSuppliers.put(
        "6", () -> new StockReport(storeStockRepository, itemRepository, reportDate));
    reportSuppliers.put(
        "7", () -> new StockReport(shelfStockRepository, itemRepository, reportDate));
    reportSuppliers.put(
        "8", () -> new StockReport(onlineStockRepository, itemRepository, reportDate));
    reportSuppliers.put("9", () -> new BillReport(billRepository, null));
  }
}
