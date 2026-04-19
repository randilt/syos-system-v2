package com.syos.domain.model;

import java.util.Objects;

/**
 * Value object wrapping a validated product code string.
 */
public final class ItemCode {
  private final String value;

  private ItemCode(String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("Item code cannot be null or empty");
    }
    this.value = value.trim().toUpperCase();
  }

  /** Of operation. */

  public static ItemCode of(String value) {
    return new ItemCode(value);
  }

  /** GetValue operation. */

  public String getValue() {
    return value;
  }

  @Override
  /** Equals operation. */
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ItemCode itemCode = (ItemCode) o;
    return Objects.equals(value, itemCode.value);
  }

  @Override
  /** HashCode operation. */
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  /** ToString operation. */
  public String toString() {
    return value;
  }
}
