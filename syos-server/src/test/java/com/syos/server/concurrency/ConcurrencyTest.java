package com.syos.server.concurrency;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and integration tests for {@link RequestQueue}, {@link WorkerThread}, and
 * {@link ThreadPool}.
 *
 * <p>All tests are time-bounded via {@link Timeout} so that a liveness bug (e.g. a thread
 * permanently blocked in {@code wait()}) causes a test failure rather than a hung suite.
 *
 * <p>Note: Although tests use {@link CountDownLatch} for coordination, the production
 * code under test uses only raw {@code synchronized}/{@code wait}/{@code notifyAll}.
 */
@DisplayName("Concurrency — RequestQueue / WorkerThread / ThreadPool")
class ConcurrencyTest {

  // ══════════════════════════════════════════════════════════════════════════
  //  RequestQueue tests
  // ══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("RequestQueue")
  class RequestQueueTests {

    // ── Constructor ────────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor accepts valid capacity")
    void constructor_validCapacity_succeeds() {
      assertDoesNotThrow(() -> new RequestQueue(10));
    }

    @Test
    @DisplayName("constructor rejects capacity <= 0")
    void constructor_nonPositiveCapacity_throws() {
      assertThrows(IllegalArgumentException.class, () -> new RequestQueue(0));
      assertThrows(IllegalArgumentException.class, () -> new RequestQueue(-1));
    }

    @Test
    @DisplayName("enqueue rejects null task")
    void enqueue_nullTask_throws() {
      RequestQueue q = new RequestQueue(5);
      assertThrows(IllegalArgumentException.class,
          () -> q.enqueue(null));
    }

    // ── Single-thread enqueue / dequeue ────────────────────────────────────

    @Test
    @DisplayName("single thread: enqueue then dequeue returns same task")
    @Timeout(5)
    void singleThread_enqueueDequeue_returnsSameTask() throws InterruptedException {
      RequestQueue q    = new RequestQueue(3);
      Runnable     task = () -> {};

      q.enqueue(task);
      Runnable retrieved = q.dequeue();

      assertSame(task, retrieved);
    }

    @Test
    @DisplayName("single thread: FIFO ordering is preserved")
    @Timeout(5)
    void singleThread_fifoOrdering() throws InterruptedException {
      RequestQueue  q     = new RequestQueue(5);
      List<Integer> order = new ArrayList<>();

      for (int i = 1; i <= 4; i++) {
        final int n = i;
        q.enqueue(() -> order.add(n));
      }
      for (int i = 0; i < 4; i++) {
        q.dequeue().run();
      }

      assertEquals(List.of(1, 2, 3, 4), order);
    }

    @Test
    @DisplayName("size() and isEmpty() and isFull() reflect current state")
    @Timeout(5)
    void stateInspection_correctAfterEnqueueAndDequeue() throws InterruptedException {
      RequestQueue q = new RequestQueue(2);

      assertTrue(q.isEmpty());
      assertFalse(q.isFull());
      assertEquals(0, q.size());

      q.enqueue(() -> {});
      assertFalse(q.isEmpty());
      assertFalse(q.isFull());
      assertEquals(1, q.size());

      q.enqueue(() -> {});
      assertFalse(q.isEmpty());
      assertTrue(q.isFull());
      assertEquals(2, q.size());

      q.dequeue();
      assertEquals(1, q.size());
      assertFalse(q.isFull());

      q.dequeue();
      assertTrue(q.isEmpty());
      assertEquals(0, q.size());
    }

    @Test
    @DisplayName("circular buffer wraps correctly after head/tail pass capacity")
    @Timeout(5)
    void circularBuffer_wrapsAround() throws InterruptedException {
      RequestQueue  q      = new RequestQueue(3);
      List<Integer> result = new ArrayList<>();

      // Fill, drain, fill again — exercises the wrap-around logic
      for (int round = 0; round < 3; round++) {
        for (int i = 0; i < 3; i++) {
          final int val = round * 3 + i;
          q.enqueue(() -> result.add(val));
        }
        for (int i = 0; i < 3; i++) {
          q.dequeue().run();
        }
      }

      assertEquals(9, result.size());
      // Values must be in insertion order
      for (int i = 0; i < 9; i++) {
        assertEquals(i, result.get(i));
      }
    }

