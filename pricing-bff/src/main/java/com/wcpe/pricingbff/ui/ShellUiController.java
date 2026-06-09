package com.wcpe.pricingbff.ui;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ShellUiController {
  private final PricingBffUiFallbackAdapter adapter;

  ShellUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @GetMapping("/api/ui/health")
  Object health(@RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
    return adapter.health(correlationId);
  }

  @GetMapping("/api/v1/ui/menus/{persona}")
  Object menu(@PathVariable String persona) {
    return adapter.menu(persona);
  }

  @GetMapping("/api/v1/ui/notices")
  Object notices() {
    return adapter.notices();
  }

  @GetMapping("/api/v1/ui/alerts/current")
  Object alerts() {
    return adapter.alerts();
  }
}
