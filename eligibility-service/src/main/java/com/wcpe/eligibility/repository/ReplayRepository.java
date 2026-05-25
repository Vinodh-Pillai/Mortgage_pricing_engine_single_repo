package com.wcpe.eligibility.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Repository
public class ReplayRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ReplayRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper.findAndRegisterModules();
    }

    public void saveReplay(UUID tenantId, Map<String, Object> replayData) {
        String json;
        try {
            json = mapper.writeValueAsString(replayData);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize replay data", e);
        }
        jdbc.update(
            "insert into eligibility.replay_record(tenant_id,replay_id,input_hash,output_hash,replay_status,policy_version,rule_set_version,replay_json,occurred_at) values (?,?,?,?,?,?,?,?,?)",
            tenantId,
            UUID.fromString((String) replayData.get("replayId")),
            (String) replayData.get("inputHash"),
            (String) replayData.get("outputHash"),
            (String) replayData.get("status"),
            (int) replayData.get("policyVersion"),
            (int) replayData.get("ruleSetVersion"),
            json,
            Timestamp.from(Instant.now())
        );
    }
}