    // ── Blocking on full ───────────────────────────────────────────────────

    @Test
    @DisplayName("enqueue blocks producer when queue is full, unblocks when consumer dequeues")
    @Timeout(5)
    void enqueue_blocksWhenFull_unblocksOnDequeue() throws InterruptedException {
      RequestQueue q      = new RequestQueue(1);
      q.enqueue(() -> {}); // fill the queue

      CountDownLatch producerBlocked  = new CountDownLatch(1);
      CountDownLatch producerFinished = new CountDownLatch(1);

      Thread producer = new Thread(() -> {
        try {
          producerBlocked.countDown();
          q.enqueue(() -> {}); // should block until consumer dequeues
          producerFinished.countDown();
        } catch (InterruptedException ignored) {}
      });
      producer.start();

      // Wait until producer has called enqueue (and is presumably waiting)
      assertTrue(producerBlocked.await(2, TimeUnit.SECONDS));

      // Give the producer a moment to actually enter wait()
      Thread.sleep(50);

      // Consumer dequeues — should unblock the producer
      q.dequeue();

      assertTrue(producerFinished.await(2, TimeUnit.SECONDS),
          "Producer should have unblocked after consumer dequeued");
      producer.join(500);
    }

    // ── Blocking on empty ──────────────────────────────────────────────────

    @Test
    @DisplayName("dequeue blocks consumer when queue is empty, unblocks when producer enqueues")
    @Timeout(5)
    void dequeue_blocksWhenEmpty_unblocksOnEnqueue() throws InterruptedException {
      RequestQueue         q        = new RequestQueue(5);
      List<Runnable>       received = Collections.synchronizedList(new ArrayList<>());
      CountDownLatch       ready    = new CountDownLatch(1);
      CountDownLatch       done     = new CountDownLatch(1);

      Thread consumer = new Thread(() -> {
        try {
          ready.countDown();
          Runnable task = q.dequeue(); // blocks on empty queue
          received.add(task);
          done.countDown();
        } catch (InterruptedException ignored) {}
      });
      consumer.start();

      assertTrue(ready.await(2, TimeUnit.SECONDS));
      Thread.sleep(50); // let consumer enter wait()

      Runnable sentTask = () -> {};
      q.enqueue(sentTask);

      assertTrue(done.await(2, TimeUnit.SECONDS),
          "Consumer should have received the task");
      assertEquals(1, received.size());
      assertSame(sentTask, received.get(0));
      consumer.join(500);
    }

    // ── Multiple producers / consumers ────────────────────────────────────

