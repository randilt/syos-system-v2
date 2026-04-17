package com.syos;

import com.syos.ui.MainWindow;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for the SYOS Swing client.
 *
 * <p>Usage: {@code java -cp ... com.syos.ClientApp [host] [port]}
 * <ul>
 *   <li>host — server hostname or IP (default: {@code localhost})</li>
 *   <li>port — server TCP port (default: 9090)</li>
 * </ul>
 */
public class ClientApp {

  private static final Logger LOGGER = Logger.getLogger(ClientApp.class.getName());
  private static final String DEFAULT_HOST = "localhost";
  private static final int    DEFAULT_PORT = 9090;

  public static void main(String[] args) {
    String host = args.length > 0 ? args[0] : DEFAULT_HOST;
    int    port = DEFAULT_PORT;

    if (args.length > 1) {
      try {
        port = Integer.parseInt(args[1]);
      } catch (NumberFormatException e) {
        LOGGER.warning("Invalid port '" + args[1] + "', using default " + DEFAULT_PORT);
      }
    }

    // Apply system look-and-feel for native OS widgets
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Could not set system look-and-feel", e);
    }

    final String finalHost = host;
    final int finalPort = port;
    SwingUtilities.invokeLater(() -> new MainWindow(finalHost, finalPort).setVisible(true));
  }
}
