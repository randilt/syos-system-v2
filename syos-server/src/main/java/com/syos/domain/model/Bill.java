package com.syos.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Bill {
  private final int serialNumber;
  private final LocalDate date;
  private final TransactionType type;
  private final List<BillItem> items;
  private final Money fullPrice;
  private final Money discount;
  private final Money cashTendered;
  private final Money change;
  private final String userId;

  private Bill(Builder builder) {
    this.serialNumber = builder.serialNumber;
    this.date = builder.date;
    this.type = builder.type;
    this.items = Collections.unmodifiableList(new ArrayList<>(builder.items));
    this.fullPrice = builder.fullPrice;
    this.discount = builder.discount;
    this.cashTendered = builder.cashTendered;
    this.change = builder.change;
    this.userId = builder.userId;
  }

  public int getSerialNumber() {
    return serialNumber;
  }

  public LocalDate getDate() {
    return date;
  }

  public TransactionType getType() {
    return type;
  }

  public List<BillItem> getItems() {
    return items;
  }

  public Money getFullPrice() {
    return fullPrice;
  }

  public Money getDiscount() {
    return discount;
  }

  public Money getCashTendered() {
    return cashTendered;
  }

  public Money getChange() {
    return change;
  }

  public String getUserId() {
    return userId;
  }

  public Money getFinalAmount() {
    return fullPrice.subtract(discount);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder Pattern: constructs immutable Bill instances with a fluent API. */
  public static class Builder {
    private int serialNumber;
    private LocalDate date;
    private TransactionType type;
    private List<BillItem> items = new ArrayList<>();
    private Money fullPrice = Money.zero();
    private Money discount = Money.zero();
    private Money cashTendered = Money.zero();
    private Money change = Money.zero();
    private String userId;

    public Builder serialNumber(int serialNumber) {
      this.serialNumber = serialNumber;
      return this;
    }

    public Builder date(LocalDate date) {
      this.date = date;
      return this;
    }

    public Builder type(TransactionType type) {
      this.type = type;
      return this;
    }

    public Builder addItem(BillItem item) {
      this.items.add(item);
      return this;
    }

    public Builder fullPrice(Money fullPrice) {
      this.fullPrice = fullPrice;
      return this;
    }

    public Builder discount(Money discount) {
      this.discount = discount;
      return this;
    }

    public Builder cashTendered(Money cashTendered) {
      this.cashTendered = cashTendered;
      return this;
    }

    public Builder change(Money change) {
      this.change = change;
      return this;
    }

    public Builder userId(String userId) {
      this.userId = userId;
      return this;
    }

    public Bill build() {
      if (date == null) throw new IllegalStateException("Date is required");
      if (type == null) throw new IllegalStateException("Transaction type is required");
      if (items.isEmpty()) throw new IllegalStateException("Bill must have at least one item");
      if (fullPrice == null) throw new IllegalStateException("Full price is required");
      if (discount == null) throw new IllegalStateException("Discount is required");
      if (type == TransactionType.IN_STORE) {
        if (cashTendered == null)
          throw new IllegalStateException("Cash tendered is required for in-store transactions");
        if (change == null)
          throw new IllegalStateException("Change is required for in-store transactions");
      }
      return new Bill(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Bill bill = (Bill) o;
    return serialNumber == bill.serialNumber;
  }

  @Override
  public int hashCode() {
    return Objects.hash(serialNumber);
  }
}
