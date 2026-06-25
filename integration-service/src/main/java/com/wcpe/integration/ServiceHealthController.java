package com.wcpe.integration;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ServiceHealthController {

  @GetMapping("/api/v1/integration/health")
  Map<String, Object> health() {
    return Map.of(
        "service", "integration-service",
        "status", "UP",
        "runtimeSurface", "health-only");
  }
}
