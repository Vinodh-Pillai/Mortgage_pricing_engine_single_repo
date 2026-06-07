package com.wcpe.observability.cache;

import java.util.Objects;

public final class CacheEnvelopeSerializer {
  public String toJson(CacheEnvelope envelope) {
    Objects.requireNonNull(envelope, "envelope is required");
    return "{"
        + "\"schemaVersion\":" + envelope.schemaVersion()
        + ",\"tenantId\":\"" + escape(envelope.tenantId().toString()) + "\""
        + ",\"createdAt\":\"" + escape(envelope.createdAt().toString()) + "\""
        + ",\"expiresAt\":\"" + escape(envelope.expiresAt().toString()) + "\""
        + ",\"producerVersion\":\"" + escape(envelope.producerVersion()) + "\""
        + ",\"payloadHash\":\"" + escape(envelope.payloadHash()) + "\""
        + ",\"compressed\":" + envelope.compressed()
        + "}";
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
