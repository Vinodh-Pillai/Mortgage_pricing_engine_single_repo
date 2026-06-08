package com.wcpe.governance;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceHealthController {
  @GetMapping("/api/governance/health")
  public Map<String, String> health() {
    return Map.of(
        "service", "governance-service",
        "status", "UP",
        "capability", "local/dev deployment health adapter only");
  }
}
