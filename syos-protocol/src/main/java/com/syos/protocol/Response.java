package com.syos.protocol;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable, serializable response sent from the server back to a client.
 *
 * <p>Use the static factory methods {@link #ok()}, {@link #ok(Map)}, and {@link #error(String)}
 * to construct instances.
 */
public final class Response implements Serializable {

  private static final long serialVersionUID = 1L;

  private final boolean success;
  private final Map<String, Object> data;
  private final String errorMessage;

  private Response(boolean success, Map<String, Object> data, String errorMessage) {
    this.success = success;
    this.data = data == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(data));
    this.errorMessage = errorMessage;
  }

  /** Successful response with no data payload. */
  public static Response ok() {
    return new Response(true, Collections.emptyMap(), null);
  }

  /** Successful response carrying result data. */
  public static Response ok(Map<String, Object> data) {
    return new Response(true, data, null);
  }

  /** Failed response with a human-readable error description. */
  public static Response error(String message) {
    return new Response(false, Collections.emptyMap(), message);
  }

  public boolean isSuccess() {
    return success;
  }

  /** Returns a defensive copy of the response data map. */
  public Map<String, Object> getData() {
    return new HashMap<>(data);
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public String toString() {
    return success
        ? "Response{success=true, data=" + data + "}"
        : "Response{success=false, error='" + errorMessage + "'}";
  }
}
