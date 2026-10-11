package com.gravixcloud.token;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Test helpers: decode a JWT independently of the library's JSON writer. Dummy values only. */
final class TestTokens {
  static final String CERT = "cert-0123456789abcdef0123456789abcdef";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private TestTokens() {}

  static String[] parts(String jwt) {
    String[] parts = jwt.split("\\.", -1);
    assertEquals(3, parts.length, "a JWT has three parts");
    return parts;
  }

  static String json(String base64Url) {
    return new String(Base64.getUrlDecoder().decode(base64Url), StandardCharsets.UTF_8);
  }

  static String payloadJson(String jwt) {
    return json(parts(jwt)[1]);
  }

  static Map<String, Object> payload(String jwt) {
    return parse(payloadJson(jwt));
  }

  static Map<String, Object> parse(String json) {
    try {
      return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> video(String jwt) {
    return (Map<String, Object>) payload(jwt).get("video");
  }

  static long number(Map<String, Object> claims, String key) {
    return ((Number) claims.get(key)).longValue();
  }

  /** Recomputes the HS256 signature over "header.payload" with the given secret. */
  static boolean signatureOk(String jwt, String secret) {
    String[] p = parts(jwt);
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] sig =
          mac.doFinal((p[0] + "." + p[1]).getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(sig).equals(p[2]);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
