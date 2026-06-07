package com.wcpe.mladvisory;

import java.util.Set;

public record MonitoringDispositionCommand(
    String tenantId,
    String alertId,
    String actorId,
    Set<String> actorRoles,
    String dispositionReason,
    String governanceTicket,
    String correlationId) {
  public MonitoringDispositionCommand {
    actorRoles = actorRoles == null ? Set.of() : Set.copyOf(actorRoles);
  }
}
