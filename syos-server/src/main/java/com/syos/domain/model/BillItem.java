package com.syos.domain.model;

import java.util.Objects;

/**
 * Value object for a single line on a {Bill}.
 */
public class BillItem {
  private final ItemCode itemCode;
  private final String itemName;
  private final int quantity;
  private final Money unitPrice;
  private final Money totalPrice;

  public BillItem(
      ItemCode itemCode, String itemName, int quantity, Money unitPrice, Money totalPrice) {
    if (itemCode == null) throw new IllegalArgumentException("Item code cannot be null");
    if (itemName == null || itemName.trim().isEmpty())
      throw new IllegalArgumentException("Item name cannot be null or empty");
    if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
    if (unitPrice == null) throw new IllegalArgumentException("Unit price cannot be null");
    if (totalPrice == null) throw new IllegalArgumentException("Total price cannot be null");

    this.itemCode = itemCode;
    this.itemName = itemName;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.totalPrice = totalPrice;
  }

  /** GetItemCode operation. */

  public ItemCode getItemCode() {
    return itemCode;
  }

  /** GetItemName operation. */

  public String getItemName() {
    return itemName;
  }

  /** GetQuantity operation. */

  public int getQuantity() {
    return quantity;
  }

  /** GetUnitPrice operation. */

  public Money getUnitPrice() {
    return unitPrice;
  }

  /** GetTotalPrice operation. */

  public Money getTotalPrice() {
    return totalPrice;
  }

  @Override
  /** Equals operation. */
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BillItem billItem = (BillItem) o;
    return quantity == billItem.quantity
        && Objects.equals(itemCode, billItem.itemCode)
        && Objects.equals(itemName, billItem.itemName)
        && Objects.equals(unitPrice, billItem.unitPrice)
        && Objects.equals(totalPrice, billItem.totalPrice);
  }

  @Override
  /** HashCode operation. */
  public int hashCode() {
    return Objects.hash(itemCode, itemName, quantity, unitPrice, totalPrice);
  }
}
