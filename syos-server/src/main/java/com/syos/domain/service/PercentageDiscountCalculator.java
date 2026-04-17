package com.syos.domain.service;

import com.syos.domain.model.Money;

public class PercentageDiscountCalculator implements DiscountCalculator {
  private final double percentage;

  public PercentageDiscountCalculator(double percentage) {
    if (percentage < 0 || percentage > 100) {
      throw new IllegalArgumentException("Discount percentage must be between 0 and 100");
    }
    this.percentage = percentage;
  }

  @Override
  public Money calculateDiscount(Money fullPrice) {
    if (fullPrice == null) {
      throw new IllegalArgumentException("Full price cannot be null");
    }
    if (percentage == 0) {
      return Money.zero();
    }
    return fullPrice.multiply(percentage / 100.0);
  }
}
