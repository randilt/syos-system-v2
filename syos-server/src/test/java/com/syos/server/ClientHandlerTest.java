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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit/integration tests for {@link ClientHandler}.
 *
 * <p>A real loopback {@link ServerSocket} is used so that {@link ObjectInputStream} and
 * {@link ObjectOutputStream} construction does not block — the same fix used in the client
 * module tests.
 */
@ExtendWith(MockitoExtension.class)
@Timeout(10)
class ClientHandlerTest {

  @Mock RequestRouter mockRouter;

  // ── Constructor guard tests ───────────────────────────────────────────────

  @Test
  void constructor_nullSocket_throws() throws Exception {
    assertThrows(IllegalArgumentException.class,
        () -> new ClientHandler(null, mockRouter, "c1"));
  }

  @Test
  void constructor_nullRouter_throws() throws Exception {
    try (ServerSocket ss = new ServerSocket(0);
         Socket s = new Socket("localhost", ss.getLocalPort())) {
      try (Socket accepted = ss.accept()) {
        assertThrows(IllegalArgumentException.class,
            () -> new ClientHandler(accepted, null, "c1"));
      }
    }
  }

  @Test
  void constructor_blankClientId_throws() throws Exception {
    try (ServerSocket ss = new ServerSocket(0);
         Socket s = new Socket("localhost", ss.getLocalPort())) {
      try (Socket accepted = ss.accept()) {
        assertThrows(IllegalArgumentException.class,
            () -> new ClientHandler(accepted, mockRouter, ""));
        assertThrows(IllegalArgumentException.class,
            () -> new ClientHandler(accepted, mockRouter, "  "));
      }
    }
  }

  // ── Normal request / response round-trip ─────────────────────────────────

  @Test
  void run_sendsResponseForRequest() throws Exception {
    when(mockRouter.route(any(Request.class))).thenReturn(Response.success("PONG"));

    try (ServerSocket ss = new ServerSocket(0)) {
      int port = ss.getLocalPort();

      // Client-side: connect and prepare streams
      Socket clientSocket = new Socket("localhost", port);

      // Server-side: accept and hand to ClientHandler
      Socket serverSocket = ss.accept();
      ClientHandler handler = new ClientHandler(serverSocket, mockRouter, "test-c1");
      Thread handlerThread = new Thread(handler);
      handlerThread.setDaemon(true);
      handlerThread.start();

      // Client writes OOS header then a request, reads response
      ObjectOutputStream clientOut = new ObjectOutputStream(clientSocket.getOutputStream());
      clientOut.flush();
      ObjectInputStream clientIn = new ObjectInputStream(clientSocket.getInputStream());

      clientOut.writeObject(Request.ping());
      clientOut.flush();

      Response resp = (Response) clientIn.readObject();
      assertTrue(resp.isSuccess());
      assertEquals("PONG", resp.getPayload());

      // Close client — handler should exit gracefully (EOF)
      clientSocket.close();
      handlerThread.join(2_000);

      verify(mockRouter, atLeastOnce()).route(any(Request.class));

      serverSocket.close();
    }
  }

  @Test
  void run_multipleRequests_allHandled() throws Exception {
    when(mockRouter.route(any(Request.class))).thenReturn(Response.success("PONG"));

    try (ServerSocket ss = new ServerSocket(0)) {
      Socket clientSocket = new Socket("localhost", ss.getLocalPort());
      Socket serverSocket = ss.accept();

      ClientHandler handler = new ClientHandler(serverSocket, mockRouter, "test-c2");
      Thread handlerThread = new Thread(handler);
      handlerThread.setDaemon(true);
      handlerThread.start();

      ObjectOutputStream clientOut = new ObjectOutputStream(clientSocket.getOutputStream());
      clientOut.flush();
      ObjectInputStream clientIn = new ObjectInputStream(clientSocket.getInputStream());

      int requestCount = 5;
      for (int i = 0; i < requestCount; i++) {
        clientOut.writeObject(Request.ping());
        clientOut.flush();
        clientOut.reset();
        Response resp = (Response) clientIn.readObject();
        assertTrue(resp.isSuccess(), "Request " + i + " must succeed");
      }

      clientSocket.close();
      handlerThread.join(2_000);

      verify(mockRouter, times(requestCount)).route(any(Request.class));
      serverSocket.close();
    }
  }

  // ── Router returns error response ─────────────────────────────────────────

  @Test
  void run_routerReturnsError_clientReceivesError() throws Exception {
    when(mockRouter.route(any(Request.class))).thenReturn(Response.error("bad request"));

    try (ServerSocket ss = new ServerSocket(0)) {
      Socket clientSocket = new Socket("localhost", ss.getLocalPort());
      Socket serverSocket = ss.accept();

      ClientHandler handler = new ClientHandler(serverSocket, mockRouter, "test-c3");
      Thread t = new Thread(handler);
      t.setDaemon(true);
      t.start();

      ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
      out.flush();
      ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());

      out.writeObject(Request.ping());
      out.flush();

      Response resp = (Response) in.readObject();
      assertFalse(resp.isSuccess());
      assertEquals("bad request", resp.getErrorMessage());

      clientSocket.close();
      t.join(2_000);
      serverSocket.close();
    }
  }

  // ── Graceful EOF on client disconnect ─────────────────────────────────────

  @Test
  void run_clientDisconnects_handlerExitsCleanly() throws Exception {
    try (ServerSocket ss = new ServerSocket(0)) {
      Socket clientSocket = new Socket("localhost", ss.getLocalPort());
      Socket serverSocket = ss.accept();

      ClientHandler handler = new ClientHandler(serverSocket, mockRouter, "test-c4");
      Thread t = new Thread(handler);
      t.setDaemon(true);
      t.start();

      // Write OOS header to let the handler's OIS initialise, then immediately disconnect
      ObjectOutputStream clientOut = new ObjectOutputStream(clientSocket.getOutputStream());
      clientOut.flush();
      clientSocket.close();

      // Handler should exit without throwing
      t.join(3_000);
      assertFalse(t.isAlive(), "Handler thread should have stopped after client disconnect");

      serverSocket.close();
    }
  }

  // ── IOException on broken pipe is handled gracefully ─────────────────────

  @Test
  void run_serverSocketClosed_handlerExitsWithoutException() throws Exception {
    try (ServerSocket ss = new ServerSocket(0)) {
      Socket clientSocket = new Socket("localhost", ss.getLocalPort());
      Socket serverSocket = ss.accept();

      ClientHandler handler = new ClientHandler(serverSocket, mockRouter, "test-c5");
      Thread t = new Thread(handler);
      t.setDaemon(true);
      t.start();

      // Write OOS header
      ObjectOutputStream clientOut = new ObjectOutputStream(clientSocket.getOutputStream());
      clientOut.flush();

      // Force-close the server side to cause an IOException in the handler
      serverSocket.close();

      t.join(3_000);
      assertFalse(t.isAlive(), "Handler thread should have exited after socket closed");

      clientSocket.close();
    }
  }
}
