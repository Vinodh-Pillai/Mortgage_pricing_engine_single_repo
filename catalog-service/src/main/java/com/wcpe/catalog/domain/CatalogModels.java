package com.wcpe.catalog.domain;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

enum CatalogStatus { DRAFT, VALIDATED, PENDING_APPROVAL, APPROVED, PUBLISHED, SUSPENDED, RETIRED, REJECTED, ROLLED_BACK }

record ProductRequest(String productCode, String productName, String productFamily, List<String> allowedChannels, List<String> allowedStates, LocalDate effectiveFrom, LocalDate effectiveTo) {}
record InvestorRequest(String investorCode, String investorName, List<String> channels, List<String> productCodes, LocalDate effectiveFrom, LocalDate effectiveTo) {}
record ReferenceCatalogRequest(String code, String label, String category, Map<String, Object> attributes, LocalDate effectiveFrom, LocalDate effectiveTo) {}
record ProductTaxonomyDraftRequest(String code, String name, String level, String parentCode, String agencyCategory, Instant effectiveStart, Instant effectiveEnd, Integer displayOrder) {}
record ProductTaxonomyValidation(List<ProductTaxonomyValidationMessage> blockingErrors, List<ProductTaxonomyValidationMessage> warnings) {}
record ProductTaxonomyValidationMessage(String field, String code, String message) {}
record ProductTaxonomyDraftResponse(UUID taxonomyEntryId, UUID taxonomyVersionId, CatalogStatus status, ProductTaxonomyValidation validation) {}
record ProductTaxonomyResolveRequest(Instant asOf, List<String> codes) {}
record ProductTaxonomyResolveResponse(Instant asOf, List<ProductTaxonomyResolvedEntry> entries) {}
record ProductTaxonomyResolvedEntry(String code, String level, UUID taxonomyEntryId, int version, CatalogStatus status, String parentCode) {}
record MarketRequest(String stateCode, String countyFips, String countyName, String marketStatus, List<String> allowedChannels, LocalDate effectiveFrom, LocalDate effectiveTo) {}
record MarketChange(MarketArea market, Map<String, Object> eventPayload) {}
record MarketImportRequest(String importName, List<MarketImportRow> rows) {}
record MarketImportRow(String stateCode, String stateName, String countyFips, String countyName, String marketStatus, String restrictionReasonCode, List<String> allowedChannelCodes, List<String> allowedProductCodes, Instant effectiveStart, Instant effectiveEnd) {}
record MarketImportResponse(UUID marketImportId, int acceptedRows, int rejectedRows, String status) {}
record MarketImportResult(MarketImportResponse response, List<Map<String, Object>> changedMarkets) {}
record MarketResolveRequest(Instant asOf, String stateCode, String countyFips, String productCode, String channelCode) {}
record MarketResolveResponse(UUID marketVersionId, String stateCode, String countyFips, String marketStatus, String restrictionReasonCode) {}
record PublishCatalogRequest(String reason, LocalDate effectiveDate) {}
record LifecycleActionRequest(String reason) {}
record VersionedLifecycleActionRequest(String reason, Integer expectedVersion) {}
record CatalogVersionControlRecord(UUID versionControlId, UUID catalogId, String artifactType, UUID artifactId, String artifactCode, int versionNumber, CatalogStatus status, String configHash, long rowVersion) {}
record CatalogVersionActionRequest(String action, UUID versionId, Long rowVersion, Instant effectiveStart, String reason) {}
record CatalogVersionActionResponse(String artifactType, UUID artifactId, UUID versionId, CatalogStatus oldStatus, CatalogStatus status, int versionNumber, String configHash, long rowVersion) {}
record CatalogVersionAsOfResponse(String artifactType, String artifactCode, UUID versionId, CatalogStatus status, int versionNumber, String configHash, Instant effectiveStart, Instant effectiveEnd) {}
record RejectCatalogRequest(String reason) {}
record ResolveCatalogRequest(Instant asOf, LocalDate asOfDate, String channelCode, String channel, String stateCode, String countyFips, String productFamilyCode, String productFamily, String investorCode, String loanPurpose, String propertyType, String occupancyType, Integer termMonths, String amortizationType, Boolean includeInactive) {
  ResolveCatalogRequest(LocalDate asOfDate, String channel, String stateCode, String productFamily, String investorCode, String loanPurpose, String propertyType, String occupancyType, Integer termMonths, String amortizationType) {
    this(null, asOfDate, channel, channel, stateCode, null, productFamily, productFamily, investorCode, loanPurpose, propertyType, occupancyType, termMonths, amortizationType, false);
  }
  Instant effectiveAsOf() { return asOf == null ? (asOfDate == null ? Instant.now() : asOfDate.atStartOfDay(ZoneOffset.UTC).toInstant()) : asOf; }
  String requestedChannel() { return channelCode != null && !channelCode.isBlank() ? channelCode : channel; }
  String requestedProductFamily() { return productFamilyCode != null && !productFamilyCode.isBlank() ? productFamilyCode : productFamily; }
  boolean includeInactiveRequested() { return Boolean.TRUE.equals(includeInactive); }
}
record CatalogResponse(UUID catalogId, int version, CatalogStatus status, List<ProductDefinition> products, List<InvestorProgram> investors, List<ReferenceEntry> references, List<MarketArea> markets, String replayHash) {}
record ProductDefinition(UUID productId, String productCode, String productName, String productFamily, List<String> allowedChannels, List<String> allowedStates, LocalDate effectiveFrom, LocalDate effectiveTo) {}
record InvestorProgram(UUID investorId, String investorCode, String investorName, List<String> channels, List<String> productCodes, LocalDate effectiveFrom, LocalDate effectiveTo) {}
record ReferenceEntry(UUID entryId, String catalogType, String code, String label, String category, Map<String, Object> attributes, LocalDate effectiveFrom, LocalDate effectiveTo) {}
record MarketArea(UUID marketId, String stateCode, String stateName, String countyFips, String countyName, String marketStatus, String restrictionReasonCode, List<String> allowedChannels, List<String> allowedProducts, LocalDate effectiveFrom, LocalDate effectiveTo) {}
record SnapshotChannel(String code, UUID versionId) {}
record SnapshotTaxonomy(String code, UUID versionId) {}
record SnapshotProduct(String productCode, UUID productVersionId, List<String> investorCodes, List<String> termProfileCodes) {}
record SnapshotInvestor(String code, UUID versionId, boolean requiresMiValidation) {}
record ProductConfigSnapshot(UUID snapshotId, UUID tenantId, String snapshotHash, LocalDate asOfDate, List<ProductDefinition> products, List<InvestorProgram> investors, List<ReferenceEntry> references, List<MarketArea> markets, Instant asOf, SnapshotChannel channel, List<SnapshotTaxonomy> taxonomy, List<SnapshotProduct> productComponents, List<SnapshotInvestor> investorComponents, Map<String, List<String>> referenceVersions, List<String> warnings, String requestHash, String correlationId) {
  ProductConfigSnapshot(UUID snapshotId, UUID tenantId, String snapshotHash, LocalDate asOfDate, List<ProductDefinition> products, List<InvestorProgram> investors, List<ReferenceEntry> references, List<MarketArea> markets) {
    this(snapshotId, tenantId, snapshotHash, asOfDate, products, investors, references, markets, asOfDate == null ? null : asOfDate.atStartOfDay(ZoneOffset.UTC).toInstant(), null, List.of(), List.of(), List.of(), Map.of(), List.of(), null, null);
  }
}
record ProductConfigSnapshotMaterialization(ProductConfigSnapshot snapshot, boolean materialized) {}
record CatalogEvent(UUID eventId, UUID tenantId, UUID catalogId, String eventType, Instant occurredAt, Map<String, Object> payload) {}
record CatalogAuditRecord(UUID auditId, UUID tenantId, UUID catalogId, String action, String replayHash, Instant occurredAt, Map<String, Object> payload) {}
record ConventionalProductDraftRequest(String productCode, String productName, String taxonomyTypeCode, List<String> investorCodes, List<String> channelCodes, List<Integer> termMonths, String amortizationType, String armIndexCode, Integer fixedPeriodMonths, Integer adjustmentPeriodMonths, List<String> allowedPropertyTypes, List<String> allowedOccupancyTypes, List<String> allowedLoanPurposes, List<String> allowedStateCodes, BigDecimal minLoanAmount, BigDecimal maxLoanAmount, Instant effectiveStart, Instant effectiveEnd) {}
record ConventionalProductValidation(List<ProductTaxonomyValidationMessage> blockingErrors, List<ProductTaxonomyValidationMessage> warnings) {}
record ConventionalProductDraftResponse(UUID productDefinitionId, UUID productVersionId, CatalogStatus status, ConventionalProductValidation validation) {}
record ConventionalProductResolveRequest(Instant asOf, String channelCode, String loanPurposeCode, String propertyTypeCode, String occupancyTypeCode, String stateCode, BigDecimal loanAmount, Integer termMonths, String amortizationType) {}
record ConventionalProductResolveResponse(List<ConventionalProductMatch> eligibleProducts, List<ConventionalProductRejected> rejectedProducts) {}
record ConventionalProductMatch(String productCode, UUID productVersionId, List<String> investorCodes, String configHash) {}
record ConventionalProductRejected(String productCode, String code, String message) {}
record TermAmortizationDraftRequest(String profileCode, String displayName, Integer termMonths, String amortizationType, Boolean interestOnlyAllowed, Boolean balloonAllowed, String armIndexCode, Integer initialFixedMonths, Integer adjustmentPeriodMonths, Integer lookbackDays, BigDecimal roundingIncrementBps, Instant effectiveStart, Instant effectiveEnd) {}
record TermAmortizationValidation(List<ProductTaxonomyValidationMessage> blockingErrors, List<ProductTaxonomyValidationMessage> warnings) {}
record TermAmortizationDraftResponse(UUID profileId, UUID profileVersionId, CatalogStatus status, TermAmortizationValidation validation) {}
record TermAmortizationResolveRequest(Instant asOf, Integer termMonths, String amortizationType, Integer initialFixedMonths, Integer adjustmentPeriodMonths) {}
record TermAmortizationResolveResponse(String profileCode, UUID profileVersionId, String armIndexCode, String configHash) {}
record PropertyTypeDraftRequest(String code, String displayName, String category, List<String> agencyAliases, Boolean eligibleForConventional, Boolean requiresProjectReview, Integer unitCountMin, Integer unitCountMax, Instant effectiveStart, Instant effectiveEnd) {}
record OccupancyTypeDraftRequest(String code, String displayName, List<String> agencyAliases, Boolean eligibleForConventional, Instant effectiveStart, Instant effectiveEnd) {}
record PropertyOccupancyValidation(List<ProductTaxonomyValidationMessage> blockingErrors, List<ProductTaxonomyValidationMessage> warnings) {}
record PropertyTypeDraftResponse(UUID propertyTypeId, UUID propertyTypeVersionId, CatalogStatus status, PropertyOccupancyValidation validation) {}
record OccupancyTypeDraftResponse(UUID occupancyTypeId, UUID occupancyTypeVersionId, CatalogStatus status, PropertyOccupancyValidation validation) {}
record PropertyOccupancyResolveRequest(Instant asOf, String propertyType, String occupancyType) {}
record PropertyTypeResolved(String code, UUID versionId, boolean requiresProjectReview) {}
record OccupancyTypeResolved(String code, UUID versionId) {}
record PropertyOccupancyResolveResponse(PropertyTypeResolved propertyType, OccupancyTypeResolved occupancyType) {}
record PropertyOccupancyListResponse(List<PropertyTypeResolved> propertyTypes, List<OccupancyTypeResolved> occupancyTypes) {}
record LoanPurposeDraftRequest(String purposeCode, String displayName, String category, Boolean isRefinance, Boolean isCashOut, Boolean requiresExistingLien, Boolean eligibleForConventional, List<String> agencyAliases, Instant effectiveStart, Instant effectiveEnd) {}
record LoanPurposeValidation(List<ProductTaxonomyValidationMessage> blockingErrors, List<ProductTaxonomyValidationMessage> warnings) {}
record LoanPurposeDraftResponse(UUID loanPurposeId, UUID loanPurposeVersionId, CatalogStatus status, LoanPurposeValidation validation) {}
record LoanPurposeResolveRequest(Instant asOf, String loanPurpose) {}
record LoanPurposeResolveResponse(String purposeCode, UUID loanPurposeVersionId, boolean isRefinance, boolean isCashOut, boolean requiresExistingLien) {}
record ChannelTaxonomyDraftRequest(String channelCode, String displayName, String description, List<ChannelSourceSystemMapping> sourceSystemMappings, List<String> allowedSourceSystems, Boolean requiresBranchAssignment, String defaultMarginGroupCode, Instant effectiveStart, Instant effectiveEnd) {}
record ChannelSourceSystemMapping(String sourceSystem, String externalValue) {}
record ChannelTaxonomyValidation(List<ProductTaxonomyValidationMessage> blockingErrors, List<ProductTaxonomyValidationMessage> warnings) {}
record ChannelTaxonomyDraftResponse(UUID channelId, UUID channelVersionId, CatalogStatus status, ChannelTaxonomyValidation validation) {}
record ChannelResolveRequest(Instant asOf, String submittedChannel, String sourceSystem, String externalValue, String actorId) {}
record ChannelResolveResponse(String channelCode, UUID channelId, UUID channelVersionId, boolean requiresBranchAssignment, String defaultMarginGroupCode) {}
record InvestorSellerServicerId(String channelCode, String sellerId, String servicerId) {}
record InvestorCatalogDraftRequest(String investorCode, String legalName, String investorType, String agency, List<InvestorSellerServicerId> sellerServicerIds, List<String> deliveryTypes, List<String> activeChannelCodes, Boolean requiresMiValidation, CatalogStatus status, Instant effectiveStart, Instant effectiveEnd) {}
record InvestorCatalogValidation(List<ProductTaxonomyValidationMessage> blockingErrors, List<ProductTaxonomyValidationMessage> warnings) {}
record InvestorCatalogDraftResponse(UUID investorId, UUID investorVersionId, CatalogStatus status, InvestorCatalogValidation validation) {}
record InvestorResolveRequest(Instant asOf, String productCode, String channelCode, String deliveryType) {}
record InvestorResolveResponse(List<ResolvedInvestor> investors) {}
record ResolvedInvestor(String investorCode, UUID investorVersionId, String sellerIdMasked, boolean requiresMiValidation) {}
