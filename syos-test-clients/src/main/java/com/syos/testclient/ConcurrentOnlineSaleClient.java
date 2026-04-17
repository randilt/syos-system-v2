package com.syos.testclient;

import com.syos.network.ServerConnection;
import com.syos.protocol.CommandType;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrent load-test client for {@code PROCESS_ONLINE_SALE}.
 *
 * <p>Spawns N threads, each opening its own TCP connection and sending one online sale
 * request for a pre-registered test user. Results are printed to stdout on completion.
 *
 * <p>Usage: {@code java -cp ... com.syos.testclient.ConcurrentOnlineSaleClient [host] [port] [threads] [userId]}
 *
 * <p><b>Pre-condition:</b> the test user must already be registered in the database.
 */
public class ConcurrentOnlineSaleClient {

  private static final String DEFAULT_HOST    = "localhost";
  private static final int    DEFAULT_PORT    = 9090;
  private static final int    DEFAULT_THREADS = 10;
  private static final String DEFAULT_USER_ID = "USER-000001";

  public static void main(String[] args) throws InterruptedException {
    String host    = args.length > 0 ? args[0]               : DEFAULT_HOST;
    int    port    = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
    int    threads = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_THREADS;
    String userId  = args.length > 3 ? args[3]               : DEFAULT_USER_ID;

    System.out.printf("=== Concurrent Online Sale Test ===%n");
    System.out.printf("Server:  %s:%d%n", host, port);
    System.out.printf("Threads: %d%n", threads);
    System.out.printf("User:    %s%n%n", userId);

    CountDownLatch  startLatch   = new CountDownLatch(1);
    CountDownLatch  doneLatch    = new CountDownLatch(threads);
    AtomicInteger   successCount = new AtomicInteger(0);
    AtomicInteger   failureCount = new AtomicInteger(0);

    ExecutorService pool = Executors.newFixedThreadPool(threads);

    for (int i = 0; i < threads; i++) {
      final int threadId = i + 1;
      pool.submit(() -> {
        try {
          startLatch.await();
          Response response = sendOnlineSaleRequest(host, port, userId, threadId);
          if (response.isSuccess()) {
            successCount.incrementAndGet();
            System.out.printf("[Thread %2d] SUCCESS — Bill #%s%n",
                threadId, response.getData().get("serialNumber"));
          } else {
            failureCount.incrementAndGet();
            System.out.printf("[Thread %2d] FAILURE — %s%n",
                threadId, response.getErrorMessage());
          }
        } catch (Exception e) {
          failureCount.incrementAndGet();
          System.out.printf("[Thread %2d] ERROR — %s%n", threadId, e.getMessage());
        } finally {
          doneLatch.countDown();
        }
      });
    }

    long start = System.currentTimeMillis();
    startLatch.countDown();
    doneLatch.await(60, TimeUnit.SECONDS);
    long elapsed = System.currentTimeMillis() - start;

    pool.shutdownNow();

    System.out.printf("%n=== Results ===%n");
    System.out.printf("Successes: %d%n", successCount.get());
    System.out.printf("Failures:  %d%n", failureCount.get());
    System.out.printf("Elapsed:   %d ms%n", elapsed);
  }

  private static Response sendOnlineSaleRequest(
      String host, int port, String userId, int threadId) throws Exception {
    ServerConnection connection = new ServerConnection(host, port);
    connection.connect();
    try {
      Map<String, Object> payload = new HashMap<>();
      payload.put("userId", userId);

      Map<String, Integer> items = new HashMap<>();
      items.put("MILK001", 1); // adjust to match seeded online stock in your DB
      payload.put("items", items);
      payload.put("date", LocalDate.now().toString());

      return connection.send(new Request(CommandType.PROCESS_ONLINE_SALE, payload));
    } finally {
      connection.close();
    }
  }
}
