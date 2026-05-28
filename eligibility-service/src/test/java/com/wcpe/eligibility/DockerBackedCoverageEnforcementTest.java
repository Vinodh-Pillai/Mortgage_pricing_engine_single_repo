package com.wcpe.eligibility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.DockerClientFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DockerBackedCoverageEnforcementTest {

  @Test
  @EnabledIfSystemProperty(named = "eligibility.enforceDockerTests", matches = "true")
  void dockerBackedValidationFailsWhenDockerIsUnavailable() {
    assertDoesNotThrow(
        () -> DockerClientFactory.instance().client(),
        "Docker-backed eligibility validation was requested, but Testcontainers cannot access Docker");
  }
}
