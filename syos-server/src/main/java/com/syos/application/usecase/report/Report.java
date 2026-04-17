package com.syos.application.usecase.report;

import java.util.List;
import java.util.Map;

public interface Report {
  void generate();

  String getTitle();

  List<Map<String, Object>> getData();
}
