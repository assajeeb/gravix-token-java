/**
 * Mint Gravix Cloud join tokens in your own Java or Kotlin backend.
 *
 * <p>Start with {@link com.gravixcloud.token.GravixToken#builder(String, String)}. Tokens are HS256
 * JWTs signed with the App Certificate; the Gravix server verifies them, so no Gravix service is
 * called when a user joins. No runtime dependencies; Java 8 or newer.
 */
package com.gravixcloud.token;
