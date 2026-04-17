package com.syos.protocol;

/**
 * All commands the client may issue to the server.
 * Transmitted as part of every {@link Request}.
 */
public enum CommandType {

  // ── Sales ────────────────────────────────────────────────────────────────

  /** Process a cash sale at the physical POS terminal. */
  PROCESS_IN_STORE_SALE,

  /** Process a sale placed through the online store. */
  PROCESS_ONLINE_SALE,

  // ── Users ────────────────────────────────────────────────────────────────

  /** Register a new online customer. */
  REGISTER_USER,

  // ── Stock management ─────────────────────────────────────────────────────

  /**
   * Receive new stock into a target location.
   * Target is specified as a parameter: {@code "STORE"} or {@code "ONLINE"}.
   */
  ADD_STOCK,

  /** Move items from STORE stock to SHELF stock. */
  MOVE_TO_SHELF,

  // ── Catalogue ────────────────────────────────────────────────────────────

  /** Retrieve the full item catalogue. */
  GET_ALL_ITEMS,

  // ── Reports ──────────────────────────────────────────────────────────────

  /** Daily sales report (all channels or filtered by {@code IN_STORE} / {@code ONLINE}). */
  GET_DAILY_SALES_REPORT,

  /** Reshelving recommendations based on in-store sales vs. store stock. */
  GET_RESHELVING_REPORT,

  /** Items whose store stock has fallen below the reorder threshold. */
  GET_REORDER_REPORT,

  /** Bill / receipt lookup. */
  GET_BILL_REPORT,

  /** Current STORE stock levels. */
  GET_STOCK_REPORT,

  /** Current SHELF stock levels. */
  GET_SHELF_STOCK_REPORT,

  /** Current ONLINE stock levels. */
  GET_ONLINE_STOCK_REPORT,

  // ── Utility ──────────────────────────────────────────────────────────────

  /** Health-check; server responds with pong. */
  PING
}
