package com.syos.ui.panels;

import com.syos.network.ServerConnection;
import com.syos.protocol.ReportDto;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import com.syos.ui.components.StyledButton;
import com.syos.ui.components.StyledTable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

/**
 * Report centre panel.
 *
 * <p>Left sidebar: 9 report-type buttons. Top: date selector.
 * Right: dynamic {@link StyledTable} with title and record count. CSV export button.
 *
 * <p>All server calls run on {@link SwingWorker} threads.
 */
public class ReportsPanel extends JPanel {

  private static final Color SIDEBAR_BG  = new Color(0x1a2744);
  private static final Color BTN_DEFAULT = new Color(0x243659);
  private static final Color BTN_ACTIVE  = new Color(0x2ecc71);
  private static final Color BG          = Color.WHITE;
  private static final Color ERR_COLOR   = new Color(0xe74c3c);
  private static final Font  LABEL_FONT  = new Font("Segoe UI", Font.PLAIN, 13);
  private static final Font  TITLE_FONT  = new Font("Segoe UI", Font.BOLD, 15);
  private static final Font  SIDEBAR_BTN_FONT = new Font("Segoe UI", Font.PLAIN, 12);

  private static final String[] REPORT_LABELS = {
      "Daily Sales (All)",
      "Daily Sales (In-Store)",
      "Daily Sales (Online)",
      "Reshelving",
      "Reorder",
      "Store Stock",
      "Shelf Stock",
      "Online Stock",
      "Bill Report"
  };

  private final ServerConnection connection;
  private final JSpinner         dateSpin     = new JSpinner(new SpinnerDateModel());
  private final JLabel           titleLabel   = new JLabel("Select a report", SwingConstants.LEFT);
  private final JLabel           countLabel   = new JLabel(" ", SwingConstants.LEFT);
  private final JLabel           msgLabel     = new JLabel(" ", SwingConstants.LEFT);
  private final JPanel           tableHolder  = new JPanel(new BorderLayout());

  private JLabel activeBtn   = null;
  private String activeReport = null;

  // Holds the last result for CSV export
  private List<Map<String, Object>> lastData  = null;
  private String[]                  lastCols  = null;

  public ReportsPanel(ServerConnection connection) {
    this.connection = connection;
    setLayout(new BorderLayout());
    setBackground(BG);

    add(buildSidebar(),  BorderLayout.WEST);
    add(buildContent(),  BorderLayout.CENTER);
  }

  // ── Sidebar ───────────────────────────────────────────────────────────────

