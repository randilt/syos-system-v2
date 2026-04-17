package com.syos.protocol;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable, serializable command sent from a client to the server.
 *
 * <p>The {@code payload} map carries all parameters required for the command. Keys and value types
 * are command-specific (see {@link CommandType} javadoc for per-command contracts).
 */
public final class Request implements Serializable {

  private static final long serialVersionUID = 1L;

  private final CommandType commandType;
  private final Map<String, Object> payload;

  public Request(CommandType commandType, Map<String, Object> payload) {
    if (commandType == null) {
      throw new IllegalArgumentException("CommandType cannot be null");
    }
    this.commandType = commandType;
    this.payload = payload == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(payload));
  }

  public CommandType getCommandType() {
    return commandType;
  }

  /** Returns a defensive copy of the full payload map. */
  public Map<String, Object> getPayload() {
    return new HashMap<>(payload);
  }

  /**
   * Convenience accessor for a single payload entry.
   *
   * @param key payload key
   * @return the value, or {@code null} if absent
   */
  public Object get(String key) {
    return payload.get(key);
  }

  @Override
  public String toString() {
    return "Request{commandType=" + commandType + ", payload=" + payload + "}";
  }
}
