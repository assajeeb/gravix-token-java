# gravix-token-java

Mint Gravix Cloud join tokens **in your own Java or Kotlin backend**. The Gravix server verifies them; no Gravix service is called when a user joins, so joins never wait on (or fail because of) a token server.

Zero runtime dependencies (JDK `javax.crypto` HMAC-SHA256 only). Java 8 or newer; works the same from Kotlin.

> Status: 0.1.0 is release-ready but not yet on Maven Central. Until it is, build it locally with
> `./gradlew publishToMavenLocal` and add `mavenLocal()` to your repositories.

## How Gravix tokens work

1. The Gravix console gives your app an **App ID** and an **App Certificate**. Both stay on your server.
2. When a user wants to join a room, your backend mints a short-lived token with this library. Minting is local: no network call, no Gravix service involved.
3. Your app connects with that token. The Gravix server checks the signature with your App Certificate and reads the room, identity and permissions from it. Rooms are scoped to your app (`<AppID>_<room>`).

The full model, with the Go, Node and client SDK sides: https://gravixcloud.com/docs#tokens

## Install

Maven Central coordinates: `com.gravixcloud:gravix-token-java`. The library has no dependencies of its own.

Gradle (Kotlin DSL):

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.gravixcloud:gravix-token-java:0.1.0")
}
```

Gradle (Groovy):

```groovy
implementation 'com.gravixcloud:gravix-token-java:0.1.0'
```

Maven:

```xml
<dependency>
  <groupId>com.gravixcloud</groupId>
  <artifactId>gravix-token-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Quickstart (Java)

```java
import com.gravixcloud.token.GravixToken;
import com.gravixcloud.token.GravixTokenException;
import java.time.Duration;

public final class JoinTokens {
  // App ID + App Certificate from the Gravix console. Keep the certificate on your server.
  private static final String APP_ID = System.getenv("GRAVIX_APP_ID");
  private static final String APP_CERTIFICATE = System.getenv("GRAVIX_APP_CERTIFICATE");

  /** Called by your own authenticated endpoint; return the string to the app. */
  public static String forUser(String userId, String displayName, String room, boolean speaker) {
    try {
      return GravixToken.builder(APP_ID, APP_CERTIFICATE)
          .room(room)                  // server-side room "<AppID>_<room>"
          .identity(userId)            // required, unique within the room
          .name(displayName)
          .canPublish(speaker)         // false = listener
          .ttl(Duration.ofMinutes(30)) // default 6 h, at most 24 h
          .toJwt();
    } catch (GravixTokenException e) {
      // The message names the field and the rule, never a value you passed: safe to log.
      throw new IllegalStateException("cannot mint a Gravix token: " + e.getMessage(), e);
    }
  }
}
```

## Quickstart (Kotlin)

```kotlin
import com.gravixcloud.token.GravixToken
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

// App ID + App Certificate from the Gravix console. Keep the certificate on your server.
private val appId = System.getenv("GRAVIX_APP_ID")
private val appCertificate = System.getenv("GRAVIX_APP_CERTIFICATE")

fun joinToken(userId: String, displayName: String, room: String, speaker: Boolean): String =
    GravixToken.builder(appId, appCertificate)
        .room(room)
        .identity(userId)
        .name(displayName)
        .canPublish(speaker)
        .attributes(mapOf("seat" to "3"))
        .ttl(30.minutes.toJavaDuration())
        .toJwt()
```

`toJwt()` throws `GravixTokenException`; `runCatching { ... }` works if you prefer a `Result`.

## API

`GravixToken.builder(appId, appCertificate)` returns a `GravixToken.Builder`:

| Method | Default | Meaning |
|---|---|---|
| `room(String)` | required | Room to join; the `"<AppID>_"` prefix is added if missing. |
| `identity(String)` | required | The user's unique id within the room. |
| `name(String)` | unset | Display name other participants see. |
| `metadata(String)` | unset | Opaque string visible to other participants. |
| `attributes(Map<String, String>)` | unset | Key/value pairs visible to others. `gravix.app` is reserved: the server sets it to your verified App ID. |
| `ttl(java.time.Duration)` | 6 h | How long the token can be used to join; more than 0, at most 24 h. |
| `canPublish(boolean)` | `true` | `false` makes a listener. |
| `canSubscribe(boolean)` | `true` | |
| `canPublishData(boolean)` | `true` | Data messages and chat. |
| `canUpdateOwnMetadata(boolean)` | `true` | The user may change its own name, metadata and attributes (the Gravix SDKs publish e.g. the camera facing). `gravix.*` attributes stay the server's. |
| `canPublishSources(String...)` / `(Collection<String>)` | any | Limit publishing to `"camera"`, `"microphone"`, `"screen_share"`, `"screen_share_audio"`. |
| `hidden(boolean)` | `false` | Invisible to others (recorders, bots). |
| `roomAdmin(boolean)` | `false` | May mute/remove others in THIS room only. |
| `toJwt()` | | Validates and signs (HS256 with the App Certificate). |

