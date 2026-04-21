package com.syos.testclient;

import com.syos.network.ServerConnection;
import com.syos.protocol.BillDto;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Concurrent load-test client for mixed in-store and online sales.
 *
 * <p>Spawns N in-store sale threads AND N online sale threads simultaneously, all synchronized
 * on a single barrier — proving the server handles heterogeneous concurrent load correctly.
 * The online phase automatically registers a fresh test user before firing the concurrent threads.
 *
 * <p>All 2N threads (both types) share the same barrier and fire simultaneously when the last
 * thread arrives.
 *
 * <h2>Threading model</h2>
 * <ul>
 *   <li>NO {@code java.util.concurrent} — all synchronization uses raw {@code synchronized},
 *       {@code wait()}, and {@code notifyAll()}.</li>
 *   <li>Each thread creates its <em>own</em> {@link ServerConnection} to maximize concurrency.</li>
 *   <li>In-store and online threads are tracked separately in the results.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   java -cp ... com.syos.testclient.MixedLoadTestClient \
 *       [host] [port] [threadsPerType] [inStoreItem] [inStoreQty] [onlineItem] [onlineQty]
 * </pre>
 */
public class MixedLoadTestClient extends BaseTestClient {

  private static final String DEFAULT_HOST          = "localhost";
  private static final int    DEFAULT_PORT          = 9090;
  private static final int    DEFAULT_THREADS_PER   = 10;
  private static final String DEFAULT_IN_STORE_ITEM = "APPLE001";
  private static final int    DEFAULT_IN_STORE_QTY  = 1;
  private static final String DEFAULT_ONLINE_ITEM   = "MILK001";
  private static final int    DEFAULT_ONLINE_QTY    = 1;

  private final int    numThreadsPerType;
  private final String inStoreItemCode;
  private final int    inStoreQty;
  private final String onlineItemCode;
  private final int    onlineQty;

  private int inStoreSuccess = 0;
  private int inStoreFailure = 0;
  private int onlineSuccess  = 0;
  private int onlineFailure  = 0;

  // ── Constructor ──────────────────────────────────────────────────────────

  public MixedLoadTestClient(
      String host,
      int    port,
      int    numThreadsPerType,
      String inStoreItemCode,
      int    inStoreQty,
      String onlineItemCode,
      int    onlineQty) {
    super(host, port);
    this.numThreadsPerType = numThreadsPerType;
    this.inStoreItemCode   = inStoreItemCode;
    this.inStoreQty        = inStoreQty;
    this.onlineItemCode    = onlineItemCode;
    this.onlineQty         = onlineQty;
  }

  // ── run() ────────────────────────────────────────────────────────────────

