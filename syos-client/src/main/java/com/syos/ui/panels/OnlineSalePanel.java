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
 * Panel for processing online sales.
 *
 * <p>The operator enters a registered user ID, builds a cart of item code / quantity
 * pairs, then submits the order to the server.
 */
public class OnlineSalePanel extends JPanel {

  private final ServerConnection connection;

  private final JTextField userIdField   = new JTextField(12);
  private final JTextField itemCodeField = new JTextField(10);
  private final JTextField qtyField      = new JTextField(5);

  private final StyledTable      cartTable;
  private final BillReceiptPanel receiptPanel;

  private final Map<String, Integer> cart = new LinkedHashMap<>();

  public OnlineSalePanel(ServerConnection connection) {
    this.connection  = connection;
    this.cartTable   = new StyledTable("Item Code", "Quantity");
    this.receiptPanel = new BillReceiptPanel();

    setLayout(new BorderLayout(8, 8));
    setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    add(buildUserPanel(), BorderLayout.NORTH);
    add(buildCartArea(),  BorderLayout.CENTER);
    add(buildButtons(),   BorderLayout.SOUTH);
  }

  private JPanel buildUserPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    panel.setBorder(BorderFactory.createTitledBorder("Customer"));
    panel.add(new JLabel("User ID:"));
    panel.add(userIdField);
    return panel;
  }

  private JPanel buildCartArea() {
    JPanel outer = new JPanel(new BorderLayout(4, 4));

    JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    inputRow.setBorder(BorderFactory.createTitledBorder("Add Item to Cart"));
    inputRow.add(new JLabel("Item Code:"));
    inputRow.add(itemCodeField);
    inputRow.add(new JLabel("Qty:"));
    inputRow.add(qtyField);

    StyledButton addBtn = StyledButton.neutral("Add to Cart");
    addBtn.addActionListener(e -> addToCart());
    inputRow.add(addBtn);

    StyledButton clearBtn = StyledButton.danger("Clear Cart");
    clearBtn.addActionListener(e -> clearCart());
    inputRow.add(clearBtn);

    outer.add(inputRow, BorderLayout.NORTH);
    outer.add(cartTable.getScrollPane(), BorderLayout.CENTER);
    return outer;
  }

  private JPanel buildButtons() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
    StyledButton processBtn = StyledButton.success("Process Online Sale");
    processBtn.addActionListener(e -> processSale());
    panel.add(processBtn);
    return panel;
  }

  private void addToCart() {
    String code = itemCodeField.getText().trim().toUpperCase();
    if (code.isEmpty()) { showError("Item code cannot be empty."); return; }
    int qty;
    try {
      qty = Integer.parseInt(qtyField.getText().trim());
      if (qty <= 0) throw new NumberFormatException();
    } catch (NumberFormatException e) {
      showError("Quantity must be a positive integer."); return;
    }
    cart.merge(code, qty, Integer::sum);
    refreshCartTable();
    itemCodeField.setText("");
    qtyField.setText("");
  }

  private void clearCart() {
    cart.clear();
    refreshCartTable();
  }

  private void processSale() {
    String userId = userIdField.getText().trim();
    if (userId.isEmpty()) { showError("User ID is required."); return; }
    if (cart.isEmpty())   { showError("Cart is empty — add items first."); return; }

    Map<String, Object> payload = new HashMap<>();
    payload.put("userId", userId);
    payload.put("items", new HashMap<>(cart));
    payload.put("date", LocalDate.now().toString());

    try {
      Response response = connection.send(new Request(CommandType.PROCESS_ONLINE_SALE, payload));
      if (response.isSuccess()) {
        receiptPanel.displayBill(response.getData());
        JOptionPane.showMessageDialog(this, receiptPanel, "Sale Processed", JOptionPane.PLAIN_MESSAGE);
        clearCart();
        userIdField.setText("");
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

  private void showError(String msg) {
    JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
  }
}
