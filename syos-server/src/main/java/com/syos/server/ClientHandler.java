package com.syos.server;

import com.syos.protocol.Request;
import com.syos.protocol.Response;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
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

  public ClientHandler(Socket socket, RequestRouter router) {
    if (socket == null) throw new IllegalArgumentException("Socket cannot be null");
    if (router == null) throw new IllegalArgumentException("Router cannot be null");
    this.socket = socket;
    this.router = router;
  }

  @Override
  public void run() {
    String clientAddress = socket.getRemoteSocketAddress().toString();
    LOGGER.info("Client connected: " + clientAddress);

    try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

      while (!socket.isClosed()) {
        Request request;
        try {
          request = (Request) in.readObject();
        } catch (EOFException e) {
          // Client closed the connection gracefully
          break;
        }

        LOGGER.fine("Received " + request.getCommandType() + " from " + clientAddress);
        Response response = router.route(request);
        out.writeObject(response);
        out.flush();
        out.reset(); // clear serialization cache between messages
      }

    } catch (ClassNotFoundException e) {
      LOGGER.log(Level.WARNING, "Unknown class in request from " + clientAddress, e);
    } catch (IOException e) {
      if (!socket.isClosed()) {
        LOGGER.log(Level.WARNING, "I/O error for client " + clientAddress, e);
      }
    } finally {
      closeSocket(clientAddress);
    }
  }

  private void closeSocket(String clientAddress) {
    try {
      socket.close();
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Error closing socket for " + clientAddress, e);
    }
    LOGGER.info("Client disconnected: " + clientAddress);
  }
}
