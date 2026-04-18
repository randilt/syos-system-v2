package com.syos.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.syos.application.usecase.AddStock;
import com.syos.application.usecase.ProcessInStoreSale;
import com.syos.application.usecase.ProcessOnlineSale;
import com.syos.application.usecase.RegisterUser;
import com.syos.application.usecase.report.Report;
import com.syos.domain.factory.ReportFactory;
import com.syos.domain.model.*;
import com.syos.domain.repository.ItemRepository;
import com.syos.infrastructure.service.StockManager;
import com.syos.protocol.*;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestRouterTest {

  @Mock ProcessInStoreSale processInStoreSale;
  @Mock ProcessOnlineSale  processOnlineSale;
  @Mock AddStock           storeAddStock;
  @Mock AddStock           onlineAddStock;
  @Mock StockManager       stockManager;
  @Mock RegisterUser       registerUser;
  @Mock ReportFactory      reportFactory;
  @Mock ItemRepository     itemRepository;

  RequestRouter router;

  @BeforeEach
  void setUp() {
    router = new RequestRouter(
        processInStoreSale, processOnlineSale,
        storeAddStock, onlineAddStock,
        stockManager, registerUser,
        reportFactory, itemRepository);
  }

  // ── PING ──────────────────────────────────────────────────────────────────

  @Test
  void ping_returnsPong() {
    Response response = router.route(Request.ping());
    assertTrue(response.isSuccess());
    assertEquals("PONG", response.getPayload());
  }

  // ── Null request ──────────────────────────────────────────────────────────

  @Test
  void nullRequest_returnsError() {
    Response response = router.route(null);
    assertFalse(response.isSuccess());
    assertNotNull(response.getErrorMessage());
  }

  // ── PROCESS_IN_STORE_SALE ─────────────────────────────────────────────────

  @Test
  void inStoreSale_successReturnsCorrectDto() throws Exception {
    Bill bill = buildInStoreBill();
    when(processInStoreSale.execute(anyMap(), any(Money.class), any(LocalDate.class)))
        .thenReturn(bill);

    Request req = Request.inStoreSale(Map.of("ITM001", 2), 100.0, "2024-01-15");
    Response response = router.route(req);

    assertTrue(response.isSuccess());
    assertInstanceOf(BillDto.class, response.getPayload());
    BillDto dto = (BillDto) response.getPayload();
    assertEquals(42, dto.getSerialNumber());
    assertEquals("IN_STORE", dto.getType());
  }

  @Test
  void inStoreSale_useCaseThrows_returnsError() throws Exception {
    when(processInStoreSale.execute(anyMap(), any(Money.class), any(LocalDate.class)))
        .thenThrow(new IllegalArgumentException("Insufficient stock"));

    Request req = Request.inStoreSale(Map.of("ITM001", 999), 1.0, "2024-01-15");
    Response response = router.route(req);

    assertFalse(response.isSuccess());
    assertTrue(response.getErrorMessage().contains("Insufficient stock"));
  }

  // ── PROCESS_ONLINE_SALE ───────────────────────────────────────────────────

  @Test
  void onlineSale_successReturnsCorrectDto() throws Exception {
    Bill bill = buildOnlineBill();
    when(processOnlineSale.execute(anyString(), anyMap(), any(LocalDate.class)))
        .thenReturn(bill);

    Request req = Request.onlineSale("user-1", Map.of("ITM001", 1), "2024-01-15");
    Response response = router.route(req);

    assertTrue(response.isSuccess());
    assertInstanceOf(BillDto.class, response.getPayload());
    BillDto dto = (BillDto) response.getPayload();
    assertEquals("ONLINE", dto.getType());
  }

  // ── ADD_STOCK ─────────────────────────────────────────────────────────────

  @Test
  void addStock_storeTarget_usesStoreAddStock() throws Exception {
    StockBatch batch = buildBatch();
    when(storeAddStock.execute(any(ItemCode.class), any(LocalDate.class),
        any(LocalDate.class), anyInt())).thenReturn(batch);

    Request req = Request.addStock("ITM001", "2024-01-01", "2025-01-01", 100, "STORE");
    Response response = router.route(req);

    assertTrue(response.isSuccess());
    assertInstanceOf(StockBatchDto.class, response.getPayload());
    verify(storeAddStock).execute(any(), any(), any(), anyInt());
    verify(onlineAddStock, never()).execute(any(), any(), any(), anyInt());
  }

  @Test
  void addStock_onlineTarget_usesOnlineAddStock() throws Exception {
    StockBatch batch = buildBatch();
    when(onlineAddStock.execute(any(ItemCode.class), any(LocalDate.class),
        any(LocalDate.class), anyInt())).thenReturn(batch);

    Request req = Request.addStock("ITM001", "2024-01-01", "2025-01-01", 100, "ONLINE");
    Response response = router.route(req);

    assertTrue(response.isSuccess());
    verify(onlineAddStock).execute(any(), any(), any(), anyInt());
    verify(storeAddStock, never()).execute(any(), any(), any(), anyInt());
  }

  // ── MOVE_TO_SHELF ─────────────────────────────────────────────────────────

  @Test
  void moveToShelf_success_returnsSuccessMessage() {
    doNothing().when(stockManager).moveToShelf(any(ItemCode.class), anyInt(), any(LocalDate.class));

    Request req = Request.moveToShelf("ITM001", 50, "2024-01-15");
    Response response = router.route(req);

    assertTrue(response.isSuccess());
    assertInstanceOf(String.class, response.getPayload());
  }

  @Test
  void moveToShelf_throws_returnsError() {
    doThrow(new IllegalArgumentException("Item not found"))
        .when(stockManager).moveToShelf(any(ItemCode.class), anyInt(), any(LocalDate.class));

    Request req = Request.moveToShelf("BADCODE", 50, "2024-01-15");
    Response response = router.route(req);

    assertFalse(response.isSuccess());
    assertTrue(response.getErrorMessage().contains("Item not found"));
  }

  // ── REGISTER_USER ─────────────────────────────────────────────────────────

  @Test
  void registerUser_success_returnsUserId() {
    User user = new User("user-99", "alice", "alice@example.com");
    when(registerUser.execute("alice", "alice@example.com")).thenReturn(user);

    Request req = Request.registerUser("alice", "alice@example.com");
    Response response = router.route(req);

    assertTrue(response.isSuccess());
    assertEquals("user-99", response.getPayload());
  }

  // ── GET_ALL_ITEMS ─────────────────────────────────────────────────────────

  @Test
  void getAllItems_returnsListOfItemDtos() {
    Item item = new Item(ItemCode.of("ITM001"), "Apple", Money.of(1.50));
    when(itemRepository.findAll()).thenReturn(List.of(item));

    Request req = Request.getAllItems();
    Response response = router.route(req);

    assertTrue(response.isSuccess());
    @SuppressWarnings("unchecked")
    List<ItemDto> dtos = (List<ItemDto>) response.getPayload();
    assertEquals(1, dtos.size());
    assertEquals("ITM001", dtos.get(0).getCode());
    assertEquals("Apple", dtos.get(0).getName());
  }

  @Test
  void getAllItems_emptyRepo_returnsEmptyList() {
    when(itemRepository.findAll()).thenReturn(Collections.emptyList());

    Response response = router.route(Request.getAllItems());

    assertTrue(response.isSuccess());
    @SuppressWarnings("unchecked")
    List<ItemDto> dtos = (List<ItemDto>) response.getPayload();
    assertTrue(dtos.isEmpty());
  }

  // ── GET_DAILY_SALES_REPORT ────────────────────────────────────────────────

  @Test
  void dailySalesReport_noType_usesKeyOne() {
    Report report = buildReport("Daily Sales");
    when(reportFactory.createReport(eq("1"), any(LocalDate.class))).thenReturn(report);

    Request req = Request.getDailySalesReport("2024-01-15", null);
    Response response = router.route(req);

    assertTrue(response.isSuccess());
    assertInstanceOf(ReportDto.class, response.getPayload());
    verify(reportFactory).createReport(eq("1"), any(LocalDate.class));
  }

  @Test
  void dailySalesReport_inStoreType_usesKeyTwo() {
    Report report = buildReport("In-Store Sales");
    when(reportFactory.createReport(eq("2"), any(LocalDate.class))).thenReturn(report);

    Request req = Request.getDailySalesReport("2024-01-15", "IN_STORE");
    Response response = router.route(req);

    assertTrue(response.isSuccess());
    verify(reportFactory).createReport(eq("2"), any(LocalDate.class));
  }

  @Test
  void dailySalesReport_onlineType_usesKeyThree() {
    Report report = buildReport("Online Sales");
    when(reportFactory.createReport(eq("3"), any(LocalDate.class))).thenReturn(report);

    Request req = Request.getDailySalesReport("2024-01-15", "ONLINE");
    Response response = router.route(req);

    assertTrue(response.isSuccess());
    verify(reportFactory).createReport(eq("3"), any(LocalDate.class));
  }

  // ── GET_RESHELVING_REPORT ─────────────────────────────────────────────────

  @Test
  void reshelvingReport_usesKeyFour() {
    Report report = buildReport("Reshelving");
    when(reportFactory.createReport(eq("4"), any(LocalDate.class))).thenReturn(report);

    Request req = Request.getReshelvingReport("2024-01-15");
    Response response = router.route(req);

    assertTrue(response.isSuccess());
    verify(reportFactory).createReport(eq("4"), any(LocalDate.class));
  }

  // ── GET_REORDER_REPORT ────────────────────────────────────────────────────

  @Test
  void reorderReport_usesKeyFive() {
    Report report = buildReport("Reorder");
    when(reportFactory.createReport(eq("5"), any(LocalDate.class))).thenReturn(report);

    Response response = router.route(Request.getReorderReport());

    assertTrue(response.isSuccess());
    verify(reportFactory).createReport(eq("5"), any(LocalDate.class));
  }

  // ── GET_BILL_REPORT ───────────────────────────────────────────────────────

  @Test
  void billReport_usesKeyNine() {
    Report report = buildReport("Bill Report");
    when(reportFactory.createReport(eq("9"), any(LocalDate.class))).thenReturn(report);

    Response response = router.route(Request.getBillReport());

    assertTrue(response.isSuccess());
    verify(reportFactory).createReport(eq("9"), any(LocalDate.class));
  }

  // ── Null report from factory ──────────────────────────────────────────────

  @Test
  void report_factoryReturnsNull_returnsError() {
    when(reportFactory.createReport(anyString(), any(LocalDate.class))).thenReturn(null);

    Response response = router.route(Request.getBillReport());

    assertFalse(response.isSuccess());
    assertNotNull(response.getErrorMessage());
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private Bill buildInStoreBill() {
    BillItem lineItem = new BillItem(
        ItemCode.of("ITM001"), "Apple", 2, Money.of(1.50), Money.of(3.00));
    return Bill.builder()
        .serialNumber(42)
        .date(LocalDate.of(2024, 1, 15))
        .type(TransactionType.IN_STORE)
        .addItem(lineItem)
        .fullPrice(Money.of(3.00))
        .discount(Money.zero())
        .cashTendered(Money.of(5.00))
        .change(Money.of(2.00))
        .build();
  }

  private Bill buildOnlineBill() {
    BillItem lineItem = new BillItem(
        ItemCode.of("ITM001"), "Apple", 1, Money.of(1.50), Money.of(1.50));
    return Bill.builder()
        .serialNumber(43)
        .date(LocalDate.of(2024, 1, 15))
        .type(TransactionType.ONLINE)
        .userId("user-1")
        .addItem(lineItem)
        .fullPrice(Money.of(1.50))
        .discount(Money.zero())
        .build();
  }

  private StockBatch buildBatch() {
    return new StockBatch(
        "BATCH-001",
        ItemCode.of("ITM001"),
        LocalDate.of(2024, 1, 1),
        LocalDate.of(2025, 1, 1),
        100);
  }

  private Report buildReport(String title) {
    Report report = mock(Report.class);
    when(report.getTitle()).thenReturn(title);
    when(report.getData()).thenReturn(Collections.emptyList());
    return report;
  }
}
