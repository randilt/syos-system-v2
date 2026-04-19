package com.syos.server.concurrency;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class RequestQueueTest {

  @Test
  void constructor_rejectsNonPositiveCapacity() {
    assertThrows(IllegalArgumentException.class, () -> new RequestQueue(0));
    assertThrows(IllegalArgumentException.class, () -> new RequestQueue(-1));
  }

  @Test
  void enqueueDequeue_preservesFifoOrder() throws Exception {
    RequestQueue queue = new RequestQueue(3);
    StringBuilder sb = new StringBuilder();

    queue.enqueue(() -> sb.append("A"));
    queue.enqueue(() -> sb.append("B"));
    queue.enqueue(() -> sb.append("C"));

    queue.dequeue().run();
    queue.dequeue().run();
    queue.dequeue().run();

    assertEquals("ABC", sb.toString());
    assertTrue(queue.isEmpty());
  }

  @Test
  @Timeout(5)
  void enqueue_blocksWhenFull_thenUnblocks() throws Exception {
    RequestQueue queue = new RequestQueue(1);
    queue.enqueue(() -> {});

    CountDownLatch producerDone = new CountDownLatch(1);
    Thread producer = new Thread(() -> {
      try {
        queue.enqueue(() -> {});
        producerDone.countDown();
      } catch (InterruptedException ignored) {
      }
    });
    producer.start();

    Thread.sleep(100);
    assertEquals(1, queue.size());

    queue.dequeue();
    assertTrue(producerDone.await(2, TimeUnit.SECONDS));
  }

  @Test
  @Timeout(5)
  void dequeue_blocksWhenEmpty_thenUnblocks() throws Exception {
    RequestQueue queue = new RequestQueue(1);
    CountDownLatch consumerDone = new CountDownLatch(1);

    Thread consumer = new Thread(() -> {
      try {
        queue.dequeue();
        consumerDone.countDown();
      } catch (InterruptedException ignored) {
      }
    });
    consumer.start();

    Thread.sleep(100);
    queue.enqueue(() -> {});

    assertTrue(consumerDone.await(2, TimeUnit.SECONDS));
  }

  @Test
  void queueStateFlags_areAccurate() throws Exception {
    RequestQueue queue = new RequestQueue(2);

    assertTrue(queue.isEmpty());
    assertFalse(queue.isFull());

    queue.enqueue(() -> {});
    assertFalse(queue.isEmpty());
    assertFalse(queue.isFull());

    queue.enqueue(() -> {});
    assertTrue(queue.isFull());

    queue.dequeue();
    assertFalse(queue.isFull());
  }
}