  @Override
  public void run() throws Exception {
    totalThreads = numThreadsPerType * 2;   // Both types share one barrier
    resetBarrier();

    double  cashTendered = inStoreQty * 100.0;
    String  today        = LocalDate.now().toString();

    System.out.println();
    System.out.println("╔══════════════════════════════════════╗");
    System.out.println("║       Mixed Load Test (Concurrent)   ║");
    System.out.println("╠══════════════════════════════════════╣");
    System.out.printf( "║  Server   : %-24s ║%n", host + ":" + port);
    System.out.printf( "║  Threads  : %-24d ║%n", totalThreads);
    System.out.printf( "║  In-Store : %-24s ║%n", numThreadsPerType + "x " + inStoreItemCode);
    System.out.printf( "║  Online   : %-24s ║%n", numThreadsPerType + "x " + onlineItemCode);
    System.out.printf( "║  Date     : %-24s ║%n", today);
    System.out.println("╚══════════════════════════════════════╝");
    System.out.println();

    // ── Step 1: Register a fresh test user (single-threaded, before threads start) ──

    String userId = registerTestUser();
    System.out.printf("Test user registered: %s%n%n", userId);

    // ── Step 2: Create all threads (both types) ──────────────────────────

    Thread[] threads = new Thread[totalThreads];
    int idx = 0;

    // Create in-store threads
    for (int i = 0; i < numThreadsPerType; i++) {
      final int id             = i + 1;
      final double cash        = cashTendered;
      final String saleDate    = today;
      final String itemCode    = inStoreItemCode;
      final int    qty         = inStoreQty;

      threads[idx] = new Thread(() -> {
        String tName = Thread.currentThread().getName();
        System.out.printf("[%s] Ready — waiting at barrier%n", tName);

        arrive();   // ← shared barrier: blocks until all 2N threads are here

        System.out.printf("[%s] GO — sending PROCESS_IN_STORE_SALE  {%s x %d, cash=%.2f, date=%s}%n",
            tName, itemCode, qty, cash, saleDate);

        ServerConnection conn = null;
        try {
          conn = new ServerConnection(host, port);
          if (!conn.connect()) {
            System.out.printf("[%s] FAILURE — could not connect to %s:%d%n", tName, host, port);
            synchronized (lock) {
              inStoreFailure++;
            }
            return;
          }

          Map<String, Integer> items = new HashMap<>();
          items.put(itemCode, qty);
          Request  req  = Request.inStoreSale(items, cash, saleDate);
          Response resp = conn.sendRequest(req);

          if (resp.isSuccess()) {
            BillDto bill = (BillDto) resp.getPayload();
            System.out.printf("[%s] SUCCESS — Bill #%d | Items: %d | Total: %.2f | Change: %.2f%n",
                tName,
                bill.getSerialNumber(),
                bill.getItems().size(),
                bill.getFinalAmount(),
                bill.getChange());
            synchronized (lock) {
              inStoreSuccess++;
            }
          } else {
            System.out.printf("[%s] FAILURE — %s%n", tName, resp.getErrorMessage());
            synchronized (lock) {
              inStoreFailure++;
            }
          }

        } catch (Exception e) {
          System.out.printf("[%s] ERROR — %s%n", tName, e.getClass().getSimpleName() + ": " + e.getMessage());
          synchronized (lock) {
            inStoreFailure++;
          }
        } finally {
          if (conn != null) conn.disconnect();
        }

      }, "MixedInStore-" + id);
      idx++;
    }

    // Create online threads
    for (int i = 0; i < numThreadsPerType; i++) {
      final int    id       = i + 1;
      final String uid      = userId;
      final String saleDate = today;
      final String itemCode = onlineItemCode;
      final int    qty      = onlineQty;

      threads[idx] = new Thread(() -> {
        String tName = Thread.currentThread().getName();
        System.out.printf("[%s] Ready — waiting at barrier%n", tName);

        arrive();   // ← shared barrier: blocks until all 2N threads are here

        System.out.printf("[%s] GO — sending PROCESS_ONLINE_SALE  {userId=%s, %s x %d, date=%s}%n",
            tName, uid, itemCode, qty, saleDate);

        ServerConnection conn = null;
        try {
          conn = new ServerConnection(host, port);
          if (!conn.connect()) {
            System.out.printf("[%s] FAILURE — could not connect to %s:%d%n", tName, host, port);
            synchronized (lock) {
              onlineFailure++;
            }
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
            synchronized (lock) {
              onlineSuccess++;
            }
          } else {
            System.out.printf("[%s] FAILURE — %s%n", tName, resp.getErrorMessage());
            synchronized (lock) {
              onlineFailure++;
            }
          }

        } catch (Exception e) {
          System.out.printf("[%s] ERROR — %s%n", tName, e.getClass().getSimpleName() + ": " + e.getMessage());
          synchronized (lock) {
            onlineFailure++;
          }
        } finally {
          if (conn != null) conn.disconnect();
        }

      }, "MixedOnline-" + id);
      idx++;
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

    printMixedResults(elapsed);
    printStockReport("STORE");
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
        return String.valueOf(resp.getPayload());
      } else {
        throw new RuntimeException("Registration failed: " + resp.getErrorMessage());
      }
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  /**
   * Prints a breakdown of in-store and online results.
   */
  private void printMixedResults(long durationMs) {
    int    inStoreTotal = inStoreSuccess + inStoreFailure;
    int    onlineTotal  = onlineSuccess + onlineFailure;
    int    totalRequests = inStoreTotal + onlineTotal;
    double throughput   = durationMs > 0 ? (totalRequests * 1_000.0) / durationMs : 0.0;

    System.out.println();
    System.out.println("╔══════════════════════════════════════╗");
    System.out.println("║          TEST RESULTS SUMMARY        ║");
    System.out.println("╠══════════════════════════════════════╣");
    System.out.printf( "║  Total threads  : %-18d ║%n", totalThreads);
    System.out.printf( "║  In-Store S     : %-18d ║%n", inStoreSuccess);
    System.out.printf( "║  In-Store F     : %-18d ║%n", inStoreFailure);
    System.out.printf( "║  Online S       : %-18d ║%n", onlineSuccess);
    System.out.printf( "║  Online F       : %-18d ║%n", onlineFailure);
    System.out.printf( "║  Duration       : %-14d ms  ║%n", durationMs);
    System.out.printf( "║  Throughput     : %-13.2f r/s ║%n", throughput);
    System.out.println("╚══════════════════════════════════════╝");
  }

  // ── main ─────────────────────────────────────────────────────────────────

  public static void main(String[] args) throws Exception {
    String host            = args.length > 0 ? args[0]                   : DEFAULT_HOST;
    int    port            = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
    int    threadsPerType  = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_THREADS_PER;
    String inStoreItem     = args.length > 3 ? args[3]                   : DEFAULT_IN_STORE_ITEM;
    int    inStoreQty      = args.length > 4 ? Integer.parseInt(args[4]) : DEFAULT_IN_STORE_QTY;
    String onlineItem      = args.length > 5 ? args[5]                   : DEFAULT_ONLINE_ITEM;
    int    onlineQty       = args.length > 6 ? Integer.parseInt(args[6]) : DEFAULT_ONLINE_QTY;

    new MixedLoadTestClient(host, port, threadsPerType, inStoreItem, inStoreQty, onlineItem, onlineQty).run();
  }
}
