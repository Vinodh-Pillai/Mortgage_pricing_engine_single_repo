package com.wcpe.margin;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceHealthController {
  @GetMapping("/api/margin/health")
  public Map<String, String> health() {
    return Map.of(
        "service", "margin-service",
        "status", "UP",
        "capability", "local/dev deployment health adapter only");
  }
}
