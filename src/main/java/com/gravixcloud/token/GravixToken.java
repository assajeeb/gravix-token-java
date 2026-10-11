package com.gravixcloud.token;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mints Gravix Cloud join tokens in your own backend.
 *
 * <p>Your backend holds the App ID and App Certificate from the Gravix console and signs a token
 * for each user locally, in microseconds. Your app passes the token to the Gravix SDK, and the
 * Gravix server verifies it. No Gravix service is called at join time, so a join never waits on
 * (or fails because of) a token server.
 *
 * <pre>{@code
 * String jwt = GravixToken.builder(appId, appCertificate)
 *     .room("standup")
 *     .identity(userId)
 *     .name(displayName)
 *     .toJwt();
 * }</pre>
 *
 * <p>Never ship the App Certificate in a mobile or web app: mint on your server.
 */
public final class GravixToken {

  /**
   * Separator between the App ID and your room name. Room names are global on the Gravix servers,
   * so every room of an app is {@code "<AppID>_<room>"}; this library adds the prefix, and the
   * server refuses a room outside it.
   */
  public static final String SEPARATOR = "_";

  /** How long a token stays valid for joining unless {@link Builder#ttl(Duration)} says otherwise. */
  public static final Duration DEFAULT_TTL = Duration.ofHours(6);

  /** The longest a token may stay valid for joining. */
  public static final Duration MAX_TTL = Duration.ofHours(24);

  /** The shortest App Certificate accepted, in UTF-8 bytes (the HMAC key length). */
  static final int MIN_CERTIFICATE_BYTES = 32;

  private static final String HMAC = "HmacSHA256";

  /** {@code {"alg":"HS256","typ":"JWT"}}, base64url: the same header every Gravix library emits. */
  private static final String HEADER = base64Url(utf8("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"));

  private GravixToken() {}

  /**
   * Starts a token for the app. Publish, subscribe, data and updating the user's own
   * name/metadata/attributes are allowed by default; the token is valid for {@link #DEFAULT_TTL}.
   *
   * @param appId the App ID from the Gravix console
   * @param appCertificate the App Certificate from the Gravix console (keep it on your server)
   * @return a new builder; set at least {@link Builder#room(String) room} and {@link
   *     Builder#identity(String) identity}, then call {@link Builder#toJwt()}
   */
  public static Builder builder(String appId, String appCertificate) {
    return new Builder(appId, appCertificate);
  }

  /**
   * The server-side name of an app's room ({@code "<AppID>_<room>"}). A room that already carries
   * the prefix is returned unchanged.
   *
   * @param appId the App ID
   * @param room the room name, with or without the app prefix
   * @return the app-prefixed room name
   */
  public static String roomName(String appId, String room) {
    Objects.requireNonNull(appId, "appId");
    Objects.requireNonNull(room, "room");
    return room.startsWith(appId + SEPARATOR) ? room : appId + SEPARATOR + room;
  }

  /**
   * Strips the app prefix, for showing a room name to users. A room without the prefix is
   * returned unchanged.
   *
   * @param appId the App ID
   * @param room the server-side room name
   * @return the room name without the {@code "<AppID>_"} prefix
   */
  public static String displayName(String appId, String room) {
    Objects.requireNonNull(appId, "appId");
    Objects.requireNonNull(room, "room");
    String prefix = appId + SEPARATOR;
    return room.startsWith(prefix) ? room.substring(prefix.length()) : room;
  }

  /**
   * Collects one token's claims. Methods chain; {@link #toJwt()} validates and signs. A builder is
   * not thread-safe: build each token on one thread (builders are cheap, make one per token).
   */
  public static final class Builder {
    private final String appId;
    private final String appCertificate;
    private String room;
    private String identity;
    private String name;
    private String metadata;
    private Map<String, String> attributes;
    private Duration ttl = DEFAULT_TTL;
    private boolean canPublish = true;
    private boolean canSubscribe = true;
    private boolean canPublishData = true;
    private boolean canUpdateOwnMetadata = true;
    private List<String> canPublishSources = Collections.emptyList();
    private boolean hidden;
    private boolean roomAdmin;
    private Clock clock = Clock.systemUTC();

