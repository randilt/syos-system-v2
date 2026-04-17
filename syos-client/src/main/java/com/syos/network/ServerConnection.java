package com.syos.network;

import com.syos.protocol.Request;
import com.syos.protocol.Response;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Thread-safe TCP connection to the SYOS server.
 *
 * <p>A single {@code ServerConnection} instance may be shared across Swing components; all
 * access to the underlying socket is guarded by the instance monitor.
 *
 * <p>Call {@link #close()} when the application exits.
 */
public class ServerConnection {

  private final String host;
  private final int port;

  private Socket socket;
  private ObjectOutputStream out;
  private ObjectInputStream in;

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

  /**
   * Opens the TCP connection to the server. Must be called before {@link #send(Request)}.
   *
   * @throws IOException if the connection cannot be established
   */
  public synchronized void connect() throws IOException {
    socket = new Socket(host, port);
    // ObjectOutputStream header must be flushed before constructing ObjectInputStream
    out = new ObjectOutputStream(socket.getOutputStream());
    out.flush();
    in = new ObjectInputStream(socket.getInputStream());
  }

  /**
   * Sends a request to the server and returns the server's response.
   *
   * <p>This method is {@code synchronized} to prevent interleaved writes/reads when called
   * from multiple Swing threads.
   *
   * @param request the command to execute
   * @return the server response
   * @throws IOException if an I/O error occurs
   * @throws ClassNotFoundException if the response class is not on the classpath
   */
  public synchronized Response send(Request request) throws IOException, ClassNotFoundException {
    if (socket == null || socket.isClosed()) {
      throw new IllegalStateException("Not connected — call connect() first");
    }
    out.writeObject(request);
    out.flush();
    out.reset(); // clear serialization cache
    return (Response) in.readObject();
  }

  /** Closes the connection, ignoring any errors during shutdown. */
  public synchronized void close() {
    try {
      if (socket != null && !socket.isClosed()) {
        socket.close();
      }
    } catch (IOException ignored) {
      // best-effort close
    }
  }

  public boolean isConnected() {
    return socket != null && socket.isConnected() && !socket.isClosed();
  }

  public String getHost() { return host; }
  public int getPort()   { return port; }
}
