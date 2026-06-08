package com.wcpe.quote;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceHealthController {
  @GetMapping("/api/quote/health")
  public Map<String, String> health() {
    return Map.of(
        "service", "quote-service",
        "status", "UP",
        "capability", "local/dev deployment health adapter only");
  }
}
