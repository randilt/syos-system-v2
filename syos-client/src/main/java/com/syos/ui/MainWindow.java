package com.syos.ui;

import com.syos.network.PushListenerService;
import com.syos.network.ServerConnection;
import com.syos.ui.panels.InStoreSalePanel;
import com.syos.ui.panels.OnlineSalePanel;
import com.syos.ui.panels.ReportsPanel;
import com.syos.ui.panels.StockManagementPanel;
import com.syos.ui.panels.UserRegistrationPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Main application window.
 *
 * <p>Layout:
 * <ul>
 *   <li>NORTH: top bar with title, clock, and connection status indicator</li>
 *   <li>WEST: dark-navy sidebar with 5 navigation buttons</li>
 *   <li>CENTER: {@link CardLayout} content area with all functional panels</li>
 * </ul>
 *
 * <p>Connection failures show a warning in the status bar but do not exit; the user
 * can try to reconnect via the sidebar. All panels gracefully show inline errors when
 * the connection is absent.
 */
public class MainWindow extends JFrame {

  // ── Colours ───────────────────────────────────────────────────────────────
  private static final Color SIDEBAR_BG     = new Color(0x1a2744);
  private static final Color SIDEBAR_HOVER  = new Color(0x2e3f6e);
  private static final Color SIDEBAR_ACTIVE = new Color(0x2ecc71);
  private static final Color TOPBAR_BG      = new Color(0x1a2744);
  private static final Color CONTENT_BG     = Color.WHITE;
  private static final Color DOT_OK         = new Color(0x2ecc71);
  private static final Color DOT_ERR        = new Color(0xe74c3c);
  private static final Color LIVE_OFFLINE   = new Color(0x95a5a6);

  // ── Fonts ─────────────────────────────────────────────────────────────────
  private static final Font SIDEBAR_FONT = new Font("Segoe UI", Font.PLAIN, 13);
  private static final Font TOPBAR_FONT  = new Font("Segoe UI", Font.BOLD, 16);
  private static final Font CLOCK_FONT   = new Font("Segoe UI", Font.PLAIN, 12);

  // ── Navigation items: label → CardLayout key ─────────────────────────────
  private static final String[][] NAV_ITEMS = {
      {"POS Terminal",       "POS"},
      {"Online Sale",        "ONLINE"},
      {"Stock Management",   "STOCK"},
      {"Reports",            "REPORTS"},
      {"User Registration",  "USERS"},
  };

  private final ServerConnection connection;
  private final CardLayout        cardLayout   = new CardLayout();
  private final JPanel            contentArea  = new JPanel(cardLayout);
  private PushListenerService pushListener;

  private InStoreSalePanel inStoreSalePanel;
  private OnlineSalePanel onlineSalePanel;
  private ReportsPanel reportsPanel;

  private JLabel activeSidebarLabel = null;
  private JLabel clockLabel;
  private JLabel connDotLabel;
  private JLabel connTextLabel;
  private JLabel pushStatusLabel;

