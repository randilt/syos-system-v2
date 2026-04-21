package com.syos.testclient;

/**
 * Runs four concurrent test clients sequentially against a live SYOS server and prints
 * a combined summary at the end.
 *
 * <p>Execution order:
 * <ol>
 *   <li>{@link ConcurrentInStoreSaleClient} — concurrent in-store sales.</li>
 *   <li>{@link ConcurrentOnlineSaleClient}  — concurrent online sales (with user registration).</li>
 *   <li>{@link MixedLoadTestClient}         — mixed in-store and online sales fired simultaneously.</li>
 *   <li>{@link StressTestClient}            — 100 concurrent PING requests for latency/throughput analysis.</li>
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

    stabilizationPause();

    // ── Phase 2: Online Sales ─────────────────────────────────────────────

    printPhaseHeader(2, "Concurrent Online Sale Load Test");
    ConcurrentOnlineSaleClient onlineClient =
        new ConcurrentOnlineSaleClient(host, port, threads, "MILK001", 1);
    onlineClient.run();

    stabilizationPause();

    // ── Phase 3: Mixed Load ───────────────────────────────────────────────

    printPhaseHeader(3, "Mixed In-Store and Online Load Test");
    MixedLoadTestClient mixedClient =
        new MixedLoadTestClient(host, port, 10, "APPLE001", 1, "MILK001", 1);
    mixedClient.run();

    stabilizationPause();

    // ── Phase 4: Stress Test ──────────────────────────────────────────────

    printPhaseHeader(4, "Stress Test (100x PING)");
    StressTestClient stressClient =
        new StressTestClient(host, port, 100);
    stressClient.run();

    // ── Combined summary ──────────────────────────────────────────────────

    System.out.println();
    System.out.println("╔══════════════════════════════════════════════╗");
    System.out.println("║             COMBINED TEST SUMMARY            ║");
    System.out.println("╠══════════════════════════════════════════════╣");

    int inStoreSuccess = inStoreClient.successCount;
    int inStoreFailure = inStoreClient.failureCount;
    int onlineSuccess  = onlineClient.successCount;
    int onlineFailure  = onlineClient.failureCount;
    int mixedInStoreSuccess = getMixedInStoreSuccess(mixedClient);
    int mixedInStoreFailure = getMixedInStoreFailure(mixedClient);
    int mixedOnlineSuccess  = getMixedOnlineSuccess(mixedClient);
    int mixedOnlineFailure  = getMixedOnlineFailure(mixedClient);
    int stressSuccess = stressClient.successCount;
    int stressFailure = stressClient.failureCount;

    int totalRequests = inStoreSuccess + inStoreFailure + onlineSuccess + onlineFailure
                      + mixedInStoreSuccess + mixedInStoreFailure
                      + mixedOnlineSuccess + mixedOnlineFailure
                      + stressSuccess + stressFailure;
    int totalSuccess = inStoreSuccess + onlineSuccess
                     + mixedInStoreSuccess + mixedOnlineSuccess
                     + stressSuccess;
    int totalFailure = inStoreFailure + onlineFailure
                     + mixedInStoreFailure + mixedOnlineFailure
                     + stressFailure;

    System.out.printf("║  In-Store    Success : %-19d ║%n", inStoreSuccess);
    System.out.printf("║  In-Store    Failure : %-19d ║%n", inStoreFailure);
    System.out.printf("║  Online      Success : %-19d ║%n", onlineSuccess);
    System.out.printf("║  Online      Failure : %-19d ║%n", onlineFailure);
    System.out.printf("║  Mixed I/S   Success : %-19d ║%n", mixedInStoreSuccess);
    System.out.printf("║  Mixed I/S   Failure : %-19d ║%n", mixedInStoreFailure);
    System.out.printf("║  Mixed Onl   Success : %-19d ║%n", mixedOnlineSuccess);
    System.out.printf("║  Mixed Onl   Failure : %-19d ║%n", mixedOnlineFailure);
    System.out.printf("║  Stress Test Success : %-19d ║%n", stressSuccess);
    System.out.printf("║  Stress Test Failure : %-19d ║%n", stressFailure);
    System.out.println("╠══════════════════════════════════════════════╣");
    System.out.printf("║  TOTAL Requests      : %-19d ║%n", totalRequests);
    System.out.printf("║  TOTAL Successes     : %-19d ║%n", totalSuccess);
    System.out.printf("║  TOTAL Failures      : %-19d ║%n", totalFailure);
    System.out.printf("║  Overall Success     : %-18.1f%% ║%n",
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
    System.out.printf( "║  Phases    : %-31s ║%n", "4 (In-Store, Online, Mixed, Stress)");
    System.out.println("╚══════════════════════════════════════════════╝");
    System.out.println();
  }

  private static void printPhaseHeader(int phase, String title) {
    System.out.printf("▶  Phase %d: %s%n", phase, title);
    System.out.println();
  }

  private static void stabilizationPause() {
    System.out.println();
    System.out.println("─".repeat(50));
    System.out.println("Stabilizing server (1s pause)...");
    try {
      Thread.sleep(1_000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    System.out.println();
  }

  /**
   * Reflection-based helper to access inStoreSuccess field from MixedLoadTestClient.
   * (Avoids making the field public in MixedLoadTestClient.)
   */
  private static int getMixedInStoreSuccess(MixedLoadTestClient client) {
    try {
      return (int) client.getClass().getDeclaredField("inStoreSuccess").get(client);
    } catch (Exception e) {
      return 0;
    }
  }

  private static int getMixedInStoreFailure(MixedLoadTestClient client) {
    try {
      return (int) client.getClass().getDeclaredField("inStoreFailure").get(client);
    } catch (Exception e) {
      return 0;
    }
  }

  private static int getMixedOnlineSuccess(MixedLoadTestClient client) {
    try {
      return (int) client.getClass().getDeclaredField("onlineSuccess").get(client);
    } catch (Exception e) {
      return 0;
    }
  }

  private static int getMixedOnlineFailure(MixedLoadTestClient client) {
    try {
      return (int) client.getClass().getDeclaredField("onlineFailure").get(client);
    } catch (Exception e) {
      return 0;
    }
  }
}
