package com.syos.server;

import com.syos.server.concurrency.ThreadPool;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TCP server that accepts incoming client connections and delegates each one to a
 * {@link ClientHandler} running in the shared {@link ThreadPool}.
 *
 * <p>Call {@link #start()} to enter the accept loop. The server runs until the JVM exits or
 * {@link #stop()} is called from another thread.
 */
public class SyosServer {

  private static final Logger LOGGER = Logger.getLogger(SyosServer.class.getName());
  private static final int DEFAULT_THREAD_POOL_SIZE = 20;

  private final int port;
  private final RequestRouter router;
  private final ThreadPool threadPool;
  private volatile boolean running;
  private ServerSocket serverSocket;

  public SyosServer(int port, ServerApplicationContext context) {
    this(port, context, DEFAULT_THREAD_POOL_SIZE);
  }

  public SyosServer(int port, ServerApplicationContext context, int threadPoolSize) {
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("Port must be between 1 and 65535");
    }
    if (context == null) throw new IllegalArgumentException("Context cannot be null");
    this.port = port;
    this.router = context.getRequestRouter();
    this.threadPool = new ThreadPool(threadPoolSize);
  }

  /**
   * Binds the server socket and enters the blocking accept loop.
   * Returns only when {@link #stop()} is called or a fatal error occurs.
   */
  public void start() {
    running = true;
    LOGGER.info("SYOS Server starting on port " + port + "...");

    try (ServerSocket socket = new ServerSocket(port)) {
      this.serverSocket = socket;
      LOGGER.info("SYOS Server listening on port " + port);

      while (running) {
        try {
          Socket clientSocket = socket.accept();
          threadPool.submit(new ClientHandler(clientSocket, router));
        } catch (IOException e) {
          if (running) {
            LOGGER.log(Level.WARNING, "Error accepting connection", e);
          }
        }
      }
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Failed to bind server socket on port " + port, e);
    } finally {
      threadPool.shutdown();
      LOGGER.info("SYOS Server stopped");
    }
  }

  /** Signals the accept loop to stop and closes the server socket. */
  public void stop() {
    running = false;
    if (serverSocket != null && !serverSocket.isClosed()) {
      try {
        serverSocket.close();
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Error closing server socket", e);
      }
    }
  }
}
