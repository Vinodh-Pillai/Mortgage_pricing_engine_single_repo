package com.wcpe.pricingbff.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.HttpCookie;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Controller
class AuthUiController {
  private static final String SYNTHETIC_SESSION_COOKIE = "LW_SYNTH_AUTH";
  private static final String PUBLIC_SYNTHETIC_PASSWORD = "Synthetic-Only-Password!";
  private static final String SYNTHETIC_DOMAIN = "wcpe.synthetic.invalid";
  private static final Set<String> SYNTHETIC_ROLES = Set.of("admin", "pricing_analyst", "loan_officer", "borrower");

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String tenantContextBaseUrl;
  private final boolean syntheticAuthEnabled;
  private final Map<String, SyntheticUser> syntheticUsers = new ConcurrentHashMap<>();
  private final Map<String, SyntheticUser> syntheticSessions = new ConcurrentHashMap<>();

  AuthUiController(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      @Value("${loanweft.integrations.tenant-context-service.base-url:${TENANT_CONTEXT_URL:}}") String tenantContextBaseUrl,
      @Value("${loanweft.dev.synthetic-auth.enabled:${LOANWEFT_DEV_SYNTHETIC_AUTH_ENABLED:false}}") boolean syntheticAuthEnabled) {
    this.restClient = restClientBuilder.build();
    this.objectMapper = objectMapper;
    this.tenantContextBaseUrl = tenantContextBaseUrl == null ? "" : tenantContextBaseUrl.trim().replaceAll("/$", "");
    this.syntheticAuthEnabled = syntheticAuthEnabled;
    if (syntheticAuthEnabled) seedSyntheticUsers();
  }

  @PostMapping("/api/auth/login")
  ResponseEntity<String> login(@RequestBody String body) {
    if (syntheticAuthEnabled) {
      SyntheticUser user = syntheticLogin(body);
      if (user != null) return syntheticLoginResponse(user);
      if (isSyntheticCredentialAttempt(body)) return syntheticUnauthorized();
    }
    return forwardWithBody("/api/auth/login", body, HttpHeaders.EMPTY);
  }

  @PostMapping("/api/auth/register")
  ResponseEntity<String> register(@RequestBody String body) {
    if (syntheticAuthEnabled) {
      ResponseEntity<String> syntheticRegistration = registerSyntheticUser(body);
      if (syntheticRegistration != null) return syntheticRegistration;
    }
    return forwardWithBody("/api/auth/register", body, HttpHeaders.EMPTY);
  }

  @PostMapping("/api/auth/logout")
  ResponseEntity<String> logout(@RequestHeader HttpHeaders headers) {
    if (syntheticAuthEnabled) {
      String token = syntheticSessionToken(headers);
      if (token != null) {
        syntheticSessions.remove(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expiredSyntheticCookie()).build();
      }
    }
    return forwardWithoutBody("/api/auth/logout", headers);
  }

  @GetMapping("/api/auth/me")
  ResponseEntity<String> me(@RequestHeader HttpHeaders headers) {
    if (syntheticAuthEnabled) {
      String token = syntheticSessionToken(headers);
      SyntheticUser user = token == null ? null : syntheticSessions.get(token);
      if (user != null) return syntheticUserResponse(user);
    }
    if (!hasAuthCredentials(headers)) return unauthenticatedContract();
    return forwardWithoutBody("/api/auth/me", headers);
  }

  @GetMapping("/api/user/profile")
  ResponseEntity<String> userProfile(@RequestHeader HttpHeaders headers) {
    if (!hasAuthCredentials(headers)) return unauthenticatedContract();
    return accountContractRequired("profile");
  }

  @GetMapping("/api/user/settings")
  ResponseEntity<String> userSettings(@RequestHeader HttpHeaders headers) {
    if (!hasAuthCredentials(headers)) return unauthenticatedContract();
    return accountContractRequired("settings");
  }

  private void seedSyntheticUsers() {
    putSyntheticUser("admin@" + SYNTHETIC_DOMAIN, "Synthetic Admin User", "admin", PUBLIC_SYNTHETIC_PASSWORD);
    putSyntheticUser("sarah.mitchell@" + SYNTHETIC_DOMAIN, "Sarah Mitchell", "loan_officer", PUBLIC_SYNTHETIC_PASSWORD);
    putSyntheticUser("loan-officer@" + SYNTHETIC_DOMAIN, "Synthetic Loan Officer", "loan_officer", PUBLIC_SYNTHETIC_PASSWORD);
    putSyntheticUser("pricing-analyst@" + SYNTHETIC_DOMAIN, "Synthetic Pricing Analyst", "pricing_analyst", PUBLIC_SYNTHETIC_PASSWORD);
    putSyntheticUser("borrower@" + SYNTHETIC_DOMAIN, "Synthetic Borrower", "borrower", PUBLIC_SYNTHETIC_PASSWORD);
  }

