package com.syos.ui.panels;

import com.syos.network.ServerConnection;
import com.syos.ui.panels.OnlineSalePanel;
import com.syos.ui.panels.StockManagementPanel;
import com.syos.ui.panels.UserRegistrationPanel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for validation methods across multiple panel classes.
 */
@ExtendWith(MockitoExtension.class)
class PanelValidationTest {

  @Mock
  private ServerConnection connection;

  // ── OnlineSalePanel.validateItemInput / validateUserId ────────────────────

  private OnlineSalePanel onlinePanel;

  @BeforeEach
  void setUp() {
    onlinePanel = new OnlineSalePanel(connection);
  }

  @Test
  void online_validateItemInput_valid_returnsNull() {
    assertNull(onlinePanel.validateItemInput("ITEM001", "5"));
  }

  @Test
  void online_validateItemInput_blankCode_returnsError() {
    assertNotNull(onlinePanel.validateItemInput("", "5"));
  }

  @Test
  void online_validateItemInput_negativeQty_returnsError() {
    assertNotNull(onlinePanel.validateItemInput("ITEM001", "-2"));
  }

  @Test
  void online_validateUserId_blank_returnsError() {
    assertNotNull(onlinePanel.validateUserId(""));
  }

  @Test
  void online_validateUserId_valid_returnsNull() {
    assertNull(onlinePanel.validateUserId("user123"));
  }

  // ── StockManagementPanel.validateStockInput ───────────────────────────────

  @Test
  void stock_validateInput_valid_returnsNull() {
    StockManagementPanel panel = new StockManagementPanel(connection);
    assertNull(panel.validateStockInput("ITEM001", "10"));
  }

  @Test
  void stock_validateInput_emptyCode_returnsError() {
    StockManagementPanel panel = new StockManagementPanel(connection);
    assertNotNull(panel.validateStockInput("", "10"));
  }

  @Test
  void stock_validateInput_zeroQty_returnsError() {
    StockManagementPanel panel = new StockManagementPanel(connection);
    assertNotNull(panel.validateStockInput("ITEM001", "0"));
  }

  @Test
  void stock_validateInput_nonNumericQty_returnsError() {
    StockManagementPanel panel = new StockManagementPanel(connection);
    assertNotNull(panel.validateStockInput("ITEM001", "xyz"));
  }

  // ── UserRegistrationPanel.validateInput ───────────────────────────────────

  @Test
  void user_validateInput_valid_returnsNull() {
    UserRegistrationPanel panel = new UserRegistrationPanel(connection);
    assertNull(panel.validateInput("alice", "alice@example.com"));
  }

  @Test
  void user_validateInput_blankUsername_returnsError() {
    UserRegistrationPanel panel = new UserRegistrationPanel(connection);
    assertNotNull(panel.validateInput("", "alice@example.com"));
  }

  @Test
  void user_validateInput_blankEmail_returnsError() {
    UserRegistrationPanel panel = new UserRegistrationPanel(connection);
    assertNotNull(panel.validateInput("alice", ""));
  }

  @Test
  void user_validateInput_invalidEmail_noAt_returnsError() {
    UserRegistrationPanel panel = new UserRegistrationPanel(connection);
    assertNotNull(panel.validateInput("alice", "notanemail"));
  }

  @Test
  void user_validateInput_invalidEmail_noDot_returnsError() {
    UserRegistrationPanel panel = new UserRegistrationPanel(connection);
    assertNotNull(panel.validateInput("alice", "alice@nodot"));
  }
}
