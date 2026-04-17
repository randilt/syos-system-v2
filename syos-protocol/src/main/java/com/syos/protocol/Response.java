package com.syos.protocol;

import java.io.Serializable;

/**
 * Immutable, serializable server response to a {@link Request}.
 *
 * <p>On success, {@link #getPayload()} contains the result object (a typed DTO, a list of DTOs,
 * or {@code null} for commands that return no data). On failure, {@link #getErrorMessage()}
 * describes the problem and {@link #getPayload()} is {@code null}.
 */
public final class Response implements Serializable {

  private static final long serialVersionUID = 1L;

  private final boolean success;
  private final Object  payload;
  private final String  errorMessage;

  // ── Constructor ──────────────────────────────────────────────────────────

  private Response(boolean success, Object payload, String errorMessage) {
    this.success      = success;
    this.payload      = payload;
    this.errorMessage = errorMessage;
  }

  // ── Static factories ─────────────────────────────────────────────────────

  /**
   * Success response with a result payload.
   *
   * @param payload result object (typed DTO, list, or {@code null})
   */
  public static Response success(Object payload) {
    return new Response(true, payload, null);
  }

  /** Success response with no payload (fire-and-forget commands). */
  public static Response success() {
    return new Response(true, null, null);
  }

  /**
   * Failure response.
   *
   * @param message human-readable description of the error
   */
  public static Response error(String message) {
    return new Response(false, null, message);
  }

  // ── Accessors ────────────────────────────────────────────────────────────

  /** Returns {@code true} if the command completed without errors. */
  public boolean isSuccess() {
    return success;
  }

  /**
   * Returns the result payload, or {@code null} if the response is an error or carries no data.
   *
   * <p>Cast to the expected DTO type after checking {@link #isSuccess()}:
   * <pre>{@code
   *   if (response.isSuccess()) {
   *     List<ItemDto> items = (List<ItemDto>) response.getPayload();
   *   }
   * }</pre>
   */
  public Object getPayload() {
    return payload;
  }

  /**
   * Returns a human-readable error description when {@link #isSuccess()} is {@code false},
   * or {@code null} on success.
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public String toString() {
    return success
        ? "Response{success=true, payload=" + payload + "}"
        : "Response{success=false, error='" + errorMessage + "'}";
  }
}