    @Test
    @DisplayName("multiple producers and consumers: all tasks delivered exactly once")
    @Timeout(10)
    void multipleProducersAndConsumers_allTasksDelivered() throws InterruptedException {
      final int TASK_COUNT     = 200;
      final int PRODUCER_COUNT = 4;
      final int CONSUMER_COUNT = 4;

      RequestQueue        q         = new RequestQueue(20);
      List<Integer>       received  = Collections.synchronizedList(new ArrayList<>());
      CountDownLatch      allDone   = new CountDownLatch(TASK_COUNT);

      // Start consumers first so they block waiting for work
      Thread[] consumers = new Thread[CONSUMER_COUNT];
      for (int c = 0; c < CONSUMER_COUNT; c++) {
        consumers[c] = new Thread(() -> {
          try {
            while (!Thread.currentThread().isInterrupted()) {
              Runnable task = q.dequeue();
              task.run();
              allDone.countDown();
            }
          } catch (InterruptedException ignored) {}
        });
        consumers[c].setDaemon(true);
        consumers[c].start();
      }

      // Producers each submit TASK_COUNT / PRODUCER_COUNT tasks
      Thread[] producers = new Thread[PRODUCER_COUNT];
      int tasksPerProducer = TASK_COUNT / PRODUCER_COUNT;
      for (int p = 0; p < PRODUCER_COUNT; p++) {
        final int producerId = p;
        producers[p] = new Thread(() -> {
          try {
            for (int i = 0; i < tasksPerProducer; i++) {
              final int taskId = producerId * tasksPerProducer + i;
              q.enqueue(() -> received.add(taskId));
            }
          } catch (InterruptedException ignored) {}
        });
        producers[p].start();
      }

      // Wait for all tasks to finish
      assertTrue(allDone.await(8, TimeUnit.SECONDS),
          "Not all tasks were processed within the time limit");
      assertEquals(TASK_COUNT, received.size(), "Every task should have been executed exactly once");

      // Interrupt consumers to let them exit cleanly
      for (Thread consumer : consumers) consumer.interrupt();
    }
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  WorkerThread tests
  // ══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("WorkerThread")
  class WorkerThreadTests {

    @Test
    @DisplayName("constructor rejects null queue")
    void constructor_nullQueue_throws() {
      assertThrows(IllegalArgumentException.class,
          () -> new WorkerThread(null, "test-worker"));
    }

    @Test
    @DisplayName("worker executes a single enqueued task")
    @Timeout(5)
    void worker_executesSingleTask() throws InterruptedException {
      RequestQueue   q       = new RequestQueue(5);
      CountDownLatch latch   = new CountDownLatch(1);
      WorkerThread   worker  = new WorkerThread(q, "test-worker");
      worker.start();

      q.enqueue(latch::countDown);

      assertTrue(latch.await(3, TimeUnit.SECONDS), "Worker should execute the task");
      worker.shutdown();
      worker.join(1000);
    }

    @Test
    @DisplayName("worker executes multiple enqueued tasks in order")
    @Timeout(5)
    void worker_executesMultipleTasksInOrder() throws InterruptedException {
      RequestQueue  q      = new RequestQueue(10);
      List<Integer> order  = Collections.synchronizedList(new ArrayList<>());
      CountDownLatch done  = new CountDownLatch(5);
      WorkerThread  worker = new WorkerThread(q, "test-worker");
      worker.start();

      for (int i = 1; i <= 5; i++) {
        final int n = i;
        q.enqueue(() -> {
          order.add(n);
          done.countDown();
        });
      }

      assertTrue(done.await(3, TimeUnit.SECONDS));
      assertEquals(List.of(1, 2, 3, 4, 5), order);

      worker.shutdown();
      worker.join(1000);
    }

    @Test
    @DisplayName("shutdown stops the worker after current task")
    @Timeout(5)
    void shutdown_stopsWorkerAfterCurrentTask() throws InterruptedException {
      RequestQueue q      = new RequestQueue(5);
      WorkerThread worker = new WorkerThread(q, "test-worker");
      worker.start();

      assertTrue(worker.isRunning(), "Worker should be running initially");

      worker.shutdown();
      worker.join(2000);

      assertFalse(worker.isAlive(), "Worker thread should have terminated");
    }

    @Test
    @DisplayName("faulty task does not kill worker — subsequent tasks still execute")
    @Timeout(5)
    void faultyTask_doesNotKillWorker() throws InterruptedException {
      RequestQueue   q      = new RequestQueue(5);
      CountDownLatch latch  = new CountDownLatch(1);
      WorkerThread   worker = new WorkerThread(q, "test-worker");
      worker.start();

      // First task throws
      q.enqueue(() -> { throw new RuntimeException("deliberate failure"); });
      // Second task should still run
      q.enqueue(latch::countDown);

      assertTrue(latch.await(3, TimeUnit.SECONDS),
          "Worker should survive a faulty task and execute the next one");

      worker.shutdown();
      worker.join(1000);
    }

