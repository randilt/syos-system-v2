package com.syos.protocol;

import java.io.Serializable;

/**
 * Protocol-layer representation of a catalogue item.
 *
 * <p>Carries no domain dependencies; all monetary values are represented as plain
 * {@code double} primitives for serialization simplicity.
 */
public final class ItemDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String code;
  private final String name;
  private final double unitPrice;

  public ItemDto(String code, String name, double unitPrice) {
    this.code      = code;
    this.name      = name;
    this.unitPrice = unitPrice;
  }

  /** The item code (e.g. {@code "ITM001"}). */
  public String getCode() {
    return code;
  }

  /** Display name of the item. */
  public String getName() {
    return name;
  }

  /** Unit price in the store's base currency. */
  public double getUnitPrice() {
    return unitPrice;
  }

  @Override
  public String toString() {
    return "ItemDto{code='" + code + "', name='" + name + "', unitPrice=" + unitPrice + "}";
  }
}
