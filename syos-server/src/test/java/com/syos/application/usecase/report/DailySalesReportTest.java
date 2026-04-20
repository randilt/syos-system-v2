package com.syos.application.usecase.report;

import static org.junit.jupiter.api.Assertions.*;

import com.syos.application.usecase.AddStock;
import com.syos.application.usecase.ProcessInStoreSale;
import com.syos.domain.event.EventPublisher;
import com.syos.domain.model.*;
import com.syos.domain.repository.*;
import com.syos.domain.service.DiscountCalculator;
import com.syos.domain.service.PercentageDiscountCalculator;
import com.syos.infrastructure.config.JdbcTransactionManager;
import com.syos.infrastructure.event.StockUpdateListener;
import com.syos.infrastructure.repository.jdbc.JdbcBillRepository;
import com.syos.infrastructure.repository.jdbc.JdbcItemRepository;
import com.syos.infrastructure.repository.jdbc.JdbcStockBatchRepository;
import com.syos.infrastructure.repository.jdbc.JdbcTransactionRepository;
import com.syos.infrastructure.service.HybridStockSelectionStrategy;
import com.syos.infrastructure.service.StockManager;
import com.syos.testutil.TestDatabaseSupport;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DailySalesReportTest {
  private BillRepository billRepository;
  private ItemRepository itemRepository;
  private LocalDate today;

  @BeforeAll
  static void migrateDatabase() {
    TestDatabaseSupport.migrate();
  }

  @BeforeEach
  void setUp() {
    TestDatabaseSupport.resetDatabase();
    var databaseManager = TestDatabaseSupport.databaseManager();
    billRepository = new JdbcBillRepository(databaseManager);
    itemRepository = new JdbcItemRepository(databaseManager);
    today = LocalDate.now();

    itemRepository.save(new Item(ItemCode.of("APPLE001"), "Red Apples", Money.of(2.50)));
    itemRepository.save(new Item(ItemCode.of("BREAD001"), "White Bread", Money.of(3.00)));
  }

  @Test
  void shouldGenerateDailySalesReport() {
    createTestBills();

    DailySalesReport report = new DailySalesReport(billRepository, itemRepository, today, null);
    report.generate();

    assertEquals("Daily Sales Report - " + today + " (All)", report.getTitle());

    var appleRow =
        report.getData().stream()
            .filter(row -> "APPLE001".equals(row.get("itemCode")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected Apple row"));

    assertEquals(2, appleRow.get("totalQuantity"));
    assertEquals(Money.of(5.00).getAmount(), appleRow.get("totalRevenue"));
  }

  @Test
  void shouldFilterByTransactionType() {
    createTestBills();

    DailySalesReport inStoreReport =
        new DailySalesReport(billRepository, itemRepository, today, TransactionType.IN_STORE);
    inStoreReport.generate();

    var breadRow =
        inStoreReport.getData().stream()
            .filter(row -> "BREAD001".equals(row.get("itemCode")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected Bread row"));

    assertEquals(1, breadRow.get("totalQuantity"));
    assertEquals(Money.of(3.00).getAmount(), breadRow.get("totalRevenue"));
  }

  private void createTestBills() {
    var databaseManager = TestDatabaseSupport.databaseManager();
    StockBatchRepository storeStock = new JdbcStockBatchRepository(databaseManager, "STORE");
    StockBatchRepository shelfStock = new JdbcStockBatchRepository(databaseManager, "SHELF");
    StockManager stockManager =
        new StockManager(storeStock, shelfStock, new HybridStockSelectionStrategy());
    DiscountCalculator discountCalculator = new PercentageDiscountCalculator(5.0);
    TransactionRepository transactionRepository = new JdbcTransactionRepository(databaseManager);
    EventPublisher eventPublisher = new EventPublisher();
    eventPublisher.register(new StockUpdateListener(stockManager));

    AddStock addStock = new AddStock(storeStock);
    addStock.execute(ItemCode.of("APPLE001"), today, today.plusDays(7), 100);
    addStock.execute(ItemCode.of("BREAD001"), today, today.plusDays(7), 100);
    stockManager.moveToShelf(ItemCode.of("APPLE001"), 50, today);
    stockManager.moveToShelf(ItemCode.of("BREAD001"), 50, today);

    ProcessInStoreSale processSale =
        new ProcessInStoreSale(
            itemRepository,
            billRepository,
            transactionRepository,
            storeStock,
            discountCalculator,
            eventPublisher,
            new JdbcTransactionManager(databaseManager));

    Map<ItemCode, Integer> items = new HashMap<>();
    items.put(ItemCode.of("APPLE001"), 2);
    items.put(ItemCode.of("BREAD001"), 1);
    processSale.execute(items, Money.of(20.0), today);
  }
}
