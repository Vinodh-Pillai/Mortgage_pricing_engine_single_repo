package com.wcpe.mladvisory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class InMemoryModelVersionRepository implements ModelVersionRepository {
  private final Map<String, ModelVersion> versions = new HashMap<>();

  @Override
  public void save(ModelVersion version) {
    versions.put(versionKey(version.tenantId(), version.modelVersionId()), version);
  }

  @Override
  public Optional<ModelVersion> find(String tenantId, String modelVersionId) {
    return Optional.ofNullable(versions.get(versionKey(tenantId, modelVersionId)));
  }

  @Override
  public boolean existsByTenantModelAndSemanticVersion(String tenantId, String modelName, String semanticVersion) {
    String uniqueKey = versionUniqueKey(tenantId, modelName, semanticVersion);
    return versions.values().stream().anyMatch(version -> uniqueKey.equals(versionUniqueKey(version)));
  }

  @Override
  public List<ModelVersion> list(String tenantId, ModelStatus status, AdvisoryType advisoryType) {
    return versions.values().stream()
        .filter(version -> version.tenantId().equals(tenantId))
        .filter(version -> status == null || version.status() == status)
        .filter(version -> advisoryType == null || version.advisoryTypes().contains(advisoryType))
        .sorted(Comparator.comparing(ModelVersion::createdAt).reversed())
        .toList();
  }

  private String versionKey(String tenantId, String modelVersionId) {
    return tenantId + ":" + modelVersionId;
  }

  private String versionUniqueKey(ModelVersion version) {
    return versionUniqueKey(version.tenantId(), version.modelName(), version.semanticVersion());
  }

  private String versionUniqueKey(String tenantId, String modelName, String semanticVersion) {
    return tenantId + ":" + normalized(modelName) + ":" + normalized(semanticVersion);
  }

  private String normalized(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }
}
