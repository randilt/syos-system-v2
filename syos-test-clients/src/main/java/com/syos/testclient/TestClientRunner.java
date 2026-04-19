package com.syos.testclient;

/**
 * Runs both concurrent test clients sequentially against a live SYOS server and prints
 * a combined summary at the end.
 *
 * <p>Execution order:
 * <ol>
 *   <li>{@link ConcurrentInStoreSaleClient} — hammers the server with concurrent in-store sales.</li>
 *   <li>{@link ConcurrentOnlineSaleClient}  — registers a test user then hammers the server with
 *       concurrent online sales.</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>
 *   java -cp ... com.syos.testclient.TestClientRunner [host] [port] [threads]
 * </pre>
 * <p>All arguments are optional and default to {@code localhost}, {@code 9090}, {@code 20}
 * threads respectively.
 *
 * <h2>Threading model</h2>
 * <p>Uses only raw {@link Thread} objects and {@code synchronized}/{@code wait}/{@code notifyAll}
 * — NO {@code java.util.concurrent} classes anywhere in this module.
 */
public class TestClientRunner {

  private static final String DEFAULT_HOST    = "localhost";
  private static final int    DEFAULT_PORT    = 9090;
  private static final int    DEFAULT_THREADS = 20;

  public static void main(String[] args) throws Exception {

    String host    = args.length > 0 ? args[0]                   : DEFAULT_HOST;
    int    port    = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
    int    threads = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_THREADS;

    printBanner(host, port, threads);

    // ── Phase 1: In-Store Sales ───────────────────────────────────────────

    printPhaseHeader(1, "Concurrent In-Store Sale Load Test");
    ConcurrentInStoreSaleClient inStoreClient =
        new ConcurrentInStoreSaleClient(host, port, threads, "APPLE001", 2);
    inStoreClient.run();

    System.out.println();
    System.out.println("─".repeat(50));
    System.out.println();

    // ── Phase 2: Online Sales ─────────────────────────────────────────────

    printPhaseHeader(2, "Concurrent Online Sale Load Test");
    ConcurrentOnlineSaleClient onlineClient =
        new ConcurrentOnlineSaleClient(host, port, threads, "MILK001", 1);
    onlineClient.run();

    // ── Combined summary ──────────────────────────────────────────────────

    System.out.println();
    System.out.println("╔══════════════════════════════════════════════╗");
    System.out.println("║             COMBINED TEST SUMMARY            ║");
    System.out.println("╠══════════════════════════════════════════════╣");

    int totalSuccess = inStoreClient.successCount + onlineClient.successCount;
    int totalFailure = inStoreClient.failureCount + onlineClient.failureCount;
    int totalRequests = totalSuccess + totalFailure;

    System.out.printf("║  In-Store  Success : %-23d ║%n", inStoreClient.successCount);
    System.out.printf("║  In-Store  Failure : %-23d ║%n", inStoreClient.failureCount);
    System.out.printf("║  Online    Success : %-23d ║%n", onlineClient.successCount);
    System.out.printf("║  Online    Failure : %-23d ║%n", onlineClient.failureCount);
    System.out.println("╠══════════════════════════════════════════════╣");
    System.out.printf("║  TOTAL Requests    : %-23d ║%n", totalRequests);
    System.out.printf("║  TOTAL Successes   : %-23d ║%n", totalSuccess);
    System.out.printf("║  TOTAL Failures    : %-23d ║%n", totalFailure);
    System.out.printf("║  Overall Success   : %-22.1f%% ║%n",
        totalRequests == 0 ? 0.0 : (100.0 * totalSuccess / totalRequests));
    System.out.println("╚══════════════════════════════════════════════╝");
    System.out.println();
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private static void printBanner(String host, int port, int threads) {
    System.out.println();
    System.out.println("╔══════════════════════════════════════════════╗");
    System.out.println("║          SYOS Concurrent Test Runner         ║");
    System.out.println("╠══════════════════════════════════════════════╣");
    System.out.printf( "║  Server    : %-31s ║%n", host + ":" + port);
    System.out.printf( "║  Threads   : %-31d ║%n", threads);
    System.out.printf( "║  Phases    : %-31s ║%n", "In-Store Sales + Online Sales");
    System.out.println("╚══════════════════════════════════════════════╝");
    System.out.println();
  }

  private static void printPhaseHeader(int phase, String title) {
    System.out.printf("▶  Phase %d: %s%n", phase, title);
    System.out.println();
  }
}
