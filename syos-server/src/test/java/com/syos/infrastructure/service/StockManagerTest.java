package com.syos.infrastructure.service;

import static org.junit.jupiter.api.Assertions.*;

import com.syos.application.usecase.AddStock;
import com.syos.domain.model.Item;
import com.syos.domain.model.ItemCode;
import com.syos.domain.model.Money;
import com.syos.domain.model.StockBatch;
import com.syos.domain.repository.ItemRepository;
import com.syos.domain.repository.StockBatchRepository;
import com.syos.infrastructure.repository.jdbc.JdbcItemRepository;
import com.syos.infrastructure.repository.jdbc.JdbcStockBatchRepository;
import com.syos.testutil.TestDatabaseSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StockManagerTest {
  private ItemRepository itemRepository;
  private StockBatchRepository storeStockRepository;
  private StockBatchRepository shelfStockRepository;
  private StockManager stockManager;
  private LocalDate today;

  @BeforeAll
  static void migrateDatabase() {
    TestDatabaseSupport.migrate();
  }

  @BeforeEach
  void setUp() {
    TestDatabaseSupport.resetDatabase();
    var databaseManager = TestDatabaseSupport.databaseManager();
    itemRepository = new JdbcItemRepository(databaseManager);
    storeStockRepository = new JdbcStockBatchRepository(databaseManager, "STORE");
    shelfStockRepository = new JdbcStockBatchRepository(databaseManager, "SHELF");
    stockManager =
        new StockManager(
            storeStockRepository, shelfStockRepository, new HybridStockSelectionStrategy());
    today = LocalDate.now();
    itemRepository.save(new Item(ItemCode.of("APPLE001"), "Red Apples", Money.of(2.50)));
  }

  @Test
  void shouldMoveStockFromStoreToShelf() {
    ItemCode itemCode = ItemCode.of("APPLE001");
    AddStock addStock = new AddStock(storeStockRepository);
    addStock.execute(itemCode, today, today.plusDays(7), 100);

    int initialStoreStock = storeStockRepository.getTotalStock(itemCode);
    int initialShelfStock = shelfStockRepository.getTotalStock(itemCode);

    stockManager.moveToShelf(itemCode, 50, today);

    assertEquals(initialStoreStock - 50, storeStockRepository.getTotalStock(itemCode));
    assertEquals(initialShelfStock + 50, shelfStockRepository.getTotalStock(itemCode));
  }

  @Test
  void shouldReduceShelfStock() {
    ItemCode itemCode = ItemCode.of("APPLE001");
    AddStock addStock = new AddStock(storeStockRepository);
    addStock.execute(itemCode, today, today.plusDays(7), 100);
    stockManager.moveToShelf(itemCode, 50, today);

    int initialShelfStock = shelfStockRepository.getTotalStock(itemCode);
    stockManager.reduceShelfStock(itemCode, 20, today);

    assertEquals(initialShelfStock - 20, shelfStockRepository.getTotalStock(itemCode));
  }

  @Test
  void shouldSelectOldestBatchFirst() {
    ItemCode itemCode = ItemCode.of("APPLE001");
    AddStock addStock = new AddStock(storeStockRepository);
    addStock.execute(itemCode, today.minusDays(5), today.plusDays(2), 30);
    addStock.execute(itemCode, today, today.plusDays(7), 50);

    stockManager.moveToShelf(itemCode, 30, today);

    assertEquals(30, shelfStockRepository.getTotalStock(itemCode));
    assertEquals(50, storeStockRepository.getTotalStock(itemCode));
  }

  @Test
  void shouldPrioritizeExpiringBatch() {
    ItemCode itemCode = ItemCode.of("APPLE001");
    AddStock addStock = new AddStock(storeStockRepository);
    addStock.execute(itemCode, today.minusDays(10), today.plusDays(30), 30);
    addStock.execute(itemCode, today.minusDays(1), today.plusDays(20), 20);

    stockManager.moveToShelf(itemCode, 20, today);

    var remainingBatches = storeStockRepository.findByItemCode(itemCode);
    int qtyLaterExpiry =
        remainingBatches.stream()
            .filter(batch -> batch.getExpiryDate().equals(today.plusDays(30)))
            .mapToInt(StockBatch::getQuantity)
            .sum();
    int qtyEarlierExpiry =
        remainingBatches.stream()
            .filter(batch -> batch.getExpiryDate().equals(today.plusDays(20)))
            .mapToInt(StockBatch::getQuantity)
            .sum();

    assertEquals(30, qtyLaterExpiry);
    assertEquals(0, qtyEarlierExpiry);
  }

  @Test
  void shouldThrowExceptionForInsufficientStoreStock() {
    ItemCode itemCode = ItemCode.of("APPLE001");
    AddStock addStock = new AddStock(storeStockRepository);
    addStock.execute(itemCode, today, today.plusDays(7), 30);

    assertThrows(IllegalStateException.class, () -> stockManager.moveToShelf(itemCode, 50, today));
  }
}
