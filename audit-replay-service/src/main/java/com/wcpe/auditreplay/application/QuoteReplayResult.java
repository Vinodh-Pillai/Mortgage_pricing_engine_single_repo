package com.wcpe.auditreplay.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.wcpe.auditreplay.domain.QuoteReplayRun;

public record QuoteReplayResult(QuoteReplayRun run, JsonNode diff, String evidenceExportRef) {
}
