package com.syos.domain.service;

import com.syos.domain.model.Money;

public interface DiscountCalculator {
  Money calculateDiscount(Money fullPrice);
}
