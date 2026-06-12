package com.wcpe.tenantcontext.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class AuthUser {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 320)
  private String email;

  @Column(name = "full_name", nullable = false, length = 160)
  private String fullName;

  @Column(nullable = false, length = 64)
  private UserRole role;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "created_at", insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private OffsetDateTime updatedAt;

  protected AuthUser() {}

  public AuthUser(String email, String fullName, UserRole role, String passwordHash) {
    this.email = email;
    this.fullName = fullName;
    this.role = role;
    this.passwordHash = passwordHash;
    this.enabled = true;
  }

  public UUID id() {
    return id;
  }

  public String email() {
    return email;
  }

  public String fullName() {
    return fullName;
  }

  public UserRole role() {
    return role;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public boolean enabled() {
    return enabled;
  }

  public OffsetDateTime createdAt() {
    return createdAt;
  }

  public OffsetDateTime updatedAt() {
    return updatedAt;
  }

  public AuthResponses.AuthUserResponse toResponse() {
    return new AuthResponses.AuthUserResponse(id.toString(), email, fullName, role.databaseValue());
  }
}
