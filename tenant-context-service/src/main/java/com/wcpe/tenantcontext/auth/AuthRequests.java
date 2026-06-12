package com.wcpe.tenantcontext.auth;

public final class AuthRequests {
  private AuthRequests() {}

  public record LoginRequest(String email, String password) {}

  public record RegisterRequest(String email, String password, String name, String role) {}
}
