package com.syos.ui.panels;

import com.syos.network.ServerConnection;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import com.syos.protocol.ReportDto;
import com.syos.ui.UiTheme;
import com.syos.ui.components.StyledButton;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.SwingWorker;

/**
 * Stock management panel with two tabs:
 * <ul>
 *   <li><b>Receive Stock</b> — add a new batch to STORE or ONLINE</li>
 *   <li><b>Restock Shelf</b>  — move items from STORE to SHELF</li>
 * </ul>
 *
 * <p>All server calls run on {@link SwingWorker} threads.
 */
public class StockManagementPanel extends JPanel {

  private static final Color BG        = UiTheme.PANEL_BG;
  private static final Color ERR_COLOR = new Color(0xe74c3c);
  private static final Color OK_COLOR  = new Color(0x27ae60);
  private final ServerConnection connection;

  // ── Receive Stock tab ──────────────────────────────────────────────────────
  private final JTextField    addCodeField    = new JTextField(12);
  private final JTextField    addQtyField     = new JTextField(6);
  private final JSpinner      purchaseSpin    = new JSpinner(new SpinnerDateModel());
  private final JSpinner      expirySpin      = new JSpinner(new SpinnerDateModel());
  private final JRadioButton  storeRadio      = new JRadioButton("STORE",  true);
  private final JRadioButton  onlineRadio     = new JRadioButton("ONLINE");
  private final JLabel        addMsgLabel     = new JLabel(" ");
  private StyledButton        receiveBtn;

  // ── Restock Shelf tab ─────────────────────────────────────────────────────
  private final JTextField    shelfCodeField  = new JTextField(12);
  private final JTextField    shelfQtyField   = new JTextField(6);
  private final JLabel        shelfMsgLabel   = new JLabel(" ");
  private StyledButton        moveBtn;

