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
 *
 * <h2>Construction</h2>
 * <p>Use {@link #SyosServer(int, int, int)} for normal operation; the context is obtained from
 * {@link ServerApplicationContext#getInstance()} and initialized inside {@link #start()}. For
 * unit / integration tests, use the package-private
 * {@link #SyosServer(int, int, int, RequestRouter)} constructor to inject a mock router and
 * bypass real DB initialization.
 */
public class SyosServer {

  private static final Logger LOGGER = Logger.getLogger(SyosServer.class.getName());

  private final int port;
  private final int pushPort;
  private final ThreadPool threadPool;

  /** Injected router; null when using the public constructor (set lazily in start()). */
  private RequestRouter router;
  private PushRegistry pushRegistry;

  private volatile boolean running;
  private ServerSocket serverSocket;
  private ServerSocket pushServerSocket;
  private Thread pushAcceptThread;

  private int connectedClients = 0;
  private int nextClientId = 0;

  // ── Constructors ──────────────────────────────────────────────────────────

  /**
   * Production constructor. {@link ServerApplicationContext} is initialized inside
   * {@link #start()}; no DB connection is created here.
   *
   * @param port          TCP port to listen on (1–65535)
   * @param threadPoolSize number of worker threads in the pool
   * @param queueCapacity  maximum number of tasks that may queue before blocking
   */
  public SyosServer(int port, int threadPoolSize, int queueCapacity) {
    this(port, threadPoolSize, queueCapacity, null, null);
  }

  /**
   * Production constructor with explicit push registry injection.
   */
  public SyosServer(int port, int threadPoolSize, int queueCapacity, PushRegistry pushRegistry) {
    this(port, threadPoolSize, queueCapacity, null, pushRegistry);
  }

  /**
   * Test constructor. Injects a pre-built {@link RequestRouter} so that
   * {@link ServerApplicationContext} is never touched.
   */
  SyosServer(int port, int threadPoolSize, int queueCapacity, RequestRouter router) {
    this(port, threadPoolSize, queueCapacity, router, null);
  }

  SyosServer(
      int port,
      int threadPoolSize,
      int queueCapacity,
      RequestRouter router,
      PushRegistry pushRegistry) {
    if (port < 1 || port > 65534) {
      throw new IllegalArgumentException("Port must be between 1 and 65534: " + port);
    }
    if (threadPoolSize < 1) throw new IllegalArgumentException("threadPoolSize must be >= 1");
    if (queueCapacity < 1) throw new IllegalArgumentException("queueCapacity must be >= 1");
    this.port = port;
    this.pushPort = port + 1;
    this.threadPool = new ThreadPool(threadPoolSize, queueCapacity);
    this.router = router; // may be null — resolved in start()
    this.pushRegistry = pushRegistry;
  }

  public synchronized void setPushRegistry(PushRegistry pushRegistry) {
    this.pushRegistry = pushRegistry;
  }

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  /**
   * Initializes the application context (if not already done), binds the server socket,
   * and enters the blocking accept loop.
   *
   * <p>Returns only when {@link #stop()} is called or a fatal socket error occurs.
   */
  public void start() {
    if (router == null) {
      ServerApplicationContext ctx = ServerApplicationContext.getInstance();
      ctx.initialize();
      router = ctx.getRequestRouter();
      setPushRegistry(ctx.getPushRegistry());
    }
    if (pushRegistry == null) {
      setPushRegistry(PushRegistry.getInstance());
    }

    running = true;
    LOGGER.info("SYOS Server starting on request port " + port + " and push port " + pushPort
        + " with " + threadPool.getPoolSize() + " threads...");

    try (ServerSocket requestSocket = new ServerSocket(port);
        ServerSocket pushSocket = new ServerSocket(pushPort)) {
      this.serverSocket = requestSocket;
      this.pushServerSocket = pushSocket;
      LOGGER.info("SYOS Server listening on request port " + port + " and push port " + pushPort);
      startPushAcceptLoop();

      while (running) {
        try {
          Socket clientSocket = requestSocket.accept();
          String cid = allocateClientId();
          incrementConnectedClients();
          threadPool.submit(wrap(new ClientHandler(clientSocket, router, cid), cid));
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
  public synchronized void stop() {
    running = false;
    if (serverSocket != null && !serverSocket.isClosed()) {
      try {
        serverSocket.close();
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Error closing server socket", e);
      }
    }
    if (pushServerSocket != null && !pushServerSocket.isClosed()) {
      try {
        pushServerSocket.close();
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Error closing push server socket", e);
      }
    }
    threadPool.shutdown();
  }

  // ── Connected-client tracking ─────────────────────────────────────────────

  private synchronized void incrementConnectedClients() {
    connectedClients++;
  }

  private synchronized void decrementConnectedClients() {
    if (connectedClients > 0) connectedClients--;
  }

  public synchronized int getConnectedClientCount() {
    return connectedClients;
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private synchronized String allocateClientId() {
    return "client-" + (++nextClientId);
  }

  /**
   * Wraps a {@link ClientHandler} so that {@link #decrementConnectedClients()} is called when
   * the handler finishes, regardless of outcome.
   */
  private Runnable wrap(ClientHandler handler, String clientId) {
    return () -> {
      try {
        handler.run();
      } finally {
        decrementConnectedClients();
        LOGGER.fine("[" + clientId + "] handler finished; active clients: "
            + getConnectedClientCount());
      }
    };
  }

  private void startPushAcceptLoop() {
    pushAcceptThread = new Thread(() -> {
      while (running) {
        try {
          Socket pushClientSocket = pushServerSocket.accept();
          PushClientHandler pushHandler = new PushClientHandler(pushClientSocket, pushRegistry);
          pushRegistry.register(pushHandler);
          pushHandler.start();
        } catch (IOException e) {
          if (running) {
            LOGGER.log(Level.WARNING, "Error accepting push connection", e);
          }
        }
      }
    }, "syos-push-accept");
    pushAcceptThread.setDaemon(true);
    pushAcceptThread.start();
  }
}
