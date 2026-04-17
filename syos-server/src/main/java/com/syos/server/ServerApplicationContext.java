package com.syos.server;

import com.syos.application.transaction.TransactionManager;
import com.syos.application.usecase.AddStock;
import com.syos.application.usecase.ProcessInStoreSale;
import com.syos.application.usecase.ProcessOnlineSale;
import com.syos.application.usecase.RegisterUser;
import com.syos.domain.event.EventPublisher;
import com.syos.domain.factory.ReportFactory;
import com.syos.domain.repository.BillRepository;
import com.syos.domain.repository.ItemRepository;
import com.syos.domain.repository.StockBatchRepository;
import com.syos.domain.repository.TransactionRepository;
import com.syos.domain.repository.UserRepository;
import com.syos.domain.service.DiscountCalculator;
import com.syos.domain.service.PercentageDiscountCalculator;
import com.syos.domain.service.ReshelvingCalculator;
import com.syos.domain.service.StockSelectionStrategy;
import com.syos.infrastructure.config.DatabaseManager;
import com.syos.infrastructure.config.JdbcTransactionManager;
import com.syos.infrastructure.event.StockUpdateListener;
import com.syos.infrastructure.repository.jdbc.JdbcBillRepository;
import com.syos.infrastructure.repository.jdbc.JdbcItemRepository;
import com.syos.infrastructure.repository.jdbc.JdbcStockBatchRepository;
import com.syos.infrastructure.repository.jdbc.JdbcTransactionRepository;
import com.syos.infrastructure.repository.jdbc.JdbcUserRepository;
import com.syos.infrastructure.service.HybridStockSelectionStrategy;
import com.syos.infrastructure.service.StockManager;
import java.util.logging.Logger;
import org.flywaydb.core.Flyway;

/**
 * Wires together all domain, infrastructure, and application objects required by the server.
 *
 * <p>Call {@link #initialize()} once on startup. The configured {@link RequestRouter} is then
 * available via {@link #getRequestRouter()}.
 */
public class ServerApplicationContext {

  private static final Logger LOGGER = Logger.getLogger(ServerApplicationContext.class.getName());

  private static final double DISCOUNT_PERCENTAGE = 5.0;
  private static final int REORDER_THRESHOLD = 50;

  private RequestRouter requestRouter;

  /**
   * Runs Flyway migrations and wires all components. Safe to call exactly once.
   */
  public void initialize() {
    LOGGER.info("Initializing server application context...");

    // ── Database ──────────────────────────────────────────────────────────
    DatabaseManager databaseManager = DatabaseManager.getInstance();

    Flyway.configure()
        .dataSource(databaseManager.getDataSource())
        .locations("classpath:db/migration")
        .load()
        .migrate();

    // ── Repositories ──────────────────────────────────────────────────────
    ItemRepository itemRepository = new JdbcItemRepository(databaseManager);
    BillRepository billRepository = new JdbcBillRepository(databaseManager);
    TransactionRepository transactionRepository = new JdbcTransactionRepository(databaseManager);
    UserRepository userRepository = new JdbcUserRepository(databaseManager);

    StockBatchRepository storeStockRepository =
        new JdbcStockBatchRepository(databaseManager, "STORE");
    StockBatchRepository shelfStockRepository =
        new JdbcStockBatchRepository(databaseManager, "SHELF");
    StockBatchRepository onlineStockRepository =
        new JdbcStockBatchRepository(databaseManager, "ONLINE");

    // ── Services ──────────────────────────────────────────────────────────
    StockSelectionStrategy stockStrategy = new HybridStockSelectionStrategy();
    StockManager stockManager =
        new StockManager(storeStockRepository, shelfStockRepository, stockStrategy);

    DiscountCalculator discountCalculator =
        new PercentageDiscountCalculator(DISCOUNT_PERCENTAGE);

    TransactionManager transactionManager = new JdbcTransactionManager(databaseManager);

    ReshelvingCalculator reshelvingCalculator =
        new ReshelvingCalculator(billRepository, storeStockRepository);

    // ── Events ────────────────────────────────────────────────────────────
    EventPublisher eventPublisher = new EventPublisher();
    eventPublisher.register(new StockUpdateListener(stockManager));

    // ── Use cases ─────────────────────────────────────────────────────────
    ProcessInStoreSale processInStoreSale =
        new ProcessInStoreSale(
            itemRepository,
            billRepository,
            transactionRepository,
            storeStockRepository,
            discountCalculator,
            eventPublisher,
            transactionManager);

    ProcessOnlineSale processOnlineSale =
        new ProcessOnlineSale(
            itemRepository,
            billRepository,
            transactionRepository,
            onlineStockRepository,
            userRepository,
            discountCalculator,
            transactionManager);

    AddStock storeAddStock = new AddStock(storeStockRepository);
    AddStock onlineAddStock = new AddStock(onlineStockRepository);
    RegisterUser registerUser = new RegisterUser(userRepository);

    // ── Reports ───────────────────────────────────────────────────────────
    ReportFactory reportFactory =
        new ReportFactory(
            billRepository,
            itemRepository,
            storeStockRepository,
            shelfStockRepository,
            onlineStockRepository,
            reshelvingCalculator,
            REORDER_THRESHOLD);

    // ── Router ────────────────────────────────────────────────────────────
    requestRouter =
        new RequestRouter(
            processInStoreSale,
            processOnlineSale,
            storeAddStock,
            onlineAddStock,
            stockManager,
            registerUser,
            reportFactory,
            itemRepository);

    LOGGER.info("Server application context initialized successfully");
  }

  public RequestRouter getRequestRouter() {
    if (requestRouter == null) {
      throw new IllegalStateException("Context not initialized — call initialize() first");
    }
    return requestRouter;
  }
}
