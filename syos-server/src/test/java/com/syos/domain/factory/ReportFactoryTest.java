package com.syos.domain.factory;

import static org.junit.jupiter.api.Assertions.*;

import com.syos.application.usecase.report.Report;
import com.syos.domain.repository.BillRepository;
import com.syos.domain.repository.ItemRepository;
import com.syos.domain.repository.StockBatchRepository;
import com.syos.domain.service.ReshelvingCalculator;
import com.syos.infrastructure.repository.jdbc.JdbcBillRepository;
import com.syos.infrastructure.repository.jdbc.JdbcItemRepository;
import com.syos.infrastructure.repository.jdbc.JdbcStockBatchRepository;
import com.syos.testutil.TestDatabaseSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportFactoryTest {

  private ReportFactory factory;
  private LocalDate today;

  @BeforeAll
  static void migrateDatabase() {
    TestDatabaseSupport.migrate();
  }

  @BeforeEach
  void setUp() {
    TestDatabaseSupport.resetDatabase();
    var db = TestDatabaseSupport.databaseManager();
    BillRepository billRepository = new JdbcBillRepository(db);
    ItemRepository itemRepository = new JdbcItemRepository(db);
    StockBatchRepository store = new JdbcStockBatchRepository(db, "STORE");
    StockBatchRepository shelf = new JdbcStockBatchRepository(db, "SHELF");
    StockBatchRepository online = new JdbcStockBatchRepository(db, "ONLINE");
    ReshelvingCalculator reshelving =
        new ReshelvingCalculator(billRepository, store);
    factory =
        new ReportFactory(
            billRepository, itemRepository, store, shelf, online, reshelving, 50);
    today = LocalDate.now();
  }

  @Test
  void createReport_nullArgs_throws() {
    assertThrows(IllegalArgumentException.class, () -> factory.createReport(null, today));
    assertThrows(IllegalArgumentException.class, () -> factory.createReport("1", null));
  }

  @Test
  void createReport_unknownType_returnsNull() {
    assertNull(factory.createReport("99", today));
  }

  @Test
  void createReport_allTypes_generateSuccessfully() {
    for (String key : new String[] {"1", "2", "3", "4", "5", "6", "7", "8", "9"}) {
      Report report = factory.createReport(key, today);
      assertNotNull(report, "Report type " + key);
      assertNotNull(report.getTitle());
      assertDoesNotThrow(report::generate);
    }
  }

  @Test
  void constructor_nullRepositories_throws() {
    var db = TestDatabaseSupport.databaseManager();
    BillRepository bills = new JdbcBillRepository(db);
    ItemRepository items = new JdbcItemRepository(db);
    StockBatchRepository store = new JdbcStockBatchRepository(db, "STORE");
    StockBatchRepository shelf = new JdbcStockBatchRepository(db, "SHELF");
    StockBatchRepository online = new JdbcStockBatchRepository(db, "ONLINE");
    ReshelvingCalculator reshelving = new ReshelvingCalculator(bills, store);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReportFactory(
                null, items, store, shelf, online, reshelving, 50));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReportFactory(
                bills, items, null, shelf, online, reshelving, 50));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReportFactory(
                bills, items, store, shelf, online, null, 50));
  }
}
