package com.gravixcloud.token;

import static com.gravixcloud.token.TestTokens.CERT;
import static com.gravixcloud.token.TestTokens.payloadJson;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Byte-for-byte parity with the reference library: each expected token below was minted by
 * gravix-token-go v0.1.2 ({@code token.go}, golang-jwt v5.2.2) with its clock fixed at the same
 * instant and the same dummy certificate. Same claims, same JSON bytes, same signature.
 *
 * <p>Regenerate (scratch copy of gravix-token-go, never committed there): a {@code _test.go} in
 * package gravixtoken that sets {@code b.now} and prints {@code ToJWT()} for these builders.
 */
class GoldenVectorsTest {

  private static final Clock FIXED = Clock.fixed(Instant.ofEpochSecond(1760000000L), ZoneOffset.UTC);

  /** Quote, backslash, slash, HTML, every short escape, other controls, DEL, non-ASCII, U+2028/9. */
  static final String TRICKY =
      "q\"b\\s/ <b>&amp; nl\n cr\r tab\t bs\b ff\f c01\u0001 c1f\u001f del\u007f "
          + "é 中文 😀 ls  ps  end";

  private static GravixToken.Builder go(String room, String identity) {
    return GravixToken.builder("APPabc", CERT).room(room).identity(identity).clock(FIXED);
  }

  private static void assertGo(String expected, GravixToken.Builder b) {
    String actual = b.toJwt();
    assertEquals(payloadJson(expected), payloadJson(actual), "payload JSON");
    assertEquals(expected, actual, "token");
  }

  @Test
  void defaults() {
    assertGo(
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJBUFBhYmMiLCJzdWIiOiJhbGljZSIsImV4cCI6MTc2MDAyMTYwMCwibmJmIjoxNzYwMDAwMDAwLCJpYXQiOjE3NjAwMDAwMDAsIm5hbWUiOiJBbGljZSIsInZpZGVvIjp7InJvb21Kb2luIjp0cnVlLCJyb29tIjoiQVBQYWJjX3N0YW5kdXAiLCJjYW5QdWJsaXNoIjp0cnVlLCJjYW5TdWJzY3JpYmUiOnRydWUsImNhblB1Ymxpc2hEYXRhIjp0cnVlLCJjYW5VcGRhdGVPd25NZXRhZGF0YSI6dHJ1ZX19.9OZ8-zZAgFn8MjxKpL4c5npsABsB8jqHk0UrlZUcSm4", // gitleaks:allow (dummy-certificate test vector, not a secret)
        go("standup", "alice").name("Alice"));
  }

  @Test
  void listenerWithSortedAttributes() {
    Map<String, String> attrs = new HashMap<>();
    attrs.put("seat", "3");
    attrs.put("b", "2");
    attrs.put("a", "1");
    assertGo(
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJBUFBhYmMiLCJzdWIiOiJiIiwiZXhwIjoxNzYwMDA3MjAwLCJuYmYiOjE3NjAwMDAwMDAsImlhdCI6MTc2MDAwMDAwMCwibWV0YWRhdGEiOiJ7XCJ4XCI6MX0iLCJhdHRyaWJ1dGVzIjp7ImEiOiIxIiwiYiI6IjIiLCJzZWF0IjoiMyJ9LCJ2aWRlbyI6eyJyb29tSm9pbiI6dHJ1ZSwicm9vbSI6IkFQUGFiY19yIiwiY2FuUHVibGlzaCI6ZmFsc2UsImNhblN1YnNjcmliZSI6dHJ1ZSwiY2FuUHVibGlzaERhdGEiOnRydWUsImNhblVwZGF0ZU93bk1ldGFkYXRhIjp0cnVlfX0.ImRJaNNsZIuudLxtNOKSsSb3gHUiencFwJbYeJtljrc", // gitleaks:allow (dummy-certificate test vector, not a secret)
        go("r", "b").canPublish(false).attributes(attrs).metadata("{\"x\":1}").ttl(Duration.ofHours(2)));
  }

  @Test
  void allGrants() {
    assertGo(
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJBUFBhYmMiLCJzdWIiOiJyZWMtMSIsImV4cCI6MTc2MDA4NjQwMCwibmJmIjoxNzYwMDAwMDAwLCJpYXQiOjE3NjAwMDAwMDAsInZpZGVvIjp7InJvb21Kb2luIjp0cnVlLCJyb29tIjoiQVBQYWJjX3N0YWdlIiwicm9vbUFkbWluIjp0cnVlLCJjYW5QdWJsaXNoIjp0cnVlLCJjYW5TdWJzY3JpYmUiOmZhbHNlLCJjYW5QdWJsaXNoRGF0YSI6ZmFsc2UsImNhblVwZGF0ZU93bk1ldGFkYXRhIjpmYWxzZSwiY2FuUHVibGlzaFNvdXJjZXMiOlsiY2FtZXJhIiwibWljcm9waG9uZSJdLCJoaWRkZW4iOnRydWV9fQ.Yog8G04Ht-7wWGbhMfHBXHgrqU4Obmqn5pjVeJUoDnA", // gitleaks:allow (dummy-certificate test vector, not a secret)
        go("APPabc_stage", "rec-1")
            .roomAdmin(true)
            .hidden(true)
            .canPublishSources("camera", "microphone")
            .canSubscribe(false)
            .canPublishData(false)
            .canUpdateOwnMetadata(false)
            .ttl(Duration.ofHours(24)));
  }

