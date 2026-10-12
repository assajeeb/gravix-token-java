package com.gravixcloud.token;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Mints tokens for gravix-token-go's conformance module, which verifies them with the Gravix
 * server's own verifier ({@code livekit/protocol/auth}).
 *
 * <p>Writes, into the directory given as the only argument:
 *
 * <ul>
 *   <li>{@code java-tokens.txt}: one JWT per line, app {@code APPconf}, the conformance module's
 *       dummy certificate. Point {@code GRAVIX_FOREIGN_TOKENS} at it and run {@code go test} in
 *       {@code gravix-token-go/conformance} ({@code TestForeignLibraryTokens}).
 *   <li>{@code java-tokens.manifest.jsonl}: per token, the grants the server must read from it
 *       (identity, name, metadata, attributes, room, every grant flag, sources, TTL), for a
 *       stricter per-field check with the same verifier.
 * </ul>
 *
 * <p>Tokens expire (the shortest after 10 minutes): generate right before verifying. Run with
 * {@code ./gradlew conformanceTokens}.
 */
public final class ConformanceTokens {
  /** The app and dummy certificate the Go conformance module verifies with. */
  static final String APP = "APPconf";

  static final String CERT = "cert-0123456789abcdef0123456789abcdef";

  private ConformanceTokens() {}

  private static final class Case {
    final String name;
    final String room;
    final String identity;
    String displayName = "";
    String metadata = "";
    Map<String, String> attributes = Collections.emptyMap();
    Duration ttl = GravixToken.DEFAULT_TTL;
    boolean canPublish = true;
    boolean canSubscribe = true;
    boolean canPublishData = true;
    boolean canUpdateOwnMetadata = true;
    List<String> sources = Collections.emptyList();
    boolean hidden;
    boolean roomAdmin;

    Case(String name, String room, String identity) {
      this.name = name;
      this.room = room;
      this.identity = identity;
    }

    GravixToken.Builder builder(Clock clock) {
      return GravixToken.builder(APP, CERT)
          .room(room)
          .identity(identity)
          .name(displayName)
          .metadata(metadata)
          .attributes(attributes)
          .ttl(ttl)
          .canPublish(canPublish)
          .canSubscribe(canSubscribe)
          .canPublishData(canPublishData)
          .canUpdateOwnMetadata(canUpdateOwnMetadata)
          .canPublishSources(sources)
          .hidden(hidden)
          .roomAdmin(roomAdmin)
          .clock(clock);
    }
  }

  static List<Case> cases() {
    Case defaults = new Case("defaults", "standup", "alice");
    defaults.displayName = "Alice";

    // Mirrors the Go conformance module's own TestServerVerifierAcceptsAndReadsTheGrants.
    Case listener = new Case("listener", "standup", "bob");
    listener.displayName = "Bob";
    listener.metadata = "m";
    listener.attributes = Collections.singletonMap("seat", "1");
    listener.canPublish = false;
    listener.ttl = Duration.ofHours(1);

    Case allGrants = new Case("all_grants_prefixed_room", APP + "_stage", "recorder-1");
    allGrants.roomAdmin = true;
    allGrants.hidden = true;
    allGrants.sources = Arrays.asList("camera", "microphone");
    allGrants.canSubscribe = false;
    allGrants.canPublishData = false;
    allGrants.canUpdateOwnMetadata = false;
    allGrants.ttl = GravixToken.MAX_TTL;

    String tricky =
        "q\"b\\s/ <b>&amp; nl\n cr\r tab\t bs\b ff\f c01\u0001 c1f\u001f del\u007f "
            + "é 中文 😀 ls  ps  end";
    Case escaping = new Case("escaping_unicode", "room <&> \"x\" café", "id é😀");
    escaping.displayName = tricky;
    escaping.metadata = "{\"json\":\"inside\",\"n\":[1,2]} " + tricky;
    Map<String, String> attrs = new LinkedHashMap<>();
    attrs.put("z", "1");
    attrs.put("é", "2");
    attrs.put("😀", "3");
    attrs.put("！", "4");
    attrs.put("A<&>", tricky);
    attrs.put("cameraFacing", "front");
    escaping.attributes = attrs;

    Case screen = new Case("screen_share_sources", "demo", "presenter");
    screen.displayName = "Presenter";
    screen.sources = Arrays.asList("screen_share", "screen_share_audio");
    screen.ttl = Duration.ofMinutes(10);

    Case fractional = new Case("fractional_ttl", "r", "a");
    fractional.ttl = Duration.ofMinutes(90).plusMillis(1500);

    Case admin = new Case("room_admin_no_data", "ops", "moderator");
    admin.roomAdmin = true;
    admin.canPublishData = false;

    return Arrays.asList(defaults, listener, allGrants, escaping, screen, fractional, admin);
  }

  /**
   * Writes the token and manifest files.
   *
   * @param args the output directory
   * @throws IOException if the files cannot be written
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      throw new IllegalArgumentException("usage: ConformanceTokens <output dir>");
    }
    File dir = new File(args[0]);
    Files.createDirectories(dir.toPath());
    File tokens = new File(dir, "java-tokens.txt");
    File manifest = new File(dir, "java-tokens.manifest.jsonl");
    // One instant (with a sub-second part, as in production) for every token, so each
    // expected TTL is exact: floor(now + ttl) - floor(now).
    Instant now = Instant.now();
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    int n = 0;
    try (Writer t = new OutputStreamWriter(Files.newOutputStream(tokens.toPath()), StandardCharsets.UTF_8);
        Writer m = new OutputStreamWriter(Files.newOutputStream(manifest.toPath()), StandardCharsets.UTF_8)) {
      for (Case c : cases()) {
        String jwt = c.builder(clock).toJwt();
        t.write(jwt + "\n");
        Map<String, String> sortedAttrs = new TreeMap<>(Json.CODE_POINT_ORDER);
        sortedAttrs.putAll(c.attributes);
        String line =
            new Json()
                .beginObject()
                .string("case", c.name)
                .string("jwt", jwt)
                .string("identity", c.identity)
                .string("name", c.displayName)
                .string("metadata", c.metadata)
                .stringMap("attributes", sortedAttrs)
                .string("room", GravixToken.roomName(APP, c.room))
                .bool("canPublish", c.canPublish)
                .bool("canSubscribe", c.canSubscribe)
                .bool("canPublishData", c.canPublishData)
                .bool("canUpdateOwnMetadata", c.canUpdateOwnMetadata)
                .stringArray("canPublishSources", c.sources)
                .bool("hidden", c.hidden)
                .bool("roomAdmin", c.roomAdmin)
                .number("ttlSeconds", now.plus(c.ttl).getEpochSecond() - now.getEpochSecond())
                .endObject()
                .toString();
        m.write(line + "\n");
        n++;
      }
    }
    // Which build of the library minted them: this build's classes, or the jar passed with
    // -PconformanceJar (e.g. the published one).
    System.out.println(
        "library: " + GravixToken.class.getProtectionDomain().getCodeSource().getLocation());
    System.out.println("wrote " + n + " tokens: " + tokens.getAbsolutePath());
    System.out.println("manifest: " + manifest.getAbsolutePath());
  }
}
