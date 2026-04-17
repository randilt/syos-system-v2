package com.syos.application.usecase.report;

import com.syos.domain.model.ItemCode;
import com.syos.domain.model.StockBatch;
import com.syos.domain.repository.ItemRepository;
import com.syos.domain.repository.StockBatchRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class StockReport extends AbstractReport {
  private final StockBatchRepository stockRepository;
  private final ItemRepository itemRepository;
  private final LocalDate currentDate;

  public StockReport(
      StockBatchRepository stockRepository, ItemRepository itemRepository, LocalDate currentDate) {
    if (stockRepository == null)
      throw new IllegalArgumentException("Stock repository cannot be null");
    if (itemRepository == null)
      throw new IllegalArgumentException("Item repository cannot be null");
    if (currentDate == null) throw new IllegalArgumentException("Current date cannot be null");

    this.stockRepository = stockRepository;
    this.itemRepository = itemRepository;
    this.currentDate = currentDate;
  }

  @Override
  public String getTitle() {
    return "Stock Report - " + currentDate;
  }

  @Override
  protected void collectData() {
    stockRepository.findAll().stream()
        .filter(StockBatch::hasQuantity)
        .sorted(
            (b1, b2) -> {
              int codeCompare = b1.getItemCode().getValue().compareTo(b2.getItemCode().getValue());
              if (codeCompare != 0) return codeCompare;
              return b1.getBatchId().compareTo(b2.getBatchId());
            })
        .forEach(
            batch -> {
              ItemCode itemCode = batch.getItemCode();
              String itemName =
                  itemRepository.findByCode(itemCode).map(item -> item.getName()).orElse("Unknown");

              Map<String, Object> row = new HashMap<>();
              row.put("batchId", batch.getBatchId());
              row.put("itemCode", itemCode.getValue());
              row.put("itemName", itemName);
              row.put("purchaseDate", batch.getPurchaseDate());
              row.put("expiryDate", batch.getExpiryDate());
              row.put("quantity", batch.getQuantity());
              row.put("isExpired", batch.isExpired(currentDate));
              addRow(row);
            });
  }
}
