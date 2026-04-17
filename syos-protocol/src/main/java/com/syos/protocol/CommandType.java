package com.syos.protocol;

/**
 * All commands the client may issue to the server. Transmitted as part of every {@link Request}.
 */
public enum CommandType {
  /** Process a cash sale at the physical POS terminal. */
  PROCESS_IN_STORE_SALE,

  /** Process a sale placed through the online store. */
  PROCESS_ONLINE_SALE,

  /** Receive new stock into the STORE location. */
  ADD_STORE_STOCK,

  /** Receive new stock directly into the ONLINE location. */
  ADD_ONLINE_STOCK,

  /** Move items from STORE stock to SHELF stock. */
  MOVE_TO_SHELF,

  /** Register a new online customer. */
  REGISTER_USER,

  /** Generate and retrieve a named report for a given date. */
  GENERATE_REPORT,

  /** Retrieve the full item catalogue. */
  GET_ITEMS,

  /** Health-check; server responds with pong. */
  PING
}
