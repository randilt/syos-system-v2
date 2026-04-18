package com.syos.server.concurrency;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A long-lived daemon thread that continuously dequeues and executes {@link Runnable} tasks
 * from a shared {@link RequestQueue}.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Created and started by {@link ThreadPool}.</li>
 *   <li>Runs an infinite loop: blocks on {@link RequestQueue#dequeue()} until a task arrives,
 *       then executes it inline on this thread.</li>
 *   <li>Stopped via {@link #shutdown()}: the {@code running} flag is set to {@code false} and
 *       the thread is interrupted, which unblocks the {@code dequeue()} call (it throws
 *       {@link InterruptedException}, which the run-loop catches and re-checks
 *       {@code running}).</li>
 * </ol>
 *
 * <h2>Error isolation</h2>
 * <p>Any {@link RuntimeException} thrown by a task is caught and logged so that a single
 * misbehaving task cannot kill the worker thread.
 *
 * <h2>Volatile flag</h2>
 * <p>{@code running} is {@code volatile} so that the write performed by {@link #shutdown()}
 * (on one thread) is immediately visible to the {@link #run()} loop (on this thread) without
 * needing to hold a lock.
 */
public class WorkerThread extends Thread {

  private static final Logger LOGGER = Logger.getLogger(WorkerThread.class.getName());

  private final RequestQueue   queue;
  /** Signals the run-loop to exit after the current task completes. */
  private volatile boolean     running = true;

  /**
   * Constructs a worker thread bound to the given queue.
   *
   * @param queue the shared request queue from which tasks are pulled; must not be {@code null}
   * @param name  a descriptive name for this thread (appears in thread dumps and log messages)
   */
  public WorkerThread(RequestQueue queue, String name) {
    super(name);
    if (queue == null) {
      throw new IllegalArgumentException("RequestQueue cannot be null");
    }
    this.queue = queue;
    setDaemon(true); // do not prevent JVM shutdown if only daemon threads remain
  }

  // ── Run loop ─────────────────────────────────────────────────────────────

  /**
   * Continuously dequeues and executes tasks until {@link #shutdown()} is called.
   *
   * <p>The loop structure is:
   * <pre>
   *   while (running) {
   *     task = queue.dequeue();   // blocks until a task is available
   *     task.run();               // executes the task on this thread
   *   }
   * </pre>
   *
   * <p>If {@link RequestQueue#dequeue()} throws {@link InterruptedException} (because
   * {@link #shutdown()} interrupted this thread), the interrupt status is restored and the
   * loop exits cleanly.
   */
  @Override
  public void run() {
    LOGGER.fine(getName() + " started");
    while (running) {
      try {
        Runnable task = queue.dequeue();
        executeTask(task);
      } catch (InterruptedException e) {
        // Restore the interrupt flag so that callers higher up the stack can observe it.
        Thread.currentThread().interrupt();
        if (!running) {
          LOGGER.fine(getName() + " interrupted during shutdown — exiting");
          break;
        }
        // Spurious interrupt while still running: clear the flag and continue.
        Thread.interrupted();
      }
    }
    LOGGER.fine(getName() + " stopped");
  }

  /**
   * Executes a single task, catching any unchecked exception so that a faulty task cannot
   * terminate this worker thread.
   *
   * @param task the task to run
   */
  private void executeTask(Runnable task) {
    try {
      task.run();
    } catch (RuntimeException e) {
      LOGGER.log(Level.WARNING, getName() + " — uncaught exception in task: " + e.getMessage(), e);
    }
  }

  // ── Lifecycle ────────────────────────────────────────────────────────────

  /**
   * Signals this worker to stop after its current task finishes and interrupts it if it is
   * currently blocked inside {@link RequestQueue#dequeue()}.
   *
   * <p>This method returns immediately; it does not wait for the thread to terminate.
   * Call {@link Thread#join()} afterwards if a synchronous stop is required.
   */
  public void shutdown() {
    running = false;
    interrupt(); // unblock queue.dequeue() if the thread is waiting in Object.wait()
  }

  /**
   * Returns {@code true} if this worker has not yet been asked to stop.
   *
   * @return {@code running} flag value
   */
  public boolean isRunning() {
    return running;
  }
}
