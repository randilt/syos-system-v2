package com.syos.ui.panels;

import com.syos.network.ServerConnection;
import com.syos.protocol.BillDto;
import com.syos.protocol.ItemDto;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import com.syos.ui.UiTheme;
import com.syos.ui.components.BillReceiptPanel;
import com.syos.ui.components.StyledButton;
import com.syos.ui.components.StyledTable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JSplitPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Panel for processing online sales.
 *
 * <p>All server calls run on {@link SwingWorker} threads; the EDT is never blocked.
 * Feedback is shown via inline coloured {@link JLabel}s.
 */
public class OnlineSalePanel extends JPanel {

  private static final Color BG        = UiTheme.PANEL_BG;
  private static final Color ERR_COLOR = new Color(0xe74c3c);
  private static final Color OK_COLOR  = new Color(0x27ae60);
  private static final Font  TOTAL_FONT = new Font("Segoe UI", Font.BOLD, 18);

  private final ServerConnection connection;

  private final JTextField userIdField   = new JTextField(12);
  private final JTextField itemCodeField = new JTextField(10);
  private final JTextField qtyField      = new JTextField(5);

  private final JLabel messageLabel = new JLabel(" ");
  private final JLabel totalLabel   = new JLabel("Total:  Rs. 0.00");

  private final List<Object[]> cartRows = new ArrayList<>();
  private final StyledTable    cartTable;

  private final Map<String, ItemDto> itemCache = new HashMap<>();
  private boolean cacheLoaded = false;

  private final BillReceiptPanel receiptPanel = new BillReceiptPanel();
  private StyledButton processBtn;

