package com.syos.server;

import com.syos.application.usecase.AddStock;
import com.syos.application.usecase.ProcessInStoreSale;
import com.syos.application.usecase.ProcessOnlineSale;
import com.syos.application.usecase.RegisterUser;
import com.syos.application.usecase.report.Report;
import com.syos.domain.factory.ReportFactory;
import com.syos.domain.model.Bill;
import com.syos.domain.model.BillItem;
import com.syos.domain.model.Item;
import com.syos.domain.model.ItemCode;
import com.syos.domain.model.Money;
import com.syos.domain.model.StockBatch;
import com.syos.domain.model.TransactionType;
import com.syos.domain.model.User;
import com.syos.domain.repository.ItemRepository;
import com.syos.infrastructure.service.StockManager;
import com.syos.protocol.BillDto;
import com.syos.protocol.BillItemDto;
import com.syos.protocol.ItemDto;
import com.syos.protocol.PushNotificationDto;
import com.syos.protocol.ReportDto;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import com.syos.protocol.StockBatchDto;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Routes incoming {@link Request} objects to the correct use-case or service method and wraps
 * the result in a {@link Response} carrying a typed DTO.
 *
 * <p>All exceptions thrown by use cases are caught here and converted to
 * {@link Response#error(String)} so that callers always receive a well-formed response object.
 *
 * <p>Domain objects are <strong>never</strong> sent over the wire; every success payload is a
 * protocol DTO ({@link BillDto}, {@link ItemDto}, etc.) or a primitive.
 */
public class RequestRouter {

  private static final Logger LOGGER = Logger.getLogger(RequestRouter.class.getName());

  private final ProcessInStoreSale processInStoreSale;
  private final ProcessOnlineSale  processOnlineSale;
  private final AddStock           storeAddStock;
  private final AddStock           onlineAddStock;
  private final StockManager       stockManager;
  private final RegisterUser       registerUser;
  private final ReportFactory      reportFactory;
  private final ItemRepository     itemRepository;

  public RequestRouter(
      ProcessInStoreSale processInStoreSale,
      ProcessOnlineSale  processOnlineSale,
      AddStock           storeAddStock,
      AddStock           onlineAddStock,
      StockManager       stockManager,
      RegisterUser       registerUser,
      ReportFactory      reportFactory,
      ItemRepository     itemRepository) {
    this.processInStoreSale = processInStoreSale;
    this.processOnlineSale  = processOnlineSale;
    this.storeAddStock      = storeAddStock;
    this.onlineAddStock     = onlineAddStock;
    this.stockManager       = stockManager;
    this.registerUser       = registerUser;
    this.reportFactory      = reportFactory;
    this.itemRepository     = itemRepository;
  }

  // ── Dispatch ─────────────────────────────────────────────────────────────

  /**
   * Dispatches the request to the appropriate handler.
   *
   * @param request the incoming client request
   * @return a response indicating success (with payload DTO) or failure (with error message)
   */
  public Response route(Request request) {
    if (request == null) {
      return Response.error("Null request received");
    }
    try {
      return switch (request.getCommandType()) {
        case PROCESS_IN_STORE_SALE   -> handleInStoreSale(request);
        case PROCESS_ONLINE_SALE     -> handleOnlineSale(request);
        case ADD_STOCK               -> handleAddStock(request);
        case MOVE_TO_SHELF           -> handleMoveToShelf(request);
        case REGISTER_USER           -> handleRegisterUser(request);
        case GET_ALL_ITEMS           -> handleGetAllItems();
        case GET_DAILY_SALES_REPORT  -> handleDailySalesReport(request);
        case GET_RESHELVING_REPORT   -> handleReshelvingReport(request);
        case GET_REORDER_REPORT      -> handleReorderReport();
        case GET_STOCK_REPORT        -> handleNamedReport("6", request); // STORE stock
        case GET_SHELF_STOCK_REPORT  -> handleNamedReport("7", request); // SHELF stock
        case GET_ONLINE_STOCK_REPORT -> handleNamedReport("8", request); // ONLINE stock
        case GET_BILL_REPORT         -> handleBillReport();
        case SUBSCRIBE_PUSH          -> Response.success("ACK");
        case PUSH_NOTIFICATION       -> Response.error("PUSH_NOTIFICATION cannot be sent by clients");
        case PING                    -> Response.success("PONG");
      };
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Error routing " + request.getCommandType(), e);
      String message = (e.getMessage() != null) ? e.getMessage() : e.getClass().getSimpleName();
      return Response.error(message);
    }
  }

  // ── Sale handlers ─────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private Response handleInStoreSale(Request request) {
    Map<String, Integer> rawItems  = (Map<String, Integer>) request.get("items");
    double               cashRaw   = ((Number) request.get("cashTendered")).doubleValue();
    LocalDate            date      = LocalDate.parse((String) request.get("date"));

    Bill bill = processInStoreSale.execute(toItemCodeMap(rawItems), Money.of(cashRaw), date);
    return Response.success(billToDto(bill));
  }

  @SuppressWarnings("unchecked")
  private Response handleOnlineSale(Request request) {
    String               userId   = (String) request.get("userId");
    Map<String, Integer> rawItems = (Map<String, Integer>) request.get("items");
    LocalDate            date     = LocalDate.parse((String) request.get("date"));

    Bill bill = processOnlineSale.execute(userId, toItemCodeMap(rawItems), date);
    return Response.success(billToDto(bill));
  }

  // ── Stock handlers ────────────────────────────────────────────────────────

  private Response handleAddStock(Request request) {
    ItemCode  itemCode     = ItemCode.of((String) request.get("itemCode"));
    LocalDate purchaseDate = LocalDate.parse((String) request.get("purchaseDate"));
    LocalDate expiryDate   = LocalDate.parse((String) request.get("expiryDate"));
    int       quantity     = ((Number) request.get("quantity")).intValue();
    String    target       = ((String) request.get("target")).toUpperCase();

    AddStock  useCase = "ONLINE".equals(target) ? onlineAddStock : storeAddStock;
    StockBatch batch  = useCase.execute(itemCode, purchaseDate, expiryDate, quantity);
    PushRegistry.getInstance().broadcast(
      new PushNotificationDto(
        "STOCK_UPDATED",
        "Stock received for " + itemCode.getValue() + " in " + target,
        System.currentTimeMillis()));
    return Response.success(batchToDto(batch, target));
  }

  private Response handleMoveToShelf(Request request) {
    ItemCode  itemCode = ItemCode.of((String) request.get("itemCode"));
    int       quantity = ((Number) request.get("quantity")).intValue();
    LocalDate date     = LocalDate.parse((String) request.get("date"));

    stockManager.moveToShelf(itemCode, quantity, date);
    PushRegistry.getInstance().broadcast(
      new PushNotificationDto(
        "STOCK_UPDATED",
        "Shelf restocked for " + itemCode.getValue() + " qty " + quantity,
        System.currentTimeMillis()));
    return Response.success("Shelf restocked successfully");
  }

  // ── User handler ──────────────────────────────────────────────────────────

  private Response handleRegisterUser(Request request) {
    String username = (String) request.get("username");
    String email    = (String) request.get("email");

    User user = registerUser.execute(username, email);
    return Response.success(user.getUserId()); // return the generated userId string
  }

  // ── Catalogue handler ─────────────────────────────────────────────────────

  private Response handleGetAllItems() {
    List<ItemDto> dtos = new ArrayList<>();
    for (Item item : itemRepository.findAll()) {
      dtos.add(itemToDto(item));
    }
    return Response.success(dtos);
  }

  // ── Report handlers ───────────────────────────────────────────────────────

  /**
   * Daily sales report.
   *
   * <p>Report factory keys: {@code "1"} = all, {@code "2"} = IN_STORE, {@code "3"} = ONLINE.
   */
  private Response handleDailySalesReport(Request request) {
    LocalDate date = LocalDate.parse((String) request.get("date"));
    String    type = (String) request.get("type"); // nullable

    String key = (type == null) ? "1" : switch (type.toUpperCase()) {
      case "IN_STORE" -> "2";
      case "ONLINE"   -> "3";
      default         -> "1";
    };
    return generateReport(reportFactory.createReport(key, date));
  }

  private Response handleReshelvingReport(Request request) {
    LocalDate date = LocalDate.parse((String) request.get("date"));
    return generateReport(reportFactory.createReport("4", date));
  }

  /** Reorder report — date is today (threshold-based, not date-specific). */
  private Response handleReorderReport() {
    return generateReport(reportFactory.createReport("5", LocalDate.now()));
  }

  /**
   * Generic stock report. Factory keys: {@code "6"} = STORE, {@code "7"} = SHELF,
   * {@code "8"} = ONLINE.
   */
  private Response handleNamedReport(String factoryKey, Request request) {
    LocalDate date = (request.get("date") != null)
        ? LocalDate.parse((String) request.get("date"))
        : LocalDate.now();
    return generateReport(reportFactory.createReport(factoryKey, date));
  }

  /** Bill report — factory key {@code "9"}, uses today as the reference date. */
  private Response handleBillReport() {
    return generateReport(reportFactory.createReport("9", LocalDate.now()));
  }

  private Response generateReport(Report report) {
    if (report == null) {
      return Response.error("Report could not be created — check the report type and parameters");
    }
    report.generate();
    return Response.success(new ReportDto(report.getTitle(), report.getData()));
  }

  // ── DTO converters ────────────────────────────────────────────────────────

  /**
   * Converts a domain {@link Bill} to a serializable {@link BillDto}.
   * For online bills, {@code cashTendered} and {@code change} are 0.0 (not applicable).
   */
  private BillDto billToDto(Bill bill) {
    List<BillItemDto> itemDtos = new ArrayList<>();
    for (BillItem item : bill.getItems()) {
      itemDtos.add(new BillItemDto(
          item.getItemCode().getValue(),
          item.getItemName(),
          item.getQuantity(),
          item.getUnitPrice().getAmount().doubleValue(),
          item.getTotalPrice().getAmount().doubleValue()));
    }

    double cashTendered = (bill.getType() == TransactionType.IN_STORE && bill.getCashTendered() != null)
        ? bill.getCashTendered().getAmount().doubleValue()
        : 0.0;
    double change = (bill.getType() == TransactionType.IN_STORE && bill.getChange() != null)
        ? bill.getChange().getAmount().doubleValue()
        : 0.0;

    return new BillDto(
        bill.getSerialNumber(),
        bill.getDate().toString(),
        bill.getType().name(),
        bill.getUserId(),
        itemDtos,
        bill.getFullPrice().getAmount().doubleValue(),
        bill.getDiscount().getAmount().doubleValue(),
        bill.getFinalAmount().getAmount().doubleValue(),
        cashTendered,
        change);
  }

  /** Converts a domain {@link Item} to a serializable {@link ItemDto}. */
  private ItemDto itemToDto(Item item) {
    return new ItemDto(
        item.getCode().getValue(),
        item.getName(),
        item.getUnitPrice().getAmount().doubleValue());
  }

  /**
   * Converts a domain {@link StockBatch} to a serializable {@link StockBatchDto}.
   *
   * @param batch     the batch to convert
   * @param stockType warehouse location string ({@code "STORE"}, {@code "SHELF"},
   *                  {@code "ONLINE"})
   */
  private StockBatchDto batchToDto(StockBatch batch, String stockType) {
    return new StockBatchDto(
        batch.getBatchId(),
        batch.getItemCode().getValue(),
        "", // item name not available at this point without an item look-up
        batch.getPurchaseDate().toString(),
        batch.getExpiryDate().toString(),
        batch.getQuantity(),
        stockType,
        batch.isExpired(LocalDate.now()));
  }

  // ── Utility helpers ───────────────────────────────────────────────────────

  private Map<ItemCode, Integer> toItemCodeMap(Map<String, Integer> raw) {
    Map<ItemCode, Integer> result = new LinkedHashMap<>();
    raw.forEach((code, qty) -> result.put(ItemCode.of(code), qty));
    return result;
  }
}