    @Test
    @DisplayName("isRunning returns false after shutdown()")
    @Timeout(5)
    void isRunning_falseAfterShutdown() throws InterruptedException {
      RequestQueue q      = new RequestQueue(5);
      WorkerThread worker = new WorkerThread(q, "test-worker");
      worker.start();

      worker.shutdown();
      worker.join(2000);

      assertFalse(worker.isRunning());
    }
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  ThreadPool tests
  // ══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("ThreadPool")
  class ThreadPoolTests {

    private ThreadPool pool;

    @AfterEach
    void tearDown() throws InterruptedException {
      if (pool != null && !pool.isShutdown()) {
        pool.shutdown();
        // Give workers a moment to drain
        Thread.sleep(200);
      }
    }

    // ── Constructor ────────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor rejects non-positive pool size")
    void constructor_nonPositivePoolSize_throws() {
      assertThrows(IllegalArgumentException.class, () -> new ThreadPool(0, 10));
      assertThrows(IllegalArgumentException.class, () -> new ThreadPool(-1, 10));
    }

    @Test
    @DisplayName("constructor rejects non-positive queue capacity")
    void constructor_nonPositiveQueueCapacity_throws() {
      assertThrows(IllegalArgumentException.class, () -> new ThreadPool(2, 0));
      assertThrows(IllegalArgumentException.class, () -> new ThreadPool(2, -5));
    }

    // ── Task execution ────────────────────────────────────────────────────

    @Test
    @DisplayName("pool executes a single submitted task")
    @Timeout(5)
    void pool_executesSingleTask() throws InterruptedException {
      pool = new ThreadPool(2, 10);
      CountDownLatch latch = new CountDownLatch(1);

      pool.submit(latch::countDown);

      assertTrue(latch.await(3, TimeUnit.SECONDS), "Task should have been executed");
    }

    @Test
    @DisplayName("pool executes all submitted tasks")
    @Timeout(10)
    void pool_executesAllTasks() throws InterruptedException {
      final int TASK_COUNT = 50;
      pool = new ThreadPool(4, 20);
      CountDownLatch latch = new CountDownLatch(TASK_COUNT);

      for (int i = 0; i < TASK_COUNT; i++) {
        pool.submit(latch::countDown);
      }

      assertTrue(latch.await(8, TimeUnit.SECONDS),
          "All " + TASK_COUNT + " tasks should have been executed");
    }

    @Test
    @DisplayName("completedTaskCount increments correctly")
    @Timeout(10)
    void completedTaskCount_incrementsForEachTask() throws InterruptedException {
      final int TASK_COUNT = 20;
      pool = new ThreadPool(3, 30);
      CountDownLatch latch = new CountDownLatch(TASK_COUNT);

      for (int i = 0; i < TASK_COUNT; i++) {
        pool.submit(latch::countDown);
      }

      assertTrue(latch.await(5, TimeUnit.SECONDS));
      // Allow the finally-block in wrapTask to complete
      Thread.sleep(100);

      assertEquals(TASK_COUNT, pool.getCompletedTaskCount());
    }

    // ── Shutdown ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("submit after shutdown throws IllegalStateException")
    @Timeout(5)
    void submit_afterShutdown_throwsIllegalState() throws InterruptedException {
      pool = new ThreadPool(2, 10);
      pool.shutdown();
      Thread.sleep(100); // let workers exit

      assertThrows(IllegalStateException.class, () -> pool.submit(() -> {}));
    }

    @Test
    @DisplayName("shutdown is idempotent — calling twice does not throw")
    @Timeout(5)
    void shutdown_calledTwice_doesNotThrow() throws InterruptedException {
      pool = new ThreadPool(2, 10);

      assertDoesNotThrow(() -> {
        pool.shutdown();
        pool.shutdown(); // second call should be a no-op
      });
    }

