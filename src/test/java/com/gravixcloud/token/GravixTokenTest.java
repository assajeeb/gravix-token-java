package com.gravixcloud.token;

import static com.gravixcloud.token.TestTokens.CERT;
import static com.gravixcloud.token.TestTokens.number;
import static com.gravixcloud.token.TestTokens.payload;
import static com.gravixcloud.token.TestTokens.signatureOk;
import static com.gravixcloud.token.TestTokens.video;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Ports every case of gravix-token-go's token_test.go (and the cases gravix-token-js adds), plus
 * Java-only behaviour (nulls, Duration, defensive copies). Dummy values only.
 */
class GravixTokenTest {

  private static final String S = "s-0123456789abcdef0123456789abcdef";

  // --- token_test.go: TestTokenCarriesTheAppPrefixedRoomAndDefaults (+ JS: signature, exact grant)

  @Test
  void tokenCarriesTheAppPrefixedRoomAndDefaults() {
    String jwt =
        GravixToken.builder("APPabc", "secret-0123456789abcdef0123456789ab")
            .room("standup")
            .identity("alice")
            .name("Alice")
            .ttl(Duration.ofHours(2))
            .toJwt();
    assertTrue(signatureOk(jwt, "secret-0123456789abcdef0123456789ab"));
    Map<String, Object> c = payload(jwt);
    assertEquals("APPabc", c.get("iss"));
    assertEquals("alice", c.get("sub"));
    assertEquals("Alice", c.get("name"));
    Map<String, Object> expectedVideo = new LinkedHashMap<>();
    expectedVideo.put("roomJoin", true);
    expectedVideo.put("room", "APPabc_standup");
    expectedVideo.put("canPublish", true);
    expectedVideo.put("canSubscribe", true);
    expectedVideo.put("canPublishData", true);
    expectedVideo.put("canUpdateOwnMetadata", true);
    assertEquals(expectedVideo, video(jwt));
    assertEquals(7200, number(c, "exp") - number(c, "nbf"));
    assertEquals(number(c, "iat"), number(c, "nbf"));
  }

  // --- token_test.go: TestAnAlreadyPrefixedRoomIsNotPrefixedTwice

  @Test
  void anAlreadyPrefixedRoomIsNotPrefixedTwice() {
    String jwt = GravixToken.builder("APPabc", S).room("APPabc_standup").identity("a").toJwt();
    assertEquals("APPabc_standup", video(jwt).get("room"));
  }

  // --- token_test.go: TestListenerOnlyAndAttributes

  @Test
  void listenerOnlyAndAttributes() {
    String jwt =
        GravixToken.builder("APPabc", S)
            .room("r")
            .identity("b")
            .canPublish(false)
            .attributes(Collections.singletonMap("seat", "3"))
            .metadata("{\"x\":1}")
            .toJwt();
    Map<String, Object> c = payload(jwt);
    Map<String, Object> v = video(jwt);
    assertEquals(false, v.get("canPublish"));
    assertEquals(true, v.get("canSubscribe"));
    assertEquals(Collections.singletonMap("seat", "3"), c.get("attributes"));
    assertEquals("{\"x\":1}", c.get("metadata"));
  }

  // --- token_test.go: TestRefusesWhatTheServerWouldRefuse (+ JS: empty room/identity strings)

