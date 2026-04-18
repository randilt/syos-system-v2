package com.syos.ui.components;

import com.syos.protocol.BillDto;
import com.syos.protocol.BillItemDto;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * A panel that renders a {@link BillDto} as a formatted text receipt with an optional
 * print action.
 *
 * <p>Call {@link #displayBill(BillDto)} after a successful sale response, and
 * {@link #clear()} to reset the receipt area between sales.
 */
public class BillReceiptPanel extends JPanel {

  private static final int    RECEIPT_WIDTH = 36;
  private static final String SEPARATOR     = "=".repeat(RECEIPT_WIDTH);
  private static final String THIN_SEP      = "-".repeat(RECEIPT_WIDTH);

  private final JTextArea receiptArea;

  public BillReceiptPanel() {
    setLayout(new BorderLayout(4, 4));
    setBorder(BorderFactory.createTitledBorder("Bill Receipt"));
    setBackground(Color.WHITE);

    receiptArea = new JTextArea(20, 38);
    receiptArea.setEditable(false);
    receiptArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
    receiptArea.setBackground(new Color(0xFAFAFA));
    receiptArea.setText("No sale processed yet.");
    add(new JScrollPane(receiptArea), BorderLayout.CENTER);

    JButton printBtn = new JButton("Print Receipt");
    printBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
    printBtn.addActionListener(e -> printReceipt());
    JPanel south = new JPanel();
    south.setLayout(new BoxLayout(south, BoxLayout.X_AXIS));
    south.setBackground(Color.WHITE);
    south.add(Box.createHorizontalGlue());
    south.add(printBtn);
    south.add(Box.createHorizontalGlue());
    add(south, BorderLayout.SOUTH);
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Renders the given {@link BillDto} as a formatted text receipt.
   *
   * @param dto the bill returned from a successful sale
   */
  public void displayBill(BillDto dto) {
    StringBuilder sb = new StringBuilder();
    sb.append(SEPARATOR).append('\n');
    center(sb, "SYOS BILLING SYSTEM");
    center(sb, "SALES RECEIPT");
    sb.append(SEPARATOR).append('\n');
    sb.append(String.format("Bill #:   %d%n", dto.getSerialNumber()));
    sb.append(String.format("Date:     %s%n", dto.getDate()));
    sb.append(String.format("Type:     %s%n", dto.getType()));
    if (dto.getUserId() != null && !dto.getUserId().isBlank()) {
      sb.append(String.format("User ID:  %s%n", dto.getUserId()));
    }
    sb.append(THIN_SEP).append('\n');
    sb.append(String.format("%-16s %4s %6s %8s%n", "Item", "Qty", "Price", "Total"));
    sb.append(THIN_SEP).append('\n');

    if (dto.getItems() != null) {
      for (BillItemDto item : dto.getItems()) {
        String name = truncate(item.getItemName(), 16);
        sb.append(String.format("%-16s %4d %6.2f %8.2f%n",
            name, item.getQuantity(), item.getUnitPrice(), item.getTotalPrice()));
      }
    }

    sb.append(THIN_SEP).append('\n');
    sb.append(String.format("%-24s %8.2f%n", "Subtotal:", dto.getFullPrice()));
    if (dto.getDiscount() > 0.0) {
      sb.append(String.format("%-24s %8.2f%n", "Discount:", dto.getDiscount()));
    }
    sb.append(String.format("%-24s %8.2f%n", "TOTAL:", dto.getFinalAmount()));
    if (dto.getCashTendered() > 0.0) {
      sb.append(String.format("%-24s %8.2f%n", "Cash:", dto.getCashTendered()));
      sb.append(String.format("%-24s %8.2f%n", "Change:", dto.getChange()));
    }
    sb.append(SEPARATOR).append('\n');
    center(sb, "Thank you for shopping at SYOS!");
    sb.append(SEPARATOR).append('\n');

    receiptArea.setText(sb.toString());
    receiptArea.setCaretPosition(0);
  }

  /** Clears the receipt display. */
  public void clear() {
    receiptArea.setText("No sale processed yet.");
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private void center(StringBuilder sb, String text) {
    int padding = Math.max(0, (RECEIPT_WIDTH - text.length()) / 2);
    sb.append(" ".repeat(padding)).append(text).append('\n');
  }

  private String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() <= max ? s : s.substring(0, max);
  }

  private void printReceipt() {
    PrinterJob job = PrinterJob.getPrinterJob();
    job.setPrintable((graphics, pageFormat, pageIndex) -> {
      if (pageIndex > 0) return java.awt.print.Printable.NO_SUCH_PAGE;
      String text = receiptArea.getText();
      graphics.setFont(new Font("Monospaced", Font.PLAIN, 10));
      java.awt.FontMetrics fm = graphics.getFontMetrics();
      int lineHeight = fm.getHeight();
      double x = pageFormat.getImageableX();
      double y = pageFormat.getImageableY() + lineHeight;
      for (String line : text.split("\n")) {
        graphics.drawString(line, (int) x, (int) y);
        y += lineHeight;
      }
      return java.awt.print.Printable.PAGE_EXISTS;
    });
    if (job.printDialog()) {
      try {
        job.print();
      } catch (PrinterException ex) {
        receiptArea.append("\n[Print failed: " + ex.getMessage() + "]");
      }
    }
  }
}
