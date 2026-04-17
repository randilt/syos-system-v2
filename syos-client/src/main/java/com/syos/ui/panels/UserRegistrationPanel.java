package com.syos.ui.panels;

import com.syos.network.ServerConnection;
import com.syos.protocol.CommandType;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import com.syos.ui.components.StyledButton;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Panel for registering new online customers.
 */
public class UserRegistrationPanel extends JPanel {

  private final ServerConnection connection;

  private final JTextField usernameField = new JTextField(20);
  private final JTextField emailField    = new JTextField(20);
  private final JLabel     resultLabel   = new JLabel(" ");

  public UserRegistrationPanel(ServerConnection connection) {
    this.connection = connection;

    setLayout(new GridLayout(0, 2, 10, 8));
    setBorder(BorderFactory.createTitledBorder("Register New Customer"));

    add(new JLabel("Username:"));
    add(usernameField);

    add(new JLabel("Email:"));
    add(emailField);

    add(new JLabel());
    StyledButton registerBtn = StyledButton.success("Register");
    registerBtn.addActionListener(e -> register());
    add(registerBtn);

    add(new JLabel("Result:"));
    add(resultLabel);
  }

  private void register() {
    String username = usernameField.getText().trim();
    String email    = emailField.getText().trim();

    if (username.isEmpty() || email.isEmpty()) {
      showError("Username and email are required."); return;
    }
    if (!email.contains("@")) {
      showError("Enter a valid email address."); return;
    }

    Map<String, Object> payload = new HashMap<>();
    payload.put("username", username);
    payload.put("email", email);

    try {
      Response response = connection.send(new Request(CommandType.REGISTER_USER, payload));
      if (response.isSuccess()) {
        Map<String, Object> data = response.getData();
        String userId = (String) data.get("userId");
        resultLabel.setText("Registered: " + username + " (ID: " + userId + ")");
        usernameField.setText("");
        emailField.setText("");
      } else {
        showError("Registration failed: " + response.getErrorMessage());
      }
    } catch (Exception ex) {
      showError("Connection error: " + ex.getMessage());
    }
  }

  private void showError(String msg) {
    JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
  }
}
