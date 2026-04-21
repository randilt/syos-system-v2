package com.syos.testclient;

import com.syos.network.ServerConnection;
import com.syos.protocol.Request;
import com.syos.protocol.Response;

/**
 * Stress-test client for maximum server load via PING requests.
 *
 * <p>Spawns N threads, each sending a PING request simultaneously (all synchronized on a
 * single barrier). Measures response times to evaluate server throughput and latency under
 * peak load.
 *
 * <h2>Threading model</h2>
 * <ul>
 *   <li>NO {@code java.util.concurrent} — all synchronization uses raw {@code synchronized},
 *       {@code wait()}, and {@code notifyAll()}.</li>
 *   <li>Each thread creates its <em>own</em> {@link ServerConnection}.</li>
 *   <li>Per-thread response times are collected in a synchronized array for statistical analysis.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   java -cp ... com.syos.testclient.StressTestClient \
 *       [host] [port] [numThreads]
 * </pre>
 */
public class StressTestClient extends BaseTestClient {

  private static final String DEFAULT_HOST    = "localhost";
  private static final int    DEFAULT_PORT    = 9090;
  private static final int    DEFAULT_THREADS = 100;

  private final int numThreads;
  private       long[] responseTimes;

  // ── Constructor ──────────────────────────────────────────────────────────

  public StressTestClient(String host, int port, int numThreads) {
    super(host, port);
    this.numThreads    = numThreads;
    this.responseTimes = new long[numThreads];
  }

  // ── run() ────────────────────────────────────────────────────────────────

  @Override
  public void run() throws InterruptedException {
    totalThreads = numThreads;
    resetBarrier();

    System.out.println();
    System.out.println("╔══════════════════════════════════════╗");
    System.out.println("║        Stress Test (PING x100)       ║");
    System.out.println("╠══════════════════════════════════════╣");
    System.out.printf( "║  Server   : %-24s ║%n", host + ":" + port);
    System.out.printf( "║  Threads  : %-24d ║%n", numThreads);
    System.out.printf( "║  Command  : %-24s ║%n", "PING (health check)");
    System.out.println("╚══════════════════════════════════════╝");
    System.out.println();

    // ── Build threads ──────────────────────────────────────────────────────

    Thread[] threads = new Thread[numThreads];

    for (int i = 0; i < numThreads; i++) {
      final int id = i;

      threads[i] = new Thread(() -> {
        String tName = Thread.currentThread().getName();
        System.out.printf("[%s] Ready — waiting at barrier%n", tName);

        arrive();   // ← manual barrier: all threads wait here until the last one arrives

        System.out.printf("[%s] GO — sending PING%n", tName);

        ServerConnection conn = null;
        try {
          // Each thread opens its own connection for maximum concurrency
          conn = new ServerConnection(host, port);
          if (!conn.connect()) {
            System.out.printf("[%s] FAILURE — could not connect to %s:%d%n", tName, host, port);
            incrementFailure();
            return;
          }

          long startTime = System.currentTimeMillis();
          Request  req   = Request.ping();
          Response resp  = conn.sendRequest(req);
          long elapsed   = System.currentTimeMillis() - startTime;

          if (resp.isSuccess()) {
            String payload = String.valueOf(resp.getPayload());
            if ("PONG".equals(payload)) {
              System.out.printf("[%s] SUCCESS — PONG received in %d ms%n", tName, elapsed);
              synchronized (lock) {
                responseTimes[id] = elapsed;
              }
              incrementSuccess();
            } else {
              System.out.printf("[%s] FAILURE — unexpected payload: %s%n", tName, payload);
              incrementFailure();
            }
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

      }, "StressThread-" + (i + 1));
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

    // ── Report with statistics ────────────────────────────────────────────

    printStressResults(elapsed);
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  /**
   * Prints test results including min/max/avg response times.
   */
  private void printStressResults(long durationMs) {
    int total      = successCount + failureCount;
    double throughput = durationMs > 0 ? (total * 1_000.0) / durationMs : 0.0;

    // Compute response time statistics (only for successful requests)
    long minTime = Long.MAX_VALUE;
    long maxTime = 0L;
    long sumTime = 0L;
    int  count   = 0;

    for (int i = 0; i < responseTimes.length; i++) {
      if (responseTimes[i] > 0) {
        minTime = Math.min(minTime, responseTimes[i]);
        maxTime = Math.max(maxTime, responseTimes[i]);
        sumTime += responseTimes[i];
        count++;
      }
    }

    double avgTime = count > 0 ? (double) sumTime / count : 0.0;

    System.out.println();
    System.out.println("╔══════════════════════════════════════╗");
    System.out.println("║          TEST RESULTS SUMMARY        ║");
    System.out.println("╠══════════════════════════════════════╣");
    System.out.printf( "║  Total threads  : %-18d ║%n", totalThreads);
    System.out.printf( "║  Successes      : %-18d ║%n", successCount);
    System.out.printf( "║  Failures       : %-18d ║%n", failureCount);
    System.out.printf( "║  Duration       : %-14d ms  ║%n", durationMs);
    System.out.printf( "║  Throughput     : %-13.2f r/s ║%n", throughput);
    System.out.println("╠══════════════════════════════════════╣");
    System.out.printf( "║  Min Latency    : %-18d ms ║%n", minTime == Long.MAX_VALUE ? 0 : minTime);
    System.out.printf( "║  Max Latency    : %-18d ms ║%n", maxTime);
    System.out.printf( "║  Avg Latency    : %-17.2f ms ║%n", avgTime);
    System.out.println("╚══════════════════════════════════════╝");
  }

  // ── main ─────────────────────────────────────────────────────────────────

  public static void main(String[] args) throws Exception {
    String host   = args.length > 0 ? args[0]                   : DEFAULT_HOST;
    int    port   = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
    int    threads = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_THREADS;

    new StressTestClient(host, port, threads).run();
  }
}
