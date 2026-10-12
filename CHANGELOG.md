# Changelog

All notable changes to `com.gravixcloud:gravix-token-java`. This project follows
[semantic versioning](https://semver.org/).

## 0.1.0 (not yet published; dated on release)

- First release. Java port of `gravix-token-go` v0.1.2 / `gravix-rtc-token` 0.1.3 with the
  same claims, defaults and validation: `GravixToken.builder(appId, appCertificate)` with
  `room`, `identity`, `name`, `metadata`, `attributes`, `ttl` (default 6 h, max 24 h),
  `canPublish`, `canSubscribe`, `canPublishData`, `canUpdateOwnMetadata` (all default true),
  `canPublishSources`, `hidden`, `roomAdmin`, and `toJwt()`; `roomName` / `displayName`
  helpers for the app-prefixed rooms (`<AppID>_<room>`).
- Validation errors (`GravixTokenException`, unchecked) name the field and the rule but never
  include a value you passed, so they are safe to log or send to crash reporting.
- Zero runtime dependencies (HMAC-SHA256 from `javax.crypto`); Java 8 bytecode; usable from
  Kotlin.
- Tokens are byte-for-byte identical to the Go library's for the same claims and clock, and
  verify with the Gravix server's own verifier (`gravix-token-go/conformance`).
- Published to Maven Central as `com.gravixcloud:gravix-token-java` with sources and javadoc
  jars, a GPG signature on every file, and a POM with no dependencies.
