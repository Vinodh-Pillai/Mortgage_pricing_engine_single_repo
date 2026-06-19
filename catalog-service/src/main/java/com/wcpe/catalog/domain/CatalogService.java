package com.wcpe.catalog.domain;

import com.wcpe.catalog.auth.AuthorizationService;
import com.wcpe.catalog.auth.TenantProductAuthorization;
import com.wcpe.catalog.auth.TenantProductAuthorizationService;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CatalogService {
  private static final ProductTaxonomyValidation TAXONOMY_VALID = new ProductTaxonomyValidation(List.of(), List.of());
  private static final Set<String> WRITER_ROLES = Set.of("CATALOG_WRITER", "CATALOG_MANAGER", "CATALOG_ADMIN");
  private static final Set<String> APPROVER_ROLES = Set.of("CATALOG_APPROVER", "CATALOG_MANAGER", "CATALOG_ADMIN");
  private static final Set<String> PUBLISHER_ROLES = Set.of("CATALOG_PUBLISHER", "CATALOG_MANAGER", "CATALOG_ADMIN");
  private final CatalogRepository repository;
  private final AuthorizationService authorizationService;
  private final TenantProductAuthorizationService tenantAuthorizationService;

  CatalogService(CatalogRepository repository, AuthorizationService authorizationService) {
    this(repository, authorizationService, null);
  }

  @Autowired
  CatalogService(CatalogRepository repository, AuthorizationService authorizationService, TenantProductAuthorizationService tenantAuthorizationService) {
    this.repository = repository;
    this.authorizationService = authorizationService;
    this.tenantAuthorizationService = tenantAuthorizationService;
  }

  @Transactional
  CatalogResponse addProduct(UUID tenantId, ProductRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    return repository.idempotent(tenantId, idempotencyKey, request, CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      ProductDefinition product = repository.addProduct(tenantId, catalogId, request, actorId);
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, "ProductDefinitionAdded.v1", Map.of("productCode", product.productCode()));
      audit(tenantId, catalogId, "PRODUCT_DEFINITION_ADDED", before, after, Map.of("productCode", product.productCode()), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  @Transactional
  ProductCreationResponse createProduct(UUID tenantId, ProductCreationRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    return repository.idempotent(tenantId, idempotencyKey, request, ProductCreationResponse.class, () -> {
      ProductCreationDraft draft = ProductCreationPolicy.validate(request);
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      ProductCreationPersistence persisted = repository.addProductCreation(tenantId, catalogId, draft, actorId);
      CatalogResponse after = repository.current(tenantId);
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("productCode", persisted.product().productCode());
      payload.put("productVersionId", persisted.productVersionId().toString());
      payload.put("status", persisted.status());
      payload.put("metadataRefKeys", persisted.metadataRefs().keySet().stream().sorted().toList());
      emit(tenantId, catalogId, "ProductCreationEndpointAccepted.v1", payload);
      audit(tenantId, catalogId, "PRODUCT_CREATION_ENDPOINT_ACCEPTED", before, after, payload, actorId, correlationId, idempotencyKey);
      String auditRef = "catalog-audit:" + catalogId + ":" + persisted.product().productCode();
      return new ProductCreationResponse(persisted.product().productId(), persisted.productVersionId(), persisted.product().productCode(),
          persisted.product().productName(), persisted.status(), List.of(), auditRef, persisted.metadataRefs());
    });
  }

  @Transactional
  ProductPricingConfigurationResponse attachProductPricingConfiguration(UUID tenantId, ProductPricingConfigurationRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    requireIdempotencyKey(idempotencyKey);
    if (request == null) throw new CatalogException("PRICING_CONFIG_REQUEST_REQUIRED");
    return repository.idempotent(tenantId, idempotencyKey, request, ProductPricingConfigurationResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      ProductPricingConfigurationResponse response = repository.attachProductPricingConfiguration(tenantId, catalogId, request, actorId);
      CatalogResponse after = repository.current(tenantId);
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("productCode", response.productCode());
      payload.put("productVersionId", response.productVersionId().toString());
      payload.put("effectiveStart", response.effectiveStart().toString());
      payload.put("refCount", response.refs().size());
      payload.put("refTypes", response.refs().stream().map(PricingConfigReference::refType).distinct().sorted().toList());
      emit(tenantId, catalogId, "ProductPricingConfigurationAttached.v1", payload);
      audit(tenantId, catalogId, "PRODUCT_PRICING_CONFIGURATION_ATTACHED", before, after, payload, actorId, correlationId, idempotencyKey);
      return response;
    });
  }

  @Transactional
  CatalogResponse addInvestor(UUID tenantId, InvestorRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    return repository.idempotent(tenantId, idempotencyKey, request, CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      InvestorProgram investor = repository.addInvestor(tenantId, catalogId, request, actorId);
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, "InvestorProgramAdded.v1", Map.of("investorCode", investor.investorCode()));
      audit(tenantId, catalogId, "INVESTOR_PROGRAM_ADDED", before, after, Map.of("investorCode", investor.investorCode()), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  @Transactional
  CatalogResponse addReference(UUID tenantId, String catalogType, ReferenceCatalogRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    return repository.idempotent(tenantId, idempotencyKey, Map.of("type", catalogType, "request", request), CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      ReferenceEntry entry = repository.addReference(tenantId, catalogId, catalogType, request, actorId);
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, catalogType + "Changed.v1", Map.of("code", entry.code()));
      audit(tenantId, catalogId, catalogType + "_CHANGED", before, after, Map.of("code", entry.code()), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  @Transactional
  EnumerationCatalogImportResponse importEnumerations(UUID tenantId, EnumerationCatalogImportRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    requireIdempotencyKey(idempotencyKey);
    List<EnumerationTypeResponse> normalized = EnumerationCatalogPolicy.normalize(request);
    return repository.idempotent(tenantId, idempotencyKey, request, EnumerationCatalogImportResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      List<EnumerationTypeResponse> persisted = new ArrayList<>();
      for (EnumerationTypeResponse enumeration : normalized) {
        persisted.add(repository.addEnumerationType(tenantId, catalogId, enumeration, actorId));
      }
      int variantCount = persisted.stream().mapToInt(e -> e.variants().size()).sum();
      emit(tenantId, catalogId, "EnumerationCatalogImported.v1", Map.of("enumTypeCount", persisted.size(), "variantCount", variantCount));
      audit(tenantId, catalogId, "ENUMERATION_CATALOG_IMPORTED", null, persisted, Map.of("enumTypeCount", persisted.size(), "variantCount", variantCount), actorId, correlationId, idempotencyKey);
      return new EnumerationCatalogImportResponse(persisted, persisted.size(), variantCount);
    });
  }

  @Transactional
  FieldMetadataImportResponse importFieldMetadata(UUID tenantId, FieldMetadataImportRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    requireIdempotencyKey(idempotencyKey);
    List<FieldMetadataResponse> normalized = FieldMetadataCatalogPolicy.normalize(request);
    return repository.idempotent(tenantId, idempotencyKey, request, FieldMetadataImportResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      List<FieldMetadataResponse> persisted = new ArrayList<>();
      for (FieldMetadataResponse field : normalized) {
        persisted.add(repository.addFieldMetadata(tenantId, catalogId, field, actorId));
      }
      emit(tenantId, catalogId, "FieldMetadataCatalogImported.v1", Map.of("fieldCount", persisted.size()));
      audit(tenantId, catalogId, "FIELD_METADATA_CATALOG_IMPORTED", null, persisted, Map.of("fieldCount", persisted.size()), actorId, correlationId, idempotencyKey);
      return new FieldMetadataImportResponse(persisted, persisted.size());
    });
  }

  @Transactional
  ProductSpecificationSystemFieldImportResponse importProductSpecificationFieldsFromSystem(UUID tenantId,
                                                                                           ProductSpecificationSystemFieldImportRequest request,
                                                                                           String idempotencyKey,
                                                                                           String actorId,
                                                                                           String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    requireIdempotencyKey(idempotencyKey);
    List<FieldMetadataResponse> selected = ProductSpecificationFieldListPolicy.normalizeSystemImport(request, repository.listSystemFieldMetadata());
    return repository.idempotent(tenantId, idempotencyKey, request, ProductSpecificationSystemFieldImportResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      List<FieldMetadataResponse> imported = repository.importProductSpecificationFieldsFromSystem(tenantId, catalogId, selected, actorId);
      ProductSpecificationFieldListResponse listed = ProductSpecificationFieldListPolicy.list(imported, Optional.empty(), Optional.empty(), true);
      emit(tenantId, catalogId, "ProductSpecificationSystemFieldsImported.v1", Map.of("fieldCount", imported.size(), "fieldIds", imported.stream().map(FieldMetadataResponse::id).toList()));
      audit(tenantId, catalogId, "PRODUCT_SPECIFICATION_SYSTEM_FIELDS_IMPORTED", null, imported,
          Map.of("fieldCount", imported.size(), "systemDefaultsChanged", false), actorId, correlationId, idempotencyKey);
      return new ProductSpecificationSystemFieldImportResponse(listed.fields(), imported.size(), false);
    });
  }

  List<FieldMetadataResponse> listFieldMetadata(UUID tenantId, String actorId, String correlationId) {
    List<FieldMetadataResponse> response = repository.listFieldMetadata(tenantId);
    UUID catalogId = repository.currentCatalogId(tenantId);
    audit(tenantId, catalogId, "FIELD_METADATA_CATALOG_LISTED", null, response, Map.of("fieldCount", response.size()), actorId, correlationId, null);
    return response;
  }

  FieldLibraryQueryResponse queryFieldLibrary(UUID tenantId, String category, boolean includeEnums, String actorId, String correlationId) {
    List<FieldMetadataResponse> fields = repository.listFieldMetadata(tenantId);
    List<EnumerationTypeResponse> enumerations = repository.listEnumerations(tenantId);
    FieldLibraryQueryResponse response = FieldLibraryQueryPolicy.query(category, fields, enumerations, includeEnums, true);
    UUID catalogId = repository.currentCatalogId(tenantId);
    audit(tenantId, catalogId, "FIELD_LIBRARY_QUERIED", null, response,
        Map.of("category", response.category(), "fieldCount", response.payloadFieldCount(), "includeEnums", includeEnums), actorId, correlationId, null);
    return response;
  }

  FieldLibraryQueryResponse querySystemFieldLibrary(String category, boolean includeEnums, String actorId, String correlationId) {
    UUID systemTenantId = new UUID(0L, 0L);
    List<FieldMetadataResponse> fields = repository.listFieldMetadata(systemTenantId);
    List<EnumerationTypeResponse> enumerations = repository.listEnumerations(systemTenantId);
    return FieldLibraryQueryPolicy.query(category, fields, enumerations, includeEnums, false);
  }

  ProductSpecificationFieldListResponse productSpecificationFields(UUID tenantId, String actorId, String correlationId) {
    List<FieldMetadataResponse> fields = repository.listFieldMetadata(tenantId);
    ProductSpecificationFieldListResponse response = ProductSpecificationFieldListPolicy.list(fields, repository.productSpecificationFieldOrderDraft(tenantId), repository.productSpecificationTenantFieldDraft(tenantId), true);
    UUID catalogId = repository.currentCatalogId(tenantId);
    audit(tenantId, catalogId, "PRODUCT_SPECIFICATION_FIELDS_LISTED", null, response,
        Map.of("fieldCount", response.payloadFieldCount(), "sourceScope", response.sourceScope()), actorId, correlationId, null);
    return response;
  }

  @Transactional
  ProductSpecificationFieldOrderDraftResponse saveProductSpecificationFieldOrderDraft(UUID tenantId, ProductSpecificationFieldOrderDraftRequest request,
                                                                                      String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    requireIdempotencyKey(idempotencyKey);
    List<FieldMetadataResponse> fields = repository.listFieldMetadata(tenantId);
    ProductSpecificationFieldOrderDraft draft = ProductSpecificationFieldListPolicy.normalizeDraft(request, fields, actorId);
    return repository.idempotent(tenantId, idempotencyKey, request, ProductSpecificationFieldOrderDraftResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      ProductSpecificationFieldOrderDraft saved = repository.saveProductSpecificationFieldOrderDraft(tenantId, catalogId, draft);
      emit(tenantId, catalogId, "ProductSpecificationFieldOrderDraftSaved.v1", Map.of("fieldCount", saved.fieldIds().size(), "draftStatus", saved.draftStatus()));
      audit(tenantId, catalogId, "PRODUCT_SPECIFICATION_FIELD_ORDER_DRAFT_SAVED", null, saved,
          Map.of("fieldCount", saved.fieldIds().size(), "draftStatus", saved.draftStatus(), "systemDefaultsChanged", false), actorId, correlationId, idempotencyKey);
      return new ProductSpecificationFieldOrderDraftResponse(saved.draftStatus(), saved.fieldIds(), false);
    });
  }

  @Transactional
  ProductSpecificationTenantFieldDraftResponse saveProductSpecificationTenantFieldDraft(UUID tenantId, ProductSpecificationTenantFieldDraftRequest request,
                                                                                        String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    requireIdempotencyKey(idempotencyKey);
    List<FieldMetadataResponse> fields = repository.listFieldMetadata(tenantId);
    ProductSpecificationTenantFieldDraft draft = ProductSpecificationFieldListPolicy.normalizeTenantFieldDraft(request, fields, actorId);
    return repository.idempotent(tenantId, idempotencyKey, request, ProductSpecificationTenantFieldDraftResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      ProductSpecificationTenantFieldDraft saved = repository.saveProductSpecificationTenantFieldDraft(tenantId, catalogId, draft);
      emit(tenantId, catalogId, "ProductSpecificationTenantFieldDraftSaved.v1", Map.of("aliasCount", saved.aliases().size(), "nativeFieldCount", saved.nativeFields().size(), "draftStatus", saved.draftStatus()));
      audit(tenantId, catalogId, "PRODUCT_SPECIFICATION_TENANT_FIELD_DRAFT_SAVED", null, saved,
          Map.of("aliasCount", saved.aliases().size(), "nativeFieldCount", saved.nativeFields().size(), "draftStatus", saved.draftStatus(), "systemDefaultsChanged", false), actorId, correlationId, idempotencyKey);
      return new ProductSpecificationTenantFieldDraftResponse(saved.draftStatus(), saved.aliases().size(), saved.nativeFields().size(), false);
    });
  }

  @Transactional
  ProductSpecificationConditionDraftResponse saveProductSpecificationConditionDraft(UUID tenantId, ProductSpecificationConditionDraftRequest request,
                                                                                   String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    requireIdempotencyKey(idempotencyKey);
    List<FieldMetadataResponse> fields = repository.listFieldMetadata(tenantId);
    List<EnumerationTypeResponse> enumerations = repository.listEnumerations(tenantId);
    ProductSpecificationConditionDraft draft = ProductSpecificationConditionRulePolicy.normalizeDraft(request, fields, enumerations, actorId);
    return repository.idempotent(tenantId, idempotencyKey, request, ProductSpecificationConditionDraftResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      ProductSpecificationConditionDraft saved = repository.saveProductSpecificationConditionDraft(tenantId, catalogId, draft);
      Map<String, Object> auditPayload = ProductSpecificationConditionRulePolicy.conditionAuditPayload(saved);
      emit(tenantId, catalogId, "ProductSpecificationConditionDraftSaved.v1", auditPayload);
      audit(tenantId, catalogId, "PRODUCT_SPECIFICATION_CONDITION_DRAFT_SAVED", null, saved,
          new LinkedHashMap<>(auditPayload), actorId, correlationId, idempotencyKey);
      return new ProductSpecificationConditionDraftResponse(saved.draftStatus(), saved.includeConditions().size(), saved.additionalConditions().size(), false);
    });
  }

  ProductSpecificationFieldConditionEvaluationResponse evaluateProductSpecificationFieldConditions(UUID tenantId, String fieldId,
                                                                                                  ProductSpecificationFieldConditionEvaluationRequest request,
                                                                                                  String actorId, String correlationId) {
    List<FieldMetadataResponse> fields = repository.listFieldMetadata(tenantId);
    List<EnumerationTypeResponse> enumerations = repository.listEnumerations(tenantId);
    ProductSpecificationFieldConditionEvaluationResponse response = ProductSpecificationConditionRulePolicy.evaluateField(fieldId, fields,
        repository.productSpecificationConditionDraft(tenantId), request == null ? Map.of() : request.parentValues(), enumerations);
    UUID catalogId = repository.currentCatalogId(tenantId);
    audit(tenantId, catalogId, "PRODUCT_SPECIFICATION_FIELD_CONDITION_EVALUATED", null, response,
        Map.of("fieldId", response.fieldId(), "visible", response.visible(), "status", response.status()), actorId, correlationId, null);
    return response;
  }

  FieldMetadataResponse resolveFieldMetadata(UUID tenantId, String fieldId, String actorId, String correlationId) {
    FieldMetadataResponse response = repository.resolveFieldMetadata(tenantId, fieldId);
    UUID catalogId = repository.currentCatalogId(tenantId);
    audit(tenantId, catalogId, "FIELD_METADATA_CATALOG_RESOLVED", null, response, Map.of("fieldId", response.id(), "valueType", response.valueType()), actorId, correlationId, null);
    return response;
  }

  EnumerationTypeResponse resolveEnumeration(UUID tenantId, String enumTypeId, String actorId, String correlationId) {
    EnumerationTypeResponse response = repository.resolveEnumeration(tenantId, enumTypeId);
    UUID catalogId = repository.currentCatalogId(tenantId);
    audit(tenantId, catalogId, "ENUMERATION_CATALOG_RESOLVED", null, response, Map.of("enumTypeId", response.enumTypeId(), "variantCount", response.variants().size()), actorId, correlationId, null);
    return response;
  }

  @Transactional
  ProductTaxonomyDraftResponse addProductTaxonomyDraft(UUID tenantId, ProductTaxonomyDraftRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    return repository.idempotent(tenantId, idempotencyKey, request, ProductTaxonomyDraftResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      ProductTaxonomyDraftResponse response = repository.addProductTaxonomyDraft(tenantId, catalogId, request, actorId);
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, "ProductTaxonomyChanged.v1", Map.of("code", request.code(), "status", response.status().name(), "versionNumber", 1));
      audit(tenantId, catalogId, "PRODUCT_TAXONOMY_CHANGED", before, after, Map.of("code", request.code(), "status", response.status().name()), actorId, correlationId, idempotencyKey);
      return response;
    });
  }

  @Transactional
  ChannelTaxonomyDraftResponse addChannelTaxonomyDraft(UUID tenantId, ChannelTaxonomyDraftRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    requireIdempotencyKey(idempotencyKey);
    return repository.idempotent(tenantId, idempotencyKey, request, ChannelTaxonomyDraftResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      ChannelTaxonomyDraftResponse response = repository.addChannelTaxonomyDraft(tenantId, catalogId, request, actorId);
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, "ChannelCatalogChanged.v1", Map.of("channelCode", request.channelCode(), "status", response.status().name(), "versionNumber", 1));
      audit(tenantId, catalogId, "CHANNEL_CATALOG_CHANGED", before, after, Map.of("channelCode", request.channelCode(), "channelVersionId", response.channelVersionId().toString()), actorId, correlationId, idempotencyKey);
      return response;
    });
  }

  @Transactional
  ConventionalProductDraftResponse addConventionalProductDraft(UUID tenantId, ConventionalProductDraftRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    requireIdempotencyKey(idempotencyKey);
    return repository.idempotent(tenantId, idempotencyKey, request, ConventionalProductDraftResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      ConventionalProductDraftResponse response = repository.addConventionalProductDraft(tenantId, catalogId, request, actorId);
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, "ConventionalProductDefinitionDrafted.v1", Map.of("productCode", request.productCode(), "status", response.status().name(), "blockingErrorCount", response.validation().blockingErrors().size()));
      audit(tenantId, catalogId, "CONVENTIONAL_PRODUCT_DEFINITION_DRAFTED", before, after, Map.of("productCode", request.productCode(), "productVersionId", response.productVersionId().toString(), "blockingErrorCount", response.validation().blockingErrors().size()), actorId, correlationId, idempotencyKey);
      return response;
    });
  }

  @Transactional
  TermAmortizationDraftResponse addTermAmortizationDraft(UUID tenantId, TermAmortizationDraftRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    requireIdempotencyKey(idempotencyKey);
    return repository.idempotent(tenantId, idempotencyKey, request, TermAmortizationDraftResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      TermAmortizationDraftResponse response = repository.addTermAmortizationDraft(tenantId, catalogId, request, actorId);
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, "TermAmortizationProfileChanged.v1", Map.of("profileCode", request.profileCode(), "profileVersionId", response.profileVersionId().toString(), "amortizationType", request.amortizationType(), "termMonths", request.termMonths(), "status", response.status().name()));
      audit(tenantId, catalogId, "TERM_AMORTIZATION_PROFILE_CHANGED", before, after, Map.of("profileCode", request.profileCode(), "profileVersionId", response.profileVersionId().toString()), actorId, correlationId, idempotencyKey);
      return response;
    });
  }

  @Transactional
  PropertyTypeDraftResponse addPropertyTypeDraft(UUID tenantId, PropertyTypeDraftRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    requireIdempotencyKey(idempotencyKey);
    return repository.idempotent(tenantId, idempotencyKey, request, PropertyTypeDraftResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      PropertyTypeDraftResponse response = repository.addPropertyTypeDraft(tenantId, catalogId, request, actorId);
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, "PropertyTypeCatalogChanged.v1", Map.of("code", request.code(), "status", response.status().name(), "requiresProjectReview", Boolean.TRUE.equals(request.requiresProjectReview())));
      audit(tenantId, catalogId, "PROPERTY_TYPE_CATALOG_CHANGED", before, after, Map.of("code", request.code(), "propertyTypeVersionId", response.propertyTypeVersionId().toString()), actorId, correlationId, idempotencyKey);
      return response;
    });
  }

  @Transactional
  OccupancyTypeDraftResponse addOccupancyTypeDraft(UUID tenantId, OccupancyTypeDraftRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    requireIdempotencyKey(idempotencyKey);
    return repository.idempotent(tenantId, idempotencyKey, request, OccupancyTypeDraftResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      OccupancyTypeDraftResponse response = repository.addOccupancyTypeDraft(tenantId, catalogId, request, actorId);
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, "OccupancyTypeCatalogChanged.v1", Map.of("code", request.code(), "status", response.status().name()));
      audit(tenantId, catalogId, "OCCUPANCY_TYPE_CATALOG_CHANGED", before, after, Map.of("code", request.code(), "occupancyTypeVersionId", response.occupancyTypeVersionId().toString()), actorId, correlationId, idempotencyKey);
      return response;
    });
  }

  @Transactional
  LoanPurposeDraftResponse addLoanPurposeDraft(UUID tenantId, LoanPurposeDraftRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    requireIdempotencyKey(idempotencyKey);
    return repository.idempotent(tenantId, idempotencyKey, request, LoanPurposeDraftResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      LoanPurposeDraftResponse response = repository.addLoanPurposeDraft(tenantId, catalogId, request, actorId);
      CatalogResponse after = repository.current(tenantId);
      CatalogVersionControlRecord version = repository.versionControls(tenantId, catalogId).stream()
          .filter(record -> record.versionControlId().equals(response.loanPurposeVersionId()))
          .findFirst()
          .orElseThrow(() -> new CatalogException("LOAN_PURPOSE_VERSION_NOT_FOUND"));
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("purposeCode", request.purposeCode().trim().toUpperCase(Locale.ROOT));
      payload.put("loanPurposeVersionId", response.loanPurposeVersionId().toString());
      payload.put("status", response.status().name());
      payload.put("versionNumber", version.versionNumber());
      payload.put("configHash", version.configHash());
      payload.put("isRefinance", Boolean.TRUE.equals(request.isRefinance()));
      payload.put("isCashOut", Boolean.TRUE.equals(request.isCashOut()));
      payload.put("requiresExistingLien", Boolean.TRUE.equals(request.requiresExistingLien()));
      payload.put("eligibleForConventional", Boolean.TRUE.equals(request.eligibleForConventional()));
      payload.put("agencyAliases", LoanPurposeCatalogPolicy.canonicalAliases(request));
      emit(tenantId, catalogId, "LoanPurposeCatalogChanged.v1", payload);
      audit(tenantId, catalogId, "LOAN_PURPOSE_CATALOG_CHANGED", before, after, Map.of("purposeCode", request.purposeCode(), "loanPurposeVersionId", response.loanPurposeVersionId().toString()), actorId, correlationId, idempotencyKey);
      return response;
    });
  }

  @Transactional
  CatalogResponse addMarket(UUID tenantId, MarketRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    return repository.idempotent(tenantId, idempotencyKey, request, CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      MarketChange market = repository.addMarket(tenantId, catalogId, request, actorId);
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, "MarketCatalogChanged.v1", market.eventPayload());
      audit(tenantId, catalogId, "MARKET_CATALOG_CHANGED", before, after, Map.of("stateCode", market.market().stateCode(), "countyFips", Objects.toString(market.market().countyFips(), ""), "marketVersionId", market.eventPayload().get("marketVersionId")), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  @Transactional
  MarketImportResponse importMarkets(UUID tenantId, MarketImportRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    requireIdempotencyKey(idempotencyKey);
    return repository.idempotent(tenantId, idempotencyKey, request, MarketImportResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      MarketImportResult result = repository.importMarkets(tenantId, catalogId, request, actorId);
      MarketImportResponse response = result.response();
      CatalogResponse after = repository.current(tenantId);
      for (Map<String, Object> payload : result.changedMarkets()) {
        emit(tenantId, catalogId, "MarketCatalogChanged.v1", payload);
      }
      audit(tenantId, catalogId, "MARKET_CATALOG_CHANGED", before, after, Map.of("marketImportId", response.marketImportId().toString(), "acceptedRows", response.acceptedRows(), "rejectedRows", response.rejectedRows(), "changedMarketCount", result.changedMarkets().size()), actorId, correlationId, idempotencyKey);
      return response;
    });
  }

  @Transactional
  CatalogResponse draft(UUID tenantId, LifecycleActionRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    return repository.idempotent(tenantId, idempotencyKey, request, CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      String current = before.status().name();
      if (!"REJECTED".equals(current) && !"ROLLED_BACK".equals(current)) {
        repository.transition(tenantId, catalogId, CatalogStatus.valueOf(current), CatalogStatus.DRAFT);
      } else {
        repository.resetToDraft(tenantId, catalogId);
      }
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, "CatalogSetToDraft.v1", Map.of("from", current, "to", "DRAFT"));
      audit(tenantId, catalogId, "CATALOG_SET_TO_DRAFT", before, after, Map.of("from", current, "to", "DRAFT"), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  @Transactional
  CatalogResponse validate(UUID tenantId, LifecycleActionRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    return transition(tenantId, CatalogStatus.DRAFT, CatalogStatus.VALIDATED, "CATALOG_VALIDATED", request, idempotencyKey, actorId, correlationId);
  }

  @Transactional
  CatalogResponse submitApproval(UUID tenantId, LifecycleActionRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    return transition(tenantId, CatalogStatus.VALIDATED, CatalogStatus.PENDING_APPROVAL, "CATALOG_SUBMITTED_FOR_APPROVAL", request, idempotencyKey, actorId, correlationId);
  }

  @Transactional
  CatalogResponse approve(UUID tenantId, LifecycleActionRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_APPROVER", APPROVER_ROLES);
    String submitterId = repository.findSubmitterId(tenantId);
    authorizationService.enforceSoD(actorId, submitterId, "SUBMITTED:" + submitterId);
    return transition(tenantId, CatalogStatus.PENDING_APPROVAL, CatalogStatus.APPROVED, "CATALOG_APPROVED", request, idempotencyKey, actorId, correlationId);
  }

  @Transactional
  CatalogResponse reject(UUID tenantId, RejectCatalogRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_APPROVER", APPROVER_ROLES);
    return transition(tenantId, CatalogStatus.PENDING_APPROVAL, CatalogStatus.REJECTED, "CATALOG_REJECTED", request, idempotencyKey, actorId, correlationId);
  }

  @Transactional
  CatalogResponse publish(UUID tenantId, PublishCatalogRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_PUBLISHER", PUBLISHER_ROLES);
    return repository.idempotent(tenantId, idempotencyKey, request, CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      ProductSpecificationConditionRulePolicy.validatePublish(repository.listFieldMetadata(tenantId), repository.productSpecificationConditionDraft(tenantId), repository.listEnumerations(tenantId));
      repository.publishProductSpecificationVersion(tenantId, catalogId, actorId);
      repository.publish(tenantId, catalogId);
      CatalogResponse after = repository.active(tenantId);
      String reason = request.reason() == null ? "publish" : request.reason();
      emit(tenantId, catalogId, "CatalogPublished.v1", Map.of("reason", reason));
      for (Map<String, Object> payload : repository.publishedConventionalProductDefinitions(tenantId, catalogId)) {
        emit(tenantId, catalogId, "ConventionalProductDefinitionPublished.v1", payload);
      }
      audit(tenantId, catalogId, "CATALOG_PUBLISHED", before, after, Map.of("reason", reason), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  @Transactional
  CatalogResponse suspend(UUID tenantId, LifecycleActionRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_PUBLISHER", PUBLISHER_ROLES);
    return transition(tenantId, CatalogStatus.PUBLISHED, CatalogStatus.SUSPENDED, "CATALOG_SUSPENDED", request, idempotencyKey, actorId, correlationId);
  }

  @Transactional
  CatalogResponse retire(UUID tenantId, LifecycleActionRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_PUBLISHER", PUBLISHER_ROLES);
    return repository.idempotent(tenantId, idempotencyKey, request, CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      CatalogStatus current = before.status();
      if (current == CatalogStatus.PUBLISHED) repository.transition(tenantId, catalogId, CatalogStatus.PUBLISHED, CatalogStatus.RETIRED);
      else if (current == CatalogStatus.SUSPENDED) repository.transition(tenantId, catalogId, CatalogStatus.SUSPENDED, CatalogStatus.RETIRED);
      else throw new CatalogException("INVALID_CATALOG_STATUS_TRANSITION");
      CatalogResponse after = repository.current(tenantId);
      String reason = request.reason() == null ? "retire" : request.reason();
      emit(tenantId, catalogId, "CatalogRetired.v1", Map.of("reason", reason));
      audit(tenantId, catalogId, "CATALOG_RETIRED", before, after, Map.of("reason", reason), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  CatalogResponse current(UUID tenantId) { return repository.current(tenantId); }
  CatalogResponse active(UUID tenantId) { return repository.active(tenantId); }

  @Transactional
  CatalogResponse rollback(UUID tenantId, VersionedLifecycleActionRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_ADMIN", Set.of("CATALOG_ADMIN"));
    return repository.idempotent(tenantId, idempotencyKey, request, CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      if (request.expectedVersion() != null) repository.requireVersion(tenantId, catalogId, request.expectedVersion());
      repository.forceStatus(tenantId, catalogId, CatalogStatus.ROLLED_BACK);
      CatalogResponse after = repository.current(tenantId);
      String reason = request.reason() == null ? "rollback" : request.reason();
      emit(tenantId, catalogId, "CatalogRolledBack.v1", Map.of("reason", reason));
      audit(tenantId, catalogId, "CATALOG_ROLLED_BACK", before, after, Map.of("reason", reason), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  @Transactional
  CatalogVersionActionResponse applyVersionAction(UUID tenantId, String artifactType, UUID artifactId, CatalogVersionActionRequest request, String idempotencyKey, String actorId, String correlationId) {
    if (request == null) throw new CatalogException("VERSION_ACTION_REQUEST_REQUIRED");
    requireIdempotencyKey(idempotencyKey);
    String action = request.action() == null ? "" : request.action().toUpperCase(Locale.ROOT);
    requireVersionActionRole(action);
    return repository.idempotent(tenantId, idempotencyKey, Map.of("artifactType", artifactType, "artifactId", artifactId, "request", request), CatalogVersionActionResponse.class, () -> {
      CatalogVersionActionResponse response = repository.applyVersionAction(tenantId, artifactType, artifactId, request, actorId);
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("artifactType", response.artifactType());
      payload.put("artifactId", response.artifactId().toString());
      payload.put("versionId", response.versionId().toString());
      payload.put("oldStatus", response.oldStatus().name());
      payload.put("newStatus", response.status().name());
      payload.put("versionNumber", response.versionNumber());
      payload.put("configHash", response.configHash());
      payload.put("reason", request.reason());
      UUID catalogId = repository.currentCatalogId(tenantId);
      emit(tenantId, catalogId, "CatalogVersionStatusChanged.v1", payload);
      audit(tenantId, catalogId, "CATALOG_VERSION_STATUS_CHANGED", Map.of("status", response.oldStatus().name()), Map.of("status", response.status().name()), payload, actorId, correlationId, idempotencyKey);
      return response;
    });
  }

  CatalogVersionAsOfResponse resolveVersionAsOf(UUID tenantId, String artifactType, String artifactCode, Instant asOf) {
    return repository.resolveVersionAsOf(tenantId, artifactType, artifactCode, asOf);
  }

  @Transactional
  ProductConfigSnapshot resolve(UUID tenantId, ResolveCatalogRequest request, String idempotencyKey, String actorId, String correlationId) {
    if (request == null) throw new CatalogException("PRODUCT_CONFIG_SNAPSHOT_REQUEST_REQUIRED");
    if (request.includeInactiveRequested() && !hasRole("CATALOG_DEBUG")) throw new CatalogException("INCLUDE_INACTIVE_REQUIRES_DEBUG_PERMISSION");
    Instant asOf = request.effectiveAsOf();
    List<TenantProductAuthorization> rules = tenantAuthorizationRules(tenantId, asOf);
    return repository.idempotent(tenantId, idempotencyKey, request, ProductConfigSnapshot.class, () -> {
      ProductConfigSnapshotMaterialization result = repository.resolveMaterialized(tenantId, request, correlationId);
      ProductConfigSnapshot snapshot = authorizeSnapshot(result.snapshot(), rules, request.requestedChannel(), request.investorCode());
      UUID catalogId = repository.activeCatalogId(tenantId);
      if (result.materialized()) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("snapshotId", snapshot.snapshotId().toString());
        payload.put("snapshotHash", snapshot.snapshotHash());
        payload.put("requestHash", snapshot.requestHash());
        payload.put("asOf", snapshot.asOf().toString());
        payload.put("channel", Objects.toString(snapshot.channel() == null ? null : snapshot.channel().code(), ""));
        payload.put("family", Objects.toString(request.requestedProductFamily(), ""));
        payload.put("componentCount", snapshot.productComponents().size() + snapshot.investorComponents().size() + snapshot.referenceVersions().values().stream().mapToInt(List::size).sum());
        emit(tenantId, catalogId, "ProductConfigSnapshotMaterialized.v1", payload);
      }
      audit(tenantId, catalogId, "PRODUCT_CONFIG_SNAPSHOT_MATERIALIZED", null, snapshot, Map.of("snapshotHash", snapshot.snapshotHash(), "requestHash", snapshot.requestHash(), "materialized", result.materialized()), actorId, correlationId, idempotencyKey);
      return snapshot;
    });
  }

  @Transactional
  ProductConfigSnapshot resolveLoanPassMappedCatalog(UUID tenantId, LoanPassMappedCatalogRequest request, String idempotencyKey, String actorId, String correlationId) {
    if (request == null) throw new CatalogException("TENANT_MAPPING_CONTEXT_REQUIRED");
    requireMappedTenant(tenantId, request.mappedTenantId());
    String mappedChannel = requireMappedValue(request.mappedChannelCode(), "TENANT_MAPPING_CHANNEL_REQUIRED");
    String mappedInvestor = requireMappedValue(request.mappedInvestorCode(), "TENANT_MAPPING_INVESTOR_REQUIRED");
    String auditRef = requireMappedValue(request.tenantMappingAuditRef(), "TENANT_MAPPING_AUDIT_REF_REQUIRED");
    ResolveCatalogRequest mappedRequest = new ResolveCatalogRequest(
        request.asOf(),
        null,
        mappedChannel,
        null,
        request.stateCode(),
        request.countyFips(),
        request.productFamilyCode(),
        null,
        mappedInvestor,
        request.loanPurpose(),
        request.propertyType(),
        request.occupancyType(),
        request.termMonths(),
        request.amortizationType(),
        request.includeInactive()
    );
    ProductConfigSnapshot snapshot = resolve(tenantId, mappedRequest, idempotencyKey, actorId, correlationId);
    UUID catalogId = repository.activeCatalogId(tenantId);
    audit(tenantId, catalogId, "LOANPASS_TENANT_MAPPING_CONSUMED", null, Map.of("tenantMappingAuditRef", auditRef), Map.of("tenantMappingAuditRef", auditRef, "channelCode", mappedChannel, "investorCode", mappedInvestor), actorId, correlationId, idempotencyKey);
    return snapshot;
  }

  ProductConfigSnapshot snapshot(UUID tenantId, UUID snapshotId) { return repository.snapshot(tenantId, snapshotId); }
  ProductPricingConfigurationResponse resolveProductPricingConfiguration(UUID tenantId, String productCode, Instant asOf) {
    Instant effectiveAsOf = asOf == null ? Instant.now() : asOf;
    List<TenantProductAuthorization> rules = tenantAuthorizationRules(tenantId, effectiveAsOf);
    ProductPricingConfigurationResponse response = repository.resolveProductPricingConfiguration(tenantId, productCode, effectiveAsOf);
    if (!isProductAuthorized(rules, response.productCode(), null, null)) throw new CatalogException("PRODUCT_NOT_AUTHORIZED");
    return response;
  }
  ConventionalProductResolveResponse resolveConventionalProducts(UUID tenantId, ConventionalProductResolveRequest request, String actorId, String correlationId) {
    Instant effectiveAsOf = request == null || request.asOf() == null ? Instant.now() : request.asOf();
    List<TenantProductAuthorization> rules = tenantAuthorizationRules(tenantId, effectiveAsOf);
    ConventionalProductResolveResponse response = authorizeConventionalResponse(repository.resolveConventionalProducts(tenantId, request), rules, request.channelCode());
    UUID catalogId = repository.activeCatalogId(tenantId);
    emit(tenantId, catalogId, "ConventionalProductDefinitionResolved.v1", Map.of("eligibleCount", response.eligibleProducts().size(), "rejectedCount", response.rejectedProducts().size()));
    audit(tenantId, catalogId, "CONVENTIONAL_PRODUCT_DEFINITION_RESOLVED", null, response, Map.of("eligibleCount", response.eligibleProducts().size(), "rejectedCount", response.rejectedProducts().size()), actorId, correlationId, null);
    return response;
  }

  private List<TenantProductAuthorization> tenantAuthorizationRules(UUID tenantId, Instant asOf) {
    if (tenantAuthorizationService == null) return List.of();
    List<TenantProductAuthorization> rules = tenantAuthorizationService.getAuthorizedRulesAsOf(tenantId, asOf);
    if (rules.isEmpty()) throw new CatalogException("TENANT_PRODUCT_AUTHORIZATION_CONFIG_REQUIRED");
    return rules;
  }

  private ProductConfigSnapshot authorizeSnapshot(ProductConfigSnapshot snapshot, List<TenantProductAuthorization> rules, String channelCode, String investorCode) {
    if (tenantAuthorizationService == null) return snapshot;
    String requestedInvestor = normalizeOptional(investorCode);
    List<ProductDefinition> products = snapshot.products().stream()
        .filter(product -> isProductAuthorized(rules, product.productCode(), requestedInvestor, channelCode)
            || (requestedInvestor == null && isProductAuthorized(rules, product.productCode(), null, channelCode)))
        .toList();
    if (products.isEmpty()) throw new CatalogException("PRODUCT_NOT_AUTHORIZED");
    Set<String> productCodes = products.stream().map(ProductDefinition::productCode).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    List<InvestorProgram> investors = snapshot.investors().stream()
        .filter(investor -> requestedInvestor == null || requestedInvestor.equals(normalizeOptional(investor.investorCode())))
        .filter(investor -> investor.productCodes().stream().anyMatch(productCodes::contains))
        .toList();
    List<SnapshotProduct> productComponents = snapshot.productComponents().stream()
        .filter(component -> productCodes.contains(component.productCode()))
        .map(component -> new SnapshotProduct(component.productCode(), component.productVersionId(), component.investorCodes().stream().filter(code -> investors.stream().anyMatch(investor -> investor.investorCode().equals(code))).toList(), component.termProfileCodes(), component.pricingConfigRefs()))
        .toList();
    List<SnapshotInvestor> investorComponents = snapshot.investorComponents().stream()
        .filter(component -> investors.stream().anyMatch(investor -> investor.investorCode().equals(component.code())))
        .toList();
    return new ProductConfigSnapshot(snapshot.snapshotId(), snapshot.tenantId(), snapshot.snapshotHash(), snapshot.asOfDate(), products, investors, snapshot.references(), snapshot.markets(), snapshot.asOf(), snapshot.channel(), snapshot.taxonomy(), productComponents, investorComponents, snapshot.referenceVersions(), snapshot.warnings(), snapshot.requestHash(), snapshot.correlationId());
  }

  private ConventionalProductResolveResponse authorizeConventionalResponse(ConventionalProductResolveResponse response, List<TenantProductAuthorization> rules, String channelCode) {
    if (tenantAuthorizationService == null) return response;
    List<ConventionalProductMatch> eligible = new ArrayList<>();
    List<ConventionalProductRejected> rejected = new ArrayList<>(response.rejectedProducts());
    for (ConventionalProductMatch match : response.eligibleProducts()) {
      List<String> authorizedInvestors = match.investorCodes().stream()
          .filter(investor -> isProductAuthorized(rules, match.productCode(), investor, channelCode))
          .toList();
      if (authorizedInvestors.isEmpty()) {
        rejected.add(new ConventionalProductRejected(match.productCode(), "PRODUCT_NOT_AUTHORIZED", "Tenant product authorization does not allow this product for the requested channel or investor."));
      } else {
        eligible.add(new ConventionalProductMatch(match.productCode(), match.productVersionId(), authorizedInvestors, match.configHash()));
      }
    }
    if (eligible.isEmpty()) throw new CatalogException("PRODUCT_NOT_AUTHORIZED");
    return new ConventionalProductResolveResponse(eligible, rejected);
  }

  private static boolean isProductAuthorized(List<TenantProductAuthorization> rules, String productCode, String investorCode, String channelCode) {
    return rules.stream().anyMatch(rule -> rule.matches(productCode, investorCode, channelCode));
  }

  private static String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
  }

  private static void requireMappedTenant(UUID pathTenantId, String mappedTenantId) {
    if (pathTenantId == null || mappedTenantId == null || mappedTenantId.isBlank()) throw new CatalogException("TENANT_MAPPING_TENANT_REQUIRED");
    try {
      UUID resolvedTenantId = UUID.fromString(mappedTenantId.trim());
      if (!pathTenantId.equals(resolvedTenantId)) throw new CatalogException("TENANT_MAPPING_TENANT_MISMATCH");
    } catch (IllegalArgumentException error) {
      throw new CatalogException("TENANT_MAPPING_TENANT_INVALID");
    }
  }

  private static String requireMappedValue(String value, String errorCode) {
    if (value == null || value.isBlank()) throw new CatalogException(errorCode);
    return value.trim();
  }

  TermAmortizationResolveResponse resolveTermAmortization(UUID tenantId, TermAmortizationResolveRequest request, String actorId, String correlationId) {
    if (request == null) throw new CatalogException("TERM_AMORTIZATION_RESOLVE_REQUEST_REQUIRED");
    Instant asOfInstant = request.asOf() == null ? Instant.now() : request.asOf();
    UUID catalogId = repository.activeCatalogId(tenantId);
    TermAmortizationResolveResponse response = repository.resolveTermAmortization(tenantId, catalogId, asOfInstant, request);
    emit(tenantId, catalogId, "TermAmortizationProfileResolved.v1", Map.of("profileCode", response.profileCode(), "amortizationType", request.amortizationType(), "termMonths", request.termMonths()));
    audit(tenantId, catalogId, "TERM_AMORTIZATION_PROFILE_RESOLVED", null, response, Map.of("profileCode", response.profileCode()), actorId, correlationId, null);
    return response;
  }

  PropertyOccupancyResolveResponse resolvePropertyOccupancy(UUID tenantId, PropertyOccupancyResolveRequest request, String actorId, String correlationId) {
    if (request == null) throw new CatalogException("PROPERTY_OCCUPANCY_RESOLVE_REQUEST_REQUIRED");
    Instant asOfInstant = request.asOf() == null ? Instant.now() : request.asOf();
    UUID catalogId = repository.activeCatalogId(tenantId);
    PropertyOccupancyResolveResponse response = repository.resolvePropertyOccupancy(tenantId, catalogId, asOfInstant, request);
    emit(tenantId, catalogId, "PropertyOccupancyResolved.v1", Map.of("propertyType", response.propertyType().code(), "occupancyType", response.occupancyType().code()));
    audit(tenantId, catalogId, "PROPERTY_OCCUPANCY_RESOLVED", null, response, Map.of("propertyType", response.propertyType().code(), "occupancyType", response.occupancyType().code()), actorId, correlationId, null);
    return response;
  }

  PropertyOccupancyListResponse listPublishedPropertyOccupancy(UUID tenantId, Instant asOf) {
    UUID catalogId = repository.activeCatalogId(tenantId);
    return repository.listPublishedPropertyOccupancy(tenantId, catalogId, asOf == null ? Instant.now() : asOf);
  }
  LoanPurposeResolveResponse resolveLoanPurpose(UUID tenantId, LoanPurposeResolveRequest request, String actorId, String correlationId) {
    if (request == null) throw new CatalogException("LOAN_PURPOSE_RESOLVE_REQUEST_REQUIRED");
    Instant asOfInstant = request.asOf() == null ? Instant.now() : request.asOf();
    UUID catalogId = repository.activeCatalogId(tenantId);
    LoanPurposeResolveResponse response = repository.resolveLoanPurpose(tenantId, catalogId, asOfInstant, request);
    emit(tenantId, catalogId, "LoanPurposeCatalogResolved.v1", Map.of("purposeCode", response.purposeCode(), "isCashOut", response.isCashOut(), "isRefinance", response.isRefinance()));
    audit(tenantId, catalogId, "LOAN_PURPOSE_CATALOG_RESOLVED", null, response, Map.of("purposeCode", response.purposeCode()), actorId, correlationId, null);
    return response;
  }
  @Transactional
  InvestorCatalogDraftResponse addInvestorCatalogDraft(UUID tenantId, InvestorCatalogDraftRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    requireIdempotencyKey(idempotencyKey);
    return repository.idempotent(tenantId, idempotencyKey, request, InvestorCatalogDraftResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      InvestorCatalogDraftResponse response = repository.addInvestorCatalogDraft(tenantId, catalogId, request, actorId);
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, "InvestorCatalogChanged.v1", Map.of("investorCode", request.investorCode(), "status", response.status().name(), "activeChannels", request.activeChannelCodes(), "deliveryTypes", request.deliveryTypes(), "requiresMiValidation", Boolean.TRUE.equals(request.requiresMiValidation())));
      audit(tenantId, catalogId, "INVESTOR_CATALOG_CHANGED", before, after, Map.of("investorCode", request.investorCode(), "investorVersionId", response.investorVersionId().toString()), actorId, correlationId, idempotencyKey);
      return response;
    });
  }
  InvestorResolveResponse resolveInvestors(UUID tenantId, InvestorResolveRequest request, String actorId, String correlationId, boolean canViewSecret) {
    if (request == null) throw new CatalogException("INVESTOR_RESOLVE_REQUEST_REQUIRED");
    Instant asOfInstant = request.asOf() == null ? Instant.now() : request.asOf();
    UUID catalogId = repository.activeCatalogId(tenantId);
    InvestorResolveResponse response = repository.resolveInvestors(tenantId, catalogId, asOfInstant, request, canViewSecret);
    if (response.investors().isEmpty()) throw new CatalogException("INVESTOR_NOT_ACTIVE_FOR_CHANNEL");
    emit(tenantId, catalogId, "InvestorCatalogResolved.v1", Map.of("eligibleCount", response.investors().size(), "channelCode", request.channelCode(), "deliveryType", request.deliveryType()));
    audit(tenantId, catalogId, "INVESTOR_CATALOG_RESOLVED", null, response, Map.of("eligibleCount", response.investors().size()), actorId, correlationId, null);
    return response;
  }

  MarketResolveResponse resolveMarket(UUID tenantId, MarketResolveRequest request, String actorId, String correlationId) {
    MarketCatalogPolicy.requireResolvable(request);
    Instant asOfInstant = request.asOf() == null ? Instant.now() : request.asOf();
    UUID catalogId = repository.activeCatalogId(tenantId);
    MarketResolveResponse response = repository.resolveMarket(tenantId, catalogId, asOfInstant, request);
    emit(tenantId, catalogId, "MarketCatalogResolved.v1", Map.of("stateCode", response.stateCode(), "countyFips", Objects.toString(response.countyFips(), ""), "marketStatus", response.marketStatus()));
    audit(tenantId, catalogId, "MARKET_CATALOG_RESOLVED", null, response, Map.of("stateCode", response.stateCode(), "countyFips", Objects.toString(response.countyFips(), ""), "marketStatus", response.marketStatus()), actorId, correlationId, null);
    return response;
  }
  ProductTaxonomyResolveResponse resolveProductTaxonomy(UUID tenantId, ProductTaxonomyResolveRequest request, String actorId, String correlationId) {
    Instant asOfInstant = request.asOf() == null ? Instant.now() : request.asOf();
    List<String> codes = request.codes() == null ? List.of() : request.codes();
    if (codes.isEmpty()) throw new CatalogException("PRODUCT_TAXONOMY_CODES_REQUIRED");
    UUID catalogId = repository.activeCatalogId(tenantId);
    List<ProductTaxonomyResolvedEntry> entries = repository.resolveProductTaxonomy(tenantId, catalogId, asOfInstant, codes);
    if (entries.size() != new LinkedHashSet<>(codes).size()) throw new CatalogException("PRODUCT_TAXONOMY_CODE_UNKNOWN");
    audit(tenantId, catalogId, "PRODUCT_TAXONOMY_RESOLVED", null, entries, Map.of("codes", codes), actorId, correlationId, null);
    return new ProductTaxonomyResolveResponse(asOfInstant, entries);
  }
  ChannelResolveResponse resolveChannel(UUID tenantId, ChannelResolveRequest request, String actorId, String correlationId) {
    if (request == null) throw new CatalogException("CHANNEL_RESOLVE_REQUEST_REQUIRED");
    Instant asOfInstant = request.asOf() == null ? Instant.now() : request.asOf();
    UUID catalogId = repository.activeCatalogId(tenantId);
    ChannelResolveResponse response = repository.resolveChannel(tenantId, catalogId, asOfInstant, request);
    if (response.requiresBranchAssignment() && (request.actorId() == null || request.actorId().isBlank())) throw new CatalogException("CHANNEL_NOT_ASSIGNED_TO_ACTOR");
    emit(tenantId, catalogId, "ChannelCatalogResolved.v1", Map.of("channelCode", response.channelCode()));
    audit(tenantId, catalogId, "CHANNEL_CATALOG_RESOLVED", null, response, Map.of("channelCode", response.channelCode()), actorId, correlationId, null);
    return response;
  }
  List<CatalogEvent> events(UUID tenantId) { return repository.events(tenantId); }
  List<CatalogAuditRecord> audit(UUID tenantId) { return repository.audit(tenantId); }
  List<CatalogVersionControlRecord> versions(UUID tenantId) { return repository.versionControls(tenantId, repository.currentCatalogId(tenantId)); }
  CatalogRepository getRepository() { return repository; }

  private CatalogResponse transition(UUID tenantId, CatalogStatus expected, CatalogStatus next, String action, Object request, String idempotencyKey, String actorId, String correlationId) {
    return repository.idempotent(tenantId, idempotencyKey, request, CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      repository.transition(tenantId, catalogId, expected, next);
      CatalogResponse after = next == CatalogStatus.PUBLISHED ? repository.active(tenantId) : repository.current(tenantId);
      emit(tenantId, catalogId, action + ".v1", Map.of("from", expected.name(), "to", next.name()));
      audit(tenantId, catalogId, action, before, after, Map.of("from", expected.name(), "to", next.name()), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  private static void requireRole(String required, Set<String> allowed) {
    String roles = RequestContext.roles();
    if (roles == null || roles.isBlank()) throw new CatalogException("ROLE_REQUIRED_" + required);
    boolean ok = Arrays.stream(roles.split(",")).map(String::trim).anyMatch(allowed::contains);
    if (!ok) throw new CatalogException("ROLE_REQUIRED_" + required);
  }

  private static boolean hasRole(String role) {
    String roles = RequestContext.roles();
    return roles != null && Arrays.stream(roles.split(",")).map(String::trim).anyMatch(role::equals);
  }

  private static void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) throw new CatalogException("IDEMPOTENCY_KEY_REQUIRED");
  }

  private static void requireVersionActionRole(String action) {
    switch (action) {
      case "VALIDATE", "SUBMIT_APPROVAL" -> requireRole("CATALOG_WRITER", WRITER_ROLES);
      case "APPROVE", "REJECT" -> requireRole("CATALOG_APPROVER", APPROVER_ROLES);
      case "PUBLISH", "SUSPEND", "RETIRE" -> requireRole("CATALOG_PUBLISHER", PUBLISHER_ROLES);
      case "ROLLBACK" -> requireRole("CATALOG_ADMIN", Set.of("CATALOG_ADMIN"));
      default -> throw new CatalogException("INVALID_VERSION_ACTION");
    }
  }

  private void enforceSoD(String submitterId, String approverId) {
    authorizationService.enforceSoD(approverId, submitterId, "SUBMITTED:" + submitterId);
  }

  private void emit(UUID tenantId, UUID catalogId, String eventType, Map<String, Object> payload) {
    repository.event(new CatalogEvent(UUID.randomUUID(), tenantId, catalogId, eventType, Instant.now(), payload));
  }

  private void audit(UUID tenantId, UUID catalogId, String action, Object before, Object after, Map<String, Object> payload, String actorId, String correlationId, String idempotencyKey) {
    repository.audit(tenantId, catalogId, action, repository.replayHash(tenantId, catalogId), before, after, payload, actorId, correlationId, idempotencyKey);
  }
}