    private Builder(String appId, String appCertificate) {
      this.appId = appId;
      this.appCertificate = appCertificate;
    }

    /**
     * The room to join (required). The {@code "<AppID>_"} prefix is added if missing, so
     * {@code room("standup")} joins the server-side room {@code "<AppID>_standup"}.
     *
     * @param room the room name, with or without the app prefix
     * @return this builder
     */
    public Builder room(String room) {
      this.room = room;
      return this;
    }

    /**
     * The user's unique id within the room (required).
     *
     * @param identity the user's id
     * @return this builder
     */
    public Builder identity(String identity) {
      this.identity = identity;
      return this;
    }

    /**
     * The display name other participants see. Empty or {@code null} leaves it unset.
     *
     * @param name the display name
     * @return this builder
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * An opaque string visible to other participants. Empty or {@code null} leaves it unset.
     *
     * @param metadata the metadata
     * @return this builder
     */
    public Builder metadata(String metadata) {
      this.metadata = metadata;
      return this;
    }

    /**
     * Key/value pairs visible to other participants. The map is copied. The key {@code
     * "gravix.app"} is reserved: the server overwrites it with the verified App ID. Empty or
     * {@code null} leaves them unset; keys and values must not be {@code null}.
     *
     * @param attributes the attributes
     * @return this builder
     */
    public Builder attributes(Map<String, String> attributes) {
      this.attributes = attributes == null ? null : new LinkedHashMap<>(attributes);
      return this;
    }

    /**
     * How long the token can be used to join: more than zero and at most {@link #MAX_TTL}
     * (default {@link #DEFAULT_TTL}).
     *
     * @param ttl the token's lifetime
     * @return this builder
     */
    public Builder ttl(Duration ttl) {
      this.ttl = ttl;
      return this;
    }

    /**
     * Whether the user may publish audio/video (default {@code true}); {@code false} makes a
     * listener.
     *
     * @param canPublish the grant
     * @return this builder
     */
    public Builder canPublish(boolean canPublish) {
      this.canPublish = canPublish;
      return this;
    }

    /**
     * Whether the user may subscribe to others' tracks (default {@code true}).
     *
     * @param canSubscribe the grant
     * @return this builder
     */
    public Builder canSubscribe(boolean canSubscribe) {
      this.canSubscribe = canSubscribe;
      return this;
    }

    /**
     * Whether the user may send data messages and chat (default {@code true}).
     *
     * @param canPublishData the grant
     * @return this builder
     */
    public Builder canPublishData(boolean canPublishData) {
      this.canPublishData = canPublishData;
      return this;
    }

    /**
     * Whether the user may change its own name, metadata and attributes (default {@code true};
     * the Gravix SDKs publish e.g. the camera facing as an attribute). Attributes under {@code
     * "gravix."} stay the server's either way.
     *
     * @param canUpdateOwnMetadata the grant
     * @return this builder
     */
    public Builder canUpdateOwnMetadata(boolean canUpdateOwnMetadata) {
      this.canUpdateOwnMetadata = canUpdateOwnMetadata;
      return this;
    }

    /**
     * Limits publishing to these sources ({@code "camera"}, {@code "microphone"}, {@code
     * "screen_share"}, {@code "screen_share_audio"}). None (the default) means any source.
     *
     * @param sources the allowed sources
     * @return this builder
     */
    public Builder canPublishSources(String... sources) {
      this.canPublishSources =
          sources == null ? Collections.<String>emptyList() : new ArrayList<>(Arrays.asList(sources));
      return this;
    }

    /**
     * Limits publishing to these sources; see {@link #canPublishSources(String...)}.
     *
     * @param sources the allowed sources
     * @return this builder
     */
    public Builder canPublishSources(Collection<String> sources) {
      this.canPublishSources =
          sources == null ? Collections.<String>emptyList() : new ArrayList<>(sources);
      return this;
    }

    /**
     * Whether the participant is invisible to others (recorders, bots). Default {@code false}.
     *
     * @param hidden the grant
     * @return this builder
     */
    public Builder hidden(boolean hidden) {
      this.hidden = hidden;
      return this;
    }

