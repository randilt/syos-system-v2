package com.syos.ui.components;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;

/**
 * A {@link JButton} with rounded corners, hover effects, and SYOS brand colours.
 *
 * <h2>Types</h2>
 * <ul>
 *   <li>{@link #success(String)} – green  (#2ecc71) — confirm / process actions</li>
 *   <li>{@link #danger(String)}  – red    (#e74c3c) — cancel / clear / error actions</li>
 *   <li>{@link #primary(String)} – navy   (#1a2744) — default navigation actions</li>
 *   <li>{@link #neutral(String)} – grey   (#7f8c8d) — secondary / utility actions</li>
 * </ul>
 */
public class StyledButton extends JButton {

  private static final Font BUTTON_FONT = new Font("Segoe UI", Font.BOLD, 13);
  private static final int  ARC         = 10;

  /** Colour variants. */
  public enum ButtonType {
    SUCCESS(new Color(0x2ecc71), new Color(0x27ae60)),
    DANGER (new Color(0xe74c3c), new Color(0xc0392b)),
    PRIMARY(new Color(0x1a2744), new Color(0x243659)),
    NEUTRAL(new Color(0x7f8c8d), new Color(0x636e72));

    final Color base;
    final Color hover;

    ButtonType(Color base, Color hover) {
      this.base  = base;
      this.hover = hover;
    }
  }

  private Color currentBg;

  public StyledButton(String text, ButtonType type) {
    super(text);
    this.currentBg = type.base;

    setForeground(Color.WHITE);
    setFont(BUTTON_FONT.getFamily().equals("Segoe UI")
        ? BUTTON_FONT : new Font("SansSerif", Font.BOLD, 13));
    setFocusPainted(false);
    setContentAreaFilled(false);
    setBorderPainted(false);
    setOpaque(false);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    setMargin(new Insets(8, 18, 8, 18));

    addMouseListener(new MouseAdapter() {
      @Override public void mouseEntered(MouseEvent e) {
        if (isEnabled()) { currentBg = type.hover; repaint(); }
      }
      @Override public void mouseExited(MouseEvent e)  {
        currentBg = type.base; repaint();
      }
    });
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setColor(isEnabled() ? currentBg : currentBg.darker());
    g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
    g2.dispose();
    super.paintComponent(g);
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension d = super.getPreferredSize();
    d.height = Math.max(d.height, 34);
    return d;
  }

  // ── Factories ────────────────────────────────────────────────────────────

  public static StyledButton success(String text) { return new StyledButton(text, ButtonType.SUCCESS); }
  public static StyledButton danger (String text) { return new StyledButton(text, ButtonType.DANGER);  }
  public static StyledButton primary(String text) { return new StyledButton(text, ButtonType.PRIMARY); }
  public static StyledButton neutral(String text) { return new StyledButton(text, ButtonType.NEUTRAL); }
}
