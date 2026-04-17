package com.syos.protocol;

import java.io.Serializable;

/**
 * Protocol-layer representation of a single line in a bill.
 *
 * <p>Carries no domain dependencies; all monetary values are plain {@code double} primitives.
 */
public final class BillItemDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String itemCode;
  private final String itemName;
  private final int    quantity;
  private final double unitPrice;
  private final double totalPrice;

  public BillItemDto(
      String itemCode,
      String itemName,
      int    quantity,
      double unitPrice,
      double totalPrice) {
    this.itemCode   = itemCode;
    this.itemName   = itemName;
    this.quantity   = quantity;
    this.unitPrice  = unitPrice;
    this.totalPrice = totalPrice;
  }

  /** The item code string (e.g. {@code "ITM001"}). */
  public String getItemCode() {
    return itemCode;
  }

  /** Display name of the item at the time of sale. */
  public String getItemName() {
    return itemName;
  }

  /** Number of units purchased. */
  public int getQuantity() {
    return quantity;
  }

  /** Price per unit at the time of sale. */
  public double getUnitPrice() {
    return unitPrice;
  }

  /** {@code unitPrice × quantity}. */
  public double getTotalPrice() {
    return totalPrice;
  }

  @Override
  public String toString() {
    return "BillItemDto{itemCode='" + itemCode
        + "', itemName='" + itemName
        + "', quantity=" + quantity
        + ", unitPrice=" + unitPrice
        + ", totalPrice=" + totalPrice + "}";
  }
}
