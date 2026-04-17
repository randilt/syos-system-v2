package com.syos.application.usecase;

import com.syos.application.usecase.report.Report;
import java.util.List;

public class GenerateReports {
  private final List<Report> reports;

  public GenerateReports(List<Report> reports) {
    if (reports == null) throw new IllegalArgumentException("Reports cannot be null");
    this.reports = reports;
  }

  public void execute() {
    for (Report report : reports) {
      report.generate();
    }
  }

  public List<Report> getReports() {
    return reports;
  }
}
