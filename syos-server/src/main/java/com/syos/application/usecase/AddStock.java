package com.syos.application.usecase;

import com.syos.domain.model.ItemCode;
import com.syos.domain.model.StockBatch;
import com.syos.domain.repository.StockBatchRepository;
import java.time.LocalDate;

/**
 * Use case: records a new stock batch for an item in a stock repository (STORE or ONLINE).
 */
public class AddStock {
  private final StockBatchRepository repository;

  public AddStock(StockBatchRepository repository) {
    if (repository == null) throw new IllegalArgumentException("Repository cannot be null");
    this.repository = repository;
  }

  /** Executes the use case with the given inputs. */

  public StockBatch execute(
      ItemCode itemCode, LocalDate purchaseDate, LocalDate expiryDate, int quantity) {
    if (itemCode == null) throw new IllegalArgumentException("Item code cannot be null");
    if (purchaseDate == null) throw new IllegalArgumentException("Purchase date cannot be null");
    if (expiryDate == null) throw new IllegalArgumentException("Expiry date cannot be null");
    if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
    if (expiryDate.isBefore(purchaseDate)) {
      throw new IllegalArgumentException("Expiry date cannot be before purchase date");
    }

    String batchId = repository.nextBatchId();
    StockBatch batch = new StockBatch(batchId, itemCode, purchaseDate, expiryDate, quantity);
    repository.save(batch);
    return batch;
  }
}
