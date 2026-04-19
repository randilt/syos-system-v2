package com.syos.application.usecase;

import static org.junit.jupiter.api.Assertions.*;

import com.syos.domain.model.*;
import com.syos.domain.repository.*;
import com.syos.domain.service.DiscountCalculator;
import com.syos.domain.service.PercentageDiscountCalculator;
import com.syos.infrastructure.config.JdbcTransactionManager;
import com.syos.infrastructure.repository.jdbc.JdbcBillRepository;
import com.syos.infrastructure.repository.jdbc.JdbcItemRepository;
import com.syos.infrastructure.repository.jdbc.JdbcStockBatchRepository;
import com.syos.infrastructure.repository.jdbc.JdbcTransactionRepository;
import com.syos.infrastructure.repository.jdbc.JdbcUserRepository;
import com.syos.testutil.TestDatabaseSupport;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessOnlineSaleTest {
  private ItemRepository itemRepository;
  private BillRepository billRepository;
  private TransactionRepository transactionRepository;
  private StockBatchRepository onlineStockRepository;
  private UserRepository userRepository;
  private DiscountCalculator discountCalculator;
  private ProcessOnlineSale processOnlineSale;
  private LocalDate today;
  private User user;

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
    userRepository = new JdbcUserRepository(databaseManager);
    discountCalculator = new PercentageDiscountCalculator(5.0);
    processOnlineSale =
        new ProcessOnlineSale(
            itemRepository,
            billRepository,
            transactionRepository,
            onlineStockRepository,
            userRepository,
            discountCalculator,
            new JdbcTransactionManager(databaseManager));
    today = LocalDate.now();

    Item item = new Item(ItemCode.of("MILK001"), "Fresh Milk", Money.of(4.50));
    itemRepository.save(item);

    RegisterUser registerUser = new RegisterUser(userRepository);
    user = registerUser.execute("test_user", "test@example.com");

    AddStock addStock = new AddStock(onlineStockRepository);
    addStock.execute(ItemCode.of("MILK001"), today, today.plusDays(7), 50);
  }

  @Test
  void shouldProcessOnlineSaleSuccessfully() {
    Map<ItemCode, Integer> items = new HashMap<>();
    items.put(ItemCode.of("MILK001"), 3);

    Bill bill = processOnlineSale.execute(user.getUserId(), items, today);

    assertNotNull(bill);
    assertEquals(TransactionType.ONLINE, bill.getType());
    assertEquals(1, bill.getItems().size());
    assertEquals(3, bill.getItems().get(0).getQuantity());
    assertEquals(user.getUserId(), bill.getUserId());
  }

  @Test
  void shouldReduceOnlineStockAfterSale() {
    int initialStock = onlineStockRepository.getTotalStock(ItemCode.of("MILK001"));
    Map<ItemCode, Integer> items = new HashMap<>();
    items.put(ItemCode.of("MILK001"), 5);

    processOnlineSale.execute(user.getUserId(), items, today);

    int finalStock = onlineStockRepository.getTotalStock(ItemCode.of("MILK001"));
    assertEquals(initialStock - 5, finalStock);
  }

  @Test
  void shouldThrowExceptionForInvalidUser() {
    Map<ItemCode, Integer> items = new HashMap<>();
    items.put(ItemCode.of("MILK001"), 3);

    assertThrows(
        IllegalArgumentException.class,
        () -> processOnlineSale.execute("INVALID_USER", items, today));
  }

  @Test
  void shouldThrowExceptionForInsufficientOnlineStock() {
    Map<ItemCode, Integer> items = new HashMap<>();
    items.put(ItemCode.of("MILK001"), 1000);

    assertThrows(
        IllegalStateException.class,
        () -> processOnlineSale.execute(user.getUserId(), items, today));
  }
}
