package com.wcpe.ratefeed.rbac;

import com.wcpe.ratefeed.domain.*;
import com.wcpe.ratefeed.role.RateFeedRoles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests X-Roles enforcement on POST endpoints.
 * Verifies RATE_FEED_WRITER, RATE_FEED_ADMIN, and RATE_FEED_APPROVER roles.
 */
class RBACAuthorizationTest {

  @AfterEach
  void cleanup() {
    RequestContext.clear();
  }

  @Test
  void writerRoleExists() {
    assertNotNull(RateFeedRoles.RATE_FEED_WRITER);
    assertEquals("RATE_FEED_WRITER", RateFeedRoles.RATE_FEED_WRITER);
  }

  @Test
  void approverRoleExists() {
    assertNotNull(RateFeedRoles.RATE_FEED_APPROVER);
    assertEquals("RATE_FEED_APPROVER", RateFeedRoles.RATE_FEED_APPROVER);
  }

  @Test
  void adminRoleExists() {
    assertNotNull(RateFeedRoles.RATE_FEED_ADMIN);
    assertEquals("RATE_FEED_ADMIN", RateFeedRoles.RATE_FEED_ADMIN);
  }

  @Test
  void writerRoleCanBeValidated() {
    String validated = RateFeedRoles.validateRole(RateFeedRoles.RATE_FEED_WRITER);
    assertEquals(RateFeedRoles.RATE_FEED_WRITER, validated);
  }

  @Test
  void approverRoleCanBeValidated() {
    String validated = RateFeedRoles.validateRole(RateFeedRoles.RATE_FEED_APPROVER);
    assertEquals(RateFeedRoles.RATE_FEED_APPROVER, validated);
  }

  @Test
  void adminRoleCanBeValidated() {
    String validated = RateFeedRoles.validateRole(RateFeedRoles.RATE_FEED_ADMIN);
    assertEquals(RateFeedRoles.RATE_FEED_ADMIN, validated);
  }

  @Test
  void unknownRoleThows() {
    assertThrows(IllegalArgumentException.class,
        () -> RateFeedRoles.validateRole("INVALID_ROLE"));
  }

  @Test
  void requireWriterRole_succeedsWhenPresent() {
    RequestContext.roles("RATE_FEED_WRITER");
    assertDoesNotThrow(() -> RateFeedRoles.require(RateFeedRoles.RATE_FEED_WRITER));
  }

  @Test
  void requireWriterRole_failsWhenAbsent() {
    RequestContext.roles("RATE_FEED_VIEW");
    assertThrows(RateFeedException.class,
        () -> RateFeedRoles.require(RateFeedRoles.RATE_FEED_WRITER));
  }

  @Test
  void requireApproverRole_succeedsWhenPresent() {
    RequestContext.roles("RATE_FEED_APPROVER");
    assertDoesNotThrow(() -> RateFeedRoles.require(RateFeedRoles.RATE_FEED_APPROVER));
  }

  @Test
  void requireApproverRole_failsWhenAbsent() {
    RequestContext.roles("RATE_FEED_VIEW");
    assertThrows(RateFeedException.class,
        () -> RateFeedRoles.require(RateFeedRoles.RATE_FEED_APPROVER));
  }

  @Test
  void adminCanPerformAllActions() {
    RequestContext.roles("RATE_FEED_ADMIN");
    assertDoesNotThrow(() -> RateFeedRoles.require(RateFeedRoles.RATE_FEED_WRITER));
    assertDoesNotThrow(() -> RateFeedRoles.require(RateFeedRoles.RATE_FEED_APPROVER));
  }

  @Test
  void ingestRequiresWriterOrAdmin() {
    // POST /ingest requires RATE_FEED_WRITER or RATE_FEED_ADMIN
    RequestContext.roles("RATE_FEED_WRITER");
    assertDoesNotThrow(() -> RateFeedRoles.require(RateFeedRoles.RATE_FEED_WRITER));

    RequestContext.roles("RATE_FEED_ADMIN, RATE_FEED_WRITER");
    assertDoesNotThrow(() -> RateFeedRoles.require(RateFeedRoles.RATE_FEED_WRITER));

    RequestContext.roles("RATE_FEED_VIEW");
    assertThrows(RateFeedException.class,
        () -> RateFeedRoles.require(RateFeedRoles.RATE_FEED_WRITER));
  }

  @Test
  void activateRequiresApproverOrAdmin() {
    // POST /{id}/activate requires RATE_FEED_APPROVER or RATE_FEED_ADMIN
    RequestContext.roles("RATE_FEED_APPROVER");
    assertDoesNotThrow(() -> RateFeedRoles.require(RateFeedRoles.RATE_FEED_APPROVER));

    RequestContext.roles("RATE_FEED_ADMIN");
    assertDoesNotThrow(() -> RateFeedRoles.require(RateFeedRoles.RATE_FEED_APPROVER));

    RequestContext.roles("RATE_FEED_UPLOAD");
    assertThrows(RateFeedException.class,
        () -> RateFeedRoles.require(RateFeedRoles.RATE_FEED_APPROVER));
  }

  @Test
  void allRolesAreInAllRolesSet() {
    var allRoles = RateFeedRoles.allRoles();
    assertTrue(allRoles.contains(RateFeedRoles.RATE_FEED_UPLOAD));
    assertTrue(allRoles.contains(RateFeedRoles.RATE_FEED_ACTIVATE));
    assertTrue(allRoles.contains(RateFeedRoles.RATE_FEED_VIEW));
    assertTrue(allRoles.contains(RateFeedRoles.RATE_FEED_WRITER));
    assertTrue(allRoles.contains(RateFeedRoles.RATE_FEED_APPROVER));
    assertTrue(allRoles.contains(RateFeedRoles.RATE_FEED_ADMIN));
  }
}
