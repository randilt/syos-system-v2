package com.syos.domain.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventPublisher {
  private final List<SaleEventListener> listeners = new CopyOnWriteArrayList<>();

  public void register(SaleEventListener listener) {
    if (listener == null) {
      throw new IllegalArgumentException("Listener cannot be null");
    }
    listeners.add(listener);
  }

  public void unregister(SaleEventListener listener) {
    listeners.remove(listener);
  }

  public void publish(SaleEvent event) {
    if (event == null) {
      throw new IllegalArgumentException("Event cannot be null");
    }
    for (SaleEventListener listener : listeners) {
      listener.onSale(event);
    }
  }
}
