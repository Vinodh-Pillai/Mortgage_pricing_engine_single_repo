package com.wcpe.scenario.domain;

import java.time.*;
import java.util.*;

// S08: Channel Submission Profile domain records

enum ProfileStatus { DRAFT, APPROVED, PUBLISHED, RETIRED }

enum FieldSeverity { BLOCKING, WARNING, INFO }

record SubmissionProfileFieldRule(String section, String fieldPath, String requiredWhenExpression,
    FieldSeverity severity, String message, String remediationHint) {}

record CreateSubmissionProfileRequest(String channel, String quoteIntent, String profileName, Instant effectiveFromUtc, Instant effectiveToUtc,
    List<SubmissionProfileFieldRule> rules) {}

record PublishSubmissionProfileRequest(UUID profileId, Instant effectiveFromUtc, Instant effectiveToUtc,
    String approvalToken, String changeSetRef, Instant publishedAt) {}

record SubmissionProfile(UUID profileId, UUID tenantId, String channel, String quoteIntent, String profileName,
    List<SubmissionProfileVersion> versions) {}

record SubmissionProfileVersion(UUID versionId, UUID submissionProfileId, int versionNumber,
    ProfileStatus status, Instant effectiveFromUtc, Instant effectiveToUtc, String checksum,
    List<SubmissionProfileFieldRule> rules, Instant createdAtUtc) {}

record SubmissionProfileResponse(UUID profileId, UUID versionId, ProfileStatus status, String channel, String quoteIntent,
    String profileName, int versionNumber, Instant effectiveFromUtc, Instant effectiveToUtc, String checksum,
    List<SubmissionProfileFieldRule> rules, List<ValidationIssue> validationIssues, Instant createdAtUtc) {}

record ActiveChannelProfile(String channel, String quoteIntent, UUID versionId, int versionNumber, String checksum,
    List<SubmissionProfileFieldRule> rules, Instant effectiveFromUtc) {}