  public OnlineSalePanel(ServerConnection connection) {
    this.connection = connection;
    this.cartTable  = new StyledTable("Item Code", "Item Name", "Qty", "Unit Price", "Total");
    UiTheme.styleTextFields(userIdField, itemCodeField, qtyField);

    // Add tooltip clear listeners for input fields
    userIdField.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent e) { userIdField.setToolTipText(null); }
      @Override public void removeUpdate(DocumentEvent e) { userIdField.setToolTipText(null); }
      @Override public void changedUpdate(DocumentEvent e) { userIdField.setToolTipText(null); }
    });
    itemCodeField.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent e) { itemCodeField.setToolTipText(null); }
      @Override public void removeUpdate(DocumentEvent e) { itemCodeField.setToolTipText(null); }
      @Override public void changedUpdate(DocumentEvent e) { itemCodeField.setToolTipText(null); }
    });
    qtyField.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent e) { qtyField.setToolTipText(null); }
      @Override public void removeUpdate(DocumentEvent e) { qtyField.setToolTipText(null); }
      @Override public void changedUpdate(DocumentEvent e) { qtyField.setToolTipText(null); }
    });

    setLayout(new BorderLayout(8, 8));
    setBackground(BG);
    setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

    JPanel leftPanel = new JPanel(new BorderLayout(6, 6));
    leftPanel.setBackground(BG);
    leftPanel.add(buildNorthArea(),          BorderLayout.NORTH);
    leftPanel.add(cartTable.getScrollPane(), BorderLayout.CENTER);
    leftPanel.add(buildSouthArea(),          BorderLayout.SOUTH);

    receiptPanel.setPreferredSize(new Dimension(300, 400));

    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, receiptPanel);
    split.setDividerLocation(700);
    split.setResizeWeight(0.7);
    split.setBorder(null);
    add(split, BorderLayout.CENTER);
  }

  // ── Layout builders ───────────────────────────────────────────────────────

  private JPanel buildNorthArea() {
    JPanel north = new JPanel();
    north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
    north.setBackground(BG);

    // User ID row
    JPanel userRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    userRow.setBackground(BG);
    userRow.setBorder(UiTheme.titledBorder("Customer"));
    userRow.add(UiTheme.label("User ID:"));
    userRow.add(userIdField);
    north.add(userRow);

    // Cart input row
    JPanel itemRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    itemRow.setBackground(BG);
    itemRow.setBorder(UiTheme.titledBorder("Add Item to Cart"));
    itemRow.add(UiTheme.label("Item Code:"));
    itemRow.add(itemCodeField);
    itemRow.add(UiTheme.label("Qty:"));
    itemRow.add(qtyField);
    StyledButton addBtn = StyledButton.primary("Add Item");
    addBtn.addActionListener(e -> handleAddItem());
    itemRow.add(addBtn);
    StyledButton removeBtn = StyledButton.danger("Remove Selected");
    removeBtn.addActionListener(e -> removeSelected());
    itemRow.add(removeBtn);
    north.add(itemRow);

    return north;
  }

  private JPanel buildSouthArea() {
    JPanel south = new JPanel();
    south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
    south.setBackground(BG);

    totalLabel.setFont(TOTAL_FONT);
    totalLabel.setForeground(UiTheme.TEXT_PRIMARY);
    JPanel totalRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
    totalRow.setBackground(BG);
    totalRow.add(totalLabel);
    south.add(totalRow);

    JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    actionRow.setBackground(BG);
    processBtn = StyledButton.success("Process Online Sale");
    processBtn.addActionListener(e -> processSale());
    actionRow.add(processBtn);
    StyledButton clearBtn = StyledButton.danger("Clear Cart");
    clearBtn.addActionListener(e -> clearCart(true));
    actionRow.add(clearBtn);
    south.add(actionRow);

    messageLabel.setFont(UiTheme.LABEL_FONT);
    messageLabel.setForeground(UiTheme.TEXT_SECONDARY);
    JPanel msgRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
    msgRow.setBackground(BG);
    msgRow.add(messageLabel);
    south.add(msgRow);

    return south;
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  private void handleAddItem() {
    String code    = itemCodeField.getText().trim().toUpperCase();
    String qtyText = qtyField.getText().trim();
    String err = validateItemInput(code, qtyText);
    if (err != null) { showError(err); return; }
    int qty = Integer.parseInt(qtyText);

    if (!cacheLoaded) {
      loadItemCacheThenAdd(code, qty);
    } else {
      addItemToCart(code, qty);
    }
  }

  private void loadItemCacheThenAdd(String code, int qty) {
    new SwingWorker<Response, Void>() {
      @Override protected Response doInBackground() throws Exception {
        return connection.sendRequest(Request.getAllItems());
      }
      @Override protected void done() {
        try {
          Response r = get();
          if (r.isSuccess() && r.getPayload() instanceof List) {
            @SuppressWarnings("unchecked")
            List<ItemDto> items = (List<ItemDto>) r.getPayload();
            for (ItemDto item : items) itemCache.put(item.getCode(), item);
            cacheLoaded = true;
            addItemToCart(code, qty);
          } else {
            showError("Failed to load items: " + r.getErrorMessage());
          }
        } catch (InterruptedException | ExecutionException ex) {
          showError("Server error: " + ex.getMessage());
        }
      }
    }.execute();
  }

  private void addItemToCart(String code, int qty) {
    ItemDto item = itemCache.get(code);
    if (item == null) { showError("Item not found: " + code); return; }

    for (Object[] row : cartRows) {
      if (row[0].equals(code)) {
        int newQty = (int) row[2] + qty;
        row[2] = newQty;
        row[4] = newQty * (double) row[3];
        refreshTable();
        clearInputs();
        showMessage(" ");
        return;
      }
    }

    double lineTotal = qty * item.getUnitPrice();
    cartRows.add(new Object[]{code, item.getName(), qty, item.getUnitPrice(), lineTotal});
    refreshTable();
    clearInputs();
    showMessage(" ");
  }

  private void removeSelected() {
    int row = cartTable.getSelectedRow();
    if (row < 0) { showError("Select a row to remove."); return; }
    cartRows.remove(row);
    refreshTable();
  }

  private void clearCart(boolean clearReceipt) {
    cartRows.clear();
    refreshTable();
    clearInputs();
    if (clearReceipt) {
      receiptPanel.clear();
    }
    showMessage(" ");
  }

  private void processSale() {
    String userId = userIdField.getText().trim();
    if (userId.isBlank())     { showError("User ID is required."); return; }
    if (cartRows.isEmpty())   { showError("Cart is empty — add items first."); return; }

    Map<String, Integer> items = new LinkedHashMap<>();
    for (Object[] row : cartRows) items.put((String) row[0], (int) row[2]);

    Request req = Request.onlineSale(userId, items, LocalDate.now().toString());

    processBtn.setEnabled(false);
    processBtn.setText("Processing...");
    showMessage(" ");

    new SwingWorker<Response, Void>() {
      @Override protected Response doInBackground() throws Exception {
        return connection.sendRequest(req);
      }
      @Override protected void done() {
        processBtn.setEnabled(true);
        processBtn.setText("Process Online Sale");
        try {
          Response r = get();
          if (r.isSuccess()) {
            Object payload = r.getPayload();
            if (payload instanceof BillDto bill) {
              receiptPanel.displayBill(bill);
              showSuccess("Online sale processed successfully! Bill #" + bill.getSerialNumber());
              clearCart(false);
              userIdField.setText("");
            } else {
              showError("Unexpected response from server.");
            }
          } else {
            showError(r.getErrorMessage());
          }
        } catch (InterruptedException | ExecutionException ex) {
          showError("Server error: " + ex.getMessage());
        }
      }
    }.execute();
  }

  // ── Validation (package-private for tests) ────────────────────────────────

  String validateItemInput(String code, String qtyText) {
    if (code == null || code.isBlank())       return "Item code cannot be empty.";
    if (qtyText == null || qtyText.isBlank()) return "Quantity cannot be empty.";
    try {
      if (Integer.parseInt(qtyText) <= 0)     return "Quantity must be a positive integer.";
    } catch (NumberFormatException e) {
      return "Quantity must be a whole number.";
    }
    return null;
  }

  String validateUserId(String userId) {
    if (userId == null || userId.isBlank()) return "User ID is required.";
    return null;
  }

  // ── UI helpers ────────────────────────────────────────────────────────────

  private void refreshTable() {
    DefaultTableModel model = cartTable.getModel();
    model.setRowCount(0);
    double total = 0.0;
    for (Object[] row : cartRows) {
      model.addRow(row);
      total += (double) row[4];
    }
    totalLabel.setText(String.format("Total:  Rs. %,.2f", total));
  }

  private void clearInputs() {
    itemCodeField.setText("");
    qtyField.setText("");
    itemCodeField.requestFocus();
  }

  /**
   * Shows an error message and sets tooltip on relevant field if applicable.
   */
  private void showError(String msg) {
    messageLabel.setForeground(ERR_COLOR);
    messageLabel.setText(msg);
    if (msg != null && msg.toLowerCase().contains("user")) {
      userIdField.setToolTipText(msg);
    } else if (msg != null && msg.toLowerCase().contains("item")) {
      itemCodeField.setToolTipText(msg);
    } else if (msg != null && msg.toLowerCase().contains("quantity")) {
      qtyField.setToolTipText(msg);
    }
  }
  private void showSuccess(String msg) { messageLabel.setForeground(OK_COLOR);  messageLabel.setText(msg); }
  private void showMessage(String msg) {
    messageLabel.setForeground(UiTheme.TEXT_SECONDARY);
    messageLabel.setText(msg);
  }

  /** Invalidates the item catalogue cache so fresh data is fetched on next use. */
  public void invalidateCache() {
    cacheLoaded = false;
    itemCache.clear();
  }
}
