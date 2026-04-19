package com.syos.application.usecase.report;

import java.util.List;
import java.util.Map;

/**
 * Report port — domain/application abstraction for printable tabular reports.
 */
public interface Report {
  void generate();

  String getTitle();

  List<Map<String, Object>> getData();
}
