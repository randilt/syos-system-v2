package com.syos.domain.repository;

import com.syos.domain.model.Item;
import com.syos.domain.model.ItemCode;
import java.util.List;
import java.util.Optional;

/**
 * Repository port for {Item} catalogue access.
 */
public interface ItemRepository {
  void save(Item item);

  Optional<Item> findByCode(ItemCode code);

  List<Item> findAll();

  boolean exists(ItemCode code);
}
