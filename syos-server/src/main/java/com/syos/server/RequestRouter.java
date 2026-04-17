package com.syos.server;

import com.syos.application.usecase.AddStock;
import com.syos.application.usecase.ProcessInStoreSale;
import com.syos.application.usecase.ProcessOnlineSale;
import com.syos.application.usecase.RegisterUser;
import com.syos.application.usecase.report.Report;
import com.syos.domain.factory.ReportFactory;
import com.syos.domain.model.Bill;
import com.syos.domain.model.BillItem;
import com.syos.domain.model.ItemCode;
import com.syos.domain.model.Money;
import com.syos.domain.model.StockBatch;
import com.syos.domain.model.User;
import com.syos.domain.repository.ItemRepository;
import com.syos.infrastructure.service.StockManager;
import com.syos.protocol.CommandType;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Routes incoming {@link Request} objects to the correct use-case or service method
 * and wraps the result in a {@link Response}.
 *
 * <p>All exceptions are caught here; callers always receive a well-formed response.
 */
public class RequestRouter {

  private static final Logger LOGGER = Logger.getLogger(RequestRouter.class.getName());

  private final ProcessInStoreSale processInStoreSale;
  private final ProcessOnlineSale processOnlineSale;
  private final AddStock storeAddStock;
  private final AddStock onlineAddStock;
  private final StockManager stockManager;
  private final RegisterUser registerUser;
  private final ReportFactory reportFactory;
  private final ItemRepository itemRepository;

  public RequestRouter(
      ProcessInStoreSale processInStoreSale,
      ProcessOnlineSale processOnlineSale,
      AddStock storeAddStock,
      AddStock onlineAddStock,
      StockManager stockManager,
      RegisterUser registerUser,
      ReportFactory reportFactory,
      ItemRepository itemRepository) {
    this.processInStoreSale = processInStoreSale;
    this.processOnlineSale = processOnlineSale;
    this.storeAddStock = storeAddStock;
    this.onlineAddStock = onlineAddStock;
    this.stockManager = stockManager;
    this.registerUser = registerUser;
    this.reportFactory = reportFactory;
    this.itemRepository = itemRepository;
  }

  /**
   * Dispatches the request to the appropriate handler.
   *
   * @param request the incoming client request
   * @return a response indicating success or failure
   */
  public Response route(Request request) {
    try {
      return switch (request.getCommandType()) {
        case PROCESS_IN_STORE_SALE -> handleInStoreSale(request);
        case PROCESS_ONLINE_SALE -> handleOnlineSale(request);
        case ADD_STORE_STOCK -> handleAddStock(request, storeAddStock);
        case ADD_ONLINE_STOCK -> handleAddStock(request, onlineAddStock);
        case MOVE_TO_SHELF -> handleMoveToShelf(request);
        case REGISTER_USER -> handleRegisterUser(request);
        case GENERATE_REPORT -> handleGenerateReport(request);
        case GET_ITEMS -> handleGetItems();
        case PING -> Response.ok(Map.of("message", "pong"));
      };
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Error routing " + request.getCommandType(), e);
      return Response.error(e.getMessage());
    }
  }

  // -------------------------------------------------------------------------
  // Private handlers
  // -------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private Response handleInStoreSale(Request request) {
    Map<String, Integer> rawItems = (Map<String, Integer>) request.get("items");
    Map<ItemCode, Integer> items = toItemCodeMap(rawItems);
    double cashRaw = ((Number) request.get("cashTendered")).doubleValue();
    LocalDate date = LocalDate.parse((String) request.get("date"));

