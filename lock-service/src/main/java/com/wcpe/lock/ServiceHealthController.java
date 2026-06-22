package com.wcpe.lock;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceHealthController {
  private final LockPersistenceGate persistenceGate;

  public ServiceHealthController(LockPersistenceGate persistenceGate) {
    this.persistenceGate = persistenceGate;
  }

  @GetMapping("/api/lock/health")
  public Map<String, String> health() {
    return Map.of(
        "service", "lock-service",
        "status", persistenceGate.lifecycleRoutesEnabled() ? "UP" : "DOWN",
        "capability", "tenant-scoped lock lifecycle HTTP surface",
        "persistenceMode", persistenceGate.mode(),
        "readiness", persistenceGate.readinessMessage());
  }
}
