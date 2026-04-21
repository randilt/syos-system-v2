package com.syos.server;

import com.syos.protocol.PushNotificationDto;
import com.syos.protocol.Response;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Handles one dedicated server-push socket for a connected client. */
public class PushClientHandler implements Runnable {

  private static final Logger LOGGER = Logger.getLogger(PushClientHandler.class.getName());

  private final Socket socket;
  private final PushRegistry registry;

  private final ObjectOutputStream out;
  private final ObjectInputStream in;

  public PushClientHandler(Socket socket, PushRegistry registry) throws IOException {
    if (socket == null) throw new IllegalArgumentException("Socket cannot be null");
    if (registry == null) throw new IllegalArgumentException("Registry cannot be null");
    this.socket = socket;
    this.registry = registry;

    out = new ObjectOutputStream(socket.getOutputStream());
    out.flush();
    in = new ObjectInputStream(socket.getInputStream());

    synchronized (out) {
      out.writeObject(Response.success("PUSH_READY"));
      out.flush();
      out.reset();
    }
  }

  public void start() {
    Thread t = new Thread(this, "push-client-" + socket.getPort());
    t.setDaemon(true);
    t.start();
  }

  public void push(PushNotificationDto dto) throws IOException {
    synchronized (out) {
      out.writeObject(Response.success(dto));
      out.flush();
      out.reset();
    }
  }

  @Override
  public void run() {
    String clientAddress = socket.getRemoteSocketAddress().toString();
    LOGGER.info("[" + Instant.now() + "] Push client connected from " + clientAddress);

    try {
      while (!socket.isClosed()) {
        try {
          in.readObject();
        } catch (EOFException e) {
          break;
        } catch (ClassNotFoundException e) {
          LOGGER.log(Level.WARNING,
              "[" + Instant.now() + "] Unknown push payload from " + clientAddress, e);
        }
      }
    } catch (IOException e) {
      if (!socket.isClosed()) {
        LOGGER.log(Level.WARNING,
            "[" + Instant.now() + "] Push I/O error for " + clientAddress, e);
      }
    } finally {
      registry.unregister(this);
      close();
      LOGGER.info("[" + Instant.now() + "] Push client disconnected: " + clientAddress);
    }
  }

  public void close() {
    try {
      socket.close();
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Error closing push socket", e);
    }
  }
}