  @Test
  void refusesWhatTheServerWouldRefuse() {
    Map<String, Supplier<GravixToken.Builder>> cases = new LinkedHashMap<>();
    cases.put("no app id", () -> GravixToken.builder("", S).room("r").identity("a"));
    cases.put("app id with _", () -> GravixToken.builder("APP_x", S).room("r").identity("a"));
    cases.put("short certificate", () -> GravixToken.builder("APPabc", "short").room("r").identity("a"));
    cases.put("no room", () -> GravixToken.builder("APPabc", S).identity("a"));
    cases.put("no identity", () -> GravixToken.builder("APPabc", S).room("r"));
    cases.put(
        "ttl over 24h",
        () -> GravixToken.builder("APPabc", S).room("r").identity("a").ttl(Duration.ofHours(25)));
    // gravix-token-js cases
    cases.put("empty room", () -> GravixToken.builder("APPabc", S).room("").identity("a"));
    cases.put("empty identity", () -> GravixToken.builder("APPabc", S).room("r").identity(""));
    cases.put(
        "ttl 90000 s",
        () -> GravixToken.builder("APPabc", S).room("r").identity("a").ttl(Duration.ofSeconds(90000)));
    // Java: nulls, zero/negative TTL, the 31-byte certificate
    cases.put("null app id", () -> GravixToken.builder(null, S).room("r").identity("a"));
    cases.put("null certificate", () -> GravixToken.builder("APPabc", null).room("r").identity("a"));
    cases.put(
        "31-byte certificate",
        () -> GravixToken.builder("APPabc", S.substring(0, 31)).room("r").identity("a"));
    cases.put("null room", () -> GravixToken.builder("APPabc", S).room(null).identity("a"));
    cases.put("null identity", () -> GravixToken.builder("APPabc", S).room("r").identity(null));
    cases.put("zero ttl", () -> GravixToken.builder("APPabc", S).room("r").identity("a").ttl(Duration.ZERO));
    cases.put(
        "negative ttl",
        () -> GravixToken.builder("APPabc", S).room("r").identity("a").ttl(Duration.ofSeconds(-1)));
    cases.put("null ttl", () -> GravixToken.builder("APPabc", S).room("r").identity("a").ttl(null));
    cases.put(
        "ttl 24h + 1ns",
        () -> GravixToken.builder("APPabc", S).room("r").identity("a").ttl(Duration.ofHours(24).plusNanos(1)));
    cases.put(
        "null attribute value",
        () -> GravixToken.builder("APPabc", S).room("r").identity("a").attributes(Collections.singletonMap("k", (String) null)));
    cases.put(
        "null attribute key",
        () -> GravixToken.builder("APPabc", S).room("r").identity("a").attributes(Collections.singletonMap((String) null, "v")));
    cases.put(
        "null source",
        () -> GravixToken.builder("APPabc", S).room("r").identity("a").canPublishSources("camera", null));
    for (Map.Entry<String, Supplier<GravixToken.Builder>> c : cases.entrySet()) {
      GravixToken.Builder b = c.getValue().get();
      assertThrows(GravixTokenException.class, b::toJwt, c.getKey() + ": no error");
    }
  }

  // --- token_test.go: TestRoomNameHelpers

  @Test
  void roomNameHelpers() {
    assertEquals("APPa_x", GravixToken.roomName("APPa", "x"));
    assertEquals("APPa_x", GravixToken.roomName("APPa", "APPa_x"));
    assertEquals("x", GravixToken.displayName("APPa", "APPa_x"));
    assertEquals("other", GravixToken.displayName("APPa", "other"));
  }

  // --- token_test.go: TestCanUpdateOwnMetadataDefaultsOnAndCanBeOff

  @Test
  void canUpdateOwnMetadataDefaultsOnAndCanBeOff() {
    String on = GravixToken.builder("APPabc", S).room("r").identity("a").toJwt();
    assertEquals(true, video(on).get("canUpdateOwnMetadata"));
    String off = GravixToken.builder("APPabc", S).room("r").identity("a").canUpdateOwnMetadata(false).toJwt();
    assertEquals(false, video(off).get("canUpdateOwnMetadata"));
  }

  // --- token_test.go: TestErrorsNameTheFieldButNeverQuoteCallerValues (+ Java-only fields)

  @Test
  void errorsNameTheFieldButNeverQuoteCallerValues() {
    final String app = "LEAKAPP9f3c";
    final String room = "leakroom77";
    final String id = "leakuser55";
    final String badCert = "leakcert";
    final String cert = S;
    final Duration badTtl = Duration.ofHours(25).plusSeconds(17);
    Map<String, String> leakyAttrs = new HashMap<>();
    leakyAttrs.put("leakkey31", null);
    String[][] fieldAndCase = {
      {"app ID", "empty"},
      {"app ID", "separator"},
      {"app certificate", "cert"},
      {"room", "room"},
      {"identity", "identity"},
      {"TTL", "ttl"},
      {"attributes", "attributes"},
      {"canPublishSources", "sources"},
    };
    for (String[] fc : fieldAndCase) {
      GravixToken.Builder b;
      switch (fc[1]) {
        case "empty":
          b = GravixToken.builder("", cert).room(room).identity(id);
          break;
        case "separator":
          b = GravixToken.builder(app + "_x", cert).room(room).identity(id);
          break;
        case "cert":
          b = GravixToken.builder(app, badCert).room(room).identity(id);
          break;
        case "room":
          b = GravixToken.builder(app, cert).identity(id);
          break;
        case "identity":
          b = GravixToken.builder(app, cert).room(room);
          break;
        case "ttl":
          b = GravixToken.builder(app, cert).room(room).identity(id).ttl(badTtl);
          break;
        case "attributes":
          b = GravixToken.builder(app, cert).room(room).identity(id).attributes(leakyAttrs);
          break;
        default:
          b = GravixToken.builder(app, cert).room(room).identity(id).canPublishSources("leaksrc", null);
      }
      String msg;
      try {
        b.toJwt();
        fail(fc[0] + ": no error");
        return;
      } catch (GravixTokenException e) {
        msg = e.getMessage();
      }
      assertTrue(msg.contains(fc[0]), "error \"" + msg + "\" does not name " + fc[0]);
      for (String v :
          Arrays.asList(app, room, id, badCert, cert, "leakkey31", "leaksrc", "25h0m17s", "PT25H17S", "90017")) {
        assertFalse(msg.contains(v), "error \"" + msg + "\" quotes a caller value");
      }
    }
  }

