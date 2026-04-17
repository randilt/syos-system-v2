package com.syos.domain.model;

import java.util.Objects;

public final class ItemCode {
  private final String value;

  private ItemCode(String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("Item code cannot be null or empty");
    }
    this.value = value.trim().toUpperCase();
  }

  public static ItemCode of(String value) {
    return new ItemCode(value);
  }

  public String getValue() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ItemCode itemCode = (ItemCode) o;
    return Objects.equals(value, itemCode.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