Helpers: `GravixToken.roomName(appId, room)` / `GravixToken.displayName(appId, room)` add or strip the app prefix. Constants: `SEPARATOR` (`"_"`), `DEFAULT_TTL` (6 h), `MAX_TTL` (24 h).

`toJwt()` throws `GravixTokenException` (an unchecked `IllegalArgumentException`) when the token would be refused: empty App ID or one containing `_`, a certificate shorter than 32 bytes, no room or identity, a TTL outside (0, 24 h], or `null` inside `attributes` / `canPublishSources`. Messages name the field and the rule, never a value you passed, so they are safe to log. Builders are cheap and not thread-safe: make one per token.

Never ship the App Certificate in a mobile or web app: mint on your server.

Tokens are byte-for-byte identical to the Go library's for the same claims and clock, and they are checked against the Gravix server's own verifier in `gravix-token-go/conformance`. Go (`github.com/assajeeb/gravix-token-go`) and Node/TypeScript (`gravix-rtc-token` on npm) versions with the same options exist.

## Versioning

[Semantic versioning](https://semver.org/). While the version is `0.x`, a minor release (`0.2.0`) may change the API; patch releases (`0.1.1`) never do. The token format is fixed by the Gravix server, not by this library: every version mints tokens that the current server accepts, and the Java, Go and Node libraries are versioned independently. Changes are listed in [CHANGELOG.md](./CHANGELOG.md).

## Development

Needs JDK 17+ to build (the library itself targets Java 8). Everything runs through the wrapper:

```sh
./gradlew test                     # JUnit 5 (Java) + the Kotlin usage tests in kotlin-usage/
./gradlew publishToMavenLocal      # jar + sources + javadoc + POM into ~/.m2 (never signed, never uploaded)
./gradlew -p examples/consumer run # a separate app that resolves the library from mavenLocal() only
```

Conformance with the Gravix server's verifier (needs the `gravix-token-go` checkout and Go 1.26):

```sh
./gradlew conformanceTokens        # writes build/conformance/java-tokens.txt (+ .manifest.jsonl)
cd ../gravix-token-go/conformance
GRAVIX_FOREIGN_TOKENS="$OLDPWD/build/conformance/java-tokens.txt" go test -count=1 -v ./...
# -> TestForeignLibraryTokens: "7 foreign tokens verified"
```

Add `-PconformanceJar=<path to a gravix-token-java jar>` to mint with a built artifact (the one in `~/.m2`, or one downloaded from Maven Central) instead of this build's classes; the task prints which jar it loaded. The tokens expire (the shortest after 10 minutes), so generate them right before running Go.

### Releasing (maintainers)

Publishing uses the `com.vanniktech.maven.publish` plugin and the Maven Central Portal, with a manual release. Nothing is signed or uploaded unless one of `publishToMavenCentral`, `publishAndReleaseToMavenCentral` or `centralBundle` is named on the command line, and those stop before doing anything if no signing key is configured.

Credentials live in `~/.gradle/gradle.properties`, never in this repository:

```properties
# Central Portal user token (central.sonatype.com > account > Generate User Token)
mavenCentralUsername=<token username>
mavenCentralPassword=<token password>
# Signing with your own gpg + gpg-agent (the private key and passphrase stay in gpg)
signing.gnupg.executable=/opt/homebrew/bin/gpg
signing.gnupg.useLegacyGpg=false
signing.gnupg.keyName=<16-hex key id or fingerprint>
```

CI without gpg can sign with an in-memory key instead: `signingInMemoryKey` (the ASCII-armored secret key; on one line in a properties file, or multi-line in `ORG_GRADLE_PROJECT_signingInMemoryKey`), `signingInMemoryKeyId` (last 8 hex digits) and `signingInMemoryKeyPassword`. `signing.gnupg.keyName` wins when both are set.

Per release:

1. Set `version` in `build.gradle.kts` and date the CHANGELOG entry; commit.
2. Optional dry run: `./gradlew centralBundle` signs and writes `build/central-bundle/gravix-token-java-<version>-bundle.zip`, the same files the upload contains. Nothing leaves the machine.
3. Unlock the key so gpg-agent holds the passphrase (Gradle runs gpg with `--batch --no-tty`, so it may not be able to ask for it): `export GPG_TTY=$(tty); echo unlock | gpg --clearsign --local-user <key id> > /dev/null`. The agent keeps it for 10 minutes after the last use by default.
4. `./gradlew publishToMavenCentral` uploads the bundle and prints a deployment id.
5. In https://central.sonatype.com/publishing/deployments wait for **VALIDATED**, check the file list, press **Publish**. It usually reaches Maven Central within 30 minutes.
6. Tag the commit `v<version>` and push it to GitHub.

## License

MIT, Copyright (c) 2024-2026 Gravity Compile. See [LICENSE](./LICENSE).
