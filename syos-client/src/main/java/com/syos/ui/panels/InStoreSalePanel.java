package com.syos.ui.panels;

import com.syos.network.ServerConnection;
import com.syos.protocol.BillDto;
import com.syos.protocol.ItemDto;
import com.syos.protocol.ReportDto;
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
 * Panel for processing in-store (POS) sales.
 *
 * <p>The cashier selects items, enters quantities and cash tendered, then submits
 * the sale. All server calls happen on a {@link SwingWorker} — the EDT is never blocked.
 * Feedback is shown inline via coloured {@link JLabel}s; no popup dialogs are used
 * for normal flow.
 */
public class InStoreSalePanel extends JPanel {

  // ── Colour / font constants ───────────────────────────────────────────────
  private static final Color  BG          = UiTheme.PANEL_BG;
  private static final Color  ERR_COLOR   = new Color(0xe74c3c);
  private static final Color  OK_COLOR    = new Color(0x27ae60);
  private static final Color  STOCK_ERR_COLOR = new Color(0xc0392b);
  private static final Font   TOTAL_FONT  = new Font("Segoe UI", Font.BOLD, 18);
  private final ServerConnection connection;

  // ── Form fields ───────────────────────────────────────────────────────────
  private final JTextField itemCodeField = new JTextField(10);
  private final JTextField qtyField      = new JTextField(5);
  private final JTextField cashField     = new JTextField(10);
  private final JLabel      stockHintLabel = new JLabel(" ");

  // ── Feedback labels ───────────────────────────────────────────────────────
  private final JLabel messageLabel = new JLabel(" ");

  // ── Cart state ────────────────────────────────────────────────────────────
  // Each row: [code(String), name(String), qty(int), unitPrice(double), lineTotal(double)]
  private final List<Object[]> cartRows = new ArrayList<>();
  private final StyledTable    cartTable;
  private final JLabel         totalLabel = new JLabel("Total:  Rs. 0.00");

  // ── Item cache ────────────────────────────────────────────────────────────
  private final Map<String, ItemDto> itemCache = new HashMap<>();
  private final Map<String, Integer> stockHints = new HashMap<>();
  private boolean cacheLoaded = false;
  private boolean cacheLoading = false;

  // ── Receipt ───────────────────────────────────────────────────────────────
  private final BillReceiptPanel receiptPanel = new BillReceiptPanel();

  // ── "Process Sale" button ref (for loading state) ─────────────────────────
  private StyledButton addBtn;
  private StyledButton processBtn;

