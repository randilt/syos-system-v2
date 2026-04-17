package com.syos.server.concurrency;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fixed-size thread pool used to handle concurrent client connections.
 * Wraps an {@link ExecutorService} and exposes a controlled lifecycle API.
 */
public class ThreadPool {

  private static final Logger LOGGER = Logger.getLogger(ThreadPool.class.getName());

  private final ExecutorService executor;
  private final int poolSize;

  /**
   * Creates a thread pool with the given number of worker threads.
   *
   * @param poolSize number of threads; must be positive
   */
  public ThreadPool(int poolSize) {
    if (poolSize <= 0) {
      throw new IllegalArgumentException("Pool size must be positive");
    }
    this.poolSize = poolSize;
    this.executor = Executors.newFixedThreadPool(poolSize);
    LOGGER.info("Thread pool started with " + poolSize + " worker threads");
  }

  /**
   * Submits a task for asynchronous execution.
   *
   * @param task the runnable to execute
   */
  public void submit(Runnable task) {
    if (task == null) {
      throw new IllegalArgumentException("Task cannot be null");
    }
    executor.submit(task);
  }

  /**
   * Initiates an orderly shutdown. Waits up to 30 seconds for in-flight tasks to complete,
   * then forces shutdown if any tasks are still running.
   */
  public void shutdown() {
    LOGGER.info("Shutting down thread pool (" + poolSize + " threads)...");
    executor.shutdown();
    try {
      if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
        LOGGER.warning("Thread pool did not terminate cleanly — forcing shutdown");
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      LOGGER.log(Level.WARNING, "Interrupted while awaiting thread pool termination", e);
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
