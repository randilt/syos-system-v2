package com.syos.protocol;

import java.io.Serializable;

/**
 * Protocol-layer representation of a server-push notification.
 */
public final class PushNotificationDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String eventType;
  private final String summary;
  private final long timestamp;

  public PushNotificationDto(String eventType, String summary, long timestamp) {
    this.eventType = eventType;
    this.summary = summary;
    this.timestamp = timestamp;
  }

  public String getEventType() {
    return eventType;
  }

  public String getSummary() {
    return summary;
  }

  public long getTimestamp() {
    return timestamp;
  }

  @Override
  public String toString() {
    return "PushNotificationDto{eventType='" + eventType + "', summary='" + summary
        + "', timestamp=" + timestamp + "}";
  }
}
