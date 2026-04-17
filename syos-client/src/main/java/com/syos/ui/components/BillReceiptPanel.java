package com.syos.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;

/**
 * A panel that renders a bill response map as a formatted receipt.
 *
 * <p>Pass the {@code data} map from a successful {@code PROCESS_IN_STORE_SALE} or
 * {@code PROCESS_ONLINE_SALE} response to {@link #displayBill(Map)}.
 */
public class BillReceiptPanel extends JPanel {

  private final JTextArea receiptArea;

  public BillReceiptPanel() {
    setLayout(new BorderLayout(4, 4));
    setBorder(BorderFactory.createTitledBorder("Bill Receipt"));
    setBackground(Color.WHITE);

    JLabel titleLabel = new JLabel("Receipt", JLabel.CENTER);
    titleLabel.setFont(new Font("Monospaced", Font.BOLD, 15));
    add(titleLabel, BorderLayout.NORTH);

    receiptArea = new JTextArea(20, 40);
    receiptArea.setEditable(false);
    receiptArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
    receiptArea.setBackground(new Color(0xFAFAFA));
    add(new JScrollPane(receiptArea), BorderLayout.CENTER);
  }

  /**
   * Renders the bill map onto the receipt area.
   *
   * @param bill the {@code data} portion of a successful sale response
   */
  @SuppressWarnings("unchecked")
  public void displayBill(Map<String, Object> bill) {
    StringBuilder sb = new StringBuilder();
    sb.append("==============================\n");
    sb.append("        SYOS RECEIPT\n");
    sb.append("==============================\n");
    sb.append(String.format("Bill #:    %s%n", bill.getOrDefault("serialNumber", "N/A")));
    sb.append(String.format("Date:      %s%n", bill.getOrDefault("date", "N/A")));
    sb.append(String.format("Type:      %s%n", bill.getOrDefault("type", "N/A")));
    if (bill.containsKey("userId")) {
      sb.append(String.format("User ID:   %s%n", bill.get("userId")));
    }
    sb.append("------------------------------\n");
    sb.append(String.format("%-16s %4s %8s%n", "Item", "Qty", "Total"));
    sb.append("------------------------------\n");

    List<Map<String, Object>> items = (List<Map<String, Object>>) bill.get("items");
    if (items != null) {
      for (Map<String, Object> item : items) {
        String name = truncate((String) item.getOrDefault("itemName", ""), 16);
        int qty = ((Number) item.getOrDefault("quantity", 0)).intValue();
        double total = ((Number) item.getOrDefault("totalPrice", 0.0)).doubleValue();
        sb.append(String.format("%-16s %4d %8.2f%n", name, qty, total));
      }
    }

    sb.append("------------------------------\n");
    double fullPrice  = ((Number) bill.getOrDefault("fullPrice",   0.0)).doubleValue();
    double discount   = ((Number) bill.getOrDefault("discount",    0.0)).doubleValue();
    double finalAmt   = ((Number) bill.getOrDefault("finalAmount", 0.0)).doubleValue();

    sb.append(String.format("%-20s %8.2f%n", "Subtotal:", fullPrice));
    sb.append(String.format("%-20s %8.2f%n", "Discount:", discount));
    sb.append(String.format("%-20s %8.2f%n", "Total:", finalAmt));
    sb.append("==============================\n");
    sb.append("     Thank you for shopping!\n");
    sb.append("==============================\n");

    receiptArea.setText(sb.toString());
    receiptArea.setCaretPosition(0);
  }

  /** Clears the receipt area. */
  public void clear() {
    receiptArea.setText("");
  }

  private String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
  }
}
