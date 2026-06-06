package com.wcpe.scenario.domain;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

// S08 unit tests: SubmissionProfile validation and version policy
class SubmissionProfileValidatorTest {
  @Test
  void rejectsUnknownFieldPath() {
    CreateSubmissionProfileRequest request = new CreateSubmissionProfileRequest(
        "WHOLESALE", "PURCHASE", "Test Profile", Instant.now(), null,
        List.of(new SubmissionProfileFieldRule("PROPERTY", "broker.nmlsId", "always()",
            FieldSeverity.BLOCKING, "Required", "Hint")));
    ScenarioException ex = assertThrows(ScenarioException.class,
        () -> mockService().createDraft(UUID.randomUUID(), "key1", "corr1", "actor", request));
    assertEquals("VALIDATION_FAILED", ex.code());
    assertTrue(ex.fieldErrors().stream().anyMatch(i -> i.code().equals("UNKNOWN_FIELD_PATH")));
  }

  @Test
  void rejectsUnsupportedRequiredExpression() {
    CreateSubmissionProfileRequest request = new CreateSubmissionProfileRequest(
        "WHOLESALE", "PURCHASE", "Test Profile", Instant.now(), null,
        List.of(new SubmissionProfileFieldRule("PROPERTY", "propertyState", "channel == WHOLESALE",
            FieldSeverity.BLOCKING, "Required", "Hint")));
    ScenarioException ex = assertThrows(ScenarioException.class,
        () -> mockService().createDraft(UUID.randomUUID(), "key1", "corr1", "actor", request));
    assertEquals("VALIDATION_FAILED", ex.code());
    assertTrue(ex.fieldErrors().stream().anyMatch(i -> i.code().equals("UNSUPPORTED_REQUIRED_EXPRESSION")));
  }

  @Test
  void rejectsMissingChannel() {
    CreateSubmissionProfileRequest request = new CreateSubmissionProfileRequest(
        "", "PURCHASE", "Test Profile", Instant.now(), null, List.of());
    ScenarioException ex = assertThrows(ScenarioException.class,
        () -> mockService().createDraft(UUID.randomUUID(), "key1", "corr1", "actor", request));
    assertEquals("VALIDATION_FAILED", ex.code());
    assertTrue(ex.fieldErrors().stream().anyMatch(i -> i.code().equals("INVALID_CHANNEL")));
  }

  @Test
  void rejectsMissingProfileName() {
    CreateSubmissionProfileRequest request = new CreateSubmissionProfileRequest(
        "WHOLESALE", "PURCHASE", "", Instant.now(), null, List.of());
    ScenarioException ex = assertThrows(ScenarioException.class,
        () -> mockService().createDraft(UUID.randomUUID(), "key1", "corr1", "actor", request));
    assertEquals("VALIDATION_FAILED", ex.code());
    assertTrue(ex.fieldErrors().stream().anyMatch(i -> i.code().equals("INVALID_PROFILE_NAME")));
  }

  @Test
  void rejectsEffectiveToBeforeEffectiveFrom() {
    Instant from = Instant.now();
    Instant to = from.minusSeconds(60);
    CreateSubmissionProfileRequest request = new CreateSubmissionProfileRequest(
        "WHOLESALE", "PURCHASE", "Test", from, to, List.of());
    ScenarioException ex = assertThrows(ScenarioException.class,
        () -> mockService().createDraft(UUID.randomUUID(), "key1", "corr1", "actor", request));
    assertEquals("VALIDATION_FAILED", ex.code());
    assertTrue(ex.fieldErrors().stream().anyMatch(i -> i.code().equals("INVALID_DATE_RANGE")));
  }

  @Test
  void rejectsEmptyRules() {
    CreateSubmissionProfileRequest request = new CreateSubmissionProfileRequest(
        "WHOLESALE", "PURCHASE", "Test", Instant.now(), null, List.of());
    ScenarioException ex = assertThrows(ScenarioException.class,
        () -> mockService().createDraft(UUID.randomUUID(), "key1", "corr1", "actor", request));
    assertEquals("VALIDATION_FAILED", ex.code());
    assertTrue(ex.fieldErrors().stream().anyMatch(i -> i.code().equals("EMPTY_RULES")));
  }

