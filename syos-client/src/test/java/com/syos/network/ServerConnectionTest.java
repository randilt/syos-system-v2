package com.syos.network;

import com.syos.network.ServerConnection;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ServerConnection}.
 *
 * <p>A real {@link ServerSocket} is created on a free OS-assigned port so no
 * hard-coded port numbers are needed.
 */
class ServerConnectionTest {

  private ServerSocket serverSocket;
  private int          freePort;

  @BeforeEach
  void openServerSocket() throws IOException {
    serverSocket = new ServerSocket(0);   // OS picks a free port
    freePort     = serverSocket.getLocalPort();
  }

  @AfterEach
  void closeServerSocket() throws IOException {
    if (serverSocket != null && !serverSocket.isClosed()) {
      serverSocket.close();
    }
  }

  // ── Constructor validation ────────────────────────────────────────────────

  @Test
  void constructor_nullHost_throws() {
    assertThrows(IllegalArgumentException.class, () -> new ServerConnection(null, 9090));
  }

  @Test
  void constructor_blankHost_throws() {
    assertThrows(IllegalArgumentException.class, () -> new ServerConnection("  ", 9090));
  }

  @Test
  void constructor_portZero_throws() {
    assertThrows(IllegalArgumentException.class, () -> new ServerConnection("localhost", 0));
  }

  @Test
  void constructor_portTooBig_throws() {
    assertThrows(IllegalArgumentException.class, () -> new ServerConnection("localhost", 99999));
  }

  @Test
  void constructor_valid_stores_host_and_port() {
    ServerConnection c = new ServerConnection("localhost", 9090);
    assertEquals("localhost", c.getHost());
    assertEquals(9090,        c.getPort());
  }

  // ── connect() ────────────────────────────────────────────────────────────

  @Test
  void connect_success_returnsTrue() throws Exception {
    // Accept one connection; write OOS header so client's OIS can initialise
    Thread acceptor = new Thread(() -> {
      try (Socket s = serverSocket.accept()) {
        new ObjectOutputStream(s.getOutputStream()).flush(); // enable client OIS
        s.getInputStream().read(); // block until client disconnects
      } catch (Exception ignored) {}
    });
    acceptor.setDaemon(true);
    acceptor.start();

    ServerConnection client = new ServerConnection("localhost", freePort);
    assertTrue(client.connect());
    assertTrue(client.isConnected());
    client.disconnect();
  }

  @Test
  void connect_noServer_returnsFalse() throws IOException {
    // Close the server socket so no listener exists on that port
    serverSocket.close();
    ServerConnection client = new ServerConnection("localhost", freePort);
    assertFalse(client.connect());
    assertFalse(client.isConnected());
  }

  @Test
  void connect_retriesUntilServerAvailable() throws Exception {
    int port = freePort;
    serverSocket.close(); // no listener yet

    Thread lateServer = new Thread(() -> {
      try (ServerSocket ss = new ServerSocket(port)) {
        try (Socket s = ss.accept()) {
          new ObjectOutputStream(s.getOutputStream()).flush();
          s.getInputStream().read();
        }
      } catch (IOException ignored) {}
    });
    lateServer.setDaemon(true);
    lateServer.start();

    ServerConnection client = new ServerConnection("localhost", port);
    assertTrue(client.connect(), "Should succeed after retry when server starts");
    assertTrue(client.isConnected());
    client.disconnect();
    lateServer.join(5_000);
  }

  // ── sendRequest() ─────────────────────────────────────────────────────────

  @Test
  void sendRequest_whenNotConnected_throwsIllegalState() {
    ServerConnection client = new ServerConnection("localhost", freePort);
    Request req = Request.ping();
    assertThrows(IllegalStateException.class, () -> client.sendRequest(req));
  }

  @Test
  void sendRequest_echoesServerResponse() throws Exception {
    Response expected = Response.success("pong");
    AtomicReference<Request> received = new AtomicReference<>();

    Thread server = new Thread(() -> {
      try (Socket sock = serverSocket.accept()) {
        // Write OOS header first, then read client OOS header — no deadlock
        ObjectOutputStream oos = new ObjectOutputStream(sock.getOutputStream());
        oos.flush();
        ObjectInputStream ois = new ObjectInputStream(sock.getInputStream());
        Request req = (Request) ois.readObject();
        received.set(req);
        oos.writeObject(expected);
        oos.flush();
      } catch (Exception ignored) {}
    });
    server.setDaemon(true);
    server.start();

    ServerConnection client = new ServerConnection("localhost", freePort);
    assertTrue(client.connect());

    Response actual = client.sendRequest(Request.ping());
    assertTrue(actual.isSuccess());
    assertEquals("pong", actual.getPayload());

    client.disconnect();
  }

  // ── disconnect() / isConnected() ─────────────────────────────────────────

  @Test
  void disconnect_setsIsConnectedFalse() throws Exception {
    Thread acceptor = new Thread(() -> {
      try (Socket s = serverSocket.accept()) {
        new ObjectOutputStream(s.getOutputStream()).flush();
        s.getInputStream().read(); // wait for client disconnect
      } catch (Exception ignored) {}
    });
    acceptor.setDaemon(true);
    acceptor.start();

    ServerConnection client = new ServerConnection("localhost", freePort);
    assertTrue(client.connect());
    assertTrue(client.isConnected());

    client.disconnect();
    assertFalse(client.isConnected());
  }

  @Test
  void close_isAliasForDisconnect() throws Exception {
    Thread acceptor = new Thread(() -> {
      try (Socket s = serverSocket.accept()) {
        new ObjectOutputStream(s.getOutputStream()).flush();
        s.getInputStream().read();
      } catch (Exception ignored) {}
    });
    acceptor.setDaemon(true);
    acceptor.start();

    ServerConnection client = new ServerConnection("localhost", freePort);
    assertTrue(client.connect());
    client.close();          // alias for disconnect
    assertFalse(client.isConnected());
  }

  @Test
  void disconnect_calledTwice_doesNotThrow() throws Exception {
    Thread acceptor = new Thread(() -> {
      try (Socket s = serverSocket.accept()) {
        new ObjectOutputStream(s.getOutputStream()).flush();
        s.getInputStream().read();
      } catch (Exception ignored) {}
    });
    acceptor.setDaemon(true);
    acceptor.start();

    ServerConnection client = new ServerConnection("localhost", freePort);
    client.connect();
    client.disconnect();
    assertDoesNotThrow(client::disconnect);
  }
}
