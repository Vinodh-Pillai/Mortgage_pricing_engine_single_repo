package com.wcpe.catalog.domain;

import java.time.*;
import java.util.*;

enum CatalogStatus { DRAFT, VALIDATED, PENDING_APPROVAL, APPROVED, PUBLISHED, SUSPENDED, RETIRED, REJECTED, ROLLED_BACK }

record ProductRequest(String productCode, String productName, String productFamily, List<String> allowedChannels, List<String> allowedStates, LocalDate effectiveFrom, LocalDate effectiveTo) {}
record InvestorRequest(String investorCode, String investorName, List<String> channels, List<String> productCodes, LocalDate effectiveFrom, LocalDate effectiveTo) {}
record ReferenceCatalogRequest(String code, String label, String category, Map<String, Object> attributes, LocalDate effectiveFrom, LocalDate effectiveTo) {}
record MarketRequest(String stateCode, String countyFips, String countyName, String marketStatus, List<String> allowedChannels, LocalDate effectiveFrom, LocalDate effectiveTo) {}
record PublishCatalogRequest(String reason, LocalDate effectiveDate) {}
record LifecycleActionRequest(String reason) {}
record VersionedLifecycleActionRequest(String reason, Integer expectedVersion) {}
record CatalogVersionControlRecord(UUID versionControlId, UUID catalogId, String artifactType, UUID artifactId, String artifactCode, int versionNumber, CatalogStatus status, String configHash, long rowVersion) {}
record RejectCatalogRequest(String reason) {}
record ResolveCatalogRequest(LocalDate asOfDate, String channel, String stateCode, String productFamily, String investorCode, String loanPurpose, String propertyType, String occupancyType, Integer termMonths, String amortizationType) {}
record CatalogResponse(UUID catalogId, int version, CatalogStatus status, List<ProductDefinition> products, List<InvestorProgram> investors, List<ReferenceEntry> references, List<MarketArea> markets, String replayHash) {}
record ProductDefinition(UUID productId, String productCode, String productName, String productFamily, List<String> allowedChannels, List<String> allowedStates, LocalDate effectiveFrom, LocalDate effectiveTo) {}
record InvestorProgram(UUID investorId, String investorCode, String investorName, List<String> channels, List<String> productCodes, LocalDate effectiveFrom, LocalDate effectiveTo) {}
record ReferenceEntry(UUID entryId, String catalogType, String code, String label, String category, Map<String, Object> attributes, LocalDate effectiveFrom, LocalDate effectiveTo) {}
record MarketArea(UUID marketId, String stateCode, String countyFips, String countyName, String marketStatus, List<String> allowedChannels, LocalDate effectiveFrom, LocalDate effectiveTo) {}
record ProductConfigSnapshot(UUID snapshotId, UUID tenantId, String snapshotHash, LocalDate asOfDate, List<ProductDefinition> products, List<InvestorProgram> investors, List<ReferenceEntry> references, List<MarketArea> markets) {}
record CatalogEvent(UUID eventId, UUID tenantId, UUID catalogId, String eventType, Instant occurredAt, Map<String, Object> payload) {}
record CatalogAuditRecord(UUID auditId, UUID tenantId, UUID catalogId, String action, String replayHash, Instant occurredAt, Map<String, Object> payload) {}