  private void putSyntheticUser(String email, String fullName, String role, String password) {
    SyntheticUser user = new SyntheticUser(
        "synthetic-" + email.substring(0, email.indexOf('@')).replaceAll("[^a-z0-9-]", "-"),
        normalizeEmail(email), fullName, role, password);
    syntheticUsers.put(user.email(), user);
  }

  private SyntheticUser syntheticLogin(String body) {
    JsonNode json = readJson(body);
    if (json == null) return null;
    String email = normalizeEmail(text(json, "email"));
    String password = text(json, "password");
    SyntheticUser user = syntheticUsers.get(email);
    if (user == null || !user.password().equals(password)) return null;
    return user;
  }

  private ResponseEntity<String> registerSyntheticUser(String body) {
    JsonNode json = readJson(body);
    if (json == null) return badSyntheticRequest("Synthetic auth registration requires a JSON body");
    String email = normalizeEmail(text(json, "email"));
    String password = text(json, "password");
    String fullName = text(json, "fullName");
    String role = normalizeRole(text(json, "role"));
    if (!email.endsWith("@" + SYNTHETIC_DOMAIN)) return null;
    if (password.isBlank() || !password.startsWith("Synthetic-") || !password.endsWith("-Only!")) {
      return badSyntheticRequest("Synthetic auth registration accepts only generated non-production persona passwords");
    }
    if (fullName.isBlank() || !SYNTHETIC_ROLES.contains(role)) {
      return badSyntheticRequest("Synthetic auth registration requires fullName and a supported synthetic role");
    }
    SyntheticUser user = new SyntheticUser(
        "synthetic-" + Integer.toUnsignedString(email.hashCode(), 36), email, fullName, role, password);
    SyntheticUser existing = syntheticUsers.putIfAbsent(email, user);
    if (existing != null) return ResponseEntity.status(409).contentType(MediaType.APPLICATION_JSON).body(userBody(existing));
    return ResponseEntity.status(201).contentType(MediaType.APPLICATION_JSON).body(userBody(user));
  }

