package com.syos.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class Money {
  private final BigDecimal amount;

  private Money(BigDecimal amount) {
    if (amount == null) {
      throw new IllegalArgumentException("Amount cannot be null");
    }
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Amount cannot be negative");
    }
    this.amount = amount.setScale(2, RoundingMode.HALF_UP);
  }

  public static Money of(double value) {
    return new Money(BigDecimal.valueOf(value));
  }

  public static Money of(BigDecimal value) {
    return new Money(value);
  }

  public static Money zero() {
    return new Money(BigDecimal.ZERO);
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Money add(Money other) {
    return new Money(this.amount.add(other.amount));
  }

  public Money subtract(Money other) {
    return new Money(this.amount.subtract(other.amount));
  }

  public Money multiply(int quantity) {
    return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)));
  }

  public Money multiply(double multiplier) {
    return new Money(this.amount.multiply(BigDecimal.valueOf(multiplier)));
  }

  public boolean isGreaterThan(Money other) {
    return this.amount.compareTo(other.amount) > 0;
  }

  public boolean isLessThan(Money other) {
    return this.amount.compareTo(other.amount) < 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Money money = (Money) o;
    return Objects.equals(amount, money.amount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(amount);
  }

  @Override
  public String toString() {
    return String.format("%.2f", amount);
  }
}
