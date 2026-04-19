package com.syos.application.usecase;

import static org.junit.jupiter.api.Assertions.*;

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

class ProcessInStoreSaleTest {
  private ItemRepository itemRepository;
  private BillRepository billRepository;
  private TransactionRepository transactionRepository;
  private StockBatchRepository storeStockRepository;
  private StockBatchRepository shelfStockRepository;
  private StockManager stockManager;
  private EventPublisher eventPublisher;
  private DiscountCalculator discountCalculator;
  private ProcessInStoreSale processInStoreSale;
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
    billRepository = new JdbcBillRepository(databaseManager);
    transactionRepository = new JdbcTransactionRepository(databaseManager);
    storeStockRepository = new JdbcStockBatchRepository(databaseManager, "STORE");
    shelfStockRepository = new JdbcStockBatchRepository(databaseManager, "SHELF");
    stockManager =
        new StockManager(
            storeStockRepository, shelfStockRepository, new HybridStockSelectionStrategy());
    eventPublisher = new EventPublisher();
    eventPublisher.register(new StockUpdateListener(stockManager));
    discountCalculator = new PercentageDiscountCalculator(5.0);
    processInStoreSale =
        new ProcessInStoreSale(
            itemRepository,
            billRepository,
            transactionRepository,
            storeStockRepository,
            discountCalculator,
            eventPublisher,
            new JdbcTransactionManager(databaseManager));
    today = LocalDate.now();

    Item item = new Item(ItemCode.of("APPLE001"), "Red Apples", Money.of(2.50));
    itemRepository.save(item);

    AddStock addStock = new AddStock(storeStockRepository);
    addStock.execute(ItemCode.of("APPLE001"), today, today.plusDays(7), 100);
    stockManager.moveToShelf(ItemCode.of("APPLE001"), 50, today);
  }

  @Test
  void shouldProcessInStoreSaleSuccessfully() {
    Map<ItemCode, Integer> items = new HashMap<>();
    items.put(ItemCode.of("APPLE001"), 5);

    Money cashTendered = Money.of(15.0);
    Bill bill = processInStoreSale.execute(items, cashTendered, today);

    assertNotNull(bill);
    assertEquals(TransactionType.IN_STORE, bill.getType());
    assertEquals(1, bill.getItems().size());
    assertEquals(5, bill.getItems().get(0).getQuantity());
    assertTrue(bill.getFullPrice().isGreaterThan(Money.zero()));
    assertTrue(bill.getDiscount().isGreaterThan(Money.zero()));
    assertEquals(cashTendered, bill.getCashTendered());
    assertFalse(bill.getChange().isLessThan(Money.zero()));
  }

  @Test
  void shouldReduceShelfStockAfterSale() {
    int initialStock = shelfStockRepository.getTotalStock(ItemCode.of("APPLE001"));
    Map<ItemCode, Integer> items = new HashMap<>();
    items.put(ItemCode.of("APPLE001"), 5);

    processInStoreSale.execute(items, Money.of(20.0), today);

    int finalStock = shelfStockRepository.getTotalStock(ItemCode.of("APPLE001"));
    assertEquals(initialStock - 5, finalStock);
  }

  @Test
  void shouldThrowExceptionForInsufficientCash() {
    Map<ItemCode, Integer> items = new HashMap<>();
    items.put(ItemCode.of("APPLE001"), 5);

    assertThrows(
        IllegalArgumentException.class,
        () -> processInStoreSale.execute(items, Money.of(1.0), today));
  }

  @Test
  void shouldThrowExceptionForItemNotFound() {
    Map<ItemCode, Integer> items = new HashMap<>();
    items.put(ItemCode.of("INVALID"), 5);

    assertThrows(
        IllegalArgumentException.class,
        () -> processInStoreSale.execute(items, Money.of(20.0), today));
  }

  @Test
  void shouldThrowExceptionForInsufficientStock() {
    Map<ItemCode, Integer> items = new HashMap<>();
    items.put(ItemCode.of("APPLE001"), 1000);

    assertThrows(
        IllegalStateException.class,
        () -> processInStoreSale.execute(items, Money.of(3000.0), today));
  }

  @Test
  void shouldThrowException_When_StoreStockInsufficient_BeforeSale() {
    Map<ItemCode, Integer> items = new HashMap<>();
    items.put(ItemCode.of("APPLE001"), 60);

    assertThrows(
        IllegalStateException.class,
        () -> processInStoreSale.execute(items, Money.of(200.0), today));
  }
}
