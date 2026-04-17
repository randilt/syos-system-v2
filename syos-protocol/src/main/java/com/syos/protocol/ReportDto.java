package com.syos.protocol;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Protocol-layer representation of a generated report.
 *
 * <p>Each row in {@link #getData()} is a {@code Map<String, Object>} where keys are column
 * headers and values are display-ready strings or numbers. The shape of each row map depends
 * on the report type and is documented alongside the corresponding {@link CommandType}.
 */
public final class ReportDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String                    title;
  private final List<Map<String, Object>> data;
  private final int                       totalRecords;

  public ReportDto(String title, List<Map<String, Object>> data) {
    this.title        = title;
    this.data         = data == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(data));
    this.totalRecords = this.data.size();
  }

  /** Human-readable report title (e.g. {@code "Daily Sales Report – 2024-01-15"}). */
  public String getTitle() {
    return title;
  }

  /**
   * Immutable list of data rows.
   *
   * <p>Each entry is a {@code Map<String, Object>} where keys are column headers and
   * values are display-ready objects (typically strings or numbers).
   */
  public List<Map<String, Object>> getData() {
    return data;
  }

  /** Total number of rows in the report (equivalent to {@code getData().size()}). */
  public int getTotalRecords() {
    return totalRecords;
  }

  @Override
  public String toString() {
    return "ReportDto{title='" + title + "', totalRecords=" + totalRecords + "}";
  }
}
