package example;

import com.gravixcloud.token.GravixToken;
import com.gravixcloud.token.GravixTokenException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;

/** Mints a token with DUMMY credentials and prints its claims (never the token or a secret). */
public final class Main {
  public static void main(String[] args) {
    String appId = "APPdemo"; // dummy
    String appCertificate = "dummy-certificate-0123456789abcdef"; // dummy, 34 characters

    String jwt =
        GravixToken.builder(appId, appCertificate)
            .room("standup")
            .identity("user-42")
            .name("Ada")
            .attributes(Collections.singletonMap("seat", "3"))
            .ttl(Duration.ofMinutes(30))
            .toJwt();
    String claims =
        new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
    System.out.println("claims: " + claims);

    try {
      GravixToken.builder(appId, "too-short").room("r").identity("a").toJwt();
    } catch (GravixTokenException e) {
      System.out.println("validation: " + e.getMessage());
    }
  }
}
