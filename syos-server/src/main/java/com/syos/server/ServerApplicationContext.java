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
 * <h2>Singleton (double-checked locking)</h2>
 * <p>Use {@link #getInstance()} to obtain the shared instance. The constructor is private to
 * prevent external instantiation. {@link #initialize()} is idempotent: subsequent calls after
 * the first are no-ops.
 *
 * <h2>Dependency graph</h2>
 * <pre>
 *   DatabaseManager ──► JdbcRepositories ──► Use cases ──► RequestRouter
 *                  └──► Flyway migrations
 * </pre>
 */
public class ServerApplicationContext {

  private static final Logger LOGGER = Logger.getLogger(ServerApplicationContext.class.getName());

  private static final double DISCOUNT_PERCENTAGE = 5.0;
  private static final int    REORDER_THRESHOLD   = 50;

  // ── Singleton ──────────────────────────────────────────────────────────────────────────────────

  private static volatile ServerApplicationContext instance;

  /** Returns the shared singleton, creating it on first call. */
  public static ServerApplicationContext getInstance() {
    if (instance == null) {
      synchronized (ServerApplicationContext.class) {
        if (instance == null) {
          instance = new ServerApplicationContext();
        }
      }
    }
    return instance;
  }

  private ServerApplicationContext() {}

  // ── Wired components (populated by initialize()) ────────────────────────────────────────────────

  private volatile boolean initialized = false;

  // repositories
  private ItemRepository         itemRepository;
  private BillRepository         billRepository;
  private TransactionRepository  transactionRepository;
  private UserRepository         userRepository;
  private StockBatchRepository   storeStockRepository;
  private StockBatchRepository   shelfStockRepository;
  private StockBatchRepository   onlineStockRepository;

  // services
  private StockManager           stockManager;

  // use cases
  private ProcessInStoreSale     processInStoreSale;
  private ProcessOnlineSale      processOnlineSale;
  private AddStock               storeAddStock;
  private AddStock               onlineAddStock;
  private RegisterUser           registerUser;

  // factories / misc
  private ReportFactory          reportFactory;
  private RequestRouter          requestRouter;

  // ── Initialization ──────────────────────────────────────────────────────────────────────────────

  /**
   * Runs Flyway migrations and wires all components.
   *
   * <p>This method is <strong>idempotent</strong>: if called more than once the subsequent calls
   * return immediately without re-initializing anything.
   */
  public void initialize() {
    if (initialized) {
      return;
    }
    synchronized (this) {
      if (initialized) {
        return;
      }
      doInitialize();
      initialized = true;
    }
  }

  private void doInitialize() {
    LOGGER.info("Initializing server application context...");

    // ── Database ──────────────────────────────────────────────────────────
    DatabaseManager databaseManager = DatabaseManager.getInstance();

    Flyway.configure()
        .dataSource(databaseManager.getDataSource())
        .locations("classpath:db/migration")
        .load()
        .migrate();

    // ── Repositories ──────────────────────────────────────────────────────
    itemRepository        = new JdbcItemRepository(databaseManager);
    billRepository        = new JdbcBillRepository(databaseManager);
    transactionRepository = new JdbcTransactionRepository(databaseManager);
    userRepository        = new JdbcUserRepository(databaseManager);

    storeStockRepository  = new JdbcStockBatchRepository(databaseManager, "STORE");
    shelfStockRepository  = new JdbcStockBatchRepository(databaseManager, "SHELF");
    onlineStockRepository = new JdbcStockBatchRepository(databaseManager, "ONLINE");

    // ── Services ──────────────────────────────────────────────────────────
    StockSelectionStrategy stockStrategy = new HybridStockSelectionStrategy();
    stockManager = new StockManager(storeStockRepository, shelfStockRepository, stockStrategy);

    DiscountCalculator discountCalculator = new PercentageDiscountCalculator(DISCOUNT_PERCENTAGE);
    TransactionManager transactionManager = new JdbcTransactionManager(databaseManager);

    ReshelvingCalculator reshelvingCalculator =
        new ReshelvingCalculator(billRepository, storeStockRepository);

    // ── Events ────────────────────────────────────────────────────────────
    EventPublisher eventPublisher = new EventPublisher();
    eventPublisher.register(new StockUpdateListener(stockManager));

    // ── Use cases ─────────────────────────────────────────────────────────
    processInStoreSale = new ProcessInStoreSale(
        itemRepository, billRepository, transactionRepository, storeStockRepository,
        discountCalculator, eventPublisher, transactionManager);

    processOnlineSale = new ProcessOnlineSale(
        itemRepository, billRepository, transactionRepository, onlineStockRepository,
        userRepository, discountCalculator, transactionManager);

    storeAddStock  = new AddStock(storeStockRepository);
    onlineAddStock = new AddStock(onlineStockRepository);
    registerUser   = new RegisterUser(userRepository);

    // ── Reports ───────────────────────────────────────────────────────────
    reportFactory = new ReportFactory(
        billRepository, itemRepository,
        storeStockRepository, shelfStockRepository, onlineStockRepository,
        reshelvingCalculator, REORDER_THRESHOLD);

    // ── Router ────────────────────────────────────────────────────────────
    requestRouter = new RequestRouter(
        processInStoreSale, processOnlineSale,
        storeAddStock, onlineAddStock,
        stockManager, registerUser,
        reportFactory, itemRepository);

    LOGGER.info("Server application context initialized successfully");
  }

  // ── Public accessors ─────────────────────────────────────────────────────────────────────────────

  private void ensureInitialized() {
    if (!initialized) {
      throw new IllegalStateException("Context not initialized — call initialize() first");
    }
  }

  public RequestRouter getRequestRouter() {
    ensureInitialized();
    return requestRouter;
  }

  public ItemRepository getItemRepository() {
    ensureInitialized();
    return itemRepository;
  }

  public BillRepository getBillRepository() {
    ensureInitialized();
    return billRepository;
  }

  public UserRepository getUserRepository() {
    ensureInitialized();
    return userRepository;
  }

  public StockBatchRepository getStoreStockRepository() {
    ensureInitialized();
    return storeStockRepository;
  }

  public StockBatchRepository getShelfStockRepository() {
    ensureInitialized();
    return shelfStockRepository;
  }

  public StockBatchRepository getOnlineStockRepository() {
    ensureInitialized();
    return onlineStockRepository;
  }

  public StockManager getStockManager() {
    ensureInitialized();
    return stockManager;
  }

  public ProcessInStoreSale getProcessInStoreSale() {
    ensureInitialized();
    return processInStoreSale;
  }

  public ProcessOnlineSale getProcessOnlineSale() {
    ensureInitialized();
    return processOnlineSale;
  }

  public AddStock getStoreAddStock() {
    ensureInitialized();
    return storeAddStock;
  }

  public AddStock getOnlineAddStock() {
    ensureInitialized();
    return onlineAddStock;
  }

  public RegisterUser getRegisterUser() {
    ensureInitialized();
    return registerUser;
  }

  public ReportFactory getReportFactory() {
    ensureInitialized();
    return reportFactory;
  }

  public boolean isInitialized() {
    return initialized;
  }
}

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
