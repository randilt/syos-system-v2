package com.syos.testclient;

import com.syos.network.ServerConnection;
import com.syos.protocol.ReportDto;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import java.time.LocalDate;
import java.util.Map;

/**
 * Abstract base for all concurrent test clients.
 *
 * <h2>Threading model</h2>
 * <p>Uses only raw {@link Thread} objects and manual {@code synchronized} / {@code wait} /
 * {@code notifyAll} — no {@code java.util.concurrent} classes.
 *
 * <h2>Barrier</h2>
 * <p>The built-in barrier ({@link #arrive()}) ensures all worker threads are ready before any
 * of them fires its request.  The last thread to call {@code arrive()} releases all waiting
 * threads simultaneously via {@code notifyAll()}.
 */
public abstract class BaseTestClient {

  // ── Configuration ────────────────────────────────────────────────────────

  protected final String host;
  protected final int    port;

  // ── Counters — all access guarded by the instance monitor ─────────────────

  protected int successCount = 0;
  protected int failureCount = 0;

  /** Convenience lock object exposed to subclasses for ad-hoc synchronization. */
  protected final Object lock = new Object();

  // ── Barrier state ────────────────────────────────────────────────────────

  /** Number of threads that have called {@link #arrive()} and are waiting. */
  private int     waitingThreads = 0;
  /** Set to {@code true} by the last arriving thread; never reset within a run. */
  private volatile boolean go = false;
  /** Total number of threads that must arrive before the barrier opens. */
  protected int totalThreads = 0;

  // ── Constructor ──────────────────────────────────────────────────────────

  protected BaseTestClient(String host, int port) {
    this.host = host;
    this.port = port;
  }

  // ── Counter helpers ──────────────────────────────────────────────────────

  protected synchronized void incrementSuccess() {
    successCount++;
  }

  protected synchronized void incrementFailure() {
    failureCount++;
  }

  // ── Barrier ──────────────────────────────────────────────────────────────

  /**
   * Cyclic barrier using only {@code synchronized} + {@code wait} / {@code notifyAll}.
   *
   * <p>Each calling thread increments {@code waitingThreads}.  When the count reaches
   * {@link #totalThreads} the last thread sets {@code go = true} and wakes all waiters.
   * Every other thread spins in a {@code while (!go) wait()} loop.
   *
   * <p>Must be called <em>before</em> starting work; {@link #totalThreads} must be set first.
   */
  protected synchronized void arrive() {
    waitingThreads++;
    if (waitingThreads == totalThreads) {
      // Last thread through — open the gate for everyone
      go = true;
      notifyAll();
    } else {
      // Earlier threads park here until the gate opens
      while (!go) {
        try {
          wait();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
  }

  /** Resets barrier state so it can be reused across multiple runs. */
  protected synchronized void resetBarrier() {
    waitingThreads = 0;
    go             = false;
  }

  // ── Results printer ──────────────────────────────────────────────────────

  /**
   * Prints a formatted summary of the completed test run to stdout.
   *
   * @param durationMs wall-clock time from first thread start to last join
   */
  protected void printResults(long durationMs) {
    int    total      = successCount + failureCount;
    double throughput = durationMs > 0 ? (total * 1_000.0) / durationMs : 0.0;

    System.out.println();
    System.out.println("╔══════════════════════════════════════╗");
    System.out.println("║          TEST RESULTS SUMMARY        ║");
    System.out.println("╠══════════════════════════════════════╣");
    System.out.printf( "║  Total threads  : %-18d ║%n", totalThreads);
    System.out.printf( "║  Successes      : %-18d ║%n", successCount);
    System.out.printf( "║  Failures       : %-18d ║%n", failureCount);
    System.out.printf( "║  Duration       : %-14d ms  ║%n", durationMs);
    System.out.printf( "║  Throughput     : %-13.2f r/s ║%n", throughput);
    System.out.println("╚══════════════════════════════════════╝");
  }

  // ── Connection factory ───────────────────────────────────────────────────

  /**
   * Creates a new {@link ServerConnection} to the configured host/port, connects it, and
   * returns it.  Caller is responsible for disconnecting when done.
   *
   * @return a connected {@code ServerConnection}
   * @throws RuntimeException if the connection attempt fails after all retries
   */
  protected ServerConnection createConnection() {
    ServerConnection conn = new ServerConnection(host, port);
    boolean ok = conn.connect();
    if (!ok) {
      throw new RuntimeException("Failed to connect to " + host + ":" + port);
    }
    return conn;
  }

  // ── Stock report helper ──────────────────────────────────────────────────

  /**
   * Fetches and prints the stock report for {@code stockType} ({@code "STORE"},
   * {@code "SHELF"}, or {@code "ONLINE"}).
   */
  protected void printStockReport(String stockType) {
    System.out.printf("%n--- Final %s Stock Report ---%n", stockType);
    ServerConnection conn = null;
    try {
      conn = createConnection();
      String   today  = LocalDate.now().toString();
      Request  req    = Request.getStockReport(stockType, today);
      Response resp   = conn.sendRequest(req);
      if (resp.isSuccess()) {
        ReportDto report = (ReportDto) resp.getPayload();
        System.out.println(report.getTitle());
        System.out.printf("%-15s %-30s %10s%n", "Item Code", "Item Name", "Quantity");
        System.out.println("-".repeat(58));
        for (Map<String, Object> row : report.getData()) {
          System.out.printf("%-15s %-30s %10s%n",
              row.getOrDefault("itemCode",  row.getOrDefault("code",     "?")),
              row.getOrDefault("itemName",  row.getOrDefault("name",     "?")),
              row.getOrDefault("quantity",  row.getOrDefault("qty",      "?")));
        }
        System.out.printf("Total rows: %d%n", report.getTotalRecords());
      } else {
        System.out.println("Stock report error: " + resp.getErrorMessage());
      }
    } catch (Exception e) {
      System.out.println("Could not fetch stock report: " + e.getMessage());
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  // ── Entry point ──────────────────────────────────────────────────────────

  /** Runs the test with the configured number of threads. */
  public abstract void run() throws Exception;
}
