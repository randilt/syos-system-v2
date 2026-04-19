package com.syos.server;

import com.syos.protocol.Request;
import com.syos.protocol.Response;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles a single client connection inside a dedicated thread.
 *
 * <p>Reads {@link Request} objects from the client, forwards each to the {@link RequestRouter},
 * and writes the resulting {@link Response} back. The loop continues until the client
 * disconnects or an I/O error occurs.
 */
public class ClientHandler implements Runnable {

  private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

  private final Socket socket;
  private final RequestRouter router;
  private final String clientId;

  public ClientHandler(Socket socket, RequestRouter router, String clientId) {
    if (socket == null) throw new IllegalArgumentException("Socket cannot be null");
    if (router == null) throw new IllegalArgumentException("Router cannot be null");
    if (clientId == null || clientId.isBlank())
      throw new IllegalArgumentException("clientId cannot be blank");
    this.socket = socket;
    this.router = router;
    this.clientId = clientId;
  }

  @Override
  public void run() {
    String clientAddress = socket.getRemoteSocketAddress().toString();
    LOGGER.info("[" + Instant.now() + "] [" + clientId + "] Client connected from " + clientAddress);

    try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

      while (!socket.isClosed()) {
        Request request;
        try {
          request = (Request) in.readObject();
        } catch (EOFException e) {
          // Client closed the connection gracefully
          break;
        } catch (ClassNotFoundException e) {
          LOGGER.log(Level.WARNING,
              "[" + Instant.now() + "] [" + clientId + "] Unknown class from " + clientAddress, e);
          sendErrorSilently(out, "Invalid request format");
          continue;
        } catch (ClassCastException e) {
          LOGGER.log(Level.WARNING,
              "[" + Instant.now() + "] [" + clientId + "] Invalid request type from " + clientAddress, e);
          sendErrorSilently(out, "Invalid request format");
          continue;
        }

        LOGGER.fine("[" + Instant.now() + "] [" + clientId + "] Received "
            + request.getCommandType() + " from " + clientAddress);
        Response response = router.route(request);
        out.writeObject(response);
        out.flush();
        out.reset(); // clear serialization cache between messages
      }

    } catch (IOException e) {
      if (!socket.isClosed()) {
        LOGGER.log(Level.WARNING,
            "[" + Instant.now() + "] [" + clientId + "] I/O error for " + clientAddress, e);
      }
    } finally {
      closeSocket(clientAddress);
    }
  }

  private void sendErrorSilently(ObjectOutputStream out, String message) {
    try {
      out.writeObject(Response.error(message));
      out.flush();
      out.reset();
    } catch (IOException ioex) {
      LOGGER.log(Level.WARNING, "[" + clientId + "] Failed to send error response", ioex);
    }
  }

  private void closeSocket(String clientAddress) {
    try {
      socket.close();
    } catch (IOException e) {
      LOGGER.log(Level.WARNING,
          "[" + Instant.now() + "] [" + clientId + "] Error closing socket for " + clientAddress, e);
    }
    LOGGER.info("[" + Instant.now() + "] [" + clientId + "] Client disconnected: " + clientAddress);
  }
}
