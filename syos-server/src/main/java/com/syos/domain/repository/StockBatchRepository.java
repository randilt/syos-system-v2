package com.syos.domain.repository;

import com.syos.domain.model.ItemCode;
import com.syos.domain.model.StockBatch;
import java.util.List;

public interface StockBatchRepository {
  void save(StockBatch batch);

  List<StockBatch> findByItemCode(ItemCode itemCode);

  List<StockBatch> findAll();

  int getTotalStock(ItemCode itemCode);

  String nextBatchId();
}