  // --- gravix-token-js: same inputs, same token

  @Test
  void sameInputsAndClockGiveTheSameToken() {
    Clock clock = Clock.fixed(Instant.ofEpochSecond(1760000000), ZoneOffset.UTC);
    GravixToken.Builder b =
        GravixToken.builder("APPabc", CERT)
            .room("r")
            .identity("b")
            .canPublish(false)
            .attributes(Collections.singletonMap("seat", "3"))
            .metadata("{\"x\":1}")
            .clock(clock);
    assertEquals(b.toJwt(), b.toJwt());
  }

  // --- Java: defaults, TTL bounds, header, encoding

  @Test
  void defaultTtlIsSixHoursAndMaxIsTwentyFour() {
    assertEquals(Duration.ofHours(6), GravixToken.DEFAULT_TTL);
    assertEquals(Duration.ofHours(24), GravixToken.MAX_TTL);
    assertEquals("_", GravixToken.SEPARATOR);
    Map<String, Object> c = payload(GravixToken.builder("APPabc", CERT).room("r").identity("a").toJwt());
    assertEquals(6 * 3600, number(c, "exp") - number(c, "iat"));
    Map<String, Object> max =
        payload(GravixToken.builder("APPabc", CERT).room("r").identity("a").ttl(GravixToken.MAX_TTL).toJwt());
    assertEquals(24 * 3600, number(max, "exp") - number(max, "iat"));
    Map<String, Object> oneSecond =
        payload(GravixToken.builder("APPabc", CERT).room("r").identity("a").ttl(Duration.ofSeconds(1)).toJwt());
    assertEquals(1, number(oneSecond, "exp") - number(oneSecond, "iat"));
  }

  @Test
  void issuedAtIsNowInWholeSeconds() {
    long before = System.currentTimeMillis() / 1000;
    Map<String, Object> c = payload(GravixToken.builder("APPabc", CERT).room("r").identity("a").toJwt());
    long after = System.currentTimeMillis() / 1000;
    long iat = number(c, "iat");
    assertTrue(iat >= before && iat <= after, "iat is the current time");
  }

  @Test
  void headerIsHs256JwtAndPartsAreUnpaddedBase64Url() {
    String jwt =
        GravixToken.builder("APPabc", CERT)
            .room("r?>~")
            .identity("ÿþ")
            .metadata("???>>>~~~")
            .toJwt();
    String[] p = TestTokens.parts(jwt);
    assertEquals("{\"alg\":\"HS256\",\"typ\":\"JWT\"}", TestTokens.json(p[0]));
    for (String part : p) {
      assertTrue(part.matches("[A-Za-z0-9_-]+"), "base64url without padding");
    }
    assertTrue(signatureOk(jwt, CERT));
    assertFalse(signatureOk(jwt, "another-cert-0123456789abcdef0123456"));
  }

