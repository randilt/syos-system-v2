package com.syos.protocol;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Protocol-layer representation of a completed bill (receipt).
 *
 * <p>Carries no domain dependencies; dates are ISO-8601 strings ({@code YYYY-MM-DD}) and
 * monetary values are plain {@code double} primitives.
 */
public final class BillDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private final int              serialNumber;
  private final String           date;
  /** {@code "IN_STORE"} or {@code "ONLINE"}. */
  private final String           type;
  /** May be {@code null} for in-store sales with no associated user account. */
  private final String           userId;
  private final List<BillItemDto> items;
  private final double           fullPrice;
  private final double           discount;
  private final double           finalAmount;
  private final double           cashTendered;
  private final double           change;

  public BillDto(
      int              serialNumber,
      String           date,
      String           type,
      String           userId,
      List<BillItemDto> items,
      double           fullPrice,
      double           discount,
      double           finalAmount,
      double           cashTendered,
      double           change) {
    this.serialNumber = serialNumber;
    this.date         = date;
    this.type         = type;
    this.userId       = userId;
    this.items        = items == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(items));
    this.fullPrice    = fullPrice;
    this.discount     = discount;
    this.finalAmount  = finalAmount;
    this.cashTendered = cashTendered;
    this.change       = change;
  }

  /** Auto-generated bill serial number. */
  public int getSerialNumber() {
    return serialNumber;
  }

  /** Sale date as {@code YYYY-MM-DD}. */
  public String getDate() {
    return date;
  }

  /** Transaction type: {@code "IN_STORE"} or {@code "ONLINE"}. */
  public String getType() {
    return type;
  }

  /** Online customer user ID, or {@code null} for anonymous in-store sales. */
  public String getUserId() {
    return userId;
  }

  /** Immutable list of line items on this bill. */
  public List<BillItemDto> getItems() {
    return items;
  }

  /** Sum of all line-item total prices before discounts. */
  public double getFullPrice() {
    return fullPrice;
  }

  /** Total discount applied. */
  public double getDiscount() {
    return discount;
  }

  /** {@code fullPrice − discount}. */
  public double getFinalAmount() {
    return finalAmount;
  }

  /** Cash amount tendered by the customer. */
  public double getCashTendered() {
    return cashTendered;
  }

  /** Change returned to the customer: {@code cashTendered − finalAmount}. */
  public double getChange() {
    return change;
  }

  @Override
  public String toString() {
    return "BillDto{serialNumber=" + serialNumber
        + ", date='" + date
        + "', type='" + type
        + "', userId='" + userId
        + "', items=" + items.size()
        + ", finalAmount=" + finalAmount + "}";
  }
}
