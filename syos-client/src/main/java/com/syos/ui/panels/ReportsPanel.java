package com.syos.ui.panels;

import com.syos.network.ServerConnection;
import com.syos.protocol.CommandType;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import com.syos.ui.components.StyledButton;
import com.syos.ui.components.StyledTable;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Panel for generating and viewing reports.
 *
 * <p>The user selects a report type and date, then presses "Generate" to retrieve
 * the report data from the server and display it in the table.
 */
public class ReportsPanel extends JPanel {

  private static final String[] REPORT_TYPES = {
      "1 - Daily Sales (All)",
      "2 - Daily Sales (In-Store)",
      "3 - Daily Sales (Online)",
      "4 - Reshelving",
      "5 - Reorder",
      "6 - Store Stock",
      "7 - Shelf Stock",
      "8 - Online Stock",
      "9 - Bill Report"
  };

  private final ServerConnection connection;

  private final JComboBox<String> reportTypeBox = new JComboBox<>(REPORT_TYPES);
  private final JTextField        dateField     = new JTextField(LocalDate.now().toString(), 12);
  private final JLabel            titleLabel    = new JLabel(" ");
  private StyledTable resultTable;

  public ReportsPanel(ServerConnection connection) {
    this.connection = connection;
    setLayout(new BorderLayout(8, 8));
    setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    add(buildControlPanel(), BorderLayout.NORTH);
    // Table is rebuilt dynamically to match column headers from the server response
    resultTable = new StyledTable("Column 1", "Column 2");
    add(resultTable.getScrollPane(), BorderLayout.CENTER);
  }

  private JPanel buildControlPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
    panel.setBorder(BorderFactory.createTitledBorder("Report Options"));
    panel.add(new JLabel("Report:"));
    panel.add(reportTypeBox);
    panel.add(new JLabel("Date (YYYY-MM-DD):"));
    panel.add(dateField);

    StyledButton generateBtn = new StyledButton("Generate");
    generateBtn.addActionListener(e -> generateReport());
    panel.add(generateBtn);
    panel.add(titleLabel);
    return panel;
  }

  @SuppressWarnings("unchecked")
  private void generateReport() {
    String selected = (String) reportTypeBox.getSelectedItem();
    if (selected == null) return;
    String reportType = selected.split(" ")[0]; // "1", "2", …
    String date = dateField.getText().trim();

    if (date.isEmpty()) { showError("Date is required."); return; }

    Map<String, Object> payload = new HashMap<>();
    payload.put("reportType", reportType);
    payload.put("date", date);

    try {
      Response response = connection.send(new Request(CommandType.GENERATE_REPORT, payload));
      if (!response.isSuccess()) {
        showError("Report failed: " + response.getErrorMessage()); return;
      }

      Map<String, Object> data = response.getData();
      titleLabel.setText((String) data.getOrDefault("title", "Report"));
      List<Map<String, Object>> rows =
          (List<Map<String, Object>>) data.getOrDefault("rows", List.of());

      displayRows(rows);

    } catch (Exception ex) {
      showError("Connection error: " + ex.getMessage());
    }
  }

  private void displayRows(List<Map<String, Object>> rows) {
    resultTable.clearRows();
    if (rows.isEmpty()) {
      return;
    }

    // Derive columns from the first row's key set
    String[] columns = rows.get(0).keySet().toArray(new String[0]);

    // Rebuild table model with correct columns
    resultTable = new StyledTable(columns);
    remove(((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.CENTER));
    add(resultTable.getScrollPane(), BorderLayout.CENTER);
    revalidate();
    repaint();

    for (Map<String, Object> row : rows) {
      Object[] values = new Object[columns.length];
      for (int i = 0; i < columns.length; i++) {
        values[i] = row.get(columns[i]);
      }
      resultTable.addRow(values);
    }
  }

  private void showError(String msg) {
    JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
  }
}