  private ResponseEntity<String> syntheticLoginResponse(SyntheticUser user) {
    String token = UUID.randomUUID().toString();
    syntheticSessions.put(token, user);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.SET_COOKIE, syntheticCookie(token))
        .body(userBody(user));
  }

  private ResponseEntity<String> syntheticUserResponse(SyntheticUser user) {
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(userBody(user));
  }

  private ResponseEntity<String> syntheticUnauthorized() {
    return ResponseEntity.status(401)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"error\":\"Invalid synthetic persona credentials\"}");
  }

  private ResponseEntity<String> badSyntheticRequest(String message) {
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"error\":\"" + message + "\"}");
  }

  private boolean isSyntheticCredentialAttempt(String body) {
    JsonNode json = readJson(body);
    if (json == null) return false;
    return normalizeEmail(text(json, "email")).endsWith("@" + SYNTHETIC_DOMAIN);
  }

  private String userBody(SyntheticUser user) {
    try {
      return objectMapper.writeValueAsString(Map.of("user", Map.of(
          "id", user.id(),
          "email", user.email(),
          "fullName", user.fullName(),
          "role", user.role(),
          "authProvider", "synthetic-dev",
          "synthetic", true)));
    } catch (JsonProcessingException exception) {
      return "{\"user\":{\"id\":\"" + user.id() + "\",\"email\":\"" + user.email() + "\",\"fullName\":\"" + user.fullName() + "\",\"role\":\"" + user.role() + "\"}}";
    }
  }

  private JsonNode readJson(String body) {
    try {
      return objectMapper.readTree(body == null ? "{}" : body);
    } catch (JsonProcessingException exception) {
      return null;
    }
  }

  private String text(JsonNode json, String field) {
    JsonNode value = json.get(field);
    return value == null || value.isNull() ? "" : value.asText("").trim();
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizeRole(String role) {
    return role == null ? "" : role.trim().toLowerCase(Locale.ROOT).replace('-', '_');
  }

  private String syntheticCookie(String token) {
    return ResponseCookie.from(SYNTHETIC_SESSION_COOKIE, token)
        .httpOnly(true)
        .secure(false)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ofHours(8))
        .build()
        .toString();
  }

  private String expiredSyntheticCookie() {
    return ResponseCookie.from(SYNTHETIC_SESSION_COOKIE, "")
        .httpOnly(true)
        .secure(false)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ZERO)
        .build()
        .toString();
  }

  private String syntheticSessionToken(HttpHeaders headers) {
    if (!headers.containsKey(HttpHeaders.COOKIE)) return null;
    for (String cookieHeader : headers.get(HttpHeaders.COOKIE)) {
      for (HttpCookie cookie : HttpCookie.parse(cookieHeader)) {
        if (SYNTHETIC_SESSION_COOKIE.equals(cookie.getName())) return cookie.getValue();
      }
    }
    return null;
  }

  private ResponseEntity<String> forwardWithBody(String path, String body, HttpHeaders headers) {
    if (tenantContextBaseUrl.isBlank()) return missingTenantContextContract();
    try {
      ResponseEntity<String> response = restClient.post()
          .uri(URI.create(tenantContextBaseUrl + path))
          .contentType(MediaType.APPLICATION_JSON)
          .headers(outbound -> copyAuthHeaders(headers, outbound))
          .body(body)
          .retrieve()
          .toEntity(String.class);
      return copyResponse(response);
    } catch (RestClientResponseException exception) {
      return copyErrorResponse(exception);
    }
  }

  private ResponseEntity<String> forwardWithoutBody(String path, HttpHeaders headers) {
    if (tenantContextBaseUrl.isBlank()) return missingTenantContextContract();
    try {
      ResponseEntity<String> response;
      if (path.endsWith("/logout")) {
        response = restClient.post()
            .uri(URI.create(tenantContextBaseUrl + path))
            .headers(outbound -> copyAuthHeaders(headers, outbound))
            .retrieve()
            .toEntity(String.class);
      } else {
        response = restClient.get()
            .uri(URI.create(tenantContextBaseUrl + path))
            .headers(outbound -> copyAuthHeaders(headers, outbound))
            .retrieve()
            .toEntity(String.class);
      }
      return copyResponse(response);
    } catch (RestClientResponseException exception) {
      return copyErrorResponse(exception);
    }
  }

  private boolean hasAuthCredentials(HttpHeaders headers) {
    return headers.containsKey(HttpHeaders.COOKIE) || headers.containsKey(HttpHeaders.AUTHORIZATION);
  }

  private ResponseEntity<String> unauthenticatedContract() {
    return ResponseEntity.status(401)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"error\":\"Authentication credentials are required\"}");
  }

  private ResponseEntity<String> missingTenantContextContract() {
    return ResponseEntity.status(503)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"error\":\"Tenant-context authentication contract is not configured for the BFF\"}");
  }

  private ResponseEntity<String> accountContractRequired(String surface) {
    String contractCode = "settings".equals(surface) ? "ACCOUNT_SETTINGS_CONTRACT_REQUIRED" : "ACCOUNT_PROFILE_CONTRACT_REQUIRED";
    String surfaceLabel = "settings".equals(surface) ? "settings" : "profile";
    String verb = "settings".equals(surface) ? "require" : "requires";
    return ResponseEntity.status(503)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"status\":\"BLOCKED\",\"code\":\"" + contractCode + "\"," 
            + "\"surface\":\"" + surface + "\"," 
            + "\"message\":\"User " + surfaceLabel + " " + verb + " a configured account-management contract; pricing-bff did not return synthetic account fallback data.\"," 
            + "\"dependencyStatus\":\"ACCOUNT_MANAGEMENT_CONTRACT_NOT_CONFIGURED\"," 
            + "\"fakePersistence\":false}");
  }

  private void copyAuthHeaders(HttpHeaders inbound, HttpHeaders outbound) {
    if (inbound.containsKey(HttpHeaders.COOKIE)) outbound.put(HttpHeaders.COOKIE, inbound.get(HttpHeaders.COOKIE));
    if (inbound.containsKey(HttpHeaders.AUTHORIZATION)) outbound.put(HttpHeaders.AUTHORIZATION, inbound.get(HttpHeaders.AUTHORIZATION));
  }

  private ResponseEntity<String> copyResponse(ResponseEntity<String> response) {
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.getStatusCode());
    copyResponseHeaders(response.getHeaders(), builder);
    return builder.body(response.getBody());
  }

  private ResponseEntity<String> copyErrorResponse(RestClientResponseException exception) {
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(exception.getStatusCode());
    copyResponseHeaders(exception.getResponseHeaders(), builder);
    return builder.body(exception.getResponseBodyAsString());
  }

  private void copyResponseHeaders(HttpHeaders source, ResponseEntity.BodyBuilder builder) {
    if (source == null) return;
    if (source.containsKey(HttpHeaders.SET_COOKIE)) {
      source.get(HttpHeaders.SET_COOKIE).forEach(cookie -> builder.header(HttpHeaders.SET_COOKIE, cookie));
    }
    MediaType contentType = source.getContentType();
    if (contentType != null) builder.contentType(contentType);
  }

  private record SyntheticUser(String id, String email, String fullName, String role, String password) {}
}
