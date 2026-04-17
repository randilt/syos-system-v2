package com.syos.server.concurrency;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A bounded, blocking queue of pending client-request tasks.
 * Wraps {@link LinkedBlockingQueue} to expose a domain-meaningful API.
 */
public class RequestQueue {

  private final LinkedBlockingQueue<Runnable> queue;

  public RequestQueue(int capacity) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("Capacity must be positive");
    }
    this.queue = new LinkedBlockingQueue<>(capacity);
  }

  /**
   * Adds a task to the queue, blocking until space is available.
   *
   * @param task the runnable to enqueue
   * @throws InterruptedException if interrupted while waiting
   */
  public void enqueue(Runnable task) throws InterruptedException {
    if (task == null) {
      throw new IllegalArgumentException("Task cannot be null");
    }
    queue.put(task);
  }

  /**
   * Retrieves and removes the head of the queue, waiting up to 1 second if empty.
   *
   * @return the next task, or {@code null} if the timeout elapsed with no task available
   * @throws InterruptedException if interrupted while waiting
   */
  public Runnable dequeue() throws InterruptedException {
    return queue.poll(1, TimeUnit.SECONDS);
  }

  /** Returns the current number of tasks waiting in the queue. */
  public int size() {
    return queue.size();
  }
}