  public StockManagementPanel(ServerConnection connection) {
    this.connection = connection;
    UiTheme.styleTextFields(addCodeField, addQtyField, shelfCodeField, shelfQtyField);
    UiTheme.styleSpinner(purchaseSpin);
    UiTheme.styleSpinner(expirySpin);
    UiTheme.styleRadioButton(storeRadio);
    UiTheme.styleRadioButton(onlineRadio);
    UiTheme.styleLabel(addMsgLabel);
    UiTheme.styleLabel(shelfMsgLabel);

    // Add tooltip clear listeners for Receive Stock tab
    addCodeField.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent e) { addCodeField.setToolTipText(null); }
      @Override public void removeUpdate(DocumentEvent e) { addCodeField.setToolTipText(null); }
      @Override public void changedUpdate(DocumentEvent e) { addCodeField.setToolTipText(null); }
    });
    addQtyField.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent e) { addQtyField.setToolTipText(null); }
      @Override public void removeUpdate(DocumentEvent e) { addQtyField.setToolTipText(null); }
      @Override public void changedUpdate(DocumentEvent e) { addQtyField.setToolTipText(null); }
    });
    // Add tooltip clear listeners for Restock Shelf tab
    shelfCodeField.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent e) { shelfCodeField.setToolTipText(null); }
      @Override public void removeUpdate(DocumentEvent e) { shelfCodeField.setToolTipText(null); }
      @Override public void changedUpdate(DocumentEvent e) { shelfCodeField.setToolTipText(null); }
    });
    shelfQtyField.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent e) { shelfQtyField.setToolTipText(null); }
      @Override public void removeUpdate(DocumentEvent e) { shelfQtyField.setToolTipText(null); }
      @Override public void changedUpdate(DocumentEvent e) { shelfQtyField.setToolTipText(null); }
    });

    setLayout(new BorderLayout());
    setBackground(BG);
    setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

    JTabbedPane tabs = new JTabbedPane();
    UiTheme.styleTabbedPane(tabs);
    tabs.addTab("Receive Stock",  buildReceiveStockTab());
    tabs.addTab("Restock Shelf",  buildRestockShelfTab());
    // Use custom JLabel components for tab headers so text colour is explicit
    // and cannot be overridden by the Linux GTK Look-and-Feel.
    for (int i = 0; i < tabs.getTabCount(); i++) {
      JLabel tabLabel = new JLabel(tabs.getTitleAt(i));
      tabLabel.setForeground(UiTheme.TEXT_PRIMARY);
      tabLabel.setFont(UiTheme.LABEL_FONT);
      tabLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
      tabs.setTabComponentAt(i, tabLabel);
    }
    add(tabs, BorderLayout.CENTER);
  }

  // ── Receive Stock tab ──────────────────────────────────────────────────────

  private JPanel buildReceiveStockTab() {
    JPanel outer = new JPanel(new BorderLayout());
    outer.setBackground(BG);
    outer.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

    JPanel form = new JPanel(new GridBagLayout());
    form.setBackground(BG);
    GridBagConstraints gc = new GridBagConstraints();
    gc.insets  = new Insets(6, 8, 6, 8);
    gc.anchor  = GridBagConstraints.WEST;
    gc.fill    = GridBagConstraints.HORIZONTAL;

    configureSpinnerDateEditor(purchaseSpin);
    configureSpinnerDateEditor(expirySpin);

    ButtonGroup bg = new ButtonGroup();
    bg.add(storeRadio);
    bg.add(onlineRadio);
    JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    radioPanel.setBackground(BG);
    radioPanel.add(storeRadio);
    radioPanel.add(onlineRadio);

    addRow(form, gc, 0, "Item Code:",           addCodeField);
    addRow(form, gc, 1, "Quantity:",             addQtyField);
    addRow(form, gc, 2, "Purchase Date:",        purchaseSpin);
    addRow(form, gc, 3, "Expiry Date:",          expirySpin);
    addRow(form, gc, 4, "Target:",               radioPanel);

    StyledButton checkStockBtn = StyledButton.neutral("Check Stock");
    checkStockBtn.addActionListener(e -> checkStock());
    gc.gridx = 0; gc.gridy = 5;
    form.add(checkStockBtn, gc);

    receiveBtn = StyledButton.success("Receive Stock");
    receiveBtn.addActionListener(e -> receiveStock());
    gc.gridx = 1; gc.gridy = 5;
    form.add(receiveBtn, gc);

    gc.gridx = 0; gc.gridy = 6; gc.gridwidth = 2;
    form.add(addMsgLabel, gc);

    outer.add(form, BorderLayout.NORTH);
    return outer;
  }

  // ── Restock Shelf tab ─────────────────────────────────────────────────────

  private JPanel buildRestockShelfTab() {
    JPanel outer = new JPanel(new BorderLayout());
    outer.setBackground(BG);
    outer.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

    JPanel form = new JPanel(new GridBagLayout());
    form.setBackground(BG);
    GridBagConstraints gc = new GridBagConstraints();
    gc.insets  = new Insets(6, 8, 6, 8);
    gc.anchor  = GridBagConstraints.WEST;
    gc.fill    = GridBagConstraints.HORIZONTAL;

    addRow(form, gc, 0, "Item Code:", shelfCodeField);
    addRow(form, gc, 1, "Quantity:",  shelfQtyField);

    moveBtn = StyledButton.success("Move to Shelf");
    moveBtn.addActionListener(e -> moveToShelf());
    gc.gridx = 1; gc.gridy = 2;
    form.add(moveBtn, gc);

    gc.gridx = 0; gc.gridy = 3; gc.gridwidth = 2;
    form.add(shelfMsgLabel, gc);

    outer.add(form, BorderLayout.NORTH);
    return outer;
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  private void receiveStock() {
    String code    = addCodeField.getText().trim().toUpperCase();
    String qtyText = addQtyField.getText().trim();

    String err = validateStockInput(code, qtyText);
    if (err != null) { showAddMsg(err, false); return; }
    int qty = Integer.parseInt(qtyText);

    Date purchaseDate = (Date) purchaseSpin.getValue();
    Date expiryDate   = (Date) expirySpin.getValue();
    String purchase   = toIsoDate(purchaseDate);
    String expiry     = toIsoDate(expiryDate);
    String target     = storeRadio.isSelected() ? "STORE" : "ONLINE";

    Request req = Request.addStock(code, purchase, expiry, qty, target);
    receiveBtn.setEnabled(false);
    receiveBtn.setText("Adding...");

    new SwingWorker<Response, Void>() {
      @Override protected Response doInBackground() throws Exception {
        return connection.sendRequest(req);
      }
      @Override protected void done() {
        receiveBtn.setEnabled(true);
        receiveBtn.setText("Receive Stock");
        try {
          Response r = get();
          if (r.isSuccess()) {
            showAddMsg("Stock received successfully!", true);
            clearAddForm();
          } else {
            showAddMsg(r.getErrorMessage(), false);
          }
        } catch (InterruptedException | ExecutionException ex) {
          showAddMsg("Server error: " + ex.getMessage(), false);
        }
      }
    }.execute();
  }

  private void moveToShelf() {
    String code    = shelfCodeField.getText().trim().toUpperCase();
    String qtyText = shelfQtyField.getText().trim();

    String err = validateStockInput(code, qtyText);
    if (err != null) { showShelfMsg(err, false); return; }
    int qty = Integer.parseInt(qtyText);

    Request req = Request.moveToShelf(code, qty, LocalDate.now().toString());
    moveBtn.setEnabled(false);
    moveBtn.setText("Moving...");

    new SwingWorker<Response, Void>() {
      @Override protected Response doInBackground() throws Exception {
        return connection.sendRequest(req);
      }
      @Override protected void done() {
        moveBtn.setEnabled(true);
        moveBtn.setText("Move to Shelf");
        try {
          Response r = get();
          if (r.isSuccess()) {
            showShelfMsg("Shelf restocked successfully!", true);
            shelfCodeField.setText("");
            shelfQtyField.setText("");
          } else {
            showShelfMsg(r.getErrorMessage(), false);
          }
        } catch (InterruptedException | ExecutionException ex) {
          showShelfMsg("Server error: " + ex.getMessage(), false);
        }
      }
    }.execute();
  }

  // ── Validation (package-private for tests) ────────────────────────────────

  String validateStockInput(String code, String qtyText) {
    if (code == null || code.isBlank())       return "Item code cannot be empty.";
    if (qtyText == null || qtyText.isBlank()) return "Quantity cannot be empty.";
    try {
      if (Integer.parseInt(qtyText) <= 0)     return "Quantity must be a positive integer.";
    } catch (NumberFormatException e) {
      return "Quantity must be a whole number.";
    }
    return null;
  }

  /**
   * Checks and displays the current stock level for an item across STORE, SHELF, and ONLINE.
   */
  private void checkStock() {
    String code = addCodeField.getText().trim().toUpperCase();
    if (code.isBlank()) {
      showAddMsg("Enter an item code to check stock.", false);
      return;
    }

    showAddMsg("Checking...", true);

    new SwingWorker<String, Void>() {
      @Override protected String doInBackground() {
        String date = LocalDate.now().toString();
        int storeQty = fetchLocationQty("STORE", code, date);
        int shelfQty = fetchLocationQty("SHELF", code, date);
        int onlineQty = fetchLocationQty("ONLINE", code, date);
        return String.format("STORE: %d | SHELF: %d | ONLINE: %d", storeQty, shelfQty, onlineQty);
      }

      @Override protected void done() {
        try {
          showAddMsg(get(), true);
        } catch (InterruptedException | ExecutionException ex) {
          showAddMsg("Error checking stock: " + ex.getMessage(), false);
        }
      }
    }.execute();
  }

  /**
   * Fetches stock quantity for one location from a stock report response.
   */
  private int fetchLocationQty(String location, String code, String date) {
    try {
      Response response = connection.sendRequest(Request.getStockReport(location, date));
      if (!response.isSuccess() || !(response.getPayload() instanceof ReportDto dto)) {
        return 0;
      }
      for (java.util.Map<String, Object> row : dto.getData()) {
        Object codeValue = row.get("Item Code") != null ? row.get("Item Code") : row.get("Code");
        if (codeValue != null && code.equalsIgnoreCase(String.valueOf(codeValue).trim())) {
          Object qtyValue = row.get("Quantity") != null ? row.get("Quantity") : row.get("Qty");
          if (qtyValue instanceof Number n) {
            return n.intValue();
          }
          if (qtyValue != null) {
            try {
              return Integer.parseInt(String.valueOf(qtyValue).trim());
            } catch (NumberFormatException ignored) {
              return 0;
            }
          }
          return 0;
        }
      }
    } catch (Exception ignored) {
      return 0;
    }
    return 0;
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private void clearAddForm() {
    addCodeField.setText("");
    addQtyField.setText("");
    purchaseSpin.setValue(new Date());
    expirySpin.setValue(new Date());
    storeRadio.setSelected(true);
  }

  private String toIsoDate(Date date) {
    return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString();
  }

  private void configureSpinnerDateEditor(JSpinner spinner) {
    JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "yyyy-MM-dd");
    spinner.setEditor(editor);
    UiTheme.styleSpinner(spinner);
  }

  private void addRow(JPanel panel, GridBagConstraints gc, int row, String labelText, java.awt.Component field) {
    gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1; gc.weightx = 0;
    panel.add(UiTheme.label(labelText), gc);
    gc.gridx = 1; gc.weightx = 1;
    panel.add(field, gc);
  }

  private void showAddMsg(String msg, boolean ok) {
    addMsgLabel.setForeground(ok ? OK_COLOR : ERR_COLOR);
    addMsgLabel.setText(msg);
    if (!ok && msg != null) {
      if (msg.toLowerCase().contains("item")) {
        addCodeField.setToolTipText(msg);
      } else if (msg.toLowerCase().contains("quantity")) {
        addQtyField.setToolTipText(msg);
      }
    }
  }

  private void showShelfMsg(String msg, boolean ok) {
    shelfMsgLabel.setForeground(ok ? OK_COLOR : ERR_COLOR);
    shelfMsgLabel.setText(msg);
    if (!ok && msg != null) {
      if (msg.toLowerCase().contains("item")) {
        shelfCodeField.setToolTipText(msg);
      } else if (msg.toLowerCase().contains("quantity")) {
        shelfQtyField.setToolTipText(msg);
      }
    }
  }
}
