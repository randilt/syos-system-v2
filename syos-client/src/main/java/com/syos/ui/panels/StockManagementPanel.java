package com.syos.ui.panels;

import com.syos.network.ServerConnection;
import com.syos.protocol.CommandType;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import com.syos.ui.components.StyledButton;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

/**
 * Panel for stock management operations:
 * <ul>
 *   <li>Receive new stock into STORE or ONLINE location</li>
 *   <li>Move stock from STORE to SHELF</li>
 * </ul>
 */
public class StockManagementPanel extends JPanel {

  private final ServerConnection connection;

  // Add-stock form
  private final JTextField addItemCodeField   = new JTextField(10);
  private final JTextField addQtyField        = new JTextField(6);
  private final JTextField purchaseDateField  = new JTextField(10);
  private final JTextField expiryDateField    = new JTextField(10);
  private final JRadioButton storeRadio       = new JRadioButton("STORE", true);
  private final JRadioButton onlineRadio      = new JRadioButton("ONLINE");

  // Move-to-shelf form
  private final JTextField shelfItemCodeField = new JTextField(10);
  private final JTextField shelfQtyField      = new JTextField(6);

  public StockManagementPanel(ServerConnection connection) {
    this.connection = connection;
    setLayout(new GridLayout(2, 1, 8, 12));
    setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    add(buildAddStockPanel());
    add(buildMoveToShelfPanel());
  }

  // ── Add Stock ─────────────────────────────────────────────────────────────

  private JPanel buildAddStockPanel() {
    JPanel panel = new JPanel(new GridLayout(0, 2, 8, 6));
    panel.setBorder(BorderFactory.createTitledBorder("Receive New Stock"));

    panel.add(new JLabel("Item Code:"));    panel.add(addItemCodeField);
    panel.add(new JLabel("Quantity:"));     panel.add(addQtyField);
    panel.add(new JLabel("Purchase Date (YYYY-MM-DD):")); panel.add(purchaseDateField);
    panel.add(new JLabel("Expiry Date (YYYY-MM-DD):"));   panel.add(expiryDateField);

    ButtonGroup group = new ButtonGroup();
    group.add(storeRadio);
    group.add(onlineRadio);
    JPanel radioPanel = new JPanel();
    radioPanel.add(storeRadio);
    radioPanel.add(onlineRadio);
    panel.add(new JLabel("Target:"));
    panel.add(radioPanel);

    // Placeholder for button row alignment
    panel.add(new JLabel());
    StyledButton addBtn = StyledButton.success("Receive Stock");
    addBtn.addActionListener(e -> receiveStock());
    panel.add(addBtn);

    return panel;
  }

  // ── Move to Shelf ─────────────────────────────────────────────────────────

  private JPanel buildMoveToShelfPanel() {
    JPanel panel = new JPanel(new GridLayout(0, 2, 8, 6));
    panel.setBorder(BorderFactory.createTitledBorder("Move STORE → SHELF"));

    panel.add(new JLabel("Item Code:"));  panel.add(shelfItemCodeField);
    panel.add(new JLabel("Quantity:"));   panel.add(shelfQtyField);

    panel.add(new JLabel());
    StyledButton moveBtn = StyledButton.neutral("Move to Shelf");
    moveBtn.addActionListener(e -> moveToShelf());
    panel.add(moveBtn);

    return panel;
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  private void receiveStock() {
    String code      = addItemCodeField.getText().trim().toUpperCase();
    String qtyText   = addQtyField.getText().trim();
    String purchase  = purchaseDateField.getText().trim();
    String expiry    = expiryDateField.getText().trim();
    boolean isStore  = storeRadio.isSelected();

    if (code.isEmpty() || purchase.isEmpty() || expiry.isEmpty()) {
      showError("All fields are required."); return;
    }
    int qty;
    try {
      qty = Integer.parseInt(qtyText);
      if (qty <= 0) throw new NumberFormatException();
    } catch (NumberFormatException e) {
      showError("Quantity must be a positive integer."); return;
    }

    CommandType cmd = isStore ? CommandType.ADD_STORE_STOCK : CommandType.ADD_ONLINE_STOCK;
    Map<String, Object> payload = new HashMap<>();
    payload.put("itemCode", code);
    payload.put("quantity", qty);
    payload.put("purchaseDate", purchase);
    payload.put("expiryDate", expiry);

    try {
      Response response = connection.send(new Request(cmd, payload));
      if (response.isSuccess()) {
        showInfo("Stock received. Batch ID: " + response.getData().get("batchId"));
        clearAddForm();
      } else {
        showError("Failed: " + response.getErrorMessage());
      }
    } catch (Exception ex) {
      showError("Connection error: " + ex.getMessage());
    }
  }

  private void moveToShelf() {
    String code    = shelfItemCodeField.getText().trim().toUpperCase();
    String qtyText = shelfQtyField.getText().trim();

    if (code.isEmpty()) { showError("Item code is required."); return; }
    int qty;
    try {
      qty = Integer.parseInt(qtyText);
      if (qty <= 0) throw new NumberFormatException();
    } catch (NumberFormatException e) {
      showError("Quantity must be a positive integer."); return;
    }

    Map<String, Object> payload = new HashMap<>();
    payload.put("itemCode", code);
    payload.put("quantity", qty);
    payload.put("date", LocalDate.now().toString());

    try {
      Response response = connection.send(new Request(CommandType.MOVE_TO_SHELF, payload));
      if (response.isSuccess()) {
        showInfo("Shelf restocked successfully.");
        shelfItemCodeField.setText("");
        shelfQtyField.setText("");
      } else {
        showError("Failed: " + response.getErrorMessage());
      }
    } catch (Exception ex) {
      showError("Connection error: " + ex.getMessage());
    }
  }

  private void clearAddForm() {
    addItemCodeField.setText("");
    addQtyField.setText("");
    purchaseDateField.setText("");
    expiryDateField.setText("");
    storeRadio.setSelected(true);
  }

  private void showInfo(String msg) {
    JOptionPane.showMessageDialog(this, msg, "Success", JOptionPane.INFORMATION_MESSAGE);
  }

  private void showError(String msg) {
    JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
  }
}