  public MainWindow(String host, int port) {
    super("SYOS Billing System");
    this.connection = new ServerConnection(host, port);

    buildUi();

    setMinimumSize(new Dimension(1200, 700));
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      @Override public void windowClosing(WindowEvent e) {
        if (pushListener != null) {
          pushListener.stop();
        }
        connection.disconnect();
        dispose();
        System.exit(0);
      }
    });
    pack();
    setSize(1280, 760);
    setLocationRelativeTo(null);

    // Connect after UI is ready so status can update
    connectToServer();

    // Live clock — fires every second on EDT
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss");
    Timer clock = new Timer(1_000, e -> clockLabel.setText(LocalDateTime.now().format(fmt)));
    clock.start();
  }

  // ── Connection ────────────────────────────────────────────────────────────

  private void connectToServer() {
    setConnectionStatus(false, "Connecting…");
    setPushLiveStatus(false);
    Thread connectThread = new Thread(() -> {
      boolean ok = connection.connect();
      javax.swing.SwingUtilities.invokeLater(() ->
          setConnectionStatus(ok, ok ? "Connected" : "Offline"));
      if (ok) {
        startPushListener();
      }
    }, "syos-connect");
    connectThread.setDaemon(true);
    connectThread.start();
  }

  private void setConnectionStatus(boolean connected, String text) {
    connDotLabel.putClientProperty("connected", connected);
    connDotLabel.repaint();
    connTextLabel.setText(text);
    connTextLabel.setForeground(connected ? DOT_OK : DOT_ERR);
  }

  private void setPushLiveStatus(boolean connected) {
    if (pushStatusLabel == null) {
      return;
    }
    pushStatusLabel.setText(connected ? "● Live" : "○ Live");
    pushStatusLabel.setForeground(connected ? DOT_OK : LIVE_OFFLINE);
  }

  private void startPushListener() {
    DataRefreshCoordinator coordinator =
        new DataRefreshCoordinator(inStoreSalePanel, onlineSalePanel, reportsPanel, connection);
    coordinator.setStatusNotifier(this::showTransientStatus);

    pushListener = new PushListenerService(
        connection.getHost(),
        connection.getPort() + 1,
        coordinator::refresh);

    Thread pushThread = new Thread(() -> {
      pushListener.connect();
      javax.swing.SwingUtilities.invokeLater(() -> setPushLiveStatus(pushListener.isConnected()));
      pushListener.start();
    }, "syos-push-connect");
    pushThread.setDaemon(true);
    pushThread.start();
  }

  private void showTransientStatus(String status) {
    String base = connection.isConnected() ? "Connected" : "Offline";
    connTextLabel.setText(base + " | " + status);
    connTextLabel.setForeground(connection.isConnected() ? DOT_OK : DOT_ERR);

    Timer reset = new Timer(2_500, e -> {
      connTextLabel.setText(base);
      connTextLabel.setForeground(connection.isConnected() ? DOT_OK : DOT_ERR);
    });
    reset.setRepeats(false);
    reset.start();
  }

  // ── UI Construction ───────────────────────────────────────────────────────

  private void buildUi() {
    JPanel root = new JPanel(new BorderLayout());
    root.add(buildTopBar(),  BorderLayout.NORTH);
    root.add(buildSidebar(), BorderLayout.WEST);
    root.add(buildContent(), BorderLayout.CENTER);
    setContentPane(root);
  }

  private JPanel buildTopBar() {
    JPanel bar = new JPanel(new BorderLayout());
    bar.setBackground(TOPBAR_BG);
    bar.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
    bar.setPreferredSize(new Dimension(0, 48));

    JLabel title = new JLabel("SYOS Billing System");
    title.setFont(TOPBAR_FONT);
    title.setForeground(Color.WHITE);
    bar.add(title, BorderLayout.WEST);

    // Right side: connection dot + text + spacer + clock
    JPanel right = new JPanel();
    right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
    right.setBackground(TOPBAR_BG);

    connDotLabel = new JLabel() {
      @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        boolean ok = Boolean.TRUE.equals(getClientProperty("connected"));
        g.setColor(ok ? DOT_OK : DOT_ERR);
        g.fillOval(2, (getHeight() - 10) / 2, 10, 10);
      }
    };
    connDotLabel.setPreferredSize(new Dimension(16, 16));
    connDotLabel.setOpaque(false);
    right.add(connDotLabel);
    right.add(Box.createHorizontalStrut(6));

    connTextLabel = new JLabel("Connecting…");
    connTextLabel.setFont(CLOCK_FONT);
    connTextLabel.setForeground(DOT_ERR);
    right.add(connTextLabel);
    right.add(Box.createHorizontalStrut(16));

    pushStatusLabel = new JLabel("○ Live");
    pushStatusLabel.setFont(CLOCK_FONT);
    pushStatusLabel.setForeground(LIVE_OFFLINE);
    right.add(pushStatusLabel);
    right.add(Box.createHorizontalStrut(24));

    clockLabel = new JLabel();
    clockLabel.setFont(CLOCK_FONT);
    clockLabel.setForeground(new Color(0xbdc3c7));
    right.add(clockLabel);

    bar.add(right, BorderLayout.EAST);
    return bar;
  }

  private JPanel buildSidebar() {
    JPanel sidebar = new JPanel();
    sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
    sidebar.setBackground(SIDEBAR_BG);
    sidebar.setPreferredSize(new Dimension(210, 0));

    sidebar.add(Box.createVerticalStrut(20));

    for (String[] nav : NAV_ITEMS) {
      JLabel btn = makeSidebarButton(nav[0], nav[1]);
      sidebar.add(btn);
      sidebar.add(Box.createVerticalStrut(2));
      // Activate first item by default
      if (activeSidebarLabel == null) {
        activeSidebarLabel = btn;
        btn.setBackground(SIDEBAR_ACTIVE);
      }
    }
    sidebar.add(Box.createVerticalGlue());
    return sidebar;
  }

  private JLabel makeSidebarButton(String text, String cardKey) {
    JLabel btn = new JLabel("   " + text);
    btn.setFont(SIDEBAR_FONT);
    btn.setForeground(Color.WHITE);
    btn.setBackground(SIDEBAR_BG);
    btn.setOpaque(true);
    btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
    btn.setPreferredSize(new Dimension(210, 44));
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

    btn.addMouseListener(new MouseAdapter() {
      @Override public void mouseEntered(MouseEvent e) {
        if (btn != activeSidebarLabel) btn.setBackground(SIDEBAR_HOVER);
      }
      @Override public void mouseExited(MouseEvent e) {
        if (btn != activeSidebarLabel) btn.setBackground(SIDEBAR_BG);
      }
      @Override public void mouseClicked(MouseEvent e) {
        if (activeSidebarLabel != null) activeSidebarLabel.setBackground(SIDEBAR_BG);
        activeSidebarLabel = btn;
        btn.setBackground(SIDEBAR_ACTIVE);
        cardLayout.show(contentArea, cardKey);
      }
    });
    return btn;
  }

  private JPanel buildContent() {
    contentArea.setBackground(CONTENT_BG);

    inStoreSalePanel = new InStoreSalePanel(connection);
    onlineSalePanel = new OnlineSalePanel(connection);
    reportsPanel = new ReportsPanel(connection);

    contentArea.add(inStoreSalePanel,   "POS");
    contentArea.add(onlineSalePanel,    "ONLINE");
    contentArea.add(new StockManagementPanel(connection), "STOCK");
    contentArea.add(reportsPanel,       "REPORTS");
    contentArea.add(new UserRegistrationPanel(connection), "USERS");

    cardLayout.show(contentArea, "POS");
    return contentArea;
  }
}
