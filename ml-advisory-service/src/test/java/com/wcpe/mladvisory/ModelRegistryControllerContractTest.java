package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class ModelRegistryControllerContractTest {
  @Test
  void shouldRequireChecksumAndAllowedUseAdvisoryOnly() {
    ModelRegistryService service = new ModelRegistryService(java.time.Clock.systemUTC(), new TestModelVersionRepository());

    MlAdvisoryResult<ModelVersionResponse> missingChecksum =
        service.register(ModelVersionGovernanceFixtures.registerCommand("", AllowedUse.ADVISORY_ONLY, true));

    assertFalse(missingChecksum.valid());
    assertEquals("ML_MODEL_CHECKSUM_REQUIRED", missingChecksum.errorCode().orElseThrow());
    assertEquals(ModelRegistryService.REGISTER_ENDPOINT, "POST /api/v1/tenants/{tenantId}/ml-advisory/model-versions");
  }

  @Test
  void productionDefaultRegistryConstructorFailsClosedWithoutJdbcRepository() {
    IllegalStateException failure = assertThrows(IllegalStateException.class, ModelRegistryService::new);

    assertTrueMessageContains(failure, "JDBC/PostgreSQL-backed ModelVersionRepository");
  }

  @Test
  void shouldExposeModelVersionPostMapping() throws NoSuchMethodException {
    RestController restController = ModelVersionGovernanceController.class.getAnnotation(RestController.class);
    RequestMapping requestMapping = ModelVersionGovernanceController.class.getAnnotation(RequestMapping.class);
    Method register =
        ModelVersionGovernanceController.class.getMethod(
            "register", String.class, ModelVersionGovernanceController.RegisterModelVersionRequest.class);
    PostMapping postMapping = register.getAnnotation(PostMapping.class);

    assertNotNull(restController);
    assertNotNull(requestMapping);
    assertEquals("/api/v1/tenants/{tenantId}/ml-advisory/model-versions", requestMapping.value()[0]);
    assertNotNull(postMapping);
  }

  private void assertTrueMessageContains(IllegalStateException failure, String expected) {
    org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains(expected));
  }
}
