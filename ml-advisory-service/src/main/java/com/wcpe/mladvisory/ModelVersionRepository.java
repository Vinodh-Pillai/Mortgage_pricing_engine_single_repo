package com.wcpe.mladvisory;

import java.util.List;
import java.util.Optional;

interface ModelVersionRepository {
  void save(ModelVersion version);

  Optional<ModelVersion> find(String tenantId, String modelVersionId);

  boolean existsByTenantModelAndSemanticVersion(String tenantId, String modelName, String semanticVersion);

  List<ModelVersion> list(String tenantId, ModelStatus status, AdvisoryType advisoryType);
}
