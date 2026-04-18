package com.syos.server.concurrency;

import java.util.logging.Logger;

/**
 * A fixed-size thread pool implemented entirely with raw Java threading primitives.
 *
 * <h2>Design — Thread Pool Pattern</h2>
 * <p>The thread pool pattern amortises the cost of thread creation by keeping a fixed set of
 * long-lived {@link WorkerThread}s alive for the lifetime of the server. Incoming client
 * connection tasks are placed into a shared {@link RequestQueue}; idle workers block on
 * {@link RequestQueue#dequeue()} and wake up as soon as work arrives.
 *
 * <h2>Components</h2>
 * <ul>
 *   <li><strong>{@link RequestQueue}</strong> — bounded, blocking circular buffer that provides
 *       back-pressure: if all workers are busy and the queue is full, the accepting thread will
 *       block until a slot becomes free.</li>
 *   <li><strong>{@link WorkerThread}</strong> — each worker runs an infinite loop pulling tasks
 *       from the shared queue and executing them.</li>
 * </ul>
 *
 * <h2>Counters</h2>
 * <p>{@code completedTasks} and {@code activeThreads} are plain {@code int} fields protected
 * by {@code synchronized} accessors and mutators (no {@code java.util.concurrent.atomic}
 * classes are used). Tasks update these counters themselves via the {@link #wrapTask(Runnable)}
 * wrapper applied at {@link #submit(Runnable)} time.
 *
 * <h2>Shutdown</h2>
 * <p>{@link #shutdown()} sets the {@code shutdown} flag then calls
 * {@link WorkerThread#shutdown()} on every worker, which interrupts each thread and causes it
 * to exit its run-loop. The flag is {@code volatile} to guarantee immediate visibility across
 * threads without holding a lock.
 */
public class ThreadPool {

  private static final Logger LOGGER = Logger.getLogger(ThreadPool.class.getName());

  private final WorkerThread[]  workers;
  private final RequestQueue    queue;
  /** Set to {@code true} once {@link #shutdown()} is called; prevents further submissions. */
  private volatile boolean      shutdown   = false;

  /** Number of tasks that have completed execution. Protected by {@code this}. */
  private int completedTasks = 0;
  /** Number of worker threads currently executing a task. Protected by {@code this}. */
  private int activeThreads  = 0;

  // ── Constructor ──────────────────────────────────────────────────────────

  /**
   * Creates a thread pool with a fixed number of workers and a bounded task queue, then
   * starts all worker threads immediately.
   *
   * @param poolSize      number of worker threads; must be positive
   * @param queueCapacity maximum number of tasks that can wait in the queue; must be positive
   * @throws IllegalArgumentException if either argument is non-positive
   */
  public ThreadPool(int poolSize, int queueCapacity) {
    if (poolSize <= 0) {
      throw new IllegalArgumentException("Pool size must be positive, got: " + poolSize);
    }
    if (queueCapacity <= 0) {
      throw new IllegalArgumentException("Queue capacity must be positive, got: " + queueCapacity);
    }
    this.queue   = new RequestQueue(queueCapacity);
    this.workers = new WorkerThread[poolSize];
    for (int i = 0; i < poolSize; i++) {
      workers[i] = new WorkerThread(queue, "syos-worker-" + (i + 1));
      workers[i].start();
    }
    LOGGER.info("ThreadPool started: " + poolSize + " workers, queue capacity " + queueCapacity);
  }

  // ── Task submission ──────────────────────────────────────────────────────

  /**
   * Submits a task for asynchronous execution by one of the worker threads.
   *
   * <p>If the queue is full, this method blocks the caller until space becomes available
   * (back-pressure). If the pool has been shut down, an {@link IllegalStateException} is
   * thrown immediately.
   *
   * @param task the {@link Runnable} to execute; must not be {@code null}
   * @throws IllegalStateException if {@link #shutdown()} has already been called
   * @throws RuntimeException      if the calling thread is interrupted while waiting on the
   *                               queue (the interrupt flag is restored before re-throwing)
   */
  public void submit(Runnable task) {
    if (shutdown) {
      throw new IllegalStateException("ThreadPool has been shut down — cannot accept new tasks");
    }
    if (task == null) {
      throw new IllegalArgumentException("Task cannot be null");
    }
    try {
      queue.enqueue(wrapTask(task));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while submitting task to thread pool", e);
    }
  }

  // ── Lifecycle ────────────────────────────────────────────────────────────

  /**
   * Initiates an orderly shutdown.
   *
   * <p>Sets the {@code shutdown} flag to prevent further task submissions, then signals every
   * {@link WorkerThread} to stop. Workers will finish their current task (if any) and then
   * exit. This method returns immediately; call {@link Thread#join()} on each worker if a
   * synchronous stop is required.
   */
  public synchronized void shutdown() {
    if (shutdown) {
      return; // idempotent
    }
    shutdown = true;
    for (WorkerThread worker : workers) {
      worker.shutdown();
    }
    LOGGER.info("ThreadPool shutdown initiated — " + workers.length + " worker(s) signalled");
  }

  // ── Metrics ──────────────────────────────────────────────────────────────

  /**
   * Returns the total number of tasks that have completed execution since this pool was
   * created.
   *
   * @return completed task count
   */
  public synchronized int getCompletedTaskCount() {
    return completedTasks;
  }

  /**
   * Returns the number of worker threads that are currently executing a task.
   *
   * @return active (busy) thread count
   */
  public synchronized int getActiveThreadCount() {
    return activeThreads;
  }

  /**
   * Returns the number of tasks currently waiting in the queue (not yet picked up by a
   * worker).
   *
   * @return pending queue size
   */
  public synchronized int getQueueSize() {
    return queue.size();
  }

  /**
   * Returns the number of worker threads in this pool (the value passed to the constructor).
   *
   * @return pool size
   */
  public int getPoolSize() {
    return workers.length;
  }

  /**
   * Returns {@code true} if this pool has been shut down.
   *
   * @return shutdown state
   */
  public boolean isShutdown() {
    return shutdown;
  }

  // ── Internal helpers ─────────────────────────────────────────────────────

  /**
   * Wraps a task with bookkeeping logic that increments / decrements {@code activeThreads}
   * and increments {@code completedTasks} upon completion.
   *
   * <p>Using a wrapper (rather than modifying {@link WorkerThread}) keeps metrics tracking
   * entirely within {@link ThreadPool} and avoids coupling {@link WorkerThread} to pool
   * internals.
   */
  private Runnable wrapTask(Runnable task) {
    return () -> {
      incrementActive();
      try {
        task.run();
      } finally {
        decrementActiveAndIncrementCompleted();
      }
    };
  }

  private synchronized void incrementActive() {
    activeThreads++;
  }

  private synchronized void decrementActiveAndIncrementCompleted() {
    activeThreads--;
    completedTasks++;
  }
}

