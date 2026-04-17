package com.syos.application.usecase.report;

import com.syos.domain.model.Item;
import com.syos.domain.repository.ItemRepository;
import com.syos.domain.repository.StockBatchRepository;
import java.util.HashMap;
import java.util.Map;

public class ReorderReport extends AbstractReport {
  private final StockBatchRepository stockRepository;
  private final ItemRepository itemRepository;
  private final int reorderThreshold;

  public ReorderReport(
      StockBatchRepository stockRepository, ItemRepository itemRepository, int reorderThreshold) {
    if (stockRepository == null)
      throw new IllegalArgumentException("Stock repository cannot be null");
    if (itemRepository == null)
      throw new IllegalArgumentException("Item repository cannot be null");
    if (reorderThreshold < 0)
      throw new IllegalArgumentException("Reorder threshold cannot be negative");

    this.stockRepository = stockRepository;
    this.itemRepository = itemRepository;
    this.reorderThreshold = reorderThreshold;
  }

  @Override
  public String getTitle() {
    return "Reorder Report (Threshold: " + reorderThreshold + " units)";
  }

  @Override
  protected void collectData() {
    itemRepository.findAll().stream()
        .map(Item::getCode)
        .filter(
            code -> {
              int totalStock = stockRepository.getTotalStock(code);
              return totalStock < reorderThreshold;
            })
        .sorted((c1, c2) -> c1.getValue().compareTo(c2.getValue()))
        .forEach(
            itemCode -> {
              Item item = itemRepository.findByCode(itemCode).orElse(null);
              if (item != null) {
                int totalStock = stockRepository.getTotalStock(itemCode);
                Map<String, Object> row = new HashMap<>();
                row.put("itemCode", itemCode.getValue());
                row.put("itemName", item.getName());
                row.put("currentStock", totalStock);
                addRow(row);
              }
            });
  }
}
