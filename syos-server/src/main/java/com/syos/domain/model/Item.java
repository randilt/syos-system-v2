package com.syos.domain.model;

import java.util.Objects;

public class Item {
  private final ItemCode code;
  private final String name;
  private final Money unitPrice;

  public Item(ItemCode code, String name, Money unitPrice) {
    if (code == null) throw new IllegalArgumentException("Item code cannot be null");
    if (name == null || name.trim().isEmpty())
      throw new IllegalArgumentException("Item name cannot be null or empty");
    if (unitPrice == null) throw new IllegalArgumentException("Unit price cannot be null");

    this.code = code;
    this.name = name.trim();
    this.unitPrice = unitPrice;
  }

  public ItemCode getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public Money getUnitPrice() {
    return unitPrice;
  }

  public Money calculateTotal(int quantity) {
    return unitPrice.multiply(quantity);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Item item = (Item) o;
    return Objects.equals(code, item.code);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code);
  }
}