  @Test
  void optionalClaimsAreOmittedWhenEmptyOrNull() {
    String jwt =
        GravixToken.builder("APPabc", CERT)
            .room("r")
            .identity("a")
            .name(null)
            .metadata("")
            .attributes(null)
            .canPublishSources((String[]) null)
            .toJwt();
    Map<String, Object> c = payload(jwt);
    assertEquals(
        Arrays.asList("iss", "sub", "exp", "nbf", "iat", "video"), Arrays.asList(c.keySet().toArray()));
    assertEquals(
        Arrays.asList("roomJoin", "room", "canPublish", "canSubscribe", "canPublishData", "canUpdateOwnMetadata"),
        Arrays.asList(video(jwt).keySet().toArray()));
    Map<String, Object> empty =
        payload(
            GravixToken.builder("APPabc", CERT)
                .room("r")
                .identity("a")
                .name("")
                .attributes(Collections.<String, String>emptyMap())
                .canPublishSources(Collections.<String>emptyList())
                .toJwt());
    assertFalse(empty.containsKey("name") || empty.containsKey("attributes"));
  }

  @Test
  void allGrantsAppearInTheGoKeyOrder() {
    String jwt =
        GravixToken.builder("APPabc", CERT)
            .room("stage")
            .identity("rec-1")
            .name("Recorder")
            .metadata("m")
            .attributes(Collections.singletonMap("k", "v"))
            .roomAdmin(true)
            .hidden(true)
            .canPublishSources(Arrays.asList("camera", "microphone"))
            .canSubscribe(false)
            .canPublishData(false)
            .toJwt();
    assertEquals(
        Arrays.asList("iss", "sub", "exp", "nbf", "iat", "name", "metadata", "attributes", "video"),
        Arrays.asList(payload(jwt).keySet().toArray()));
    Map<String, Object> v = video(jwt);
    assertEquals(
        Arrays.asList(
            "roomJoin", "room", "roomAdmin", "canPublish", "canSubscribe", "canPublishData",
            "canUpdateOwnMetadata", "canPublishSources", "hidden"),
        Arrays.asList(v.keySet().toArray()));
    assertEquals(Arrays.asList("camera", "microphone"), v.get("canPublishSources"));
    assertEquals(true, v.get("roomAdmin"));
    assertEquals(true, v.get("hidden"));
    assertEquals(false, v.get("canSubscribe"));
    assertEquals(false, v.get("canPublishData"));
    assertEquals("APPabc_stage", v.get("room"));
  }

  @Test
  void attributesAndSourcesAreCopied() {
    Map<String, String> attrs = new HashMap<>();
    attrs.put("seat", "1");
    String[] sources = {"camera"};
    GravixToken.Builder b =
        GravixToken.builder("APPabc", CERT).room("r").identity("a").attributes(attrs).canPublishSources(sources);
    attrs.put("seat", "2");
    attrs.put("extra", "x");
    sources[0] = "screen_share";
    String jwt = b.toJwt();
    assertEquals(Collections.singletonMap("seat", "1"), payload(jwt).get("attributes"));
    assertEquals(Collections.singletonList("camera"), video(jwt).get("canPublishSources"));
  }

  @Test
  void certificateLengthIsCountedInUtf8BytesLikeGo() {
    // 16 x U+00E9 is 16 UTF-16 units but 32 UTF-8 bytes: Go's len() accepts it, so does Java.
    StringBuilder twoByte = new StringBuilder();
    for (int i = 0; i < 16; i++) {
      twoByte.append('é');
    }
    String jwt = GravixToken.builder("APPabc", twoByte.toString()).room("r").identity("a").toJwt();
    assertTrue(signatureOk(jwt, twoByte.toString()));
  }

  @Test
  void nonAsciiClaimsRoundTrip() {
    String name = "Jörg 中文 😀 <b>&amp;</b> \"q\" \\  ";
    Map<String, String> attrs = new HashMap<>();
    attrs.put("😀", "emoji key");
    attrs.put("！", "fullwidth key");
    String jwt =
        GravixToken.builder("APPabc", CERT).room("café").identity("ü").name(name).attributes(attrs).toJwt();
    Map<String, Object> c = payload(jwt);
    assertEquals(name, c.get("name"));
    assertEquals("ü", c.get("sub"));
    assertEquals(attrs, c.get("attributes"));
    assertEquals("APPabc_café", video(jwt).get("room"));
  }

  @Test
  void helpersRejectNullWithTheParameterNameOnly() {
    NullPointerException e = assertThrows(NullPointerException.class, () -> GravixToken.roomName("APPa", null));
    assertEquals("room", e.getMessage());
    assertThrows(NullPointerException.class, () -> GravixToken.displayName(null, "x"));
  }
}
