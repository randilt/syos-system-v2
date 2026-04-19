package com.syos.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.syos.application.usecase.report.Report;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerateReportsTest {

  @Test
  void constructor_nullReports_throws() {
    assertThrows(IllegalArgumentException.class, () -> new GenerateReports(null));
  }

  @Test
  void execute_callsGenerateOnEachReport() {
    Report r1 = mock(Report.class);
    Report r2 = mock(Report.class);
    GenerateReports useCase = new GenerateReports(List.of(r1, r2));

    useCase.execute();

    verify(r1).generate();
    verify(r2).generate();
    assertEquals(2, useCase.getReports().size());
  }
}
