package com.syos.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for all protocol-layer classes: {@link Request}, {@link Response}, {@link CommandType},
 * and every DTO ({@link BillDto}, {@link BillItemDto}, {@link ItemDto}, {@link ReportDto},
 * {@link StockBatchDto}).
 *
 * <p>Each DTO and message type must be {@link Serializable} — the tests verify round-trip
 * Java serialization to guarantee compatibility with the socket transport.
 */
class ProtocolTest {

  // ══════════════════════════════════════════════════════════════════════════
  //  CommandType
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void commandType_allValuesPresent() {
    // Enumerating expected values guards against accidental deletion
    CommandType[] values = CommandType.values();
    assertTrue(values.length >= 14, "Expected at least 14 CommandType values");
  }

  @Test
  void commandType_pingExists() {
    assertDoesNotThrow(() -> CommandType.valueOf("PING"));
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  Request factory methods — correct CommandType
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void ping_createsCorrectCommandType() {
    assertEquals(CommandType.PING, Request.ping().getCommandType());
  }

  @Test
  void inStoreSale_createsCorrectCommandType() {
    Request req = Request.inStoreSale(Map.of("A001", 1), 10.0, "2024-01-01");
    assertEquals(CommandType.PROCESS_IN_STORE_SALE, req.getCommandType());
  }

  @Test
  void inStoreSale_parametersAreStored() {
    Request req = Request.inStoreSale(Map.of("A001", 2), 20.0, "2024-06-15");
    assertEquals(20.0, (double) req.get("cashTendered"), 1e-9);
    assertEquals("2024-06-15", req.get("date"));
    assertNotNull(req.get("items"));
  }

  @Test
  void onlineSale_createsCorrectCommandType() {
    Request req = Request.onlineSale("u1", Map.of("A001", 1), "2024-01-01");
    assertEquals(CommandType.PROCESS_ONLINE_SALE, req.getCommandType());
  }

  @Test
  void onlineSale_parametersAreStored() {
    Request req = Request.onlineSale("user-42", Map.of("B001", 3), "2024-03-01");
    assertEquals("user-42", req.get("userId"));
    assertEquals("2024-03-01", req.get("date"));
  }

  @Test
  void registerUser_createsCorrectCommandType() {
    Request req = Request.registerUser("Alice", "alice@example.com");
    assertEquals(CommandType.REGISTER_USER, req.getCommandType());
    assertEquals("Alice", req.get("username"));
    assertEquals("alice@example.com", req.get("email"));
  }

  @Test
  void addStock_createsCorrectCommandType() {
    Request req = Request.addStock("A001", "2024-01-01", "2025-01-01", 100, "STORE");
    assertEquals(CommandType.ADD_STOCK, req.getCommandType());
    assertEquals("A001", req.get("itemCode"));
    assertEquals(100, req.get("quantity"));
    assertEquals("STORE", req.get("target"));
  }

  @Test
  void moveToShelf_createsCorrectCommandType() {
    Request req = Request.moveToShelf("A001", 50, "2024-06-01");
    assertEquals(CommandType.MOVE_TO_SHELF, req.getCommandType());
    assertEquals("A001", req.get("itemCode"));
    assertEquals(50, req.get("quantity"));
    assertEquals("2024-06-01", req.get("date"));
  }

  @Test
  void getAllItems_createsCorrectCommandType() {
    assertEquals(CommandType.GET_ALL_ITEMS, Request.getAllItems().getCommandType());
  }

  @Test
  void getDailySalesReport_createsCorrectCommandType() {
    Request req = Request.getDailySalesReport("2024-01-15", "IN_STORE");
    assertEquals(CommandType.GET_DAILY_SALES_REPORT, req.getCommandType());
    assertEquals("2024-01-15", req.get("date"));
    assertEquals("IN_STORE", req.get("type"));
  }

  @Test
  void getDailySalesReport_nullType_isStored() {
    Request req = Request.getDailySalesReport("2024-01-15", null);
    assertNull(req.get("type"));
  }

  @Test
  void getReshelvingReport_createsCorrectCommandType() {
    Request req = Request.getReshelvingReport("2024-01-15");
    assertEquals(CommandType.GET_RESHELVING_REPORT, req.getCommandType());
  }

  @Test
  void getReorderReport_createsCorrectCommandType() {
    assertEquals(CommandType.GET_REORDER_REPORT, Request.getReorderReport().getCommandType());
  }

  @Test
  void getBillReport_createsCorrectCommandType() {
    assertEquals(CommandType.GET_BILL_REPORT, Request.getBillReport().getCommandType());
  }

  @Test
  void getStockReport_storeType_mapsToStoreCommand() {
    Request req = Request.getStockReport("STORE", "2024-01-01");
    assertEquals(CommandType.GET_STOCK_REPORT, req.getCommandType());
  }

  @Test
  void getStockReport_shelfType_mapsToShelfCommand() {
    Request req = Request.getStockReport("SHELF", "2024-01-01");
    assertEquals(CommandType.GET_SHELF_STOCK_REPORT, req.getCommandType());
  }

  @Test
  void getStockReport_onlineType_mapsToOnlineCommand() {
    Request req = Request.getStockReport("ONLINE", "2024-01-01");
    assertEquals(CommandType.GET_ONLINE_STOCK_REPORT, req.getCommandType());
  }

  @Test
  void request_of_nullCommandType_throws() {
    assertThrows(IllegalArgumentException.class,
        () -> Request.of(null, null));
  }

  @Test
  void request_getParameters_returnsDefensiveCopy() {
    Request req = Request.inStoreSale(Map.of("A001", 1), 5.0, "2024-01-01");
    Map<String, Object> params = req.getParameters();
    params.put("injected", "value"); // mutate the copy
    assertNull(req.get("injected"), "Mutating returned map must not affect the Request");
  }

  @Test
  void request_toString_containsCommandType() {
    Request req = Request.ping();
    assertTrue(req.toString().contains("PING"));
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  Request — Java serialization round-trip
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void request_serializationRoundTrip_preservesCommandType() throws Exception {
    Request original = Request.inStoreSale(Map.of("APPLE001", 2), 10.0, "2024-05-01");
    Request deserialized = roundTrip(original);

    assertEquals(original.getCommandType(), deserialized.getCommandType());
    assertEquals(original.get("date"), deserialized.get("date"));
    assertEquals(((Number) original.get("cashTendered")).doubleValue(),
        ((Number) deserialized.get("cashTendered")).doubleValue(), 1e-9);
  }

  @Test
  void pingRequest_serializationRoundTrip() throws Exception {
    Request req = Request.ping();
    Request copy = roundTrip(req);
    assertEquals(CommandType.PING, copy.getCommandType());
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  Response
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void response_success_withPayload() {
    Response r = Response.success("PONG");
    assertTrue(r.isSuccess());
    assertEquals("PONG", r.getPayload());
    assertNull(r.getErrorMessage());
  }

  @Test
  void response_success_noPayload() {
    Response r = Response.success();
    assertTrue(r.isSuccess());
    assertNull(r.getPayload());
    assertNull(r.getErrorMessage());
  }

  @Test
  void response_error() {
    Response r = Response.error("Something went wrong");
    assertFalse(r.isSuccess());
    assertNull(r.getPayload());
    assertEquals("Something went wrong", r.getErrorMessage());
  }

  @Test
  void response_serializationRoundTrip_success() throws Exception {
    Response original = Response.success("hello");
    Response copy = roundTrip(original);
    assertTrue(copy.isSuccess());
    assertEquals("hello", copy.getPayload());
  }

  @Test
  void response_serializationRoundTrip_error() throws Exception {
    Response original = Response.error("oops");
    Response copy = roundTrip(original);
    assertFalse(copy.isSuccess());
    assertEquals("oops", copy.getErrorMessage());
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  BillItemDto
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void billItemDto_accessorsCorrect() {
    BillItemDto dto = new BillItemDto("A001", "Apple", 3, 1.50, 4.50);
    assertEquals("A001",   dto.getItemCode());
    assertEquals("Apple",  dto.getItemName());
    assertEquals(3,        dto.getQuantity());
    assertEquals(1.50,     dto.getUnitPrice(),  1e-9);
    assertEquals(4.50,     dto.getTotalPrice(), 1e-9);
  }

  @Test
  void billItemDto_serializationRoundTrip() throws Exception {
    BillItemDto original = new BillItemDto("B001", "Banana", 5, 0.99, 4.95);
    BillItemDto copy = roundTrip(original);
    assertEquals(original.getItemCode(),   copy.getItemCode());
    assertEquals(original.getItemName(),   copy.getItemName());
    assertEquals(original.getQuantity(),   copy.getQuantity());
    assertEquals(original.getUnitPrice(),  copy.getUnitPrice(),  1e-9);
    assertEquals(original.getTotalPrice(), copy.getTotalPrice(), 1e-9);
  }

  @Test
  void billItemDto_toString_containsItemCode() {
    BillItemDto dto = new BillItemDto("X001", "Mango", 1, 2.00, 2.00);
    assertTrue(dto.toString().contains("X001"));
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  BillDto
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void billDto_accessorsCorrect() {
    List<BillItemDto> items = List.of(new BillItemDto("A001", "Apple", 2, 1.50, 3.00));
    BillDto dto = new BillDto(1, "2024-01-01", "IN_STORE", null, items,
        3.00, 0.00, 3.00, 5.00, 2.00);

    assertEquals(1,          dto.getSerialNumber());
    assertEquals("2024-01-01", dto.getDate());
    assertEquals("IN_STORE", dto.getType());
    assertNull(dto.getUserId());
    assertEquals(1,          dto.getItems().size());
    assertEquals(3.00,       dto.getFullPrice(),    1e-9);
    assertEquals(0.00,       dto.getDiscount(),     1e-9);
    assertEquals(3.00,       dto.getFinalAmount(),  1e-9);
    assertEquals(5.00,       dto.getCashTendered(), 1e-9);
    assertEquals(2.00,       dto.getChange(),       1e-9);
  }

  @Test
  void billDto_nullItems_defaultsToEmptyList() {
    BillDto dto = new BillDto(2, "2024-01-01", "ONLINE", "u1", null,
        10.0, 0.0, 10.0, 0.0, 0.0);
    assertNotNull(dto.getItems());
    assertTrue(dto.getItems().isEmpty());
  }

  @Test
  void billDto_items_areImmutable() {
    List<BillItemDto> mutable = new java.util.ArrayList<>();
    mutable.add(new BillItemDto("A001", "Apple", 1, 1.0, 1.0));
    BillDto dto = new BillDto(3, "2024-01-01", "IN_STORE", null, mutable,
        1.0, 0.0, 1.0, 2.0, 1.0);
    assertThrows(UnsupportedOperationException.class, () -> dto.getItems().add(null));
  }

  @Test
  void billDto_serializationRoundTrip() throws Exception {
    List<BillItemDto> items = List.of(new BillItemDto("A001", "Apple", 1, 2.0, 2.0));
    BillDto original = new BillDto(10, "2024-07-04", "IN_STORE", null, items,
        2.0, 0.0, 2.0, 5.0, 3.0);
    BillDto copy = roundTrip(original);

    assertEquals(original.getSerialNumber(), copy.getSerialNumber());
    assertEquals(original.getDate(),         copy.getDate());
    assertEquals(original.getType(),         copy.getType());
    assertEquals(original.getFinalAmount(),  copy.getFinalAmount(), 1e-9);
    assertEquals(original.getChange(),       copy.getChange(),      1e-9);
    assertEquals(1, copy.getItems().size());
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  ItemDto
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void itemDto_accessorsCorrect() {
    ItemDto dto = new ItemDto("C001", "Carrot", 0.75);
    assertEquals("C001",   dto.getCode());
    assertEquals("Carrot", dto.getName());
    assertEquals(0.75,     dto.getUnitPrice(), 1e-9);
  }

  @Test
  void itemDto_serializationRoundTrip() throws Exception {
    ItemDto original = new ItemDto("D001", "Date", 3.99);
    ItemDto copy = roundTrip(original);
    assertEquals(original.getCode(),      copy.getCode());
    assertEquals(original.getName(),      copy.getName());
    assertEquals(original.getUnitPrice(), copy.getUnitPrice(), 1e-9);
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  StockBatchDto
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void stockBatchDto_accessorsCorrect() {
    StockBatchDto dto = new StockBatchDto(
        "BATCH-001", "E001", "Egg", "2024-01-01", "2024-07-01", 200, "STORE", false);

    assertEquals("BATCH-001", dto.getBatchId());
    assertEquals("E001",      dto.getItemCode());
    assertEquals("Egg",       dto.getItemName());
    assertEquals("2024-01-01", dto.getPurchaseDate());
    assertEquals("2024-07-01", dto.getExpiryDate());
    assertEquals(200,          dto.getQuantity());
    assertEquals("STORE",      dto.getStockType());
    assertFalse(dto.isExpired());
  }

  @Test
  void stockBatchDto_serializationRoundTrip() throws Exception {
    StockBatchDto original = new StockBatchDto(
        "BATCH-002", "F001", "Fig", "2023-01-01", "2023-06-01", 50, "ONLINE", true);
    StockBatchDto copy = roundTrip(original);

    assertEquals(original.getBatchId(),     copy.getBatchId());
    assertEquals(original.getItemCode(),    copy.getItemCode());
    assertEquals(original.getQuantity(),    copy.getQuantity());
    assertEquals(original.getStockType(),   copy.getStockType());
    assertEquals(original.isExpired(),      copy.isExpired());
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  ReportDto
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void reportDto_accessorsCorrect() {
    List<Map<String, Object>> data = List.of(Map.of("key", "value"));
    ReportDto dto = new ReportDto("My Report", data);

    assertEquals("My Report", dto.getTitle());
    assertEquals(1,           dto.getData().size());
    assertEquals(1,           dto.getTotalRecords());
  }

  @Test
  void reportDto_nullData_defaultsToEmpty() {
    ReportDto dto = new ReportDto("Empty", null);
    assertNotNull(dto.getData());
    assertTrue(dto.getData().isEmpty());
    assertEquals(0, dto.getTotalRecords());
  }

  @Test
  void reportDto_serializationRoundTrip() throws Exception {
    List<Map<String, Object>> data =
        List.of(Map.of("item", "Apple", "qty", 10));
    ReportDto original = new ReportDto("Stock Report", data);
    ReportDto copy = roundTrip(original);

    assertEquals(original.getTitle(),        copy.getTitle());
    assertEquals(original.getTotalRecords(), copy.getTotalRecords());
    assertEquals(1, copy.getData().size());
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  Helper
  // ══════════════════════════════════════════════════════════════════════════

  @SuppressWarnings("unchecked")
  private static <T extends Serializable> T roundTrip(T object) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject(object);
    }
    try (ObjectInputStream ois =
        new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
      return (T) ois.readObject();
    }
  }
}
