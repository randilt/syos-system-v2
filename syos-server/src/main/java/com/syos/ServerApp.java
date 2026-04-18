package com.syos;

import com.syos.server.SyosServer;
import java.util.logging.Logger;

/**
 * Entry point for the SYOS server process.
 *
 * <p>Usage: {@code java -cp ... com.syos.ServerApp [port]}
 * <ul>
 *   <li>port — TCP port to listen on (default: {@value #DEFAULT_PORT})</li>
 * </ul>
 *
 * <p>Database connection is configured via environment variables:
 * <ul>
 *   <li>{@code SYOS_DB_URL} (default: {@code jdbc:mysql://localhost:3306/syos_billing?...})</li>
 *   <li>{@code SYOS_DB_USER} (default: {@code root})</li>
 *   <li>{@code SYOS_DB_PASSWORD} (default: {@code root})</li>
 * </ul>
 *
 * <p>{@link com.syos.server.ServerApplicationContext} initialization (Flyway + JDBC wiring) is
 * deferred to {@link SyosServer#start()} so that this class stays purely concerned with
 * start-up orchestration.
 */
public class ServerApp {

  private static final Logger LOGGER = Logger.getLogger(ServerApp.class.getName());

  static final int DEFAULT_PORT           = 9090;
  static final int DEFAULT_POOL_SIZE      = 10;
  static final int DEFAULT_QUEUE_CAPACITY = 100;

  public static void main(String[] args) {
    int port = parsePort(args);

    // ── Startup banner ────────────────────────────────────────────────────
    System.out.println("╔══════════════════════════════════════════╗");
    System.out.println("║   SYOS Billing System v2  —  Server      ║");
    System.out.printf ("║   Port: %-5d  Pool: %-3d  Queue: %-5d   ║%n",
        port, DEFAULT_POOL_SIZE, DEFAULT_QUEUE_CAPACITY);
    System.out.println("╚══════════════════════════════════════════╝");
    LOGGER.info("Starting SYOS server on port " + port);

    // Context initialization is handled inside SyosServer.start()
    SyosServer server = new SyosServer(port, DEFAULT_POOL_SIZE, DEFAULT_QUEUE_CAPACITY);

    Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown-hook"));

    server.start(); // blocks until stop() or fatal error
  }

  private static int parsePort(String[] args) {
    if (args.length > 0) {
      try {
        int port = Integer.parseInt(args[0]);
        if (port < 1 || port > 65535) {
          System.err.println("Port out of range '" + args[0] + "', using default " + DEFAULT_PORT);
          return DEFAULT_PORT;
        }
        return port;
      } catch (NumberFormatException e) {
        System.err.println("Invalid port '" + args[0] + "', using default " + DEFAULT_PORT);
      }
    }
    return DEFAULT_PORT;
  }
}
