package com.syos.application.usecase.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Template Method Pattern: defines the report generation skeleton while delegating data creation to
 * subclasses.
 */
public abstract class AbstractReport implements Report {
  protected List<Map<String, Object>> data = new ArrayList<>();

  @Override
  public void generate() {
    data.clear();
    collectData();
  }

  protected abstract void collectData();

  protected void addRow(Map<String, Object> row) {
    data.add(new HashMap<>(row));
  }

  @Override
  public List<Map<String, Object>> getData() {
    return new ArrayList<>(data);
  }
}
