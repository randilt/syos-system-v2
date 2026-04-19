package com.syos.server.concurrency;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ThreadPoolTest {

  @Test
  void constructor_rejectsInvalidArgs() {
    assertThrows(IllegalArgumentException.class, () -> new ThreadPool(0, 10));
    assertThrows(IllegalArgumentException.class, () -> new ThreadPool(2, 0));
  }

  @Test
  @Timeout(10)
  void submit_executesTask() throws Exception {
    ThreadPool pool = new ThreadPool(2, 10);
    CountDownLatch done = new CountDownLatch(1);

    pool.submit(done::countDown);

    assertTrue(done.await(2, TimeUnit.SECONDS));
    pool.shutdown();
  }

  @Test
  @Timeout(10)
  void submit_updatesCompletedTaskCount() throws Exception {
    ThreadPool pool = new ThreadPool(2, 10);
    CountDownLatch done = new CountDownLatch(3);

    pool.submit(done::countDown);
    pool.submit(done::countDown);
    pool.submit(done::countDown);

    assertTrue(done.await(3, TimeUnit.SECONDS));

    Thread.sleep(100);
    assertEquals(3, pool.getCompletedTaskCount());
    pool.shutdown();
  }

  @Test
  void shutdown_setsFlagAndRejectsNewTasks() {
    ThreadPool pool = new ThreadPool(1, 2);
    pool.shutdown();

    assertTrue(pool.isShutdown());
    assertThrows(IllegalStateException.class, () -> pool.submit(() -> {}));
  }

  @Test
  void submit_nullTask_throws() {
    ThreadPool pool = new ThreadPool(1, 2);
    try {
      assertThrows(IllegalArgumentException.class, () -> pool.submit(null));
    } finally {
      pool.shutdown();
    }
  }

  @Test
  @Timeout(10)
  void getPoolSize_returnsConfiguredValue() {
    ThreadPool pool = new ThreadPool(3, 5);
    try {
      assertEquals(3, pool.getPoolSize());
    } finally {
      pool.shutdown();
    }
  }
}
