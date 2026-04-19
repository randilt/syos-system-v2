package com.syos.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Entity recording a stock movement linked to a bill.
 */
public class Transaction {
  private final String transactionId;
  private final int billSerialNumber;
  private final LocalDate date;
  private final TransactionType type;
  private final String userId;

  public Transaction(
      String transactionId,
      int billSerialNumber,
      LocalDate date,
      TransactionType type,
      String userId) {
    if (transactionId == null || transactionId.trim().isEmpty())
      throw new IllegalArgumentException("Transaction ID cannot be null or empty");
    if (date == null) throw new IllegalArgumentException("Date cannot be null");
    if (type == null) throw new IllegalArgumentException("Transaction type cannot be null");

    this.transactionId = transactionId;
    this.billSerialNumber = billSerialNumber;
    this.date = date;
    this.type = type;
    this.userId = userId;
  }

  /** GetTransactionId operation. */

  public String getTransactionId() {
    return transactionId;
  }

  /** GetBillSerialNumber operation. */

  public int getBillSerialNumber() {
    return billSerialNumber;
  }

  /** GetDate operation. */

  public LocalDate getDate() {
    return date;
  }

  /** GetType operation. */

  public TransactionType getType() {
    return type;
  }

  /** GetUserId operation. */

  public String getUserId() {
    return userId;
  }

  @Override
  /** Equals operation. */
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Transaction that = (Transaction) o;
    return Objects.equals(transactionId, that.transactionId);
  }

  @Override
  /** HashCode operation. */
  public int hashCode() {
    return Objects.hash(transactionId);
  }
}
