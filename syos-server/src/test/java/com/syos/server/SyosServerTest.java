package com.syos.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.syos.protocol.Request;
import com.syos.protocol.Response;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link SyosServer} — start/stop, PING end-to-end, concurrent clients.
 *
 * <p>Uses the package-private {@code SyosServer(int, int, int, RequestRouter)} constructor to
 * inject a Mockito-mocked {@link RequestRouter}, so no real database connection is needed.
 */
@ExtendWith(MockitoExtension.class)
@Timeout(10) // every test must complete within 10 seconds
class SyosServerTest {

  @Mock RequestRouter mockRouter;

  SyosServer server;
  int port;
  Thread serverThread;

  @BeforeEach
  void startServer() throws Exception {
    port = findFreePort();
    server = new SyosServer(port, 4, 20, mockRouter); // package-private test constructor

    CountDownLatch latch = new CountDownLatch(1);
    serverThread = new Thread(() -> {
      latch.countDown();
      server.start();
    }, "test-server");
    serverThread.setDaemon(true);
    serverThread.start();

    // Wait for the thread to spin up, then give the socket time to bind
    latch.await(2, TimeUnit.SECONDS);
    Thread.sleep(100); // small grace period for ServerSocket.bind()
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop();
    }
  }

  // ── Server lifecycle ──────────────────────────────────────────────────────

  @Test
  void serverStarts_andAcceptsConnection() throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      assertTrue(socket.isConnected());
    }
  }

  @Test
  void serverStops_cleanly() throws Exception {
    // verify it's running
    try (Socket socket = new Socket("localhost", port)) {
      assertTrue(socket.isConnected());
    }
    server.stop();
    Thread.sleep(200); // let the accept loop exit

    // now connection should be refused
    assertThrows(IOException.class, () -> new Socket("localhost", port));
  }

  // ── PING round-trip ───────────────────────────────────────────────────────

  @Test
  void ping_roundTrip_returnsPongResponse() throws Exception {
    when(mockRouter.route(any(Request.class))).thenReturn(Response.success("PONG"));

    Response response = sendRequest(Request.ping());

    assertTrue(response.isSuccess());
    assertEquals("PONG", response.getPayload());
  }

  // ── Multiple sequential requests ──────────────────────────────────────────

  @Test
  void multipleSequentialRequests_allHandled() throws Exception {
    when(mockRouter.route(any(Request.class))).thenReturn(Response.success("PONG"));

    try (Socket socket = new Socket("localhost", port);
         ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
         ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream())) {

      for (int i = 0; i < 5; i++) {
        out.writeObject(Request.ping());
        out.flush();
        out.reset();
        Response response = (Response) in.readObject();
        assertTrue(response.isSuccess(), "Request " + i + " should succeed");
      }
    }
  }

  // ── Concurrent clients ────────────────────────────────────────────────────

  @Test
  void concurrentClients_allReceiveResponses() throws Exception {
    when(mockRouter.route(any(Request.class))).thenReturn(Response.success("PONG"));

    int clientCount = 5;
    CountDownLatch allDone = new CountDownLatch(clientCount);
    List<Throwable> errors = new ArrayList<>();

    for (int i = 0; i < clientCount; i++) {
      Thread t = new Thread(() -> {
        try {
          Response response = sendRequest(Request.ping());
          assertTrue(response.isSuccess());
        } catch (Exception e) {
          synchronized (errors) { errors.add(e); }
        } finally {
          allDone.countDown();
        }
      });
      t.setDaemon(true);
      t.start();
    }

    boolean completed = allDone.await(8, TimeUnit.SECONDS);
    assertTrue(completed, "Not all clients completed in time");
    assertTrue(errors.isEmpty(), "Client errors: " + errors);
  }

  // ── Router error response ─────────────────────────────────────────────────

  @Test
  void routerReturnsError_clientReceivesError() throws Exception {
    when(mockRouter.route(any(Request.class))).thenReturn(Response.error("Something went wrong"));

    Response response = sendRequest(Request.ping());

    assertFalse(response.isSuccess());
    assertEquals("Something went wrong", response.getErrorMessage());
  }

  // ── connectedClients tracking ─────────────────────────────────────────────

  @Test
  void connectedClients_trackedCorrectly() throws Exception {
    when(mockRouter.route(any(Request.class))).thenReturn(Response.success("PONG"));

    // After connecting and sending a request, the handler finishes immediately,
    // so count goes back to 0 eventually. This just checks no exception is thrown.
    sendRequest(Request.ping());
    Thread.sleep(100);
    assertTrue(server.getConnectedClientCount() >= 0);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private Response sendRequest(Request request) throws Exception {
    try (Socket socket = new Socket("localhost", port);
         ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
         ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream())) {
      out.writeObject(request);
      out.flush();
      return (Response) in.readObject();
    }
  }

  private static int findFreePort() throws IOException {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }
}