  @Test
  void escapingAndKeyOrder() {
    Map<String, String> attrs = new HashMap<>();
    attrs.put("z", "1");
    attrs.put("é", "2");
    attrs.put("😀", "3");
    attrs.put("！", "4");
    attrs.put("A<&>", TRICKY);
    attrs.put("", "empty-key");
    assertGo(
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJBUFBhYmMiLCJzdWIiOiJpZFx1MjAyOMOp8J-YgCIsImV4cCI6MTc2MDAyMTYwMCwibmJmIjoxNzYwMDAwMDAwLCJpYXQiOjE3NjAwMDAwMDAsIm5hbWUiOiJxXCJiXFxzLyBcdTAwM2NiXHUwMDNlXHUwMDI2YW1wOyBubFxuIGNyXHIgdGFiXHQgYnNcYiBmZlxmIGMwMVx1MDAwMSBjMWZcdTAwMWYgZGVsfyDDqSDkuK3mlocg8J-YgCBsc1x1MjAyOCBwc1x1MjAyOSBlbmQiLCJtZXRhZGF0YSI6InFcImJcXHMvIFx1MDAzY2JcdTAwM2VcdTAwMjZhbXA7IG5sXG4gY3JcciB0YWJcdCBic1xiIGZmXGYgYzAxXHUwMDAxIGMxZlx1MDAxZiBkZWx_IMOpIOS4reaWhyDwn5iAIGxzXHUyMDI4IHBzXHUyMDI5IGVuZCIsImF0dHJpYnV0ZXMiOnsiIjoiZW1wdHkta2V5IiwiQVx1MDAzY1x1MDAyNlx1MDAzZSI6InFcImJcXHMvIFx1MDAzY2JcdTAwM2VcdTAwMjZhbXA7IG5sXG4gY3JcciB0YWJcdCBic1xiIGZmXGYgYzAxXHUwMDAxIGMxZlx1MDAxZiBkZWx_IMOpIOS4reaWhyDwn5iAIGxzXHUyMDI4IHBzXHUyMDI5IGVuZCIsInoiOiIxIiwiw6kiOiIyIiwi77yBIjoiNCIsIvCfmIAiOiIzIn0sInZpZGVvIjp7InJvb21Kb2luIjp0cnVlLCJyb29tIjoiQVBQYWJjX3Jvb20gXHUwMDNjXHUwMDI2XHUwMDNlIFwieFwiIiwiY2FuUHVibGlzaCI6dHJ1ZSwiY2FuU3Vic2NyaWJlIjp0cnVlLCJjYW5QdWJsaXNoRGF0YSI6dHJ1ZSwiY2FuVXBkYXRlT3duTWV0YWRhdGEiOnRydWV9fQ.dvlc6Q31xcB9AEOl6PmYCWrt8Qf8OuDVuFsps1KlQGw", // gitleaks:allow (dummy-certificate test vector, not a secret)
        go("room <&> \"x\"", "id é😀").name(TRICKY).metadata(TRICKY).attributes(attrs));
  }

  @Test
  void fractionalTtlRoundsDownLikeGo() {
    // now = ...00.7 s, TTL = 5401.5 s: iat = floor(now), exp = floor(now + TTL) = iat + 5402.
    Clock withNanos = Clock.fixed(Instant.ofEpochSecond(1760000000L, 700_000_000L), ZoneOffset.UTC);
    assertGo(
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJBUFBhYmMiLCJzdWIiOiJhIiwiZXhwIjoxNzYwMDA1NDAyLCJuYmYiOjE3NjAwMDAwMDAsImlhdCI6MTc2MDAwMDAwMCwidmlkZW8iOnsicm9vbUpvaW4iOnRydWUsInJvb20iOiJBUFBhYmNfciIsImNhblB1Ymxpc2giOnRydWUsImNhblN1YnNjcmliZSI6dHJ1ZSwiY2FuUHVibGlzaERhdGEiOnRydWUsImNhblVwZGF0ZU93bk1ldGFkYXRhIjp0cnVlfX0.zrAplE8-P3RFIRn_-GENLFjIZPsyclGpHB79ykFjlEs", // gitleaks:allow (dummy-certificate test vector, not a secret)
        go("r", "a").ttl(Duration.ofMinutes(90).plusMillis(1500)).clock(withNanos));
  }

  @Test
  void emptyOptionalsAreOmitted() {
    assertGo(
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJBUFBhYmMiLCJzdWIiOiJhIiwiZXhwIjoxNzYwMDIxNjAwLCJuYmYiOjE3NjAwMDAwMDAsImlhdCI6MTc2MDAwMDAwMCwidmlkZW8iOnsicm9vbUpvaW4iOnRydWUsInJvb20iOiJBUFBhYmNfciIsImNhblB1Ymxpc2giOnRydWUsImNhblN1YnNjcmliZSI6dHJ1ZSwiY2FuUHVibGlzaERhdGEiOnRydWUsImNhblVwZGF0ZU93bk1ldGFkYXRhIjp0cnVlfX0.jDh2BGG8drDYPTKhF0oEcDQH9jwmhLmlxTpK_Mp4_CQ", // gitleaks:allow (dummy-certificate test vector, not a secret)
        go("r", "a").name("").metadata("").attributes(Collections.<String, String>emptyMap()).canPublishSources());
  }
}
