package com.syos.application.usecase;

import com.syos.application.usecase.report.Report;
import java.util.List;

/**
 * Use case: runs a list of {Report} instances and prints each via {Report#generate}.
 */
public class GenerateReports {
  private final List<Report> reports;

  public GenerateReports(List<Report> reports) {
    if (reports == null) throw new IllegalArgumentException("Reports cannot be null");
    this.reports = reports;
  }

  /** Executes the use case with the given inputs. */

  public void execute() {
    for (Report report : reports) {
      report.generate();
    }
  }

  /** Returns the configured report list. */

  public List<Report> getReports() {
    return reports;
  }
}
