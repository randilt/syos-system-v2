package com.syos.application.usecase.report;

import com.syos.domain.repository.ItemRepository;
import com.syos.domain.service.ReshelvingCalculator;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class ReshelvingReport extends AbstractReport {
  private final ReshelvingCalculator reshelvingCalculator;
  private final ItemRepository itemRepository;
  private final LocalDate date;

  public ReshelvingReport(
      ReshelvingCalculator reshelvingCalculator, ItemRepository itemRepository, LocalDate date) {
    if (reshelvingCalculator == null)
      throw new IllegalArgumentException("Reshelving calculator cannot be null");
    if (itemRepository == null)
      throw new IllegalArgumentException("Item repository cannot be null");
    if (date == null) throw new IllegalArgumentException("Date cannot be null");

    this.reshelvingCalculator = reshelvingCalculator;
    this.itemRepository = itemRepository;
    this.date = date;
  }

  @Override
  public String getTitle() {
    return "Reshelving Report - " + date;
  }

  @Override
  protected void collectData() {
    var reshelving = reshelvingCalculator.calculate(date);

    reshelving.values().stream()
        .sorted((s1, s2) -> s1.getItemCode().getValue().compareTo(s2.getItemCode().getValue()))
        .forEach(
            summary -> {
              var itemCode = summary.getItemCode();

              String itemName =
                  itemRepository.findByCode(itemCode).map(item -> item.getName()).orElse("Unknown");

              Map<String, Object> row = new HashMap<>();
              row.put("itemCode", itemCode.getValue());
              row.put("itemName", itemName);
              row.put("soldQty", summary.getSoldQty());
              row.put("availableStoreQty", summary.getAvailableStoreQty());
              row.put("reshelvedQty", summary.getReshelvedQty());
              addRow(row);
            });
  }
}
