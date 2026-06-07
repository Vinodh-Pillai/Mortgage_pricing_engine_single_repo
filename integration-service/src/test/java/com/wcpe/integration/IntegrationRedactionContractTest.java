package com.wcpe.integration;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class IntegrationRedactionContractTest {
  @Test
  void contractFixturesDoNotExposeForbiddenSecretOrBorrowerTokens() throws IOException {
    IntegrationContractFixtureSupport.assertAllFixturesRedacted();
  }
}
