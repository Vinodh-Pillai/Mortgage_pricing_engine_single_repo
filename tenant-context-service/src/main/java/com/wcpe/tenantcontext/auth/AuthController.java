package com.wcpe.tenantcontext.auth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;
  private final String cookieName;
  private final boolean cookieSecure;
  private final String cookieSameSite;
  private final Duration cookieTtl;

  public AuthController(
    AuthService authService,
    @Value("${auth.cookie.name}") String cookieName,
    @Value("${auth.cookie.secure}") boolean cookieSecure,
    @Value("${auth.cookie.same-site}") String cookieSameSite,
    @Value("${auth.jwt.ttl-minutes}") long ttlMinutes
  ) {
    this.authService = authService;
    this.cookieName = cookieName;
    this.cookieSecure = cookieSecure;
    this.cookieSameSite = cookieSameSite;
    this.cookieTtl = Duration.ofMinutes(Math.max(1L, ttlMinutes));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponses.AuthResponse> login(@RequestBody AuthRequests.LoginRequest request) {
    return withAuthCookie(authService.login(request));
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponses.AuthResponse> register(@RequestBody AuthRequests.RegisterRequest request) {
    return withAuthCookie(authService.register(request));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    ResponseCookie cookie = baseCookie("").maxAge(Duration.ZERO).build();
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
  }

  @GetMapping("/me")
  public AuthResponses.AuthResponse me(
    @CookieValue(name = "${auth.cookie.name}", required = false) String cookieToken,
    @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    String token = resolveToken(cookieToken, authorization);
    return new AuthResponses.AuthResponse(authService.currentUser(token).toResponse());
  }

  @ExceptionHandler(AuthException.class)
  public ResponseEntity<AuthResponses.ErrorResponse> handleAuth(AuthException exception) {
    return ResponseEntity.status(exception.status()).body(new AuthResponses.ErrorResponse(exception.getMessage()));
  }

  private ResponseEntity<AuthResponses.AuthResponse> withAuthCookie(AuthService.AuthResult result) {
    ResponseCookie cookie = baseCookie(result.token()).maxAge(cookieTtl).build();
    return ResponseEntity.ok()
      .header(HttpHeaders.SET_COOKIE, cookie.toString())
      .body(new AuthResponses.AuthResponse(result.user().toResponse()));
  }

  private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
    return ResponseCookie.from(cookieName, value)
      .httpOnly(true)
      .secure(cookieSecure)
      .sameSite(cookieSameSite)
      .path("/");
  }

  private String resolveToken(String cookieToken, String authorization) {
    if (cookieToken != null && !cookieToken.isBlank()) return cookieToken;
    if (authorization != null && authorization.startsWith("Bearer ")) return authorization.substring("Bearer ".length()).trim();
    throw new AuthException(HttpStatus.UNAUTHORIZED, "Authentication is required");
  }
}
