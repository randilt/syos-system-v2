package com.syos.server;

import com.syos.protocol.PushNotificationDto;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Singleton registry of active push-channel client handlers. */
public final class PushRegistry {

  private static final Logger LOGGER = Logger.getLogger(PushRegistry.class.getName());

  private static volatile PushRegistry instance;

  private final List<PushClientHandler> handlers = Collections.synchronizedList(new ArrayList<>());

  private PushRegistry() {}

  public static PushRegistry getInstance() {
    if (instance == null) {
      synchronized (PushRegistry.class) {
        if (instance == null) {
          instance = new PushRegistry();
        }
      }
    }
    return instance;
  }

  public void register(PushClientHandler handler) {
    if (handler == null) {
      throw new IllegalArgumentException("Handler cannot be null");
    }
    handlers.add(handler);
  }

  public void unregister(PushClientHandler handler) {
    handlers.remove(handler);
  }

  public void broadcast(PushNotificationDto dto) {
    synchronized (handlers) {
      Iterator<PushClientHandler> iterator = handlers.iterator();
      while (iterator.hasNext()) {
        PushClientHandler handler = iterator.next();
        try {
          handler.push(dto);
        } catch (IOException e) {
          LOGGER.log(Level.WARNING, "Failed to push notification to client", e);
          iterator.remove();
          handler.close();
        }
      }
    }
  }
}
