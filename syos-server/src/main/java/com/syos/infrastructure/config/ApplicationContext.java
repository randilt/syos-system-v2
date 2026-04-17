package com.syos.infrastructure.config;

import com.syos.application.transaction.TransactionManager;
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
import com.syos.infrastructure.event.StockUpdateListener;
import com.syos.infrastructure.repository.jdbc.JdbcBillRepository;
import com.syos.infrastructure.repository.jdbc.JdbcItemRepository;
import com.syos.infrastructure.repository.jdbc.JdbcStockBatchRepository;
import com.syos.infrastructure.repository.jdbc.JdbcTransactionRepository;
import com.syos.infrastructure.repository.jdbc.JdbcUserRepository;
import com.syos.infrastructure.service.HybridStockSelectionStrategy;
import com.syos.infrastructure.service.StockManager;
import com.syos.presentation.BillPrinter;
import com.syos.presentation.ReportPrinter;
import com.syos.presentation.StandardBillPrinter;
import com.syos.presentation.cli.CliApplication;
import com.syos.presentation.cli.CliState;
import com.syos.presentation.cli.Command;
import com.syos.presentation.cli.ExitCommand;
import com.syos.presentation.cli.InStoreSaleCommand;
import com.syos.presentation.cli.OnlineSaleCommand;
import com.syos.presentation.cli.RegisterUserCommand;
import com.syos.presentation.cli.ReportsCommand;
import com.syos.presentation.cli.StockManagementCommand;
import com.syos.presentation.decorator.SeasonalHeaderDecorator;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import org.flywaydb.core.Flyway;

public class ApplicationContext {
  private final CliApplication cliApplication;

  private ApplicationContext() {
    System.out.println("SYOS Billing and Stock Management System");
    System.out.println("========================================\n");

    LocalDate today = LocalDate.now();
    DatabaseManager databaseManager = DatabaseManager.getInstance();
    Flyway.configure()
        .dataSource(databaseManager.getDataSource())
        .locations("classpath:db/migration")
        .load()
        .migrate();

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

    StockSelectionStrategy stockStrategy = new HybridStockSelectionStrategy();
    StockManager stockManager =
        new StockManager(storeStockRepository, shelfStockRepository, stockStrategy);
    DiscountCalculator discountCalculator = new PercentageDiscountCalculator(5.0);
    TransactionManager transactionManager = new JdbcTransactionManager(databaseManager);
    ReshelvingCalculator reshelvingCalculator =
        new ReshelvingCalculator(billRepository, storeStockRepository);

    RegisterUser registerUser = new RegisterUser(userRepository);
    EventPublisher eventPublisher = new EventPublisher();
    eventPublisher.register(new StockUpdateListener(stockManager));

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

    BillPrinter billPrinter = new SeasonalHeaderDecorator(new StandardBillPrinter(System.out));
    ReportPrinter reportPrinter = new ReportPrinter();
    ReportFactory reportFactory =
        new ReportFactory(
            billRepository,
            itemRepository,
            storeStockRepository,
            shelfStockRepository,
            onlineStockRepository,
            reshelvingCalculator,
            50);
    Scanner scanner = new Scanner(System.in);
    CliState state = new CliState();

    Map<String, Command> commands = new LinkedHashMap<>();
    commands.put(
        "1",
        new InStoreSaleCommand(processInStoreSale, billPrinter, itemRepository, scanner, today));
    commands.put("2", new OnlineSaleCommand(processOnlineSale, billPrinter, scanner, today));
    commands.put(
        "3",
        new StockManagementCommand(
            stockManager, storeStockRepository, onlineStockRepository, scanner, today));
    commands.put("4", new ReportsCommand(reportPrinter, reportFactory, scanner, today));
    commands.put("5", new RegisterUserCommand(registerUser, scanner));
    commands.put("6", new ExitCommand(state));

    System.out.println("=== INITIALIZATION COMPLETE ===\n");

    this.cliApplication = new CliApplication(commands, scanner, state);
  }

  public static ApplicationContext initialize() {
    return new ApplicationContext();
  }

  public void start() {
    cliApplication.start();
  }
}
