package com.syos.ui.components;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import javax.swing.JButton;

/**
 * A {@link JButton} with a consistent brand-styled look.
 * Use in place of plain {@code JButton} throughout the UI for uniform appearance.
 */
public class StyledButton extends JButton {

  private static final Color DEFAULT_BG = new Color(0x1976D2);  // Material blue
  private static final Color DEFAULT_FG = Color.WHITE;
  private static final Font  DEFAULT_FONT = new Font("SansSerif", Font.BOLD, 13);

  public StyledButton(String text) {
    super(text);
    applyStyle(DEFAULT_BG, DEFAULT_FG);
  }

  public StyledButton(String text, Color background) {
    super(text);
    applyStyle(background, Color.WHITE);
  }

  private void applyStyle(Color bg, Color fg) {
    setBackground(bg);
    setForeground(fg);
    setFont(DEFAULT_FONT);
    setFocusPainted(false);
    setBorderPainted(false);
    setOpaque(true);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  /** Returns a danger-styled (red) button, e.g. for clear/cancel actions. */
  public static StyledButton danger(String text) {
    return new StyledButton(text, new Color(0xD32F2F));
  }

  /** Returns a success-styled (green) button, e.g. for process/confirm actions. */
  public static StyledButton success(String text) {
    return new StyledButton(text, new Color(0x388E3C));
  }

  /** Returns a neutral (grey) button. */
  public static StyledButton neutral(String text) {
    return new StyledButton(text, new Color(0x757575));
  }
}
