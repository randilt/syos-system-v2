package com.syos.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value object for non-negative monetary amounts using {BigDecimal}.
 */
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

  /** Of operation. */

  public static Money of(double value) {
    return new Money(BigDecimal.valueOf(value));
  }

  /** Of operation. */

  public static Money of(BigDecimal value) {
    return new Money(value);
  }

  /** Zero operation. */

  public static Money zero() {
    return new Money(BigDecimal.ZERO);
  }

  /** Returns the monetary amount. */

  public BigDecimal getAmount() {
    return amount;
  }

  /** Add operation. */

  public Money add(Money other) {
    return new Money(this.amount.add(other.amount));
  }

  /** Subtract operation. */

  public Money subtract(Money other) {
    return new Money(this.amount.subtract(other.amount));
  }

  /** Multiply operation. */

  public Money multiply(int quantity) {
    return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)));
  }

  /** Multiply operation. */

  public Money multiply(double multiplier) {
    return new Money(this.amount.multiply(BigDecimal.valueOf(multiplier)));
  }

  /** IsGreaterThan operation. */

  public boolean isGreaterThan(Money other) {
    return this.amount.compareTo(other.amount) > 0;
  }

  /** IsLessThan operation. */

  public boolean isLessThan(Money other) {
    return this.amount.compareTo(other.amount) < 0;
  }

  @Override
  /** Equals operation. */
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Money money = (Money) o;
    return Objects.equals(amount, money.amount);
  }

  @Override
  /** HashCode operation. */
  public int hashCode() {
    return Objects.hash(amount);
  }

  @Override
  /** ToString operation. */
  public String toString() {
    return String.format("%.2f", amount);
  }
}
