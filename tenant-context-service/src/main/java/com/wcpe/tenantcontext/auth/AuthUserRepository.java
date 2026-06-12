package com.wcpe.tenantcontext.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {
  Optional<AuthUser> findByEmail(String email);
}
