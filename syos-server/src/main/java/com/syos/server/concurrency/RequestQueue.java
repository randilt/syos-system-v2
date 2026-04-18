package com.syos.server.concurrency;

/**
 * A thread-safe, bounded, blocking queue of pending {@link Runnable} tasks implemented
 * entirely with raw Java synchronization primitives.
 *
 * <h2>Design — Producer-Consumer Pattern</h2>
 * <p>The queue uses a <em>circular buffer</em> backed by a plain {@code Object[]} array.
 * Two integer pointers, {@code head} (read cursor) and {@code tail} (write cursor), advance
 * modulo the capacity so that the array is reused continuously without shifting elements.
 * A separate {@code count} variable tracks the number of live elements, which avoids the
 * ambiguity that arises when {@code head == tail} in a pointer-only design (it could mean
 * either full or empty).
 *
 * <h2>Why {@code wait} / {@code notifyAll}?</h2>
 * <ul>
 *   <li><strong>Producer blocks on full:</strong> when the queue is at capacity, callers of
 *       {@link #enqueue(Runnable)} call {@code wait()}, releasing the monitor until a consumer
 *       removes an element and calls {@code notifyAll()}.</li>
 *   <li><strong>Consumer blocks on empty:</strong> when the queue is empty, callers of
 *       {@link #dequeue()} call {@code wait()}, releasing the monitor until a producer adds an
 *       element and calls {@code notifyAll()}.</li>
 *   <li>{@code notifyAll()} is used (rather than {@code notify()}) to wake <em>all</em> waiting
 *       threads so that both producers and consumers have a chance to re-evaluate their
 *       condition predicate. This prevents the scenario where a consumer wakes another consumer
 *       instead of a producer, leaving an available slot untaken.</li>
 *   <li>The condition check is always wrapped in a {@code while} loop (not {@code if}) to guard
 *       against <em>spurious wakeups</em> — the JVM specification permits a thread to wake up
 *       from {@code wait()} even when no {@code notify} was issued.</li>
 * </ul>
 *
 * <p>All public methods are {@code synchronized} on {@code this}, so only one thread at a time
 * can inspect or mutate the buffer state.
 */
public class RequestQueue {

  private final Object[] buffer;
  private       int      head;       // index of the next element to dequeue
  private       int      tail;       // index of the next empty slot to enqueue into
  private       int      count;      // number of live elements currently in the buffer
  private final int      capacity;

  /**
   * Constructs a bounded blocking queue with the given maximum capacity.
   *
   * @param capacity maximum number of tasks the queue can hold at any one time;
   *                 must be a positive integer
   * @throws IllegalArgumentException if {@code capacity <= 0}
   */
  public RequestQueue(int capacity) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("Capacity must be positive, got: " + capacity);
    }
    this.capacity = capacity;
    this.buffer   = new Object[capacity];
    this.head     = 0;
    this.tail     = 0;
    this.count    = 0;
  }

  // ── Producer side ────────────────────────────────────────────────────────

  /**
   * Inserts a task at the tail of the queue, blocking the calling thread if the queue is
   * currently at capacity.
   *
   * <p>Once space becomes available, the task is placed in the buffer, {@code count} is
   * incremented, {@code tail} advances (wrapping around the array), and all threads waiting
   * on this object's monitor are notified.
   *
   * @param task the {@link Runnable} to enqueue; must not be {@code null}
   * @throws InterruptedException     if the calling thread is interrupted while waiting
   * @throws IllegalArgumentException if {@code task} is {@code null}
   */
  public synchronized void enqueue(Runnable task) throws InterruptedException {
    if (task == null) {
      throw new IllegalArgumentException("Task cannot be null");
    }
    // Spin-wait (on condition) until there is space in the buffer.
    while (count >= capacity) {
      wait(); // releases the monitor; re-acquires it before the while condition is re-checked
    }
    buffer[tail] = task;
    tail         = (tail + 1) % capacity; // advance tail, wrapping around
    count++;
    notifyAll(); // wake any consumer threads that were blocked on an empty queue
  }

  // ── Consumer side ────────────────────────────────────────────────────────

  /**
   * Retrieves and removes the task at the head of the queue, blocking the calling thread if
   * the queue is currently empty.
   *
   * <p>Once a task is available, it is read from the buffer, the slot is nulled out to allow
   * GC, {@code count} is decremented, {@code head} advances (wrapping around), and all
   * waiting threads are notified.
   *
   * @return the oldest task in the queue (never {@code null})
   * @throws InterruptedException if the calling thread is interrupted while waiting
   */
  public synchronized Runnable dequeue() throws InterruptedException {
    // Spin-wait (on condition) until there is at least one task.
    while (count == 0) {
      wait(); // releases the monitor; re-acquires it before the while condition is re-checked
    }
    Runnable task = (Runnable) buffer[head];
    buffer[head]  = null;              // allow GC of the held reference
    head          = (head + 1) % capacity; // advance head, wrapping around
    count--;
    notifyAll(); // wake any producer threads that were blocked on a full queue
    return task;
  }

  // ── Inspection ───────────────────────────────────────────────────────────

  /** Returns the number of tasks currently held in the queue. */
  public synchronized int size() {
    return count;
  }

  /** Returns {@code true} if the queue contains no tasks. */
  public synchronized boolean isEmpty() {
    return count == 0;
  }

  /** Returns {@code true} if the queue has reached its maximum capacity. */
  public synchronized boolean isFull() {
    return count >= capacity;
  }

  /** Returns the maximum number of tasks this queue can hold at any one time. */
  public int getCapacity() {
    return capacity; // final — no synchronization needed
  }
}

