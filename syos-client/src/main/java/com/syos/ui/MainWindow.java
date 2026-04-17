package com.syos.ui;

import com.syos.network.ServerConnection;
import com.syos.ui.panels.InStoreSalePanel;
import com.syos.ui.panels.OnlineSalePanel;
import com.syos.ui.panels.ReportsPanel;
import com.syos.ui.panels.StockManagementPanel;
import com.syos.ui.panels.UserRegistrationPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;

/**
 * Main application window.
 *
 * <p>Creates a {@link ServerConnection}, connects to the server, then builds a tabbed UI
 * containing all functional panels. Closes the connection when the window is closed.
 */
public class MainWindow extends JFrame {

  private final ServerConnection connection;

  public MainWindow(String host, int port) {
    super("SYOS Billing System v2");

    connection = new ServerConnection(host, port);
    try {
      connection.connect();
    } catch (IOException e) {
      JOptionPane.showMessageDialog(
          null,
          "Cannot connect to server at " + host + ":" + port + "\n" + e.getMessage(),
          "Connection Error",
          JOptionPane.ERROR_MESSAGE);
      System.exit(1);
    }

    buildUi();

    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        connection.close();
        dispose();
        System.exit(0);
      }
    });

    setSize(900, 650);
    setLocationRelativeTo(null);
  }

  private void buildUi() {
    setLayout(new BorderLayout());

    // Header banner
    JLabel header = new JLabel("  SYOS Billing System v2", JLabel.LEFT);
    header.setFont(new Font("SansSerif", Font.BOLD, 18));
    header.setOpaque(true);
    header.setBackground(new Color(0x1565C0));
    header.setForeground(Color.WHITE);
    header.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    add(header, BorderLayout.NORTH);

    // Tabbed pane
    JTabbedPane tabs = new JTabbedPane();
    tabs.setFont(new Font("SansSerif", Font.PLAIN, 13));

    tabs.addTab("In-Store Sale",     new InStoreSalePanel(connection));
    tabs.addTab("Online Sale",       new OnlineSalePanel(connection));
    tabs.addTab("Stock Management",  new StockManagementPanel(connection));
    tabs.addTab("Reports",           new ReportsPanel(connection));
    tabs.addTab("Register Customer", new UserRegistrationPanel(connection));

    add(tabs, BorderLayout.CENTER);

    // Status bar
    JLabel statusBar = new JLabel(
        "  Connected to server — " + connection.getHost() + ":" + connection.getPort());
    statusBar.setFont(new Font("SansSerif", Font.ITALIC, 11));
    statusBar.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
    add(statusBar, BorderLayout.SOUTH);
  }
}
