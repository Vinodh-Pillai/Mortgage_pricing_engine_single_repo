package com.wcpe.auditreplay.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.wcpe.auditreplay.domain.LockReplayRun;

public record LockReplayResult(LockReplayRun run, JsonNode diff, String evidenceExportRef) {
}
