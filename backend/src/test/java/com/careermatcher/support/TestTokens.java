package com.careermatcher.support;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Crafts access tokens for negative security tests. */
public final class TestTokens {

  public static final String TEST_SECRET = "test-only-secret-0123456789abcdef0123456789abcdef";

  private TestTokens() {}

  public static String sign(String secret, UUID subject, Instant issuedAt, Instant expiresAt, Integer ver) {
    var encoder =
        new NimbusJwtEncoder(
            new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .issuer("career-matcher")
            .audience(List.of("career-matcher-api"))
            .subject(subject.toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("role", "CANDIDATE");
    if (ver != null) {
      claims.claim("ver", ver);
    }
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
        .getTokenValue();
  }

  /** Unsigned token ({@code alg: none}) claiming to be {@code subject}. */
  public static String unsigned(UUID subject) {
    Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
    long now = Instant.now().getEpochSecond();
    String header = b64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
    String payload =
        b64.encodeToString(
            ("{\"iss\":\"career-matcher\",\"aud\":[\"career-matcher-api\"],\"sub\":\""
                    + subject
                    + "\",\"iat\":"
                    + now
                    + ",\"exp\":"
                    + (now + 600)
                    + ",\"ver\":0}")
                .getBytes());
    return header + "." + payload + ".";
  }

  /** Replaces the payload of a valid token, keeping the original signature. */
  public static String withTamperedSubject(String token, UUID newSubject) {
    String[] parts = token.split("\\.");
    String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    String tampered = payload.replaceAll("\"sub\":\"[^\"]+\"", "\"sub\":\"" + newSubject + "\"");
    return parts[0]
        + "."
        + Base64.getUrlEncoder().withoutPadding().encodeToString(tampered.getBytes(StandardCharsets.UTF_8))
        + "."
        + parts[2];
  }
}
