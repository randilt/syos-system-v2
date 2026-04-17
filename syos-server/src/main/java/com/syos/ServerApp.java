package com.syos;

import com.syos.server.ServerApplicationContext;
import com.syos.server.SyosServer;
import java.util.logging.Logger;

/**
 * Entry point for the SYOS server process.
 *
 * <p>Usage: {@code java -cp ... com.syos.ServerApp [port]}
 * <ul>
 *   <li>port — TCP port to listen on (default: 9090)</li>
 * </ul>
 *
 * <p>Database connection is configured via environment variables:
 * <ul>
 *   <li>{@code SYOS_DB_URL} (default: {@code jdbc:mysql://localhost:3306/syos_billing?...})</li>
 *   <li>{@code SYOS_DB_USER} (default: {@code root})</li>
 *   <li>{@code SYOS_DB_PASSWORD} (default: {@code root})</li>
 * </ul>
 */
public class ServerApp {

  private static final Logger LOGGER = Logger.getLogger(ServerApp.class.getName());
  private static final int DEFAULT_PORT = 9090;

  public static void main(String[] args) {
    int port = parsePort(args);

    LOGGER.info("=== SYOS Billing System v2 — Server ===");
    LOGGER.info("Starting on port " + port);

    ServerApplicationContext context = new ServerApplicationContext();
    context.initialize();

    SyosServer server = new SyosServer(port, context);

    // Ensure the thread pool is shut down cleanly on JVM exit
    Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown-hook"));

    server.start();
  }

  private static int parsePort(String[] args) {
    if (args.length > 0) {
      try {
        int port = Integer.parseInt(args[0]);
        if (port < 1 || port > 65535) {
          throw new IllegalArgumentException("Port out of range: " + port);
        }
        return port;
      } catch (NumberFormatException e) {
        System.err.println("Invalid port '" + args[0] + "', using default " + DEFAULT_PORT);
      }
    }
    return DEFAULT_PORT;
  }
}
