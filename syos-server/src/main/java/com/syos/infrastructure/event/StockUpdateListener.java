package com.syos.infrastructure.event;

import com.syos.domain.event.SaleEvent;
import com.syos.domain.event.SaleEventListener;
import com.syos.domain.model.BillItem;
import com.syos.infrastructure.service.StockManager;
import java.time.LocalDate;

/**
 * Observer that restocks the shelf via {StockManager} when a sale event fires.
 */
public class StockUpdateListener implements SaleEventListener {
  private final StockManager stockManager;

  public StockUpdateListener(StockManager stockManager) {
    if (stockManager == null) {
      throw new IllegalArgumentException("Stock manager cannot be null");
    }
    this.stockManager = stockManager;
  }

  @Override
  /** OnSale operation. */
  public void onSale(SaleEvent event) {
    LocalDate date = event.getBill().getDate();
    for (BillItem item : event.getBill().getItems()) {
      stockManager.reduceShelfStock(item.getItemCode(), item.getQuantity(), date);
    }
  }
}
