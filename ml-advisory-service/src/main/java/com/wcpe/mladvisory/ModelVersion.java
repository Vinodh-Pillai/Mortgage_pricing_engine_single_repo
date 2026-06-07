package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ModelVersion(
    String modelVersionId,
    String tenantId,
    String modelName,
    String semanticVersion,
    List<AdvisoryType> advisoryTypes,
    AllowedUse allowedUse,
    ModelStatus status,
    String artifactUri,
    String artifactChecksum,
    String featureSchemaVersion,
    String createdBy,
    Instant createdAt,
    String approvedBy,
    Instant approvedAt,
    String retiredAt,
    List<ModelEvidence> evidence,
    Map<String, String> lineageRefs,
    int version) {
  public ModelVersion {
    advisoryTypes = advisoryTypes == null ? List.of() : List.copyOf(advisoryTypes);
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
    lineageRefs = lineageRefs == null ? Map.of() : Map.copyOf(lineageRefs);
  }

  ModelVersion withStatus(ModelStatus newStatus, String actorId, Instant approvedAt, int newVersion) {
    return new ModelVersion(
        modelVersionId,
        tenantId,
        modelName,
        semanticVersion,
        advisoryTypes,
        allowedUse,
        newStatus,
        artifactUri,
        artifactChecksum,
        featureSchemaVersion,
        createdBy,
        createdAt,
        actorId,
        approvedAt,
        retiredAt,
        evidence,
        lineageRefs,
        newVersion);
  }
}