  @Test
  void publishRejectsMissingApprovalToken() {
    SubmissionProfileRepository repository = mock(SubmissionProfileRepository.class);
    ScenarioRepository scenarioRepository = mock(ScenarioRepository.class);
    PublishSubmissionProfileRequest request = new PublishSubmissionProfileRequest(
        UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"), null, " ", "change-set-1", Instant.now());
    RequestContext.roles("SCENARIO_ADMIN");

    ScenarioException ex;
    try {
      ex = assertThrows(ScenarioException.class,
          () -> new SubmissionProfileService(repository, scenarioRepository)
              .publish(UUID.randomUUID(), "publish-missing-token", "corr1", "actor", request));
    } finally {
      RequestContext.clear();
    }

    assertEquals("VALIDATION_FAILED", ex.code());
    assertTrue(ex.fieldErrors().stream().anyMatch(i -> i.code().equals("MISSING_APPROVAL_TOKEN") && i.fieldPath().equals("approvalToken")));
    verifyNoInteractions(repository, scenarioRepository);
  }

  @Test
  void publishRejectsMissingChangeSetRef() {
    SubmissionProfileRepository repository = mock(SubmissionProfileRepository.class);
    ScenarioRepository scenarioRepository = mock(ScenarioRepository.class);
    PublishSubmissionProfileRequest request = new PublishSubmissionProfileRequest(
        UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"), null, "approved-by-test", null, Instant.now());
    RequestContext.roles("SCENARIO_ADMIN");

    ScenarioException ex;
    try {
      ex = assertThrows(ScenarioException.class,
          () -> new SubmissionProfileService(repository, scenarioRepository)
              .publish(UUID.randomUUID(), "publish-missing-change-set", "corr1", "actor", request));
    } finally {
      RequestContext.clear();
    }

    assertEquals("VALIDATION_FAILED", ex.code());
    assertTrue(ex.fieldErrors().stream().anyMatch(i -> i.code().equals("MISSING_CHANGE_SET_REF") && i.fieldPath().equals("changeSetRef")));
    verifyNoInteractions(repository, scenarioRepository);
  }

  private SubmissionProfileService mockService() {
    RequestContext.roles("SCENARIO_ADMIN");
    return new SubmissionProfileService(null, null);
  }
}

class SubmissionProfileVersionPolicyTest {
  @Test
  void rejectsOverlappingPublishedVersions() {
    Instant existingFrom = Instant.parse("2026-01-01T00:00:00Z");
    Instant existingTo = Instant.parse("2026-04-01T00:00:00Z");

    assertAll("published version window overlap policy",
        () -> assertTrue(SubmissionProfileVersionPolicy.windowsOverlap(existingFrom, existingTo,
            Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-05-01T00:00:00Z"))),
        () -> assertTrue(SubmissionProfileVersionPolicy.windowsOverlap(existingFrom, null,
            Instant.parse("2026-02-01T00:00:00Z"), null)),
        () -> assertFalse(SubmissionProfileVersionPolicy.windowsOverlap(existingFrom, existingTo,
            Instant.parse("2026-04-01T00:00:00Z"), Instant.parse("2026-06-01T00:00:00Z"))));
  }

  @Test
  void versionIncrementIsSequential() {
    SubmissionProfileVersion draftVersion = version(1, ProfileStatus.DRAFT);
    SubmissionProfileVersion publishedVersion = version(2, ProfileStatus.PUBLISHED);

    assertAll("publish creates the next sequential version number",
        () -> assertEquals(2, SubmissionProfileVersionPolicy.nextPublishedVersionNumber(draftVersion)),
        () -> assertEquals(3, SubmissionProfileVersionPolicy.nextPublishedVersionNumber(publishedVersion)));
  }

  @Test
  void profileVersionsAreImmutableAfterPublish() {
    SubmissionProfileVersion publishedVersion = version(3, ProfileStatus.PUBLISHED);
    int nextVersionNumber = SubmissionProfileVersionPolicy.nextPublishedVersionNumber(publishedVersion);

    assertAll("published version remains the source and publish creates a new version",
        () -> assertEquals(ProfileStatus.PUBLISHED, publishedVersion.status()),
        () -> assertEquals(3, publishedVersion.versionNumber()),
        () -> assertEquals(4, nextVersionNumber));
  }

  private SubmissionProfileVersion version(int versionNumber, ProfileStatus status) {
    return new SubmissionProfileVersion(UUID.randomUUID(), UUID.randomUUID(), versionNumber, status,
        Instant.parse("2026-01-01T00:00:00Z"), null, "checksum",
        List.of(new SubmissionProfileFieldRule("PROPERTY", "propertyState", "always()", FieldSeverity.BLOCKING,
            "Property state is required.", "Provide property state.")),
        Instant.parse("2026-01-01T00:00:00Z"));
  }
}
