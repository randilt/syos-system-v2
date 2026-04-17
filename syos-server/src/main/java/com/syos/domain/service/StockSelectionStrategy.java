package com.syos.domain.service;

import com.syos.domain.model.ItemCode;
import com.syos.domain.model.StockBatch;
import java.time.LocalDate;
import java.util.List;

public interface StockSelectionStrategy {
  StockBatch selectBatch(
      List<StockBatch> batches, ItemCode itemCode, int quantity, LocalDate currentDate);
}
