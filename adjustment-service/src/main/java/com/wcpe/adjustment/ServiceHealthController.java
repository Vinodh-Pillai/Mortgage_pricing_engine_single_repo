package com.wcpe.adjustment;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceHealthController {
  @GetMapping("/api/adjustment/health")
  public Map<String, String> health() {
    return Map.of(
        "service", "adjustment-service",
        "status", "UP",
        "capability", "local/dev deployment health adapter only");
  }
}