    @Test
    @DisplayName("isShutdown returns true after shutdown()")
    @Timeout(5)
    void isShutdown_trueAfterShutdown() throws InterruptedException {
      pool = new ThreadPool(2, 10);
      assertFalse(pool.isShutdown());

      pool.shutdown();
      Thread.sleep(100);

      assertTrue(pool.isShutdown());
    }

    // ── Concurrency correctness ────────────────────────────────────────────

    @Test
    @DisplayName("concurrent task execution: shared counter incremented exactly N times")
    @Timeout(10)
    void concurrentExecution_sharedCounterIsExact() throws InterruptedException {
      final int      TASK_COUNT = 100;
      pool = new ThreadPool(5, 50);
      // Use a synchronized list to avoid data races on the counter
      int[]          counter    = {0};
      Object         lock       = new Object();
      CountDownLatch latch      = new CountDownLatch(TASK_COUNT);

      for (int i = 0; i < TASK_COUNT; i++) {
        pool.submit(() -> {
          synchronized (lock) {
            counter[0]++;
          }
          latch.countDown();
        });
      }

      assertTrue(latch.await(8, TimeUnit.SECONDS));
      synchronized (lock) {
        assertEquals(TASK_COUNT, counter[0],
            "Counter must equal the number of tasks submitted");
      }
    }

    @Test
    @DisplayName("concurrent producers: pool handles tasks from multiple submitting threads")
    @Timeout(10)
    void multipleSubmittingThreads_allTasksProcessed() throws InterruptedException {
      final int PRODUCER_THREADS = 5;
      final int TASKS_PER_THREAD = 20;
      final int TOTAL            = PRODUCER_THREADS * TASKS_PER_THREAD;

      pool = new ThreadPool(4, 50);
      CountDownLatch latch = new CountDownLatch(TOTAL);

      Thread[] producers = new Thread[PRODUCER_THREADS];
      for (int p = 0; p < PRODUCER_THREADS; p++) {
        producers[p] = new Thread(() -> {
          for (int i = 0; i < TASKS_PER_THREAD; i++) {
            pool.submit(latch::countDown);
          }
        });
        producers[p].start();
      }

      for (Thread producer : producers) producer.join(3000);

      assertTrue(latch.await(8, TimeUnit.SECONDS),
          "All " + TOTAL + " tasks should have been processed");
    }

    @Test
    @DisplayName("getQueueSize decreases as workers drain the queue")
    @Timeout(10)
    void queueSize_decreasesAsWorkersDrain() throws InterruptedException {
      // Single worker so tasks queue up predictably
      pool = new ThreadPool(1, 100);
      CountDownLatch startLatch  = new CountDownLatch(1);
      CountDownLatch blockLatch  = new CountDownLatch(1);

      // First task blocks the single worker
      pool.submit(() -> {
        startLatch.countDown();
        try { blockLatch.await(); } catch (InterruptedException ignored) {}
      });

      // Wait for worker to pick up the first task
      assertTrue(startLatch.await(3, TimeUnit.SECONDS));

      // Now enqueue more tasks while worker is busy
      for (int i = 0; i < 5; i++) pool.submit(() -> {});

      int queuedSize = pool.getQueueSize();
      assertTrue(queuedSize > 0, "Queue should have pending tasks while worker is busy");

      // Unblock worker — queue should drain
      blockLatch.countDown();

      long deadline = System.currentTimeMillis() + 3000;
      while (pool.getQueueSize() > 0 && System.currentTimeMillis() < deadline) {
        Thread.sleep(20);
      }
      assertEquals(0, pool.getQueueSize(), "Queue should be empty after tasks are drained");
    }

    @Test
    @DisplayName("null task submission throws IllegalArgumentException")
    void submitNullTask_throws() {
      pool = new ThreadPool(2, 10);
      assertThrows(IllegalArgumentException.class, () -> pool.submit(null));
    }
  }
}