  private JPanel buildSidebar() {
    JPanel sidebar = new JPanel();
    sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
    sidebar.setBackground(SIDEBAR_BG);
    sidebar.setPreferredSize(new Dimension(190, 0));
    sidebar.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));

    JLabel heading = new JLabel("  Reports", SwingConstants.LEFT);
    heading.setForeground(Color.WHITE);
    heading.setFont(new Font("Segoe UI", Font.BOLD, 14));
    heading.setAlignmentX(LEFT_ALIGNMENT);
    sidebar.add(heading);
    sidebar.add(Box.createVerticalStrut(10));

    for (String label : REPORT_LABELS) {
      JLabel btn = makeSidebarButton(label);
      sidebar.add(btn);
    }
    sidebar.add(Box.createVerticalGlue());
    return sidebar;
  }

  private JLabel makeSidebarButton(String text) {
    JLabel btn = new JLabel("  " + text, SwingConstants.LEFT);
    btn.setFont(SIDEBAR_BTN_FONT);
    btn.setForeground(Color.WHITE);
    btn.setBackground(BTN_DEFAULT);
    btn.setOpaque(true);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
    btn.setPreferredSize(new Dimension(190, 36));
    btn.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

    btn.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override public void mouseEntered(java.awt.event.MouseEvent e) {
        if (btn != activeBtn) btn.setBackground(new Color(0x2e3f6e));
      }
      @Override public void mouseExited(java.awt.event.MouseEvent e) {
        if (btn != activeBtn) btn.setBackground(BTN_DEFAULT);
      }
      @Override public void mouseClicked(java.awt.event.MouseEvent e) {
        setActiveButton(btn);
        activeReport = text;
        generateReport();
      }
    });
    return btn;
  }

  private void setActiveButton(JLabel btn) {
    if (activeBtn != null) { activeBtn.setBackground(BTN_DEFAULT); }
    activeBtn = btn;
    activeBtn.setBackground(BTN_ACTIVE);
  }

  // ── Content area ──────────────────────────────────────────────────────────

  private JPanel buildContent() {
    JPanel content = new JPanel(new BorderLayout(8, 8));
    content.setBackground(BG);
    content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

    // Top controls: date spinner + generate button + export
    JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpin, "yyyy-MM-dd");
    dateSpin.setEditor(dateEditor);
    dateSpin.setValue(new Date());

    JPanel topBar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 4));
    topBar.setBackground(BG);
    topBar.add(label("Date:"));
    topBar.add(dateSpin);
    StyledButton genBtn = StyledButton.primary("Generate");
    genBtn.addActionListener(e -> generateReport());
    topBar.add(genBtn);
    StyledButton csvBtn = StyledButton.neutral("Export CSV");
    csvBtn.addActionListener(e -> exportCsv());
    topBar.add(csvBtn);
    content.add(topBar, BorderLayout.NORTH);

    // Centre: title + table + count + message
    JPanel centre = new JPanel(new BorderLayout(4, 4));
    centre.setBackground(BG);

    titleLabel.setFont(TITLE_FONT);
    titleLabel.setForeground(new Color(0x1a2744));
    centre.add(titleLabel, BorderLayout.NORTH);

    tableHolder.setBackground(BG);
    // Initial empty table
    tableHolder.add(new StyledTable("—").getScrollPane(), BorderLayout.CENTER);
    centre.add(tableHolder, BorderLayout.CENTER);

    JPanel foot = new JPanel(new GridLayout(2, 1));
    foot.setBackground(BG);
    countLabel.setFont(LABEL_FONT);
    msgLabel.setFont(LABEL_FONT);
    foot.add(countLabel);
    foot.add(msgLabel);
    centre.add(foot, BorderLayout.SOUTH);

    content.add(centre, BorderLayout.CENTER);
    return content;
  }

  // ── Report generation ─────────────────────────────────────────────────────

  private void generateReport() {
    if (activeReport == null) { showMsg("Select a report from the left panel.", false); return; }

    String date = getSelectedDate();
    Request req = buildRequest(activeReport, date);
    if (req == null) { showMsg("Unknown report type.", false); return; }

    msgLabel.setText("Loading…");
    msgLabel.setForeground(Color.DARK_GRAY);
    titleLabel.setText("Loading: " + activeReport);
    countLabel.setText(" ");

    new SwingWorker<Response, Void>() {
      @Override protected Response doInBackground() throws Exception {
        return connection.sendRequest(req);
      }
      @Override protected void done() {
        try {
          Response r = get();
          if (r.isSuccess() && r.getPayload() instanceof ReportDto) {
            displayReport((ReportDto) r.getPayload());
          } else {
            showMsg(r.getErrorMessage(), false);
            titleLabel.setText(activeReport);
          }
        } catch (InterruptedException | ExecutionException ex) {
          showMsg("Server error: " + ex.getMessage(), false);
          titleLabel.setText(activeReport);
        }
      }
    }.execute();
  }

  private void displayReport(ReportDto dto) {
    titleLabel.setText(dto.getTitle());
    List<Map<String, Object>> data = dto.getData();
    lastData = data;

    if (data == null || data.isEmpty()) {
      countLabel.setText("No records found.");
      tableHolder.removeAll();
      tableHolder.add(new StyledTable("—").getScrollPane(), BorderLayout.CENTER);
      tableHolder.revalidate();
      tableHolder.repaint();
      msgLabel.setText(" ");
      return;
    }

    lastCols = data.get(0).keySet().toArray(new String[0]);
    StyledTable table = new StyledTable(lastCols);
    for (Map<String, Object> row : data) {
      Object[] values = new Object[lastCols.length];
      for (int i = 0; i < lastCols.length; i++) values[i] = row.get(lastCols[i]);
      table.addRow(values);
    }

    tableHolder.removeAll();
    tableHolder.add(table.getScrollPane(), BorderLayout.CENTER);
    tableHolder.revalidate();
    tableHolder.repaint();

    countLabel.setText("Records: " + dto.getTotalRecords());
    msgLabel.setText(" ");
  }

  // ── CSV export ────────────────────────────────────────────────────────────

  private void exportCsv() {
    if (lastData == null || lastCols == null) {
      showMsg("Generate a report first before exporting.", false);
      return;
    }
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new File(activeReport + ".csv"));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
    File file = chooser.getSelectedFile();
    try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
      pw.println(String.join(",", lastCols));
      for (Map<String, Object> row : lastData) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lastCols.length; i++) {
          if (i > 0) sb.append(",");
          Object val = row.get(lastCols[i]);
          String cell = val == null ? "" : val.toString().replace("\"", "\"\"");
          if (cell.contains(",") || cell.contains("\"") || cell.contains("\n")) {
            sb.append('"').append(cell).append('"');
          } else {
            sb.append(cell);
          }
        }
        pw.println(sb);
      }
      showMsg("Exported to: " + file.getName(), true);
    } catch (IOException ex) {
      showMsg("Export failed: " + ex.getMessage(), false);
    }
  }

  // ── Request routing ───────────────────────────────────────────────────────

  private Request buildRequest(String reportName, String date) {
    return switch (reportName) {
      case "Daily Sales (All)"      -> Request.getDailySalesReport(date, null);
      case "Daily Sales (In-Store)" -> Request.getDailySalesReport(date, "IN_STORE");
      case "Daily Sales (Online)"   -> Request.getDailySalesReport(date, "ONLINE");
      case "Reshelving"             -> Request.getReshelvingReport(date);
      case "Reorder"                -> Request.getReorderReport();
      case "Store Stock"            -> Request.getStockReport("STORE", date);
      case "Shelf Stock"            -> Request.getStockReport("SHELF", date);
      case "Online Stock"           -> Request.getStockReport("ONLINE", date);
      case "Bill Report"            -> Request.getBillReport();
      default                       -> null;
    };
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private String getSelectedDate() {
    Date d = (Date) dateSpin.getValue();
    return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
  }

  private void showMsg(String msg, boolean ok) {
    msgLabel.setForeground(ok ? new Color(0x27ae60) : ERR_COLOR);
    msgLabel.setText(msg);
  }

  private JLabel label(String text) {
    JLabel l = new JLabel(text);
    l.setFont(LABEL_FONT);
    return l;
  }
}
