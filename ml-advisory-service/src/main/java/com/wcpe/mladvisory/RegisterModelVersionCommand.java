package com.wcpe.mladvisory;

import java.util.List;
import java.util.Map;

public record RegisterModelVersionCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    String modelName,
    String semanticVersion,
    List<AdvisoryType> advisoryTypes,
    AllowedUse allowedUse,
    String artifactUri,
    String artifactChecksum,
    String featureSchemaVersion,
    String owner,
    List<ModelEvidence> evidence,
    Map<String, String> lineageRefs,
    String correlationId) {
  public RegisterModelVersionCommand {
    advisoryTypes = advisoryTypes == null ? List.of() : List.copyOf(advisoryTypes);
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
    lineageRefs = lineageRefs == null ? Map.of() : Map.copyOf(lineageRefs);
  }
}