    Bill bill = processInStoreSale.execute(items, Money.of(cashRaw), date);
    return Response.ok(billToMap(bill));
  }

  @SuppressWarnings("unchecked")
  private Response handleOnlineSale(Request request) {
    String userId = (String) request.get("userId");
    Map<String, Integer> rawItems = (Map<String, Integer>) request.get("items");
    Map<ItemCode, Integer> items = toItemCodeMap(rawItems);
    LocalDate date = LocalDate.parse((String) request.get("date"));

    Bill bill = processOnlineSale.execute(userId, items, date);
    return Response.ok(billToMap(bill));
  }

  private Response handleAddStock(Request request, AddStock addStock) {
    ItemCode itemCode = ItemCode.of((String) request.get("itemCode"));
    LocalDate purchaseDate = LocalDate.parse((String) request.get("purchaseDate"));
    LocalDate expiryDate = LocalDate.parse((String) request.get("expiryDate"));
    int quantity = ((Number) request.get("quantity")).intValue();

    StockBatch batch = addStock.execute(itemCode, purchaseDate, expiryDate, quantity);
    return Response.ok(Map.of(
        "batchId", batch.getBatchId(),
        "quantity", batch.getQuantity()
    ));
  }

  private Response handleMoveToShelf(Request request) {
    ItemCode itemCode = ItemCode.of((String) request.get("itemCode"));
    int quantity = ((Number) request.get("quantity")).intValue();
    LocalDate date = LocalDate.parse((String) request.get("date"));

    stockManager.moveToShelf(itemCode, quantity, date);
    return Response.ok(Map.of("message", "Shelf restocked successfully"));
  }

  private Response handleRegisterUser(Request request) {
    String username = (String) request.get("username");
    String email = (String) request.get("email");

    User user = registerUser.execute(username, email);
    return Response.ok(Map.of("userId", user.getUserId(), "username", user.getUsername()));
  }

  private Response handleGenerateReport(Request request) {
    String reportType = (String) request.get("reportType");
    LocalDate date = LocalDate.parse((String) request.get("date"));

    Report report = reportFactory.createReport(reportType, date);
    if (report == null) {
      return Response.error("Unknown report type: " + reportType);
    }
    report.generate();

    Map<String, Object> data = new HashMap<>();
    data.put("title", report.getTitle());
    data.put("rows", report.getData());
    return Response.ok(data);
  }

  private Response handleGetItems() {
    List<Map<String, Object>> items = new ArrayList<>();
    itemRepository.findAll().forEach(item -> {
      Map<String, Object> row = new HashMap<>();
      row.put("itemCode", item.getItemCode().getValue());
      row.put("name", item.getName());
      row.put("unitPrice", item.getUnitPrice().getAmount().doubleValue());
      items.add(row);
    });
    return Response.ok(Map.of("items", items));
  }

  // -------------------------------------------------------------------------
  // Utility helpers
  // -------------------------------------------------------------------------

  private Map<ItemCode, Integer> toItemCodeMap(Map<String, Integer> rawItems) {
    Map<ItemCode, Integer> result = new LinkedHashMap<>();
    rawItems.forEach((code, qty) -> result.put(ItemCode.of(code), qty));
    return result;
  }

  private Map<String, Object> billToMap(Bill bill) {
    Map<String, Object> data = new HashMap<>();
    data.put("serialNumber", bill.getSerialNumber());
    data.put("date", bill.getDate().toString());
    data.put("type", bill.getType().name());
    data.put("fullPrice", bill.getFullPrice().getAmount().doubleValue());
    data.put("discount", bill.getDiscount().getAmount().doubleValue());
    data.put("finalAmount", bill.getFinalAmount().getAmount().doubleValue());
    if (bill.getUserId() != null) {
      data.put("userId", bill.getUserId());
    }

    List<Map<String, Object>> itemsList = new ArrayList<>();
    for (BillItem item : bill.getItems()) {
      Map<String, Object> row = new HashMap<>();
      row.put("itemCode", item.getItemCode().getValue());
      row.put("itemName", item.getItemName());
      row.put("quantity", item.getQuantity());
      row.put("unitPrice", item.getUnitPrice().getAmount().doubleValue());
      row.put("totalPrice", item.getTotalPrice().getAmount().doubleValue());
      itemsList.add(row);
    }
    data.put("items", itemsList);
    return data;
  }
}
