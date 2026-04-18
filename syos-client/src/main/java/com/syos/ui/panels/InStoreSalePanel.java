package com.syos.ui.panels;

import com.syos.network.ServerConnection;
import com.syos.protocol.BillDto;
import com.syos.protocol.ItemDto;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import com.syos.ui.components.BillReceiptPanel;
import com.syos.ui.components.StyledButton;
import com.syos.ui.components.StyledTable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
  private static final Color  BG          = Color.WHITE;
  private static final Color  ERR_COLOR   = new Color(0xe74c3c);
  private static final Color  OK_COLOR    = new Color(0x27ae60);
  private static final Font   TOTAL_FONT  = new Font("Segoe UI", Font.BOLD, 18);
  private static final Font   LABEL_FONT  = new Font("Segoe UI", Font.PLAIN, 13);

  private final ServerConnection connection;

  // ── Form fields ───────────────────────────────────────────────────────────
  private final JTextField itemCodeField = new JTextField(10);
  private final JTextField qtyField      = new JTextField(5);
  private final JTextField cashField     = new JTextField(10);

  // ── Feedback labels ───────────────────────────────────────────────────────
  private final JLabel messageLabel = new JLabel(" ");

  // ── Cart state ────────────────────────────────────────────────────────────
  // Each row: [code(String), name(String), qty(int), unitPrice(double), lineTotal(double)]
  private final List<Object[]> cartRows = new ArrayList<>();
  private final StyledTable    cartTable;
  private final JLabel         totalLabel = new JLabel("Total:  Rs. 0.00");

  // ── Item cache ────────────────────────────────────────────────────────────
  private final Map<String, ItemDto> itemCache = new HashMap<>();
  private boolean cacheLoaded = false;

  // ── Receipt ───────────────────────────────────────────────────────────────
  private final BillReceiptPanel receiptPanel = new BillReceiptPanel();

  // ── "Process Sale" button ref (for loading state) ─────────────────────────
  private StyledButton processBtn;

  public InStoreSalePanel(ServerConnection connection) {
    this.connection = connection;
    this.cartTable  = new StyledTable("Item Code", "Item Name", "Qty", "Unit Price", "Total");

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
    JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
    p.setBackground(BG);
    p.setBorder(BorderFactory.createTitledBorder("Add Item to Cart"));

    p.add(label("Item Code:"));
    p.add(itemCodeField);
    p.add(label("Qty:"));
    p.add(qtyField);

    StyledButton addBtn = StyledButton.primary("Add Item");
    addBtn.addActionListener(e -> handleAddItem());
    p.add(addBtn);

    StyledButton removeBtn = StyledButton.danger("Remove Selected");
    removeBtn.addActionListener(e -> removeSelected());
    p.add(removeBtn);

    return p;
  }

  private JPanel buildSouthArea() {
    JPanel south = new JPanel();
    south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
    south.setBackground(BG);

    // Total row
    totalLabel.setFont(TOTAL_FONT);
    totalLabel.setForeground(new Color(0x1a2744));
    JPanel totalRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
    totalRow.setBackground(BG);
    totalRow.add(totalLabel);
    south.add(totalRow);

    // Cash + process row
    JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    actionRow.setBackground(BG);
    actionRow.add(label("Cash Tendered (Rs.):"));
    actionRow.add(cashField);
    processBtn = StyledButton.success("Process Sale");
    processBtn.addActionListener(e -> processSale());
    actionRow.add(processBtn);
    StyledButton clearBtn = StyledButton.danger("Clear Cart");
    clearBtn.addActionListener(e -> clearCart());
    actionRow.add(clearBtn);
    south.add(actionRow);

    // Message label
    messageLabel.setFont(LABEL_FONT);
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
      addItemToCart(code, qty);
    }
  }

  /** Loads the item cache from the server, then adds the item. */
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

  private void removeSelected() {
    int row = cartTable.getSelectedRow();
    if (row < 0) { showError("Select a row to remove."); return; }
    cartRows.remove(row);
    refreshTable();
  }

  private void clearCart() {
    cartRows.clear();
    refreshTable();
    cashField.setText("");
    receiptPanel.clear();
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
            receiptPanel.displayBill((BillDto) r.getPayload());
            showSuccess("Sale processed successfully!");
            clearCart();
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

  private void showError(String msg)   { messageLabel.setForeground(ERR_COLOR); messageLabel.setText(msg); }
  private void showSuccess(String msg) { messageLabel.setForeground(OK_COLOR);  messageLabel.setText(msg); }
  private void showMessage(String msg) { messageLabel.setForeground(Color.BLACK); messageLabel.setText(msg); }

  private JLabel label(String text) {
    JLabel l = new JLabel(text);
    l.setFont(LABEL_FONT);
    return l;
  }
}
