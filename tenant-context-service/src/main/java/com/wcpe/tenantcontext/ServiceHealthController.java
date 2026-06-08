package com.wcpe.tenantcontext;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceHealthController {
  @GetMapping("/api/tenant-context/health")
  public Map<String, String> health() {
    return Map.of(
        "service", "tenant-context-service",
        "status", "UP",
        "capability", "local/dev deployment health adapter only");
  }
}
