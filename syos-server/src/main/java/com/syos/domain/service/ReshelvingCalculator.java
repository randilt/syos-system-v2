package com.syos.domain.service;

import com.syos.domain.model.Bill;
import com.syos.domain.model.BillItem;
import com.syos.domain.model.ItemCode;
import com.syos.domain.model.TransactionType;
import com.syos.domain.repository.BillRepository;
import com.syos.domain.repository.StockBatchRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain service calculating reshelving quantities from sales history.
 */
public class ReshelvingCalculator {
  private final BillRepository billRepository;
  private final StockBatchRepository storeStockRepository;

  public ReshelvingCalculator(
      BillRepository billRepository, StockBatchRepository storeStockRepository) {
    if (billRepository == null) {
      throw new IllegalArgumentException("Bill repository cannot be null");
    }
    if (storeStockRepository == null) {
      throw new IllegalArgumentException("Store stock repository cannot be null");
    }
    this.billRepository = billRepository;
    this.storeStockRepository = storeStockRepository;
  }

  /** Calculate operation. */

  public Map<ItemCode, ReshelvingSummary> calculate(LocalDate date) {
    if (date == null) {
      throw new IllegalArgumentException("Date cannot be null");
    }

    List<Bill> bills = billRepository.findByDateAndType(date, TransactionType.IN_STORE);
    Map<ItemCode, Integer> soldQuantities = new HashMap<>();

    for (Bill bill : bills) {
      for (BillItem item : bill.getItems()) {
        soldQuantities.merge(item.getItemCode(), item.getQuantity(), Integer::sum);
      }
    }

    Map<ItemCode, ReshelvingSummary> result = new HashMap<>();
    for (Map.Entry<ItemCode, Integer> entry : soldQuantities.entrySet()) {
      ItemCode itemCode = entry.getKey();
      int soldQty = entry.getValue();
      int availableStoreQty = storeStockRepository.getTotalStock(itemCode);
      int reshelvedQty = Math.min(soldQty, availableStoreQty);
      result.put(
          itemCode, new ReshelvingSummary(itemCode, soldQty, availableStoreQty, reshelvedQty));
    }

    return result;
  }

  public static class ReshelvingSummary {
    private final ItemCode itemCode;
    private final int soldQty;
    private final int availableStoreQty;
    private final int reshelvedQty;

    public ReshelvingSummary(
        ItemCode itemCode, int soldQty, int availableStoreQty, int reshelvedQty) {
      this.itemCode = itemCode;
      this.soldQty = soldQty;
      this.availableStoreQty = availableStoreQty;
      this.reshelvedQty = reshelvedQty;
    }

    /** GetItemCode operation. */

    public ItemCode getItemCode() {
      return itemCode;
    }

    /** GetSoldQty operation. */

    public int getSoldQty() {
      return soldQty;
    }

    /** GetAvailableStoreQty operation. */

    public int getAvailableStoreQty() {
      return availableStoreQty;
    }

    /** GetReshelvedQty operation. */

    public int getReshelvedQty() {
      return reshelvedQty;
    }
  }
}
