package com.syos.protocol;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable, serializable command sent from a client to the server.
 *
 * <p>Use the static factory methods (e.g. {@link #inStoreSale}, {@link #onlineSale}) to
 * construct instances — they document exactly which parameters each command requires.
 * For commands without typed factories, use {@link #of(CommandType, Map)}.
 */
public final class Request implements Serializable {

  private static final long serialVersionUID = 1L;

  private final CommandType commandType;
  private final Map<String, Object> parameters;

  // ── Constructor ──────────────────────────────────────────────────────────

  private Request(CommandType commandType, Map<String, Object> parameters) {
    if (commandType == null) {
      throw new IllegalArgumentException("CommandType cannot be null");
    }
    this.commandType = commandType;
    this.parameters  = parameters == null
        ? Collections.emptyMap()
        : Collections.unmodifiableMap(new HashMap<>(parameters));
  }

  // ── Generic factory ──────────────────────────────────────────────────────

  /**
   * General-purpose factory for commands that do not have a typed convenience method.
   *
   * @param commandType the command to issue
   * @param parameters  parameter map (may be null for parameter-less commands)
   */
  public static Request of(CommandType commandType, Map<String, Object> parameters) {
    return new Request(commandType, parameters);
  }

  // ── Typed factories ──────────────────────────────────────────────────────

  /**
   * In-store (POS) sale request.
   *
   * @param itemsWithQuantities map of item-code string → quantity
   * @param cashTendered        cash amount provided by the customer
   * @param date                sale date in {@code YYYY-MM-DD} format
   */
  public static Request inStoreSale(
      Map<String, Integer> itemsWithQuantities, double cashTendered, String date) {
    Map<String, Object> p = new HashMap<>();
    p.put("items",        new HashMap<>(itemsWithQuantities));
    p.put("cashTendered", cashTendered);
    p.put("date",         date);
    return new Request(CommandType.PROCESS_IN_STORE_SALE, p);
  }

  /**
   * Online sale request.
   *
   * @param userId user ID of the registered customer
   * @param items  map of item-code string → quantity
   * @param date   sale date in {@code YYYY-MM-DD} format
   */
  public static Request onlineSale(String userId, Map<String, Integer> items, String date) {
    Map<String, Object> p = new HashMap<>();
    p.put("userId", userId);
    p.put("items",  new HashMap<>(items));
    p.put("date",   date);
    return new Request(CommandType.PROCESS_ONLINE_SALE, p);
  }

  /**
   * Register a new online customer.
   *
   * @param username display name
   * @param email    contact email
   */
  public static Request registerUser(String username, String email) {
    Map<String, Object> p = new HashMap<>();
    p.put("username", username);
    p.put("email",    email);
    return new Request(CommandType.REGISTER_USER, p);
  }

  /**
   * Receive new stock into either the STORE or ONLINE location.
   *
   * @param itemCode     item code string
   * @param purchaseDate purchase date in {@code YYYY-MM-DD} format
   * @param expiryDate   expiry date in {@code YYYY-MM-DD} format
   * @param quantity     units to add (must be positive)
   * @param target       {@code "STORE"} or {@code "ONLINE"}
   */
  public static Request addStock(
      String itemCode,
      String purchaseDate,
      String expiryDate,
      int    quantity,
      String target) {
    Map<String, Object> p = new HashMap<>();
    p.put("itemCode",     itemCode);
    p.put("purchaseDate", purchaseDate);
    p.put("expiryDate",   expiryDate);
    p.put("quantity",     quantity);
    p.put("target",       target);
    return new Request(CommandType.ADD_STOCK, p);
  }

  /**
   * Move stock from the STORE location to the SHELF.
   *
   * @param itemCode item code string
   * @param quantity units to move (must be positive)
   * @param date     transfer date in {@code YYYY-MM-DD} format
   */
  public static Request moveToShelf(String itemCode, int quantity, String date) {
    Map<String, Object> p = new HashMap<>();
    p.put("itemCode",  itemCode);
    p.put("quantity",  quantity);
    p.put("date",      date);
    return new Request(CommandType.MOVE_TO_SHELF, p);
  }

  /** Retrieve the full item catalogue. */
  public static Request getAllItems() {
    return new Request(CommandType.GET_ALL_ITEMS, null);
  }

  /**
   * Daily sales report.
   *
   * @param date sale date in {@code YYYY-MM-DD} format
   * @param type transaction type filter: {@code "IN_STORE"}, {@code "ONLINE"}, or {@code null}
   *             for all channels
   */
  public static Request getDailySalesReport(String date, String type) {
    Map<String, Object> p = new HashMap<>();
    p.put("date", date);
    p.put("type", type); // may be null
    return new Request(CommandType.GET_DAILY_SALES_REPORT, p);
  }

  /**
   * Reshelving recommendations.
   *
   * @param date reference date in {@code YYYY-MM-DD} format
   */
  public static Request getReshelvingReport(String date) {
    Map<String, Object> p = new HashMap<>();
    p.put("date", date);
    return new Request(CommandType.GET_RESHELVING_REPORT, p);
  }

  /** Reorder report (items below the reorder threshold). */
  public static Request getReorderReport() {
    return new Request(CommandType.GET_REORDER_REPORT, null);
  }

  /**
   * Stock level report.
   *
   * <p>Selects the appropriate command based on {@code stockType}:
   * <ul>
   *   <li>{@code "STORE"}  → {@link CommandType#GET_STOCK_REPORT}</li>
   *   <li>{@code "SHELF"}  → {@link CommandType#GET_SHELF_STOCK_REPORT}</li>
   *   <li>{@code "ONLINE"} → {@link CommandType#GET_ONLINE_STOCK_REPORT}</li>
   * </ul>
   *
   * @param stockType {@code "STORE"}, {@code "SHELF"}, or {@code "ONLINE"}
   * @param date      reference date in {@code YYYY-MM-DD} format
   */
  public static Request getStockReport(String stockType, String date) {
    CommandType cmd = switch (stockType.toUpperCase()) {
      case "SHELF"  -> CommandType.GET_SHELF_STOCK_REPORT;
      case "ONLINE" -> CommandType.GET_ONLINE_STOCK_REPORT;
      default       -> CommandType.GET_STOCK_REPORT;
    };
    Map<String, Object> p = new HashMap<>();
    p.put("stockType", stockType.toUpperCase());
    p.put("date",      date);
    return new Request(cmd, p);
  }

  /** Bill / receipt lookup report. */
  public static Request getBillReport() {
    return new Request(CommandType.GET_BILL_REPORT, null);
  }

  /** Health-check ping. */
  public static Request ping() {
    return new Request(CommandType.PING, null);
  }

  /** Subscribe this connection to receive server-push notifications. */
  public static Request subscribePush() {
    return new Request(CommandType.SUBSCRIBE_PUSH, null);
  }

  // ── Accessors ────────────────────────────────────────────────────────────

  public CommandType getCommandType() {
    return commandType;
  }

  /** Returns a defensive copy of the full parameters map. */
  public Map<String, Object> getParameters() {
    return new HashMap<>(parameters);
  }

  /**
   * Convenience accessor for a single parameter entry.
   *
   * @param key parameter key
   * @return the value, or {@code null} if absent
   */
  public Object get(String key) {
    return parameters.get(key);
  }

  @Override
  public String toString() {
    return "Request{commandType=" + commandType + ", parameters=" + parameters + "}";
  }
}
