package com.syos.infrastructure.service;

import com.syos.domain.model.ItemCode;
import com.syos.domain.model.StockBatch;
import com.syos.domain.repository.StockBatchRepository;
import com.syos.domain.service.StockSelectionStrategy;
import java.time.LocalDate;
import java.util.List;

/**
 * Application service coordinating stock moves between STORE and SHELF batches.
 */
public class StockManager {
  private final StockBatchRepository storeStockRepository;
  private final StockBatchRepository shelfStockRepository;
  private final StockSelectionStrategy selectionStrategy;

  public StockManager(
      StockBatchRepository storeStockRepository,
      StockBatchRepository shelfStockRepository,
      StockSelectionStrategy selectionStrategy) {
    if (storeStockRepository == null)
      throw new IllegalArgumentException("Store stock repository cannot be null");
    if (shelfStockRepository == null)
      throw new IllegalArgumentException("Shelf stock repository cannot be null");
    if (selectionStrategy == null)
      throw new IllegalArgumentException("Selection strategy cannot be null");

    this.storeStockRepository = storeStockRepository;
    this.shelfStockRepository = shelfStockRepository;
    this.selectionStrategy = selectionStrategy;
  }

  /**
   * Moves quantity from store stock to shelf stock. Thread safety is delegated to the database
   * layer via FOR UPDATE row locking when called within a JdbcTransactionManager transaction.
   * The synchronized keyword is intentionally absent to prevent monitor-DB-lock inversion
   * deadlock, which would occur if a thread holds a DB transaction lock while waiting for a
   * synchronized method lock held by another thread that itself needs a DB lock.
   */
  public void moveToShelf(ItemCode itemCode, int quantity, LocalDate currentDate) {
    if (itemCode == null) throw new IllegalArgumentException("Item code cannot be null");
    if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");

    List<StockBatch> storeBatches = storeStockRepository.findByItemCode(itemCode);
    int remaining = quantity;

    while (remaining > 0 && !storeBatches.isEmpty()) {
      StockBatch selectedBatch =
          selectionStrategy.selectBatch(storeBatches, itemCode, remaining, currentDate);
      int toMove = Math.min(remaining, selectedBatch.getQuantity());

      selectedBatch.reduceQuantity(toMove);
      storeStockRepository.save(selectedBatch);
      if (!selectedBatch.hasQuantity()) {
        storeBatches.remove(selectedBatch);
      }

      StockBatch shelfBatch =
          findOrCreateShelfBatch(
              itemCode,
              selectedBatch.getPurchaseDate(),
              selectedBatch.getExpiryDate(),
              currentDate);
      shelfBatch.addQuantity(toMove);
      shelfStockRepository.save(shelfBatch);

      remaining -= toMove;
    }

    if (remaining > 0) {
      throw new IllegalStateException("Insufficient store stock for item: " + itemCode);
    }
  }

  /**
   * Reduces shelf stock for an item by the specified quantity. Thread safety is delegated to
   * the database layer via FOR UPDATE row locking when called within a JdbcTransactionManager
   * transaction. The synchronized keyword is intentionally absent to prevent monitor-DB-lock
   * inversion deadlock, which would occur if a thread holds a DB transaction lock while waiting
   * for a synchronized method lock held by another thread that itself needs a DB lock.
   */
  public void reduceShelfStock(
      ItemCode itemCode, int quantity, LocalDate currentDate) {
    if (itemCode == null) throw new IllegalArgumentException("Item code cannot be null");
    if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");

    List<StockBatch> shelfBatches = shelfStockRepository.findByItemCode(itemCode);
    int remaining = quantity;

    while (remaining > 0 && !shelfBatches.isEmpty()) {
      StockBatch selectedBatch =
          selectionStrategy.selectBatch(shelfBatches, itemCode, remaining, currentDate);
      int toReduce = Math.min(remaining, selectedBatch.getQuantity());

      selectedBatch.reduceQuantity(toReduce);
      shelfStockRepository.save(selectedBatch);
      if (!selectedBatch.hasQuantity()) {
        shelfBatches.remove(selectedBatch);
      }

      remaining -= toReduce;
    }

    if (remaining > 0) {
      throw new IllegalStateException("Insufficient shelf stock for item: " + itemCode);
    }
  }

  private StockBatch findOrCreateShelfBatch(
      ItemCode itemCode, LocalDate purchaseDate, LocalDate expiryDate, LocalDate currentDate) {
    List<StockBatch> shelfBatches = shelfStockRepository.findByItemCode(itemCode);

    return shelfBatches.stream()
        .filter(
            batch ->
                batch.getPurchaseDate().equals(purchaseDate)
                    && batch.getExpiryDate().equals(expiryDate))
        .findFirst()
        .orElseGet(
            () -> {
              StockBatch newBatch =
                  new StockBatch(
                      shelfStockRepository.nextBatchId(), itemCode, purchaseDate, expiryDate, 0);
              shelfStockRepository.save(newBatch);
              return newBatch;
            });
  }
}
