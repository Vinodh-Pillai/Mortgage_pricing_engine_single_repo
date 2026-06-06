package com.wcpe.ratefeed.domain;

import java.util.*;

public final class RequestContext {
  private static final ThreadLocal<Set<String>> ROLES = ThreadLocal.withInitial(Set::of);

  private RequestContext() {}

  public static void roles(String roles) {
    if (roles == null || roles.isBlank()) {
      ROLES.set(Set.of());
      return;
    }
    Set<String> parsed = new HashSet<>();
    for (String role : roles.split(",")) {
      String normalized = role.trim().toUpperCase(Locale.ROOT);
      if (!normalized.isBlank()) parsed.add(normalized);
    }
    ROLES.set(parsed);
  }

  public static boolean hasRole(String role) { return ROLES.get().contains(role); }
  public static void clear() { ROLES.remove(); }
}
