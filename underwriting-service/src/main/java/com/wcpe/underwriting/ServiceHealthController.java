package com.wcpe.underwriting;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ServiceHealthController {
  @GetMapping("/health")
  Map<String, String> health() {
    return Map.of("status", "UP", "service", "underwriting-service");
  }
}
