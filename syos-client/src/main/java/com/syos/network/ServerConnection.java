package com.syos.network;

import com.syos.protocol.Request;
import com.syos.protocol.Response;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe TCP connection to the SYOS server.
 *
 * <p>A single {@code ServerConnection} instance may be shared across all Swing panels; every
 * access to the underlying socket streams is guarded by the instance monitor.
 *
 * <h2>Connection policy</h2>
 * <p>{@link #connect()} retries up to {@value #MAX_RETRIES} times with a
 * {@value #RETRY_DELAY_MS} ms pause between attempts. It returns {@code true} on success and
 * {@code false} if all attempts fail — it never throws.
 *
 * <p>Call {@link #disconnect()} when the application exits.
 */
public class ServerConnection {

  private static final Logger LOGGER        = Logger.getLogger(ServerConnection.class.getName());
  private static final int    MAX_RETRIES    = 3;
  private static final long   RETRY_DELAY_MS = 1_000L;

  private final String host;
  private final int    port;

  private Socket             socket;
  private ObjectOutputStream out;
  private ObjectInputStream  in;
  private volatile boolean   connected = false;

  public ServerConnection(String host, int port) {
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("Host cannot be null or blank");
    }
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("Port must be between 1 and 65535");
    }
    this.host = host;
    this.port = port;
  }

  // ── Connect ──────────────────────────────────────────────────────────────

  /**
   * Attempts to open the TCP connection, retrying up to {@value #MAX_RETRIES} times.
   *
   * @return {@code true} if connected successfully, {@code false} after all retries exhausted
   */
  public boolean connect() {
    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 2_000);  // 2-second connect timeout
        s.setSoTimeout(10_000);                                // 10-second read timeout
        ObjectOutputStream o = new ObjectOutputStream(s.getOutputStream());
        o.flush();
        ObjectInputStream  i = new ObjectInputStream(s.getInputStream());
        synchronized (this) {
          socket    = s;
          out       = o;
          in        = i;
          connected = true;
        }
        LOGGER.info("Connected to " + host + ":" + port + " (attempt " + attempt + ")");
        return true;
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Attempt " + attempt + " failed: " + e.getMessage());
        if (attempt < MAX_RETRIES) {
          try {
            Thread.sleep(RETRY_DELAY_MS);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
          }
        }
      }
    }
    connected = false;
    LOGGER.warning("Could not connect to " + host + ":" + port + " after " + MAX_RETRIES + " attempts");
    return false;
  }

  // ── Send/Receive ──────────────────────────────────────────────────────────

  /**
   * Sends {@code request} to the server and returns the response.
   *
   * <p>Synchronized to prevent interleaved reads/writes from multiple SwingWorker threads.
   *
   * @throws IOException            on I/O failure
   * @throws ClassNotFoundException if the server returns an unknown class
   * @throws IllegalStateException  if not connected
   */
  public synchronized Response sendRequest(Request request)
      throws IOException, ClassNotFoundException {
    if (!connected || socket == null || socket.isClosed()) {
      throw new IllegalStateException("Not connected — call connect() first");
    }
    out.writeObject(request);
    out.flush();
    out.reset(); // clear serialization cache to avoid stale references
    return (Response) in.readObject();
  }

  /**
   * Sends a health-check ping to the server. Returns true if the server responds correctly,
   * false on any error.
   */
  public boolean ping() {
    if (!isConnected()) {
      return false;
    }
    try {
      Response response = sendRequest(Request.ping());
      return response.isSuccess() && "PONG".equals(response.getPayload());
    } catch (Exception e) {
      return false;
    }
  }

  // ── Disconnect ────────────────────────────────────────────────────────────

  /** Closes the socket and marks the connection as disconnected. */
  public synchronized void disconnect() {
    try {
      if (socket != null && !socket.isClosed()) {
        socket.close();
      }
    } catch (IOException ignored) { /* best-effort */ }
    connected = false;
  }

  /** Alias for {@link #disconnect()} for backward compatibility. */
  public synchronized void close() {
    disconnect();
  }

  // ── Status ────────────────────────────────────────────────────────────────

  /** Returns {@code true} if the socket is open and the connection flag is set. */
  public boolean isConnected() {
    return connected && socket != null && socket.isConnected() && !socket.isClosed();
  }

  public String getHost() { return host; }
  public int    getPort() { return port; }
}
