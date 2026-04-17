package com.syos.ui.panels;

import com.syos.network.ServerConnection;
import com.syos.protocol.CommandType;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import com.syos.ui.components.BillReceiptPanel;
import com.syos.ui.components.StyledButton;
import com.syos.ui.components.StyledTable;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Panel for processing in-store (POS) sales.
 *
 * <p>The cashier enters item codes and quantities to build a cart, then provides
 * the cash amount and submits the sale to the server.
 */
public class InStoreSalePanel extends JPanel {

  private final ServerConnection connection;

  private final JTextField itemCodeField  = new JTextField(10);
  private final JTextField qtyField       = new JTextField(5);
  private final JTextField cashField      = new JTextField(10);
  private final JLabel     totalLabel     = new JLabel("Total: Rs. 0.00");

  private final StyledTable   cartTable;
  private final BillReceiptPanel receiptPanel;

  // Item code → quantity map for the current cart
  private final Map<String, Integer> cart = new LinkedHashMap<>();

  public InStoreSalePanel(ServerConnection connection) {
    this.connection = connection;
    this.cartTable   = new StyledTable("Item Code", "Quantity");
    this.receiptPanel = new BillReceiptPanel();

    setLayout(new BorderLayout(8, 8));
    setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    add(buildInputPanel(),   BorderLayout.NORTH);
    add(cartTable.getScrollPane(), BorderLayout.CENTER);
    add(buildBottomPanel(),  BorderLayout.SOUTH);
  }

  // ── Layout helpers ───────────────────────────────────────────────────────

  private JPanel buildInputPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    panel.setBorder(BorderFactory.createTitledBorder("Add Item to Cart"));
    panel.add(new JLabel("Item Code:"));
    panel.add(itemCodeField);
    panel.add(new JLabel("Qty:"));
    panel.add(qtyField);

    StyledButton addBtn = StyledButton.neutral("Add to Cart");
    addBtn.addActionListener(e -> addToCart());
    panel.add(addBtn);

    StyledButton clearBtn = StyledButton.danger("Clear Cart");
    clearBtn.addActionListener(e -> clearCart());
    panel.add(clearBtn);

    return panel;
  }

  private JPanel buildBottomPanel() {
    JPanel panel = new JPanel(new GridLayout(1, 3, 8, 0));
    panel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

    // Total
    JPanel totalPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    totalLabel.setFont(totalLabel.getFont().deriveFont(14f));
    totalPanel.add(totalLabel);
    panel.add(totalPanel);

    // Cash input
    JPanel cashPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    cashPanel.add(new JLabel("Cash Tendered: Rs."));
    cashPanel.add(cashField);
    panel.add(cashPanel);

    // Process button
    JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    StyledButton processBtn = StyledButton.success("Process Sale");
    processBtn.addActionListener(e -> processSale());
    btnPanel.add(processBtn);
    panel.add(btnPanel);

    return panel;
  }

  // ── Actions ──────────────────────────────────────────────────────────────

  private void addToCart() {
    String code = itemCodeField.getText().trim().toUpperCase();
    String qtyText = qtyField.getText().trim();

    if (code.isEmpty()) {
      showError("Item code cannot be empty.");
      return;
    }
    int qty;
    try {
      qty = Integer.parseInt(qtyText);
      if (qty <= 0) throw new NumberFormatException();
    } catch (NumberFormatException e) {
      showError("Quantity must be a positive integer.");
      return;
    }

    cart.merge(code, qty, Integer::sum);
    refreshCartTable();
    itemCodeField.setText("");
    qtyField.setText("");
    itemCodeField.requestFocus();
  }

  private void clearCart() {
    cart.clear();
    refreshCartTable();
    totalLabel.setText("Total: Rs. 0.00");
    cashField.setText("");
    receiptPanel.clear();
  }

  private void processSale() {
    if (cart.isEmpty()) {
      showError("Cart is empty — add items first.");
      return;
    }
    double cash;
    try {
      cash = Double.parseDouble(cashField.getText().trim());
      if (cash <= 0) throw new NumberFormatException();
    } catch (NumberFormatException e) {
      showError("Enter a valid cash amount.");
      return;
    }

    Map<String, Object> payload = new HashMap<>();
    payload.put("items", new HashMap<>(cart));
    payload.put("cashTendered", cash);
    payload.put("date", LocalDate.now().toString());

    try {
      Response response = connection.send(new Request(CommandType.PROCESS_IN_STORE_SALE, payload));
      if (response.isSuccess()) {
        showReceipt(response.getData());
        clearCart();
      } else {
        showError("Sale failed: " + response.getErrorMessage());
      }
    } catch (Exception ex) {
      showError("Connection error: " + ex.getMessage());
    }
  }

  private void refreshCartTable() {
    cartTable.clearRows();
    cart.forEach((code, qty) -> cartTable.addRow(code, qty));
  }

  private void showReceipt(Map<String, Object> data) {
    receiptPanel.displayBill(data);
    JOptionPane.showMessageDialog(this, receiptPanel, "Sale Processed", JOptionPane.PLAIN_MESSAGE);
  }

  private void showError(String msg) {
    JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
  }
}
