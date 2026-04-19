package com.syos.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Entity representing a quantity of stock with purchase/expiry dates.
 */
public class StockBatch {
  private final String batchId;
  private final ItemCode itemCode;
  private final LocalDate purchaseDate;
  private final LocalDate expiryDate;
  private int quantity;

  public StockBatch(
      String batchId,
      ItemCode itemCode,
      LocalDate purchaseDate,
      LocalDate expiryDate,
      int quantity) {
    if (batchId == null || batchId.trim().isEmpty())
      throw new IllegalArgumentException("Batch ID cannot be null or empty");
    if (itemCode == null) throw new IllegalArgumentException("Item code cannot be null");
    if (purchaseDate == null) throw new IllegalArgumentException("Purchase date cannot be null");
    if (expiryDate == null) throw new IllegalArgumentException("Expiry date cannot be null");
    if (quantity < 0) throw new IllegalArgumentException("Quantity cannot be negative");
    if (expiryDate.isBefore(purchaseDate))
      throw new IllegalArgumentException("Expiry date cannot be before purchase date");

    this.batchId = batchId;
    this.itemCode = itemCode;
    this.purchaseDate = purchaseDate;
    this.expiryDate = expiryDate;
    this.quantity = quantity;
  }

  /** GetBatchId operation. */

  public String getBatchId() {
    return batchId;
  }

  /** GetItemCode operation. */

  public ItemCode getItemCode() {
    return itemCode;
  }

  /** GetPurchaseDate operation. */

  public LocalDate getPurchaseDate() {
    return purchaseDate;
  }

  /** GetExpiryDate operation. */

  public LocalDate getExpiryDate() {
    return expiryDate;
  }

  /** GetQuantity operation. */

  public int getQuantity() {
    return quantity;
  }

  /** IsExpired operation. */

  public boolean isExpired(LocalDate date) {
    return expiryDate.isBefore(date);
  }

  /** ReduceQuantity operation. */

  public int reduceQuantity(int amount) {
    if (amount < 0) throw new IllegalArgumentException("Reduction amount cannot be negative");
    if (amount > quantity)
      throw new IllegalArgumentException("Cannot reduce more than available quantity");
    quantity -= amount;
    return quantity;
  }

  /** AddQuantity operation. */

  public int addQuantity(int amount) {
    if (amount < 0) throw new IllegalArgumentException("Amount to add cannot be negative");
    quantity += amount;
    return quantity;
  }

  /** HasQuantity operation. */

  public boolean hasQuantity() {
    return quantity > 0;
  }

  @Override
  /** Equals operation. */
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StockBatch that = (StockBatch) o;
    return Objects.equals(batchId, that.batchId);
  }

  @Override
  /** HashCode operation. */
  public int hashCode() {
    return Objects.hash(batchId);
  }
}
