package com.syos.domain.event;

import com.syos.domain.model.Bill;

/**
 * Domain event raised when a sale is completed; consumed by {StockUpdateListener}.
 */
public class SaleEvent {
  private final Bill bill;

  public SaleEvent(Bill bill) {
    if (bill == null) {
      throw new IllegalArgumentException("Bill cannot be null");
    }
    this.bill = bill;
  }

  /** GetBill operation. */

  public Bill getBill() {
    return bill;
  }
}
