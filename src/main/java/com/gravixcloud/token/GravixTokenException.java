package com.gravixcloud.token;

/**
 * Thrown by {@link GravixToken.Builder#toJwt()} when the claims would not make a token the Gravix
 * server accepts (empty App ID, short certificate, missing room or identity, TTL out of range, and
 * so on).
 *
 * <p>The message names the field and the rule, never a value you passed, so it is safe to log or
 * send to crash reporting.
 */
public final class GravixTokenException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  GravixTokenException(String message) {
    super("gravix-token: " + message);
  }
}
