package com.syos.ui.components;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * A styled, non-editable {@link JTable} bundled with a {@link JScrollPane}.
 *
 * <p>Callers should add {@link #getScrollPane()} to a container instead of the table directly.
 * Use {@link #getModel()} to update row data.
 */
public class StyledTable extends JTable {

  private final DefaultTableModel model;
  private final JScrollPane scrollPane;

  /**
   * Creates the table with the given column headers.
   *
   * @param columns column display names
   */
  public StyledTable(String... columns) {
    model = new DefaultTableModel(columns, 0) {
      @Override
      public boolean isCellEditable(int row, int col) {
        return false; // table is display-only
      }
    };
    setModel(model);
    applyStyle();
    scrollPane = new JScrollPane(this);
  }

  private void applyStyle() {
    setFont(new Font("SansSerif", Font.PLAIN, 13));
    setRowHeight(24);
    setGridColor(new Color(0xE0E0E0));
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

    getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
    getTableHeader().setBackground(new Color(0x1976D2));
    getTableHeader().setForeground(Color.WHITE);
    getTableHeader().setReorderingAllowed(false);

    setFillsViewportHeight(true);
  }

  /** Returns the scroll pane that wraps this table. Add this to panels, not the table itself. */
  public JScrollPane getScrollPane() {
    return scrollPane;
  }

  /** Exposes the underlying {@link DefaultTableModel} for row manipulation. */
  public DefaultTableModel getModel() {
    return model;
  }

  /** Clears all rows. */
  public void clearRows() {
    model.setRowCount(0);
  }

  /** Appends a single row. Values must match column order. */
  public void addRow(Object... values) {
    model.addRow(values);
  }
}
