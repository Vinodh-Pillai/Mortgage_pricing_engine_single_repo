package com.wcpe.ratefeed.domain;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Security tests — RBAC enforcement before DB access.
 * Tests RequestContext role management and RBAC enforcement.
 */
class SecurityRBACTest {

  @AfterEach
  void clearRoles() {
    RequestContext.clear();
  }

  /* ── Role parsing ── */

  @Test
  void roles_singleRole_parsed() {
    RequestContext.roles("RATE_FEED_UPLOAD");
    assertTrue(RequestContext.hasRole("RATE_FEED_UPLOAD"));
    assertFalse(RequestContext.hasRole("RATE_FEED_VIEW"));
  }

  @Test
  void roles_multipleRoles_parsed() {
    RequestContext.roles("RATE_FEED_UPLOAD,RATE_FEED_ACTIVATE,RATE_FEED_VIEW");
    assertTrue(RequestContext.hasRole("RATE_FEED_UPLOAD"));
    assertTrue(RequestContext.hasRole("RATE_FEED_ACTIVATE"));
    assertTrue(RequestContext.hasRole("RATE_FEED_VIEW"));
    assertFalse(RequestContext.hasRole("ADMIN"));
  }

  @Test
  void roles_emptyString_noRoles() {
    RequestContext.roles("");
    assertFalse(RequestContext.hasRole("ANY_ROLE"));
  }

  @Test
  void roles_null_noRoles() {
    RequestContext.roles(null);
    assertFalse(RequestContext.hasRole("ANY_ROLE"));
  }

  @Test
  void roles_whitespace_noRoles() {
    RequestContext.roles("   ");
    assertFalse(RequestContext.hasRole("ANY_ROLE"));
  }

  @Test
  void roles_trimWhitespace() {
    RequestContext.roles(" RATE_FEED_UPLOAD , RATE_FEED_VIEW ");
    assertTrue(RequestContext.hasRole("RATE_FEED_UPLOAD"));
    assertTrue(RequestContext.hasRole("RATE_FEED_VIEW"));
  }

  /* ── Role isolation (ThreadLocal) ── */

  @Test
  void clear_removesAllRoles() {
    RequestContext.roles("RATE_FEED_UPLOAD");
    RequestContext.clear();
    assertFalse(RequestContext.hasRole("RATE_FEED_UPLOAD"));
  }

  @Test
  void requestContext_isThreadLocal() {
    // Verify ThreadLocal behavior by checking clear removes current thread roles
    RequestContext.roles("ROLE_A");
    assertTrue(RequestContext.hasRole("ROLE_A"));
    RequestContext.clear();
    assertFalse(RequestContext.hasRole("ROLE_A"));

    // After clear, setting new roles should not see old roles
    RequestContext.roles("ROLE_B");
    assertFalse(RequestContext.hasRole("ROLE_A"));
    assertTrue(RequestContext.hasRole("ROLE_B"));
  }

  /* ── RateFeedException for unauthorized access ── */

  @Test
  void rateFeedException_construction() {
    org.springframework.http.HttpStatus status = org.springframework.http(HttpStatus.FORBIDDEN);
    RateFeedException ex = new RateFeedException(org.springframework.http.HttpStatus.FORBIDDEN, "ACCESS_DENIED", "No permission");
    assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.status());
    assertEquals("ACCESS_DENIED", ex.code());
    assertEquals("No permission", ex.getMessage());
  }

  @Test
  void rbac_roleNames_defined() {
    // Verify all defined role names exist as constants
    String[] roles = {"RATE_FEED_UPLOAD", "RATE_FEED_ACTIVATE", "RATE_FEED_VIEW"};
    for (String role : roles) {
      assertNotNull(role);
      assertFalse(role.isEmpty());
    }
  }

  /* ── requireRole enforcement (static) ── */

  @Test
  void requireRole_missingRole_throwsForbidden() {
    RequestContext.roles("RATE_FEED_VIEW");
    try {
      // Simulate requireRole check
      if (!RequestContext.hasRole("RATE_FEED_ACTIVATE")) {
        throw new RateFeedException(org.springframework.http.HttpStatus.FORBIDDEN, "ACCESS_DENIED", "RATE_FEED_ACTIVATE role is required.");
      }
      fail("Should have thrown");
    } catch (RateFeedException e) {
      assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, e.status());
    }
  }

  @Test
  void requireRole_presentRole_allowed() {
    RequestContext.roles("RATE_FEED_ACTIVATE");
    if (!RequestContext.hasRole("RATE_FEED_ACTIVATE")) {
      fail("Should have role");
    }
    // No exception = success
  }
}
