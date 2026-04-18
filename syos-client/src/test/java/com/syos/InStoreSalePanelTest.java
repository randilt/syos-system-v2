package com.syos.ui.panels;

import com.syos.network.ServerConnection;
import com.syos.ui.panels.InStoreSalePanel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the business-logic (non-Swing) methods of {@link InStoreSalePanel}.
 */
@ExtendWith(MockitoExtension.class)
class InStoreSalePanelTest {

  @Mock
  private ServerConnection connection;

  private InStoreSalePanel panel;

  @BeforeEach
  void setUp() {
    // Suppress Swing — we only test non-UI methods
    panel = new InStoreSalePanel(connection);
  }

  // ── validateItemInput ─────────────────────────────────────────────────────

  @Test
  void validateItemInput_valid_returnsNull() {
    assertNull(panel.validateItemInput("APPLE001", "3"));
  }

  @Test
  void validateItemInput_emptyCode_returnsError() {
    assertNotNull(panel.validateItemInput("", "3"));
  }

  @Test
  void validateItemInput_blankCode_returnsError() {
    assertNotNull(panel.validateItemInput("   ", "3"));
  }

  @Test
  void validateItemInput_emptyQty_returnsError() {
    assertNotNull(panel.validateItemInput("APPLE001", ""));
  }

  @Test
  void validateItemInput_negativeQty_returnsError() {
    assertNotNull(panel.validateItemInput("APPLE001", "-1"));
  }

  @Test
  void validateItemInput_zeroQty_returnsError() {
    assertNotNull(panel.validateItemInput("APPLE001", "0"));
  }

  @Test
  void validateItemInput_nonNumericQty_returnsError() {
    assertNotNull(panel.validateItemInput("APPLE001", "abc"));
  }

  // ── validateCash ─────────────────────────────────────────────────────────

  @Test
  void validateCash_valid_returnsNull() {
    assertNull(panel.validateCash("500.00"));
  }

  @Test
  void validateCash_empty_returnsError() {
    assertNotNull(panel.validateCash(""));
  }

  @Test
  void validateCash_zero_returnsError() {
    assertNotNull(panel.validateCash("0"));
  }

  @Test
  void validateCash_negative_returnsError() {
    assertNotNull(panel.validateCash("-100"));
  }

  @Test
  void validateCash_nonNumeric_returnsError() {
    assertNotNull(panel.validateCash("lots"));
  }

  // ── calculateTotal ────────────────────────────────────────────────────────

  @Test
  void calculateTotal_emptyRows_returnsZero() {
    assertEquals(0.0, panel.calculateTotal(new ArrayList<>()), 1e-9);
  }

  @Test
  void calculateTotal_singleRow() {
    List<Object[]> rows = List.of(new Object[]{"CODE", "Name", 2, 10.00, 20.00});
    assertEquals(20.00, panel.calculateTotal(rows), 1e-9);
  }

  @Test
  void calculateTotal_multipleRows() {
    List<Object[]> rows = List.of(
        new Object[]{"A", "Item A", 2, 10.00, 20.00},
        new Object[]{"B", "Item B", 3, 5.00,  15.00}
    );
    assertEquals(35.00, panel.calculateTotal(rows), 1e-9);
  }
}
