package com.wcpe.catalog.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

record ProductOverlay(UUID overlayId, UUID tenantId, String productCode, String attribute, String overrideValue, LocalDate effectiveDate, LocalDate expiryDate, String reason, String status, String createdBy, Instant createdAt, Instant updatedAt) {}
record OverlayCreateRequest(String productCode, String attribute, String overrideValue, LocalDate effectiveDate, LocalDate expiryDate, String reason, String idempotencyKey) {}
record OverlayResolveRequest(String productCode, String investorCode, String channelCode, LocalDate asOfDate) {}
record AppliedOverlay(UUID overlayId, String attribute, String originalValue, String overrideValue, LocalDate effectiveDate, String reason) {}
record OverlayResolveResponse(String productCode, String investorCode, String channelCode, Map<String,String> baseAttributes, java.util.List<AppliedOverlay> appliedOverlays, Map<String,String> resolvedAttributes, Instant resolvedAt) {}
record OverlayCreateResponse(UUID overlayId, String productCode, String attribute, String overrideValue, LocalDate effectiveDate, LocalDate expiryDate, String status, String createdBy, Instant createdAt) {}

record VersionDiff(UUID diffId, Integer versionA, Integer versionB, String artifactType, String artifactCode, String diffType, String attribute, String oldValue, String newValue) {}
record DiffResponse(Map<String,Object> versionAMeta, Map<String,Object> versionBMeta, java.util.List<VersionDiff> diffs, int diffCount, Instant computedAt) {}
