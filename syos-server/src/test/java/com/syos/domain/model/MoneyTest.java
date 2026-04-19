package com.syos.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {
  @Test
  void shouldCreateMoneyFromDouble() {
    Money money = Money.of(10.50);
    assertEquals(0, BigDecimal.valueOf(10.50).compareTo(money.getAmount()));
  }

  @Test
  void shouldCreateMoneyFromBigDecimal() {
    Money money = Money.of(BigDecimal.valueOf(25.75));
    assertEquals(BigDecimal.valueOf(25.75), money.getAmount());
  }

  @Test
  void shouldCreateZeroMoney() {
    Money zero = Money.zero();
    assertEquals(0, BigDecimal.ZERO.compareTo(zero.getAmount()));
  }

  @Test
  void shouldRejectNegativeAmount() {
    assertThrows(IllegalArgumentException.class, () -> Money.of(-10.0));
  }

  @Test
  void shouldRejectNullAmount() {
    assertThrows(IllegalArgumentException.class, () -> Money.of((BigDecimal) null));
  }

  @Test
  void shouldAddMoney() {
    Money m1 = Money.of(10.0);
    Money m2 = Money.of(5.5);
    Money result = m1.add(m2);
    assertEquals(0, BigDecimal.valueOf(15.50).compareTo(result.getAmount()));
  }

  @Test
  void shouldSubtractMoney() {
    Money m1 = Money.of(10.0);
    Money m2 = Money.of(3.5);
    Money result = m1.subtract(m2);
    assertEquals(0, BigDecimal.valueOf(6.50).compareTo(result.getAmount()));
  }

  @Test
  void shouldMultiplyByQuantity() {
    Money money = Money.of(2.5);
    Money result = money.multiply(3);
    assertEquals(0, BigDecimal.valueOf(7.50).compareTo(result.getAmount()));
  }

  @Test
  void shouldCompareMoney() {
    Money m1 = Money.of(10.0);
    Money m2 = Money.of(5.0);
    assertTrue(m1.isGreaterThan(m2));
    assertTrue(m2.isLessThan(m1));
  }
}
