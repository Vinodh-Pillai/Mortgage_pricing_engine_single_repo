package com.wcpe.tenantcontext.auth;

public final class AuthResponses {
  private AuthResponses() {}

  public record AuthUserResponse(String id, String email, String name, String role) {}

  public record AuthResponse(AuthUserResponse user) {}

  public record ErrorResponse(String error) {}
}
