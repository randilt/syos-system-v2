package com.syos.domain.event;

import com.syos.domain.model.Bill;

public class SaleEvent {
  private final Bill bill;

  public SaleEvent(Bill bill) {
    if (bill == null) {
      throw new IllegalArgumentException("Bill cannot be null");
    }
    this.bill = bill;
  }

  public Bill getBill() {
    return bill;
  }
}
