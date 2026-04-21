package com.syos.ui;

import com.syos.network.ServerConnection;
import com.syos.ui.panels.InStoreSalePanel;
import com.syos.ui.panels.OnlineSalePanel;
import com.syos.ui.panels.ReportsPanel;

/**
 * Coordinates cross-panel data refresh when a live push notification is received.
 */
public class DataRefreshCoordinator {

  /** Callback used by MainWindow to show subtle status updates. */
  public interface StatusNotifier {
    void showStatus(String status);
  }

  private final InStoreSalePanel inStoreSalePanel;
  private final OnlineSalePanel onlineSalePanel;
  private final ReportsPanel reportsPanel;
  private final ServerConnection connection;

  private StatusNotifier statusNotifier;

  public DataRefreshCoordinator(
      InStoreSalePanel inStoreSalePanel,
      OnlineSalePanel onlineSalePanel,
      ReportsPanel reportsPanel,
      ServerConnection connection) {
    if (inStoreSalePanel == null) throw new IllegalArgumentException("In-store panel cannot be null");
    if (onlineSalePanel == null) throw new IllegalArgumentException("Online panel cannot be null");
    if (reportsPanel == null) throw new IllegalArgumentException("Reports panel cannot be null");
    if (connection == null) throw new IllegalArgumentException("Connection cannot be null");

    this.inStoreSalePanel = inStoreSalePanel;
    this.onlineSalePanel = onlineSalePanel;
    this.reportsPanel = reportsPanel;
    this.connection = connection;
  }

  public void setStatusNotifier(StatusNotifier statusNotifier) {
    this.statusNotifier = statusNotifier;
  }

  public void refresh() {
    inStoreSalePanel.invalidateCache();
    onlineSalePanel.invalidateCache();
    reportsPanel.refreshCurrentReport();

    if (statusNotifier != null) {
      String status = connection.isConnected()
          ? "Live update received - data refreshed"
          : "Live update received";
      statusNotifier.showStatus(status);
    }
  }
}
