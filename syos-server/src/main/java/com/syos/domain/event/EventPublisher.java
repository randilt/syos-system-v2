package com.syos.domain.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observer subject that notifies {SaleEventListener} implementations after a sale.
 */
public class EventPublisher {
  private final List<SaleEventListener> listeners = new CopyOnWriteArrayList<>();

  /** Registers a listener to receive events. */

  public void register(SaleEventListener listener) {
    if (listener == null) {
      throw new IllegalArgumentException("Listener cannot be null");
    }
    listeners.add(listener);
  }

  /** Unregister operation. */

  public void unregister(SaleEventListener listener) {
    listeners.remove(listener);
  }

  /** Publishes an event to all registered listeners. */

  public void publish(SaleEvent event) {
    if (event == null) {
      throw new IllegalArgumentException("Event cannot be null");
    }
    for (SaleEventListener listener : listeners) {
      listener.onSale(event);
    }
  }
}
