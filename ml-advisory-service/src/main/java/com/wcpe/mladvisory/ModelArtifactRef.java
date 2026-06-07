package com.wcpe.mladvisory;

public record ModelArtifactRef(
    String modelVersionId,
    String artifactUri,
    String registryChecksum,
    String actualChecksum,
    String approvalStatus,
    String schemaVersion) {}
