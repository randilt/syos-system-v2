package com.syos.infrastructure.event;

import com.syos.domain.event.SaleEvent;
import com.syos.domain.event.SaleEventListener;
import com.syos.protocol.PushNotificationDto;
import com.syos.server.PushRegistry;

/** Observer that broadcasts a push notification when a sale is completed. */
public class SaleEventPushListener implements SaleEventListener {
  private final PushRegistry pushRegistry;

  public SaleEventPushListener(PushRegistry pushRegistry) {
    if (pushRegistry == null) {
      throw new IllegalArgumentException("Push registry cannot be null");
    }
    this.pushRegistry = pushRegistry;
  }

  @Override
  /** OnSale operation. */
  public void onSale(SaleEvent event) {
    String summary = "Bill #" + event.getBill().getSerialNumber() + " processed - stock levels updated";
    PushNotificationDto dto =
        new PushNotificationDto("SALE_COMPLETED", summary, System.currentTimeMillis());
    pushRegistry.broadcast(dto);
  }
}
