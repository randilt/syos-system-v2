package com.syos.testclient;

import com.syos.network.ServerConnection;
import com.syos.protocol.BillDto;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Concurrent load-test client for {@code PROCESS_IN_STORE_SALE}.
 *
 * <p>Spawns N {@link Thread} objects, each opening its own TCP connection and firing one
 * in-store sale request simultaneously.  A manual barrier ({@link BaseTestClient#arrive()})
 * ensures all threads start within the same OS scheduling quantum.
 *
 * <h2>Threading model</h2>
 * <ul>
 *   <li>NO {@code java.util.concurrent} — all synchronization uses raw {@code synchronized},
 *       {@code wait()}, and {@code notifyAll()}.</li>
 *   <li>Each thread creates its <em>own</em> {@link ServerConnection} to maximise concurrency.</li>
 *   <li>Every exception is caught and counted as a failure; the thread never silently exits.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   java -cp ... com.syos.testclient.ConcurrentInStoreSaleClient \
 *       [host] [port] [threads] [itemCode] [quantity]
 * </pre>
 */
public class ConcurrentInStoreSaleClient extends BaseTestClient {

  private static final String DEFAULT_HOST      = "localhost";
  private static final int    DEFAULT_PORT      = 9090;
  private static final int    DEFAULT_THREADS   = 20;
  private static final String DEFAULT_ITEM_CODE = "APPLE001";
  private static final int    DEFAULT_QTY       = 2;

  private final int    numThreads;
  private final String itemCode;
  private final int    qty;

  // ── Constructor ──────────────────────────────────────────────────────────

  public ConcurrentInStoreSaleClient(
      String host, int port, int numThreads, String itemCode, int qty) {
    super(host, port);
    this.numThreads = numThreads;
    this.itemCode   = itemCode;
    this.qty        = qty;
  }

  // ── run() ────────────────────────────────────────────────────────────────

  @Override
  public void run() throws InterruptedException {
    totalThreads = numThreads;
    resetBarrier();

    // Cash always covers the purchase (price is arbitrary; server enforces real amounts)
    double cashTendered = qty * 100.0;   // generous — ensures change is returned
    String today        = LocalDate.now().toString();

    System.out.println();
    System.out.println("╔══════════════════════════════════════╗");
    System.out.println("║    Concurrent In-Store Sale Test     ║");
    System.out.println("╠══════════════════════════════════════╣");
    System.out.printf( "║  Server   : %-24s ║%n", host + ":" + port);
    System.out.printf( "║  Threads  : %-24d ║%n", numThreads);
    System.out.printf( "║  Item     : %-24s ║%n", itemCode);
    System.out.printf( "║  Qty/sale : %-24d ║%n", qty);
    System.out.printf( "║  Cash     : %-24.2f ║%n", cashTendered);
    System.out.printf( "║  Date     : %-24s ║%n", today);
    System.out.println("╚══════════════════════════════════════╝");
    System.out.println();

    // ── Build threads ──────────────────────────────────────────────────────

    Thread[] threads = new Thread[numThreads];

    for (int i = 0; i < numThreads; i++) {
      final int id             = i + 1;
      final double cash        = cashTendered;
      final String saleDate    = today;

      threads[i] = new Thread(() -> {
        String tName = Thread.currentThread().getName();
        System.out.printf("[%s] Ready — waiting at barrier%n", tName);

        arrive();   // ← manual barrier: all threads wait here until the last one arrives

        System.out.printf("[%s] GO — sending PROCESS_IN_STORE_SALE  {%s x %d, cash=%.2f, date=%s}%n",
            tName, itemCode, qty, cash, saleDate);

        ServerConnection conn = null;
        try {
          // Each thread opens its own connection for maximum concurrency
          conn = new ServerConnection(host, port);
          if (!conn.connect()) {
            System.out.printf("[%s] FAILURE — could not connect to %s:%d%n", tName, host, port);
            incrementFailure();
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

      }, "InStoreSaleThread-" + id);
    }

    // ── Start all threads ──────────────────────────────────────────────────

    long startTime = System.currentTimeMillis();
    for (Thread t : threads) {
      t.start();
    }

    // ── Join all threads (manual loop — no ExecutorService) ────────────────

    for (Thread t : threads) {
      try {
        t.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        System.out.println("Main thread interrupted while waiting for " + t.getName());
      }
    }

    long elapsed = System.currentTimeMillis() - startTime;

    // ── Report ─────────────────────────────────────────────────────────────

    printResults(elapsed);
    printStockReport("STORE");
  }

  // ── main ─────────────────────────────────────────────────────────────────

  public static void main(String[] args) throws Exception {
    String host     = args.length > 0 ? args[0] : DEFAULT_HOST;
    int    port     = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
    int    threads  = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_THREADS;
    String itemCode = args.length > 3 ? args[3] : DEFAULT_ITEM_CODE;
    int    qty      = args.length > 4 ? Integer.parseInt(args[4]) : DEFAULT_QTY;

    new ConcurrentInStoreSaleClient(host, port, threads, itemCode, qty).run();
  }
}

