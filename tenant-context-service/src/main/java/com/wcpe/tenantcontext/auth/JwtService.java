package com.wcpe.tenantcontext.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private static final String HMAC_SHA256 = "HmacSHA256";
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final byte[] secret;
  private final String issuer;
  private final long ttlSeconds;

  public JwtService(
    ObjectMapper objectMapper,
    @Value("${auth.jwt.secret}") String secret,
    @Value("${auth.jwt.issuer}") String issuer,
    @Value("${auth.jwt.ttl-minutes}") long ttlMinutes
  ) {
    this(objectMapper, Clock.systemUTC(), secret, issuer, ttlMinutes);
  }

  JwtService(ObjectMapper objectMapper, Clock clock, String secret, String issuer, long ttlMinutes) {
    if (secret == null || secret.length() < 32) {
      throw new IllegalStateException("auth.jwt.secret must be at least 32 characters");
    }
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.issuer = issuer;
    this.ttlSeconds = Math.max(1L, ttlMinutes) * 60L;
  }

  public String issue(AuthUser user) {
    Instant now = clock.instant();
    Map<String, Object> header = new LinkedHashMap<>();
    header.put("alg", "HS256");
    header.put("typ", "JWT");

    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("iss", issuer);
    claims.put("sub", user.id().toString());
    claims.put("email", user.email());
    claims.put("name", user.fullName());
    claims.put("role", user.role().databaseValue());
    claims.put("iat", now.getEpochSecond());
    claims.put("exp", now.plusSeconds(ttlSeconds).getEpochSecond());

    String headerPart = encodeJson(header);
    String claimsPart = encodeJson(claims);
    String signingInput = headerPart + "." + claimsPart;
    return signingInput + "." + sign(signingInput);
  }

  public UUID verifySubject(String token) {
    return verifyPrincipal(token).subject();
  }

  public JwtPrincipal verifyPrincipal(String token) {
    try {
      String[] parts = token == null ? new String[0] : token.split("\\.");
      if (parts.length != 3) throw new IllegalArgumentException("Invalid token shape");
      String signingInput = parts[0] + "." + parts[1];
      if (!constantTimeEquals(sign(signingInput), parts[2])) throw new IllegalArgumentException("Invalid token signature");
      Map<String, Object> header = objectMapper.readValue(URL_DECODER.decode(parts[0]), new TypeReference<>() {});
      if (!"HS256".equals(header.get("alg"))) throw new IllegalArgumentException("Invalid token algorithm");
      Map<String, Object> claims = objectMapper.readValue(URL_DECODER.decode(parts[1]), new TypeReference<>() {});
      if (!issuer.equals(claims.get("iss"))) throw new IllegalArgumentException("Invalid issuer");
      long exp = ((Number) claims.get("exp")).longValue();
      if (clock.instant().getEpochSecond() >= exp) throw new IllegalArgumentException("Token expired");
      UUID subject = UUID.fromString(String.valueOf(claims.get("sub")));
      UserRole role = UserRole.fromDatabaseValue(String.valueOf(claims.get("role")));
      return new JwtPrincipal(subject, role);
    } catch (Exception exception) {
      throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid or expired authentication token");
    }
  }

  public record JwtPrincipal(UUID subject, UserRole role) {}

  private String encodeJson(Map<String, Object> value) {
    try {
      return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to encode JWT", exception);
    }
  }

  private String sign(String signingInput) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA256);
      mac.init(new SecretKeySpec(secret, HMAC_SHA256));
      return URL_ENCODER.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to sign JWT", exception);
    }
  }

  private boolean constantTimeEquals(String expected, String actual) {
    if (expected == null || actual == null) return false;
    byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
    byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
    if (expectedBytes.length != actualBytes.length) return false;
    int result = 0;
    for (int index = 0; index < expectedBytes.length; index++) {
      result |= expectedBytes[index] ^ actualBytes[index];
    }
    return result == 0;
  }
}
