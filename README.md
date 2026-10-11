# gravix-token-java

Mint Gravix Cloud join tokens **in your own Java or Kotlin backend**. The Gravix server verifies them; no Gravix service is called when a user joins, so joins never wait on (or fail because of) a token server.

Zero runtime dependencies (JDK `javax.crypto` HMAC-SHA256 only). Java 8 or newer; works the same from Kotlin.

> Status: 0.1.0 is release-ready but not yet on Maven Central. Until it is, build it locally with
> `./gradlew publishToMavenLocal` and add `mavenLocal()` to your repositories.

## Install

Gradle (Kotlin DSL):

```kotlin
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

## Usage (Java)

```java
import com.gravixcloud.token.GravixToken;

// App ID + App Certificate from the Gravix console. Keep the certificate on your server.
String token = GravixToken.builder(System.getenv("GRAVIX_APP_ID"), System.getenv("GRAVIX_APP_CERTIFICATE"))
    .room("standup")          // server-side room "<AppID>_standup"
    .identity(user.getId())   // required
    .name(user.getName())
    .canPublish(true)         // false = listener
    .toJwt();
```

## Usage (Kotlin)

```kotlin
import com.gravixcloud.token.GravixToken
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

val token = GravixToken.builder(appId, appCertificate)
    .room("standup")
    .identity(user.id)
    .name(user.name)
    .attributes(mapOf("seat" to "3"))
    .ttl(30.minutes.toJavaDuration())
    .toJwt()
```

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

Tokens are byte-for-byte identical to the Go library's for the same claims and clock, and they are checked against the Gravix server's own verifier in `gravix-token-go/conformance`. Go and TypeScript versions with the same options are available; see https://www.gravixcloud.com/docs.

## Development

Needs JDK 17+ to build (the library itself targets Java 8). Everything runs through the wrapper:

```sh
./gradlew test                     # JUnit 5 (Java) + the Kotlin usage tests in kotlin-usage/
./gradlew publishToMavenLocal      # jar + sources + javadoc + POM into ~/.m2 (signed only if a key is set)
./gradlew -p examples/consumer run # a separate app that resolves the library from mavenLocal() only
```

Conformance with the Gravix server's verifier (needs the `gravix-token-go` checkout and Go 1.26):

```sh
./gradlew conformanceTokens        # writes build/conformance/java-tokens.txt (+ .manifest.jsonl)
cd ../gravix-token-go/conformance
GRAVIX_FOREIGN_TOKENS="$OLDPWD/build/conformance/java-tokens.txt" go test -count=1 -v ./...
# -> TestForeignLibraryTokens: "7 foreign tokens verified"
```

The tokens expire (the shortest after 10 minutes), so generate them right before running Go.

### Releasing (maintainers)

Publishing goes through the `com.vanniktech.maven.publish` plugin to the Maven Central Portal, with manual release. Before the first release, set the `scm` block in `build.gradle.kts` (it is a TODO placeholder). Provide `mavenCentralUsername` / `mavenCentralPassword` and a signing key (`signingInMemoryKey`, `signingInMemoryKeyId`, `signingInMemoryKeyPassword`) as Gradle properties or `ORG_GRADLE_PROJECT_*` environment variables; signing is enabled only when a key is present.

## License

MIT, Copyright (c) 2024-2026 Gravity Compile. See [LICENSE](./LICENSE).