  public InStoreSalePanel(ServerConnection connection) {
    this.connection = connection;
    this.cartTable  = new StyledTable("Item Code", "Item Name", "Qty", "Unit Price", "Total");
    UiTheme.styleTextFields(itemCodeField, qtyField, cashField);
    
    // Style stock hint label
    stockHintLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
    stockHintLabel.setForeground(new Color(0x27ae60));

    // Add tooltip clear listeners for input fields
    itemCodeField.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent e) { itemCodeField.setToolTipText(null); onItemInputChanged(); }
      @Override public void removeUpdate(DocumentEvent e) { itemCodeField.setToolTipText(null); onItemInputChanged(); }
      @Override public void changedUpdate(DocumentEvent e) { itemCodeField.setToolTipText(null); onItemInputChanged(); }
    });
    qtyField.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent e) { qtyField.setToolTipText(null); onItemInputChanged(); }
      @Override public void removeUpdate(DocumentEvent e) { qtyField.setToolTipText(null); onItemInputChanged(); }
      @Override public void changedUpdate(DocumentEvent e) { qtyField.setToolTipText(null); onItemInputChanged(); }
    });
    cashField.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent e) { cashField.setToolTipText(null); }
      @Override public void removeUpdate(DocumentEvent e) { cashField.setToolTipText(null); }
      @Override public void changedUpdate(DocumentEvent e) { cashField.setToolTipText(null); }
    });

    setLayout(new BorderLayout(8, 8));
    setBackground(BG);
    setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

    // Left side: input + cart + total + cash + message
    JPanel leftPanel = new JPanel(new BorderLayout(6, 6));
    leftPanel.setBackground(BG);
    leftPanel.add(buildInputRow(),          BorderLayout.NORTH);
    leftPanel.add(cartTable.getScrollPane(), BorderLayout.CENTER);
    leftPanel.add(buildSouthArea(),         BorderLayout.SOUTH);

    // Right side: receipt
    receiptPanel.setPreferredSize(new Dimension(300, 400));

    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, receiptPanel);
    split.setDividerLocation(700);
    split.setResizeWeight(0.7);
    split.setBorder(null);
    add(split, BorderLayout.CENTER);
  }

  // ── Layout builders ───────────────────────────────────────────────────────

  private JPanel buildInputRow() {
    JPanel p = new JPanel(new BorderLayout(0, 6));
    p.setBackground(BG);
    p.setBorder(UiTheme.titledBorder("Add Item to Cart"));
    p.setPreferredSize(new Dimension(0, 132));

    JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
    topRow.setBackground(BG);
    topRow.add(UiTheme.label("Item Code:"));
    topRow.add(itemCodeField);
    topRow.add(UiTheme.label("Qty:"));
    topRow.add(qtyField);
    topRow.add(stockHintLabel);

    StyledButton addBtn = StyledButton.primary("Add Item");
    addBtn.setPreferredSize(new Dimension(136, 44));
    addBtn.setMinimumSize(new Dimension(136, 44));
    this.addBtn = addBtn;
    addBtn.addActionListener(e -> handleAddItem());
    topRow.add(addBtn);

    JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    bottomRow.setBackground(BG);
    StyledButton removeBtn = StyledButton.danger("Remove Selected");
    removeBtn.setPreferredSize(new Dimension(196, 44));
    removeBtn.setMinimumSize(new Dimension(196, 44));
    removeBtn.addActionListener(e -> removeSelected());
    bottomRow.add(removeBtn);

    p.add(topRow, BorderLayout.NORTH);
    p.add(bottomRow, BorderLayout.SOUTH);

    return p;
  }

  private JPanel buildSouthArea() {
    JPanel south = new JPanel();
    south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
    south.setBackground(BG);

    // Total row
    totalLabel.setFont(TOTAL_FONT);
    totalLabel.setForeground(UiTheme.TEXT_PRIMARY);
    JPanel totalRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
    totalRow.setBackground(BG);
    totalRow.add(totalLabel);
    south.add(totalRow);

    // Cash + process row
    JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    actionRow.setBackground(BG);
    actionRow.add(UiTheme.label("Cash Tendered (Rs.):"));
    actionRow.add(cashField);
    processBtn = StyledButton.success("Process Sale");
    processBtn.addActionListener(e -> processSale());
    actionRow.add(processBtn);
    StyledButton clearBtn = StyledButton.danger("Clear Cart");
    clearBtn.addActionListener(e -> clearCart(true));
    actionRow.add(clearBtn);
    south.add(actionRow);

    // Message label
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
    String code = itemCodeField.getText().trim().toUpperCase();
    String qtyText = qtyField.getText().trim();

    String err = validateItemInput(code, qtyText);
    if (err != null) { showError(err); return; }
    int qty = Integer.parseInt(qtyText);

    if (!cacheLoaded) {
      loadItemCacheThenAdd(code, qty);
    } else {
      String stockErr = validateAvailableStock(code, qty);
      if (stockErr != null) { showError(stockErr); return; }
      addItemToCart(code, qty);
    }
  }

  /** Loads the item cache and shelf-stock hints from the server, then optionally adds the item. */
  private void loadItemCacheThenAdd(String code, int qty) {
    if (cacheLoading) {
      return;
    }
    cacheLoading = true;
    if (addBtn != null) {
      addBtn.setEnabled(false);
    }
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
            for (ItemDto item : items) {
              itemCache.put(item.getCode(), item);
            }

            Response stockResponse = connection.sendRequest(
                Request.getStockReport("SHELF", LocalDate.now().toString()));
            if (stockResponse.isSuccess() && stockResponse.getPayload() instanceof ReportDto stockReport) {
              stockHints.clear();
              for (Map<String, Object> row : stockReport.getData()) {
                Object codeValue = row.get("Item Code") != null
                    ? row.get("Item Code")
                    : (row.get("Code") != null ? row.get("Code") : row.get("itemCode"));
                Object qtyValue = row.get("Quantity") != null
                    ? row.get("Quantity")
                    : (row.get("Qty") != null ? row.get("Qty") : row.get("quantity"));
                if (codeValue != null && qtyValue instanceof Number number) {
                  stockHints.put(String.valueOf(codeValue).trim().toUpperCase(), number.intValue());
                }
              }
            }

            cacheLoaded = true;
            updateStockHint(code);
            if (qty > 0) {
              String stockErr = validateAvailableStock(code, qty);
              if (stockErr != null) {
                showError(stockErr);
              } else {
                addItemToCart(code, qty);
              }
            }
          } else {
            showError("Failed to load items: " + r.getErrorMessage());
          }
        } catch (InterruptedException | ExecutionException ex) {
          showError("Server error: " + ex.getMessage());
        } catch (Exception ex) {
          showError("Server error: " + ex.getMessage());
        } finally {
          cacheLoading = false;
          updateAddButtonState();
        }
      }
    }.execute();
  }

  private void addItemToCart(String code, int qty) {
    ItemDto item = itemCache.get(code);
    if (item == null) { showError("Item not found: " + code); return; }

    // Check if already in cart → merge
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

  /**
   * Updates the stock hint label to show current stock quantity for an item code.
   */
  private void updateStockHint(String code) {
    if (code == null || code.isBlank()) {
      stockHintLabel.setText(" ");
      stockHintLabel.setForeground(OK_COLOR);
      return;
    }
    Integer stock = stockHints.get(code.toUpperCase());
    int requestedQty = parseQty(qtyField.getText().trim());
    if (stock != null) {
      if (requestedQty > 0 && requestedQty > stock) {
        stockHintLabel.setForeground(STOCK_ERR_COLOR);
      } else {
        stockHintLabel.setForeground(OK_COLOR);
      }
      stockHintLabel.setText("  Stock: " + stock);
    } else {
      stockHintLabel.setForeground(UiTheme.TEXT_SECONDARY);
      stockHintLabel.setText("  Stock: N/A");
    }
  }

  private void onItemInputChanged() {
    String code = itemCodeField.getText().trim().toUpperCase();
    if (cacheLoaded) {
      updateStockHint(code);
      updateAddButtonState();
    } else if (!code.isBlank() && !cacheLoading) {
      stockHintLabel.setForeground(UiTheme.TEXT_SECONDARY);
      stockHintLabel.setText("  Checking...");
      loadItemCacheThenAdd(code, -1);
      updateAddButtonState();
    }
  }

  private void updateAddButtonState() {
    if (addBtn == null) {
      return;
    }
    if (cacheLoading) {
      addBtn.setEnabled(false);
      return;
    }
    String code = itemCodeField.getText().trim().toUpperCase();
    String qtyText = qtyField.getText().trim();
    if (validateItemInput(code, qtyText) != null) {
      addBtn.setEnabled(false);
      return;
    }
    int qty = parseQty(qtyText);
    if (!cacheLoaded || qty <= 0) {
      addBtn.setEnabled(false);
      return;
    }
    addBtn.setEnabled(validateAvailableStock(code, qty) == null);
  }

  private int parseQty(String qtyText) {
    try {
      return Integer.parseInt(qtyText);
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }

  private String validateAvailableStock(String code, int qty) {
    Integer stock = stockHints.get(code.toUpperCase());
    if (stock == null) {
      return "Stock is unavailable for item: " + code;
    }
    if (qty > stock) {
      return "Quantity exceeds available stock (Available: " + stock + ").";
    }
    return null;
  }

  private void removeSelected() {
    int row = cartTable.getSelectedRow();
    if (row < 0) { showError("Select a row to remove."); return; }
    cartRows.remove(row);
    refreshTable();
  }

  /** Clears the cart form; optionally clears the receipt panel (Clear Cart button only). */
  private void clearCart(boolean clearReceipt) {
    cartRows.clear();
    refreshTable();
    cashField.setText("");
    clearInputs();
    if (clearReceipt) {
      receiptPanel.clear();
    }
    showMessage(" ");
  }

  private void processSale() {
    if (cartRows.isEmpty())         { showError("Cart is empty — add items first."); return; }
    String cashText = cashField.getText().trim();
    String cashErr = validateCash(cashText);
    if (cashErr != null)            { showError(cashErr); return; }
    double cash = Double.parseDouble(cashText);

    Map<String, Integer> items = new LinkedHashMap<>();
    for (Object[] row : cartRows) items.put((String) row[0], (int) row[2]);

    Request req = Request.inStoreSale(items, cash, LocalDate.now().toString());

    processBtn.setEnabled(false);
    processBtn.setText("Processing...");
    showMessage(" ");

    new SwingWorker<Response, Void>() {
      @Override protected Response doInBackground() throws Exception {
        return connection.sendRequest(req);
      }
      @Override protected void done() {
        processBtn.setEnabled(true);
        processBtn.setText("Process Sale");
        try {
          Response r = get();
          if (r.isSuccess()) {
            Object payload = r.getPayload();
            if (payload instanceof BillDto bill) {
              receiptPanel.displayBill(bill);
              showSuccess("Sale processed successfully! Bill #" + bill.getSerialNumber());
              clearCart(false);
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
    if (code == null || code.isBlank())   return "Item code cannot be empty.";
    if (qtyText == null || qtyText.isBlank()) return "Quantity cannot be empty.";
    try {
      int q = Integer.parseInt(qtyText);
      if (q <= 0) return "Quantity must be a positive integer.";
    } catch (NumberFormatException e) {
      return "Quantity must be a whole number.";
    }
    return null; // valid
  }

  String validateCash(String cashText) {
    if (cashText == null || cashText.isBlank()) return "Cash amount cannot be empty.";
    try {
      double c = Double.parseDouble(cashText);
      if (c <= 0) return "Cash amount must be positive.";
    } catch (NumberFormatException e) {
      return "Cash amount must be a number.";
    }
    return null;
  }

  double calculateTotal(List<Object[]> rows) {
    return rows.stream().mapToDouble(r -> (double) r[4]).sum();
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
    if (msg != null && msg.toLowerCase().contains("item")) {
      itemCodeField.setToolTipText(msg);
    } else if (msg != null && msg.toLowerCase().contains("quantity")) {
      qtyField.setToolTipText(msg);
    } else if (msg != null && msg.toLowerCase().contains("cash")) {
      cashField.setToolTipText(msg);
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
    cacheLoading = false;
    itemCache.clear();
    stockHints.clear();
    stockHintLabel.setText(" ");
    updateAddButtonState();
  }
}
