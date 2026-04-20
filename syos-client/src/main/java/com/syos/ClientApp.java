package com.syos;

import com.syos.ui.MainWindow;
import com.syos.ui.UiTheme;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for the SYOS Swing client.
 *
 * <p>Presents a connection dialog that asks for host and port, then launches
 * {@link MainWindow}. The dialog pre-fills {@code localhost:9090} as defaults.
 *
 * <p>Command-line arguments (for scripted / headless launch):
 * <ul>
 *   <li>args[0] — host (skips dialog and uses this value directly)</li>
 *   <li>args[1] — port (optional, defaults to 9090)</li>
 * </ul>
 */
public class ClientApp {

  private static final Logger LOGGER       = Logger.getLogger(ClientApp.class.getName());
  private static final String DEFAULT_HOST = "localhost";
  private static final int    DEFAULT_PORT = 9090;

  public static void main(String[] args) {
    // Apply system look-and-feel for native OS widgets
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      UiTheme.installLightFormDefaults();
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Could not set system look-and-feel", e);
    }

    final String host;
    final int    port;

    if (args.length >= 1) {
      // Scripted / headless launch via CLI args
      host = args[0];
      int p = DEFAULT_PORT;
      if (args.length >= 2) {
        try { p = Integer.parseInt(args[1]); }
        catch (NumberFormatException e) {
          LOGGER.warning("Invalid port '" + args[1] + "', using default " + DEFAULT_PORT);
        }
      }
      port = p;
    } else {
      // Interactive connection dialog
      String hostInput = (String) JOptionPane.showInputDialog(
          null,
          "Server host:",
          "Connect to SYOS Server",
          JOptionPane.PLAIN_MESSAGE,
          null, null,
          DEFAULT_HOST);
      if (hostInput == null) { System.exit(0); return; }   // user cancelled
      host = hostInput.isBlank() ? DEFAULT_HOST : hostInput.trim();

      String portInput = (String) JOptionPane.showInputDialog(
          null,
          "Server port:",
          "Connect to SYOS Server",
          JOptionPane.PLAIN_MESSAGE,
          null, null,
          String.valueOf(DEFAULT_PORT));
      if (portInput == null) { System.exit(0); return; }   // user cancelled
      int p = DEFAULT_PORT;
      try { p = Integer.parseInt(portInput.trim()); }
      catch (NumberFormatException e) {
        LOGGER.warning("Invalid port input, using default " + DEFAULT_PORT);
      }
      port = p;
    }

    SwingUtilities.invokeLater(() -> new MainWindow(host, port).setVisible(true));
  }
}
