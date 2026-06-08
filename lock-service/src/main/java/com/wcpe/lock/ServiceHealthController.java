package com.wcpe.lock;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceHealthController {
  @GetMapping("/api/lock/health")
  public Map<String, String> health() {
    return Map.of(
        "service", "lock-service",
        "status", "UP",
        "capability", "local/dev deployment health adapter only");
  }
}
