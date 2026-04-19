package com.syos.testclient;

import com.syos.network.ServerConnection;
import com.syos.protocol.BillDto;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Concurrent load-test client for {@code PROCESS_ONLINE_SALE}.
 *
 * <p>Before firing concurrent sales this client <em>automatically registers a fresh test user</em>
 * via {@code REGISTER_USER} so the test is self-contained — no pre-existing data is required.
 *
 * <p>Then N {@link Thread} objects are created; each opens its own TCP connection and fires one
 * online-sale request for the registered user simultaneously.  The manual barrier in
 * {@link BaseTestClient#arrive()} guarantees all threads start within the same OS scheduling
 * quantum.
 *
 * <h2>Threading model</h2>
 * <ul>
 *   <li>NO {@code java.util.concurrent} — all synchronization uses raw {@code synchronized},
 *       {@code wait()}, and {@code notifyAll()}.</li>
 *   <li>Each thread owns its {@link ServerConnection}.</li>
 *   <li>Every exception is caught, logged, and counted as a failure.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   java -cp ... com.syos.testclient.ConcurrentOnlineSaleClient \
 *       [host] [port] [threads] [itemCode] [quantity]
 * </pre>
 */
public class ConcurrentOnlineSaleClient extends BaseTestClient {

  private static final String DEFAULT_HOST      = "localhost";
  private static final int    DEFAULT_PORT      = 9090;
  private static final int    DEFAULT_THREADS   = 20;
  private static final String DEFAULT_ITEM_CODE = "MILK001";
  private static final int    DEFAULT_QTY       = 1;

  private final int    numThreads;
  private final String itemCode;
  private final int    qty;

  // ── Constructor ──────────────────────────────────────────────────────────

  public ConcurrentOnlineSaleClient(
      String host, int port, int numThreads, String itemCode, int qty) {
    super(host, port);
    this.numThreads = numThreads;
    this.itemCode   = itemCode;
    this.qty        = qty;
  }

  // ── run() ────────────────────────────────────────────────────────────────

  @Override
  public void run() throws Exception {
    totalThreads = numThreads;
    resetBarrier();

    String today = LocalDate.now().toString();

    System.out.println();
    System.out.println("╔══════════════════════════════════════╗");
    System.out.println("║      Concurrent Online Sale Test     ║");
    System.out.println("╠══════════════════════════════════════╣");
    System.out.printf( "║  Server   : %-24s ║%n", host + ":" + port);
    System.out.printf( "║  Threads  : %-24d ║%n", numThreads);
    System.out.printf( "║  Item     : %-24s ║%n", itemCode);
    System.out.printf( "║  Qty/sale : %-24d ║%n", qty);
    System.out.printf( "║  Date     : %-24s ║%n", today);
    System.out.println("╚══════════════════════════════════════╝");
    System.out.println();

    // ── Step 1: Register a fresh test user (single-threaded, before the storm) ──

    String userId = registerTestUser();
    System.out.printf("Test user registered: %s%n%n", userId);

    // ── Step 2: Create worker threads ────────────────────────────────────────

    Thread[] threads = new Thread[numThreads];

    for (int i = 0; i < numThreads; i++) {
      final int    id       = i + 1;
      final String uid      = userId;
      final String saleDate = today;

      threads[i] = new Thread(() -> {
        String tName = Thread.currentThread().getName();
        System.out.printf("[%s] Ready — waiting at barrier%n", tName);

        arrive();   // ← manual barrier: blocks until all N threads are here

        System.out.printf("[%s] GO — sending PROCESS_ONLINE_SALE  {userId=%s, %s x %d, date=%s}%n",
            tName, uid, itemCode, qty, saleDate);

        ServerConnection conn = null;
        try {
          conn = new ServerConnection(host, port);
          if (!conn.connect()) {
            System.out.printf("[%s] FAILURE — could not connect to %s:%d%n", tName, host, port);
            incrementFailure();
            return;
          }

          Map<String, Integer> items = new HashMap<>();
          items.put(itemCode, qty);
          Request  req  = Request.onlineSale(uid, items, saleDate);
          Response resp = conn.sendRequest(req);

          if (resp.isSuccess()) {
            BillDto bill = (BillDto) resp.getPayload();
            System.out.printf("[%s] SUCCESS — Bill #%d | Total: %.2f%n",
                tName, bill.getSerialNumber(), bill.getFinalAmount());
            incrementSuccess();
          } else {
            System.out.printf("[%s] FAILURE — %s%n", tName, resp.getErrorMessage());
            incrementFailure();
          }

        } catch (Exception e) {
          System.out.printf("[%s] ERROR — %s%n", tName, e.getClass().getSimpleName() + ": " + e.getMessage());
          incrementFailure();
        } finally {
          if (conn != null) conn.disconnect();
        }

      }, "OnlineSaleThread-" + id);
    }

    // ── Step 3: Start all threads ─────────────────────────────────────────

    long startTime = System.currentTimeMillis();
    for (Thread t : threads) {
      t.start();
    }

    // ── Step 4: Join all threads (manual loop) ────────────────────────────

    for (Thread t : threads) {
      try {
        t.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        System.out.println("Main thread interrupted while waiting for " + t.getName());
      }
    }

    long elapsed = System.currentTimeMillis() - startTime;

    // ── Step 5: Report ────────────────────────────────────────────────────

    printResults(elapsed);
    printStockReport("ONLINE");
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  /**
   * Registers a uniquely-named test user and returns the server-assigned userId string.
   *
   * @throws Exception if registration fails
   */
  private String registerTestUser() throws Exception {
    String   username = "TestUser-" + System.currentTimeMillis();
    String   email    = username.toLowerCase() + "@test.syos";
    System.out.printf("Registering test user: %s <%s>%n", username, email);

    ServerConnection conn = null;
    try {
      conn = createConnection();
      Request  req  = Request.registerUser(username, email);
      Response resp = conn.sendRequest(req);
      if (resp.isSuccess()) {
        // Payload is the assigned userId string
        return String.valueOf(resp.getPayload());
      } else {
        throw new RuntimeException("Registration failed: " + resp.getErrorMessage());
      }
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  // ── main ─────────────────────────────────────────────────────────────────

  public static void main(String[] args) throws Exception {
    String host     = args.length > 0 ? args[0] : DEFAULT_HOST;
    int    port     = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
    int    threads  = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_THREADS;
    String itemCode = args.length > 3 ? args[3] : DEFAULT_ITEM_CODE;
    int    qty      = args.length > 4 ? Integer.parseInt(args[4]) : DEFAULT_QTY;

    new ConcurrentOnlineSaleClient(host, port, threads, itemCode, qty).run();
  }
}

