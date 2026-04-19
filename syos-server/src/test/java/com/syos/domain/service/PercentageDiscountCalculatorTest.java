package com.syos.domain.service;

import static org.junit.jupiter.api.Assertions.*;

import com.syos.domain.model.Money;
import org.junit.jupiter.api.Test;

class PercentageDiscountCalculatorTest {
  @Test
  void shouldCalculatePercentageDiscount() {
    DiscountCalculator calculator = new PercentageDiscountCalculator(10.0);
    Money fullPrice = Money.of(100.0);
    Money discount = calculator.calculateDiscount(fullPrice);
    assertEquals(Money.of(10.0), discount);
  }

  @Test
  void shouldReturnZeroForZeroPercentage() {
    DiscountCalculator calculator = new PercentageDiscountCalculator(0.0);
    Money fullPrice = Money.of(100.0);
    Money discount = calculator.calculateDiscount(fullPrice);
    assertEquals(Money.zero(), discount);
  }

  @Test
  void shouldRejectInvalidPercentage() {
    assertThrows(IllegalArgumentException.class, () -> new PercentageDiscountCalculator(-1.0));
    assertThrows(IllegalArgumentException.class, () -> new PercentageDiscountCalculator(101.0));
  }

  @Test
  void shouldRejectNullFullPrice() {
    DiscountCalculator calculator = new PercentageDiscountCalculator(10.0);
    assertThrows(IllegalArgumentException.class, () -> calculator.calculateDiscount(null));
  }
}
