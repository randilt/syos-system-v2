package com.syos.ui.panels;

import com.syos.network.ServerConnection;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import com.syos.ui.UiTheme;
import com.syos.ui.components.StyledButton;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

/**
 * Panel for registering new online customers.
 *
 * <p>The server generates the user ID. On success, the assigned ID is shown in green;
 * errors are shown in red. The EDT is never blocked — the server call runs on a
 * {@link SwingWorker}.
 */
public class UserRegistrationPanel extends JPanel {

  private static final Color BG        = UiTheme.PANEL_BG;
  private static final Color ERR_COLOR = new Color(0xe74c3c);
  private static final Color OK_COLOR  = new Color(0x27ae60);
  private static final Font  LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 13);
  private static final Font  MSG_FONT   = new Font("Segoe UI", Font.BOLD, 13);

  private final ServerConnection connection;

  private final JTextField usernameField = new JTextField(22);
  private final JTextField emailField    = new JTextField(22);
  private final JLabel     msgLabel      = new JLabel(" ");
  private StyledButton     registerBtn;

  public UserRegistrationPanel(ServerConnection connection) {
    this.connection = connection;
    UiTheme.styleTextFields(usernameField, emailField);
    UiTheme.styleLabel(msgLabel);

    setLayout(new BorderLayout());
    setBackground(BG);
    setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

    JPanel form = new JPanel(new GridBagLayout());
    form.setBackground(BG);
    GridBagConstraints gc = new GridBagConstraints();
    gc.insets  = new Insets(8, 8, 8, 8);
    gc.anchor  = GridBagConstraints.WEST;
    gc.fill    = GridBagConstraints.HORIZONTAL;

    // Title
    JLabel title = new JLabel("Register New Customer");
    title.setFont(new Font("Segoe UI", Font.BOLD, 18));
    title.setForeground(UiTheme.TEXT_PRIMARY);
    gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2;
    form.add(title, gc);

    gc.gridwidth = 1;
    addRow(form, gc, 1, "Username:", usernameField);
    addRow(form, gc, 2, "Email:",    emailField);

    // Button row
    registerBtn = StyledButton.success("Register Customer");
    registerBtn.addActionListener(e -> register());
    gc.gridx = 1; gc.gridy = 3; gc.fill = GridBagConstraints.NONE;
    form.add(registerBtn, gc);

    // Message label
    msgLabel.setFont(MSG_FONT);
    gc.gridx = 0; gc.gridy = 4; gc.gridwidth = 2;
    gc.fill = GridBagConstraints.HORIZONTAL;
    form.add(msgLabel, gc);

    add(form, BorderLayout.CENTER);
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  private void register() {
    String username = usernameField.getText().trim();
    String email    = emailField.getText().trim();

    String err = validateInput(username, email);
    if (err != null) { showMsg(err, false); return; }

    registerBtn.setEnabled(false);
    registerBtn.setText("Registering...");
    showMsg(" ", true);

    new SwingWorker<Response, Void>() {
      @Override protected Response doInBackground() throws Exception {
        return connection.sendRequest(Request.registerUser(username, email));
      }
      @Override protected void done() {
        registerBtn.setEnabled(true);
        registerBtn.setText("Register Customer");
        try {
          Response r = get();
          if (r.isSuccess()) {
            String userId = String.valueOf(r.getPayload());
            showMsg("Registered! User ID: " + userId, true);
            usernameField.setText("");
            emailField.setText("");
          } else {
            showMsg(r.getErrorMessage(), false);
          }
        } catch (InterruptedException | ExecutionException ex) {
          showMsg("Server error: " + ex.getMessage(), false);
        }
      }
    }.execute();
  }

  // ── Validation (package-private for tests) ────────────────────────────────

  String validateInput(String username, String email) {
    if (username == null || username.isBlank()) return "Username cannot be empty.";
    if (email == null || email.isBlank())       return "Email cannot be empty.";
    if (!email.contains("@") || !email.contains(".")) return "Enter a valid email address.";
    return null;
  }

  // ── UI helpers ────────────────────────────────────────────────────────────

  private void addRow(JPanel panel, GridBagConstraints gc, int row, String text, JTextField field) {
    gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1; gc.weightx = 0; gc.fill = GridBagConstraints.NONE;
    panel.add(UiTheme.label(text), gc);
    gc.gridx = 1; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(field, gc);
  }

  private void showMsg(String msg, boolean ok) {
    msgLabel.setForeground(ok ? OK_COLOR : ERR_COLOR);
    msgLabel.setText(msg);
  }
}
