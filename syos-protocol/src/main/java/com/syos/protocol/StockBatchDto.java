package com.syos.protocol;

import java.io.Serializable;

/**
 * Protocol-layer representation of a stock batch record.
 *
 * <p>Carries no domain dependencies; dates are ISO-8601 strings ({@code YYYY-MM-DD}).
 * The {@link #stockType} field indicates which warehouse location the batch belongs to
 * ({@code "STORE"}, {@code "SHELF"}, or {@code "ONLINE"}).
 */
public final class StockBatchDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String  batchId;
  private final String  itemCode;
  private final String  itemName;
  private final String  purchaseDate;
  private final String  expiryDate;
  private final int     quantity;
  /**
   * Warehouse location: {@code "STORE"}, {@code "SHELF"}, or {@code "ONLINE"}.
   */
  private final String  stockType;
  private final boolean expired;

  public StockBatchDto(
      String  batchId,
      String  itemCode,
      String  itemName,
      String  purchaseDate,
      String  expiryDate,
      int     quantity,
      String  stockType,
      boolean expired) {
    this.batchId      = batchId;
    this.itemCode     = itemCode;
    this.itemName     = itemName;
    this.purchaseDate = purchaseDate;
    this.expiryDate   = expiryDate;
    this.quantity     = quantity;
    this.stockType    = stockType;
    this.expired      = expired;
  }

  /** Unique identifier for this batch. */
  public String getBatchId() {
    return batchId;
  }

  /** Item code string (e.g. {@code "ITM001"}). */
  public String getItemCode() {
    return itemCode;
  }

  /** Display name of the item at the time of receipt. */
  public String getItemName() {
    return itemName;
  }

  /** Date the batch was purchased / received, as {@code YYYY-MM-DD}. */
  public String getPurchaseDate() {
    return purchaseDate;
  }

  /** Expiry date of the batch, as {@code YYYY-MM-DD}. */
  public String getExpiryDate() {
    return expiryDate;
  }

  /** Current quantity remaining in this batch. */
  public int getQuantity() {
    return quantity;
  }

  /**
   * Warehouse location this batch is held in.
   * One of {@code "STORE"}, {@code "SHELF"}, or {@code "ONLINE"}.
   */
  public String getStockType() {
    return stockType;
  }

  /** {@code true} if the batch's expiry date has passed as of the report date. */
  public boolean isExpired() {
    return expired;
  }

  @Override
  public String toString() {
    return "StockBatchDto{batchId='" + batchId
        + "', itemCode='" + itemCode
        + "', stockType='" + stockType
        + "', quantity=" + quantity
        + ", expired=" + expired + "}";
  }
}
