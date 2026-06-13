package com.wcpe.tenantcontext.auth;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final AuthUserRepository users;
  private final JwtService jwtService;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  public AuthService(AuthUserRepository users, JwtService jwtService) {
    this(users, jwtService, new BCryptPasswordEncoder());
  }

  AuthService(AuthUserRepository users, JwtService jwtService, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.jwtService = jwtService;
    this.passwordEncoder = passwordEncoder;
  }

  public AuthResult login(AuthRequests.LoginRequest request) {
    String email = normalizeEmail(request.email());
    String password = request.password() == null ? "" : request.password();
    AuthUser user = users.findByEmail(email)
      .filter(AuthUser::enabled)
      .filter(candidate -> passwordEncoder.matches(password, candidate.passwordHash()))
      .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
    return new AuthResult(user, jwtService.issue(user));
  }

  public AuthResult register(AuthRequests.RegisterRequest request) {
    String email = normalizeEmail(request.email());
    String name = requireText(request.name(), "Name is required");
    String password = requireText(request.password(), "Password is required");
    if (password.length() < 8) throw new AuthException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
    UserRole role;
    try {
      role = UserRole.fromDatabaseValue(request.role());
    } catch (IllegalArgumentException exception) {
      throw new AuthException(HttpStatus.BAD_REQUEST, "Unsupported role");
    }
    try {
      AuthUser user = users.saveAndFlush(new AuthUser(email, name, role, passwordEncoder.encode(password)));
      return new AuthResult(user, jwtService.issue(user));
    } catch (DataIntegrityViolationException exception) {
      throw new AuthException(HttpStatus.CONFLICT, "User already exists");
    }
  }

  public AuthUser currentUser(String token) {
    JwtService.JwtPrincipal principal = jwtService.verifyPrincipal(token);
    AuthUser user = users.findById(principal.subject())
      .filter(AuthUser::enabled)
      .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Authenticated user is no longer active"));
    if (user.role() != principal.role()) {
      throw new AuthException(HttpStatus.UNAUTHORIZED, "Authentication token is stale");
    }
    return user;
  }

  private String normalizeEmail(String value) {
    return requireText(value, "Email is required").toLowerCase(Locale.ROOT);
  }

  private String requireText(String value, String message) {
    if (value == null || value.isBlank()) throw new AuthException(HttpStatus.BAD_REQUEST, message);
    return value.trim();
  }

  public record AuthResult(AuthUser user, String token) {}
}
