package com.syos.domain.event;

/**
 * Observer Pattern: listeners react to published sale events without coupling use cases to
 * handlers.
 */
public interface SaleEventListener {
  void onSale(SaleEvent event);
}