    /**
     * Whether the user may mute or remove others in THIS room only. Default {@code false}.
     *
     * @param roomAdmin the grant
     * @return this builder
     */
    public Builder roomAdmin(boolean roomAdmin) {
      this.roomAdmin = roomAdmin;
      return this;
    }

    /** Test hook: the clock that stamps iat/nbf/exp. */
    Builder clock(Clock clock) {
      this.clock = Objects.requireNonNull(clock, "clock");
      return this;
    }

    /**
     * Validates the claims and signs them (HS256 with the App Certificate).
     *
     * <p>Validation messages name the field and the rule but never include a value you passed,
     * so they are safe to log or send to crash reporting.
     *
     * @return the signed token (a compact JWT)
     * @throws GravixTokenException if the App ID, certificate, room, identity, TTL, attributes or
     *     sources are invalid
     */
    public String toJwt() {
      validate();

      Instant now = clock.instant();
      // Whole seconds, rounded down, as the Go library's jwt.NumericDate does.
      long issuedAt = now.getEpochSecond();
      long expiresAt = now.plus(ttl).getEpochSecond();

      Json payload = new Json().beginObject();
      payload.string("iss", appId).string("sub", identity);
      payload.number("exp", expiresAt).number("nbf", issuedAt).number("iat", issuedAt);
      if (!isEmpty(name)) {
        payload.string("name", name);
      }
      if (!isEmpty(metadata)) {
        payload.string("metadata", metadata);
      }
      if (attributes != null && !attributes.isEmpty()) {
        Map<String, String> sorted = new TreeMap<>(Json.CODE_POINT_ORDER);
        sorted.putAll(attributes);
        payload.stringMap("attributes", sorted);
      }
      payload.beginObject("video");
      payload.bool("roomJoin", true).string("room", roomName(appId, room));
      if (roomAdmin) {
        payload.bool("roomAdmin", true);
      }
      payload.bool("canPublish", canPublish).bool("canSubscribe", canSubscribe);
      payload.bool("canPublishData", canPublishData);
      payload.bool("canUpdateOwnMetadata", canUpdateOwnMetadata);
      if (!canPublishSources.isEmpty()) {
        payload.stringArray("canPublishSources", canPublishSources);
      }
      if (hidden) {
        payload.bool("hidden", true);
      }
      payload.endObject().endObject();

      String signingInput = HEADER + "." + base64Url(utf8(payload.toString()));
      return signingInput + "." + base64Url(hmacSha256(utf8(appCertificate), utf8(signingInput)));
    }

    private void validate() {
      if (isEmpty(appId)) {
        throw new GravixTokenException("app ID is empty");
      }
      if (appId.contains(SEPARATOR)) {
        throw new GravixTokenException("app ID must not contain \"" + SEPARATOR + "\"");
      }
      if (appCertificate == null || utf8(appCertificate).length < MIN_CERTIFICATE_BYTES) {
        throw new GravixTokenException(
            "app certificate is shorter than " + MIN_CERTIFICATE_BYTES + " characters");
      }
      if (isEmpty(room)) {
        throw new GravixTokenException("room is required");
      }
      if (isEmpty(identity)) {
        throw new GravixTokenException("identity is required");
      }
      if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(MAX_TTL) > 0) {
        throw new GravixTokenException("TTL must be in (0, 24h]");
      }
      if (attributes != null) {
        for (Map.Entry<String, String> e : attributes.entrySet()) {
          if (e.getKey() == null || e.getValue() == null) {
            throw new GravixTokenException("attributes must not contain null keys or values");
          }
        }
      }
      if (canPublishSources.contains(null)) {
        throw new GravixTokenException("canPublishSources must not contain null");
      }
    }
  }

  private static boolean isEmpty(String s) {
    return s == null || s.isEmpty();
  }

  private static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static String base64Url(byte[] bytes) {
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static byte[] hmacSha256(byte[] key, byte[] data) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(key, HMAC));
      return mac.doFinal(data);
    } catch (GeneralSecurityException e) {
      // Every Java platform must provide HmacSHA256; reaching this means a broken JCE setup.
      throw new IllegalStateException("gravix-token: HmacSHA256 is unavailable", e);
    }
  }
}
