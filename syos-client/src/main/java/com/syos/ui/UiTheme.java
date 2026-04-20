package com.syos.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Consistent light-theme styling for form panels on white backgrounds.
 *
 * <p>The system look-and-feel on Linux (GTK) may use dark defaults for labels, text fields,
 * and tables. Explicit colours here keep the main content area readable regardless of OS theme.
 */
public final class UiTheme {

  public static final Color PANEL_BG          = Color.WHITE;
  public static final Color TEXT_PRIMARY      = new Color(0x1a2744);
  public static final Color TEXT_SECONDARY    = new Color(0x333333);
  public static final Color FIELD_BG          = Color.WHITE;
  public static final Color FIELD_BORDER      = new Color(0xBDC3C7);
  public static final Color TABLE_BG            = Color.WHITE;
  public static final Color TABLE_ALT_ROW     = new Color(0xF5F7FA);
  public static final Color TABLE_SELECTION_BG = new Color(0xD6EAF8);
  public static final Color TABLE_HEADER_BG   = new Color(0x1976D2);
  public static final Color TABLE_GRID        = new Color(0xE0E0E0);

  public static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 13);
  public static final Font FIELD_FONT = new Font("Segoe UI", Font.PLAIN, 13);

  private UiTheme() {}

  /**
   * Call once at startup before any Swing components are created. Reduces GTK/dark-theme
   * overrides on text fields inside white content panels.
   */
  public static void installLightFormDefaults() {
    Color text = TEXT_PRIMARY;
    Color bg = FIELD_BG;
    Color selBg = TABLE_SELECTION_BG;
    String[] keys = {
        "TextField.background", "TextField.foreground", "TextField.caretForeground",
        "TextField.selectionBackground", "TextField.selectionForeground",
        "TextField.inactiveForeground", "TextField.disabledForeground",
        "FormattedTextField.background", "FormattedTextField.foreground",
        "FormattedTextField.caretForeground", "Spinner.background", "Spinner.foreground",
        "TextArea.background", "TextArea.foreground", "TextArea.caretForeground",
        "Table.background", "Table.foreground", "Table.selectionBackground",
        "Table.selectionForeground"
    };
    for (String key : keys) {
      if (key.contains("background") || key.contains("Background")) {
        UIManager.put(key, bg);
      } else if (key.contains("selectionBackground")) {
        UIManager.put(key, selBg);
      } else if (key.contains("selectionForeground") || key.contains("caret")) {
        UIManager.put(key, text);
      } else {
        UIManager.put(key, text);
      }
    }
  }

  public static void stylePanel(JPanel panel) {
    panel.setBackground(PANEL_BG);
    panel.setOpaque(true);
  }

  public static JLabel label(String text) {
    JLabel l = new JLabel(text);
    styleLabel(l);
    return l;
  }

  public static void styleLabel(JLabel label) {
    label.setFont(LABEL_FONT);
    label.setForeground(TEXT_PRIMARY);
    label.setOpaque(false);
  }

  public static void styleTextField(JTextField field) {
    field.setUI(new BasicTextFieldUI());
    field.setFont(FIELD_FONT);
    field.setBackground(FIELD_BG);
    field.setForeground(TEXT_PRIMARY);
    field.setDisabledTextColor(TEXT_SECONDARY);
    field.setSelectedTextColor(TEXT_PRIMARY);
    field.setSelectionColor(TABLE_SELECTION_BG);
    field.setCaretColor(TEXT_PRIMARY);
    field.setOpaque(true);
    field.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(FIELD_BORDER),
        BorderFactory.createEmptyBorder(4, 6, 4, 6)));
  }

  public static void styleTextFields(JTextField... fields) {
    for (JTextField f : fields) {
      styleTextField(f);
    }
  }

  public static void styleTextArea(JTextArea area) {
    area.setUI(new BasicTextAreaUI());
    area.setFont(new Font("Monospaced", Font.PLAIN, 12));
    area.setBackground(new Color(0xFAFAFA));
    area.setForeground(TEXT_PRIMARY);
    area.setDisabledTextColor(TEXT_SECONDARY);
    area.setSelectedTextColor(TEXT_PRIMARY);
    area.setSelectionColor(TABLE_SELECTION_BG);
    area.setCaretColor(TEXT_PRIMARY);
    area.setOpaque(true);
  }

  public static void styleRadioButton(JRadioButton radio) {
    radio.setFont(LABEL_FONT);
    radio.setBackground(PANEL_BG);
    radio.setForeground(TEXT_PRIMARY);
    radio.setOpaque(true);
  }

  public static void styleSpinner(JSpinner spinner) {
    spinner.setFont(FIELD_FONT);
    spinner.setBackground(FIELD_BG);
    spinner.setForeground(TEXT_PRIMARY);
    spinner.setOpaque(true);
    Component editor = spinner.getEditor();
    if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
      JTextField tf = defaultEditor.getTextField();
      styleTextField(tf);
      defaultEditor.setBackground(FIELD_BG);
    }
  }

  public static void styleTabbedPane(JTabbedPane tabs) {
    tabs.setFont(LABEL_FONT);
    tabs.setBackground(PANEL_BG);
    tabs.setForeground(TEXT_PRIMARY);
  }

  public static Border titledBorder(String title) {
    TitledBorder border = BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(FIELD_BORDER),
        title);
    border.setTitleColor(TEXT_PRIMARY);
    border.setTitleFont(new Font("Segoe UI", Font.BOLD, 12));
    return border;
  }

  public static void styleTable(JTable table) {
    table.setFont(FIELD_FONT);
    table.setBackground(TABLE_BG);
    table.setForeground(TEXT_PRIMARY);
    table.setGridColor(TABLE_GRID);
    table.setSelectionBackground(TABLE_SELECTION_BG);
    table.setSelectionForeground(TEXT_PRIMARY);
    table.setRowHeight(24);

    DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(
          JTable t, Object value, boolean selected, boolean focused, int row, int column) {
        Component c = super.getTableCellRendererComponent(t, value, selected, focused, row, column);
        if (!selected) {
          c.setBackground(row % 2 == 0 ? TABLE_BG : TABLE_ALT_ROW);
          c.setForeground(TEXT_PRIMARY);
        }
        return c;
      }
    };
    table.setDefaultRenderer(Object.class, renderer);

    if (table.getTableHeader() != null) {
      table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
      table.getTableHeader().setBackground(TABLE_HEADER_BG);
      table.getTableHeader().setForeground(Color.WHITE);
      table.getTableHeader().setReorderingAllowed(false);
    }
  }

  public static void styleScrollPane(JScrollPane scroll) {
    scroll.getViewport().setBackground(TABLE_BG);
    scroll.setBackground(PANEL_BG);
    if (scroll.getViewport().getView() instanceof JComponent view) {
      view.setBackground(TABLE_BG);
    }
  }

  /** Applies label + text field styling to a form component tree. */
  public static void styleFormRoot(JComponent root) {
    if (root instanceof JPanel panel) {
      stylePanel(panel);
    }
    for (Component child : root.getComponents()) {
      if (child instanceof JLabel lbl) {
        if (lbl.getForeground() == null
            || lbl.getForeground().equals(javax.swing.UIManager.getColor("Label.foreground"))) {
          styleLabel(lbl);
        }
      } else if (child instanceof JTextField tf) {
        styleTextField(tf);
      } else if (child instanceof JRadioButton rb) {
        styleRadioButton(rb);
      } else if (child instanceof JSpinner sp) {
        styleSpinner(sp);
      } else if (child instanceof JPanel p) {
        styleFormRoot(p);
      }
    }
  }
}
