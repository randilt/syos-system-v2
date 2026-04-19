package com.syos.domain.service;

import com.syos.domain.model.Money;

/**
 * Strategy interface for computing bill discounts.
 */
public interface DiscountCalculator {
  Money calculateDiscount(Money fullPrice);
}
