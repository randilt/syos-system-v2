package com.syos.infrastructure.service;

import com.syos.domain.model.ItemCode;
import com.syos.domain.model.StockBatch;
import com.syos.domain.service.StockSelectionStrategy;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Strategy Pattern: selects stock batches using a hybrid purchase-date/expiry rule.
 *
 * <p>Business rule (Scenario Brief Point 2(b)): prioritize FIFO (oldest batch), unless a newer
 * batch has an expiry date strictly earlier than the oldest batch. In that case the nearer-expiring
 * batch is selected to reduce spoilage risk.
 */
public class HybridStockSelectionStrategy implements StockSelectionStrategy {
  @Override
  /**
   * Selects the batch to deduct from based on FIFO, with a strict override for earlier expiries.
   *
   * @param batches candidate batches for the item
   * @param itemCode the item to reduce stock for
   * @param quantity quantity requested
   * @param currentDate current business date
   * @return the batch to decrement
   */
  public StockBatch selectBatch(
      List<StockBatch> batches, ItemCode itemCode, int quantity, LocalDate currentDate) {
    if (batches == null || batches.isEmpty()) {
      throw new IllegalArgumentException("No batches available for item: " + itemCode);
    }
    if (quantity <= 0) {
      throw new IllegalArgumentException("Quantity must be positive");
    }

    List<StockBatch> availableBatches =
        batches.stream()
            .filter(batch -> batch.getItemCode().equals(itemCode))
            .filter(batch -> !batch.isExpired(currentDate))
            .filter(StockBatch::hasQuantity)
            .sorted(Comparator.comparing(StockBatch::getPurchaseDate))
            .collect(Collectors.toList());

    if (availableBatches.isEmpty()) {
      throw new IllegalStateException("No available stock for item: " + itemCode);
    }

    StockBatch oldestBatch = availableBatches.get(0);
    StockBatch earliestExpiryBatch =
        availableBatches.stream()
            .min(
                Comparator.comparing(StockBatch::getExpiryDate)
                    .thenComparing(StockBatch::getPurchaseDate))
            .orElse(oldestBatch);

    boolean expiryEarlierThanOldest =
        earliestExpiryBatch.getExpiryDate().isBefore(oldestBatch.getExpiryDate());

    StockBatch selectedBatch = expiryEarlierThanOldest ? earliestExpiryBatch : oldestBatch;
    if (selectedBatch.getQuantity() < quantity) {
      throw new IllegalStateException(
          "Insufficient stock in selected batch. Available: "
              + selectedBatch.getQuantity()
              + ", Required: "
              + quantity);
    }

    return selectedBatch;
  }
}
