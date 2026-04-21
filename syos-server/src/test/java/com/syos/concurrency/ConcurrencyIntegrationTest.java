package com.syos.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syos.application.usecase.AddStock;
import com.syos.application.usecase.ProcessInStoreSale;
import com.syos.application.usecase.ProcessOnlineSale;
import com.syos.application.usecase.RegisterUser;
import com.syos.domain.event.EventPublisher;
import com.syos.domain.model.Bill;
import com.syos.domain.model.Item;
import com.syos.domain.model.ItemCode;
import com.syos.domain.model.Money;
import com.syos.domain.model.TransactionType;
import com.syos.domain.model.User;
import com.syos.domain.repository.BillRepository;
import com.syos.domain.repository.ItemRepository;
import com.syos.domain.repository.StockBatchRepository;
import com.syos.domain.repository.TransactionRepository;
import com.syos.domain.repository.UserRepository;
import com.syos.domain.service.DiscountCalculator;
import com.syos.domain.service.PercentageDiscountCalculator;
import com.syos.infrastructure.config.JdbcTransactionManager;
import com.syos.infrastructure.event.StockUpdateListener;
import com.syos.infrastructure.repository.jdbc.JdbcBillRepository;
import com.syos.infrastructure.repository.jdbc.JdbcItemRepository;
import com.syos.infrastructure.repository.jdbc.JdbcStockBatchRepository;
import com.syos.infrastructure.repository.jdbc.JdbcTransactionRepository;
import com.syos.infrastructure.repository.jdbc.JdbcUserRepository;
import com.syos.infrastructure.service.HybridStockSelectionStrategy;
import com.syos.infrastructure.service.StockManager;
import com.syos.testutil.TestDatabaseSupport;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConcurrencyIntegrationTest {
  private ItemRepository itemRepository;
  private BillRepository billRepository;
  private TransactionRepository transactionRepository;
  private StockBatchRepository onlineStockRepository;
  private StockBatchRepository storeStockRepository;
  private StockBatchRepository shelfStockRepository;
  private UserRepository userRepository;
  private DiscountCalculator discountCalculator;
  private ProcessOnlineSale processOnlineSale;
  private ProcessInStoreSale processInStoreSale;
  private StockManager stockManager;
  private EventPublisher eventPublisher;
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
    onlineStockRepository = new JdbcStockBatchRepository(databaseManager, "ONLINE");
    storeStockRepository = new JdbcStockBatchRepository(databaseManager, "STORE");
    shelfStockRepository = new JdbcStockBatchRepository(databaseManager, "SHELF");
    userRepository = new JdbcUserRepository(databaseManager);
    discountCalculator = new PercentageDiscountCalculator(5.0);
    stockManager =
        new StockManager(
            storeStockRepository, shelfStockRepository, new HybridStockSelectionStrategy());
    eventPublisher = new EventPublisher();
    eventPublisher.register(new StockUpdateListener(stockManager));
    processOnlineSale =
        new ProcessOnlineSale(
            itemRepository,
            billRepository,
            transactionRepository,
            onlineStockRepository,
            userRepository,
            discountCalculator,
            new JdbcTransactionManager(databaseManager));
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
  }

  @Test
  void shouldThrowException_When_StockDepleted_Concurrently() throws InterruptedException {
    ItemCode itemCode = ItemCode.of("MILK001");
    itemRepository.save(new Item(itemCode, "Fresh Milk", Money.of(4.50)));
    User user = new RegisterUser(userRepository).execute("online_user", "online@example.com");

    AddStock addStock = new AddStock(onlineStockRepository);
    addStock.execute(itemCode, today, today.plusDays(7), 5);

    int threads = 50;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threads);
    AtomicInteger successCount = new AtomicInteger();
    AtomicInteger failureCount = new AtomicInteger();

    for (int i = 0; i < threads; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              Map<ItemCode, Integer> items = new HashMap<>();
              items.put(itemCode, 1);
              Bill bill = processOnlineSale.execute(user.getUserId(), items, today);
              assertEquals(TransactionType.ONLINE, bill.getType());
              successCount.incrementAndGet();
            } catch (Exception ex) {
              failureCount.incrementAndGet();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);

    assertEquals(5, successCount.get());
    assertEquals(45, failureCount.get());
    assertEquals(0, onlineStockRepository.getTotalStock(itemCode));
  }

  @Test
  void shouldKeepStockConsistent_When_MixedTrafficOccurs() throws InterruptedException {
    ItemCode itemCode = ItemCode.of("APPLE001");
    itemRepository.save(new Item(itemCode, "Red Apples", Money.of(2.50)));
    User user = new RegisterUser(userRepository).execute("mixed_user", "mixed@example.com");

    AddStock storeStock = new AddStock(storeStockRepository);
    storeStock.execute(itemCode, today, today.plusDays(7), 10);
    stockManager.moveToShelf(itemCode, 5, today);

    AddStock onlineStock = new AddStock(onlineStockRepository);
    onlineStock.execute(itemCode, today, today.plusDays(7), 5);

    int totalThreads = 20;
    ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(totalThreads);

    AtomicInteger inStoreSuccess = new AtomicInteger();
    AtomicInteger inStoreFailure = new AtomicInteger();
    AtomicInteger onlineSuccess = new AtomicInteger();
    AtomicInteger onlineFailure = new AtomicInteger();

    for (int i = 0; i < 10; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              Map<ItemCode, Integer> items = new HashMap<>();
              items.put(itemCode, 1);
              processInStoreSale.execute(items, Money.of(10.0), today);
              inStoreSuccess.incrementAndGet();
            } catch (Exception ex) {
              inStoreFailure.incrementAndGet();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    for (int i = 0; i < 10; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              Map<ItemCode, Integer> items = new HashMap<>();
              items.put(itemCode, 1);
              processOnlineSale.execute(user.getUserId(), items, today);
              onlineSuccess.incrementAndGet();
            } catch (Exception ex) {
              onlineFailure.incrementAndGet();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);

    assertEquals(5, inStoreSuccess.get());
    assertEquals(5, inStoreFailure.get());
    assertEquals(5, onlineSuccess.get());
    assertEquals(5, onlineFailure.get());
    assertEquals(0, shelfStockRepository.getTotalStock(itemCode));
    assertEquals(0, onlineStockRepository.getTotalStock(itemCode));
    assertEquals(5, storeStockRepository.getTotalStock(itemCode));
  }

  @Test
  void shouldNotOversellShelfStock_WhenConcurrentInStoreSales() throws InterruptedException {
    ItemCode itemCode = ItemCode.of("APPLE001");
    itemRepository.save(new Item(itemCode, "Red Apples", Money.of(2.50)));

    // Set up 20 units in STORE so all 20 threads pass the store availability check
    AddStock storeStock = new AddStock(storeStockRepository);
    storeStock.execute(itemCode, today, today.plusDays(7), 20);
    // Move 10 to SHELF so only 10 can be sold (first 10 threads succeed, next 10 fail)
    stockManager.moveToShelf(itemCode, 10, today);

    int threadCount = 20;
    Thread[] threads = new Thread[threadCount];
    int[] successCount = {0};
    int[] failureCount = {0};
    RuntimeException[] lastException = {null};

    Object barrier = new Object();
    int[] readyCount = {0};

    for (int i = 0; i < threadCount; i++) {
      threads[i] =
          new Thread(
              () -> {
                synchronized (barrier) {
                  readyCount[0]++;
                  while (readyCount[0] < threadCount) {
                    try {
                      barrier.wait();
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                      return;
                    }
                  }
                  barrier.notifyAll();
                }

                try {
                  Map<ItemCode, Integer> items = new HashMap<>();
                  items.put(itemCode, 1);
                  Bill bill = processInStoreSale.execute(items, Money.of(10.0), today);
                  assertEquals(TransactionType.IN_STORE, bill.getType());
                  synchronized (successCount) {
                    successCount[0]++;
                  }
                } catch (RuntimeException ex) {
                  synchronized (failureCount) {
                    failureCount[0]++;
                    lastException[0] = ex;
                  }
                }
              });
      threads[i].start();
    }

    for (Thread t : threads) {
      t.join();
    }

    assertEquals(10, successCount[0], "Expected 10 successful sales. Last exception: " + (lastException[0] != null ? lastException[0].getMessage() : "none"));
    assertEquals(10, failureCount[0]);
    assertEquals(0, shelfStockRepository.getTotalStock(itemCode));
    assertEquals(10, storeStockRepository.getTotalStock(itemCode));
  }
}
