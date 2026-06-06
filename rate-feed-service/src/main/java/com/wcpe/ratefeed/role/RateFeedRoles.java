package com.wcpe.ratefeed.role;

import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import org.springframework.http.HttpStatus;

import java.util.Set;
import java.util.Locale;

/**
 * D-007 fix: Centralized, canonical RBAC role names.
 *
 * Previously, role names were hardcoded strings scattered throughout
 * RateFeedController, ActivationService, and RequestContext. A typo
 * (e.g. "RATE_FEED_UPLOAD_") would silently grant no role, or worse,
 * bypass the check entirely.
 *
 * This enum is the single source of truth. All RBAC checks go through
 * this class. If a role check fails, it throws a FORBIDDEN.
 */
public final class RateFeedRoles {

  private RateFeedRoles() {}

  // ── Canonical role names ─────────────────────────────────────────────
  public static final String RATE_FEED_UPLOAD   = "RATE_FEED_UPLOAD";
  public static final String RATE_FEED_PARSE    = "RATE_FEED_PARSE";
  public static final String RATE_FEED_NORMALIZE = "RATE_FEED_NORMALIZE";
  public static final String RATE_FEED_ACTIVATE = "RATE_FEED_ACTIVATE";
  public static final String RATE_FEED_VIEW     = "RATE_FEED_VIEW";
  public static final String RATE_FEED_WRITER   = "RATE_FEED_WRITER";
  public static final String RATE_FEED_APPROVER = "RATE_FEED_APPROVER";
  public static final String RATE_FEED_ADMIN    = "RATE_FEED_ADMIN";
  public static final String RATE_FEED_VALIDATE = "RATE_FEED_VALIDATE";
  public static final String RATE_FEED_OCR_REVIEW = "RATE_FEED_OCR_REVIEW";
  public static final String RATE_FEED_OPERATIONS = "RATE_FEED_OPERATIONS";
  public static final String RATE_FEED_AUDIT_VIEW = "RATE_FEED_AUDIT_VIEW";
  public static final String RATE_FEED_AUDIT_EXPORT = "RATE_FEED_AUDIT_EXPORT";

  private static final Set<String> ALL_ROLES = Set.of(
      RATE_FEED_UPLOAD,
      RATE_FEED_PARSE,
      RATE_FEED_NORMALIZE,
      RATE_FEED_ACTIVATE,
      RATE_FEED_VIEW,
      RATE_FEED_WRITER,
      RATE_FEED_APPROVER,
      RATE_FEED_ADMIN,
      RATE_FEED_VALIDATE,
      RATE_FEED_OCR_REVIEW,
      RATE_FEED_OPERATIONS,
      RATE_FEED_AUDIT_VIEW,
      RATE_FEED_AUDIT_EXPORT
  );

  /**
   * Validate that the requested role is a known role name.
   * This prevents typos and ensures only registered roles are checked.
   */
  public static String validateRole(String role) {
    String normalized = role != null ? role.toUpperCase(Locale.ROOT) : null;
    if (normalized == null || !ALL_ROLES.contains(normalized)) {
      throw new IllegalArgumentException("Unknown role: " + role + ". Must be one of: " + ALL_ROLES);
    }
    return normalized;
  }

  /** Get all known role names for introspection, configuration validation, etc. */
  public static Set<String> allRoles() {
    return Set.copyOf(ALL_ROLES);
  }

  /**
   * Check if the current request context has the given role.
   * Delegates to RequestContext but validates the role name first.
   */
  public static boolean hasRole(String role) {
    return com.wcpe.ratefeed.domain.RequestContext.hasRole(validateRole(role));
  }

  /**
   * Require the current request context to have the given role.
   * Throws FORBIDDEN if not.
   */
  public static void require(String role) {
    if (!hasRole(role)) {
      throw new RateFeedException(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
          role + " role is required.");
    }
  }
}
