package com.syos.network;

import com.syos.protocol.PushNotificationDto;
import com.syos.protocol.Request;
import com.syos.protocol.Response;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/**
 * Dedicated client-side listener for server-push notifications over a secondary TCP connection.
 */
public class PushListenerService {

  private static final Logger LOGGER = Logger.getLogger(PushListenerService.class.getName());

  private static final int MAX_RECONNECT_ATTEMPTS = 5;
  private static final long RECONNECT_DELAY_MS = 3_000L;

  private final String host;
  private final int pushPort;
  private final Runnable onNotification;

  private volatile boolean running = false;
  private volatile boolean connected = false;

  private Socket socket;
  private ObjectInputStream in;
  private Thread listenerThread;

  public PushListenerService(String host, int pushPort, Runnable onNotification) {
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("Host cannot be null or blank");
    }
    if (pushPort < 1 || pushPort > 65535) {
      throw new IllegalArgumentException("Push port must be between 1 and 65535");
    }
    if (onNotification == null) {
      throw new IllegalArgumentException("Notification callback cannot be null");
    }
    this.host = host;
    this.pushPort = pushPort;
    this.onNotification = onNotification;
  }

  public synchronized boolean connect() {
    try {
      closeSocketSilently();

      Socket s = new Socket();
      s.connect(new InetSocketAddress(host, pushPort), 2_000);
      ObjectOutputStream o = new ObjectOutputStream(s.getOutputStream());
      o.flush();
      ObjectInputStream i = new ObjectInputStream(s.getInputStream());

      o.writeObject(Request.subscribePush());
      o.flush();
      o.reset();

      Response ready = (Response) i.readObject();
      if (!ready.isSuccess() || !(ready.getPayload() instanceof String)
          || !"PUSH_READY".equals(ready.getPayload())) {
        LOGGER.warning("Push channel did not return PUSH_READY");
        s.close();
        connected = false;
        return false;
      }

      socket = s;
      in = i;
      connected = true;
      LOGGER.info("Push channel connected to " + host + ":" + pushPort);
      return true;
    } catch (IOException | ClassNotFoundException e) {
      connected = false;
      LOGGER.log(Level.WARNING, "Failed to connect push channel", e);
      return false;
    }
  }

  public synchronized void start() {
    if (listenerThread != null && listenerThread.isAlive()) {
      return;
    }

    running = true;
    listenerThread = new Thread(this::listenLoop, "syos-push-listener");
    listenerThread.setDaemon(true);
    listenerThread.start();
  }

  public synchronized void stop() {
    running = false;
    connected = false;
    closeSocketSilently();
  }

  public boolean isConnected() {
    return connected;
  }

  private void listenLoop() {
    while (running) {
      if (!connected) {
        if (!reconnect()) {
          running = false;
          break;
        }
      }

      try {
        Response response = (Response) in.readObject();
        Object payload = response.getPayload();
        if (payload instanceof PushNotificationDto) {
          SwingUtilities.invokeLater(onNotification);
        }
      } catch (IOException e) {
        if (running) {
          connected = false;
          LOGGER.log(Level.WARNING, "Push channel disconnected", e);
        }
      } catch (ClassNotFoundException e) {
        LOGGER.log(Level.WARNING, "Unknown push payload type", e);
      }
    }
  }

  private boolean reconnect() {
    for (int attempt = 1; attempt <= MAX_RECONNECT_ATTEMPTS && running; attempt++) {
      LOGGER.info("Attempting push reconnect " + attempt + "/" + MAX_RECONNECT_ATTEMPTS);
      if (connect()) {
        return true;
      }
      if (attempt < MAX_RECONNECT_ATTEMPTS) {
        try {
          Thread.sleep(RECONNECT_DELAY_MS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
    }
    LOGGER.warning("Push reconnect attempts exhausted");
    return false;
  }

  private synchronized void closeSocketSilently() {
    try {
      if (socket != null && !socket.isClosed()) {
        socket.close();
      }
    } catch (IOException ignored) {
      // best-effort close
    } finally {
      socket = null;
      in = null;
    }
  }
}
