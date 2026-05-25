package com.wcpe.catalog.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class DiffService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    DiffService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    DiffResponse computeDiff(UUID tenantId, int versionA, int versionB) {
        // Load version A snapshot
        List<Map<String, Object>> versionARows = jdbc.queryForList(
            "select artifact_type, artifact_code, snapshot_json, config_hash from catalog.catalog_version_control " +
            "where tenant_id = ? and version_number = ? and status in ('PUBLISHED', 'ACTIVE') " +
            "order by artifact_type, artifact_code",
            tenantId, versionA);

        // Load version B snapshot
        List<Map<String, Object>> versionBRows = jdbc.queryForList(
            "select artifact_type, artifact_code, snapshot_json, config_hash from catalog.catalog_version_control " +
            "where tenant_id = ? and version_number = ? and status in ('PUBLISHED', 'ACTIVE') " +
            "order by artifact_type, artifact_code",
            tenantId, versionB);

        Map<String, Object> versionAMeta = Map.of(
            "version", versionA,
            "artifactCount", versionARows.size(),
            "tenantId", tenantId.toString()
        );
        Map<String, Object> versionBMeta = Map.of(
            "version", versionB,
            "artifactCount", versionBRows.size(),
            "tenantId", tenantId.toString()
        );

        // Index by artifact_type:artifact_code
        Map<String, Map<String, Object>> aMap = new LinkedHashMap<>();
        for (Map<String, Object> row : versionARows) {
            String key = row.get("artifact_type") + ":" + row.get("artifact_code");
            aMap.put(key, parseJsonb(row.get("snapshot_json")));
        }

        Map<String, Map<String, Object>> bMap = new LinkedHashMap<>();
        for (Map<String, Object> row : versionBRows) {
            String key = row.get("artifact_type") + ":" + row.get("artifact_code");
            bMap.put(key, parseJsonb(row.get("snapshot_json")));
        }

        List<VersionDiff> diffs = new ArrayList<>();
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(aMap.keySet());
        allKeys.addAll(bMap.keySet());

        // Find REMOVED artifacts
        for (String key : aMap.keySet()) {
            if (!bMap.containsKey(key)) {
                String[] parts = key.split(":", 2);
                Map<String, Object> oldAttrs = aMap.get(key);
                for (Map.Entry<String, Object> entry : oldAttrs.entrySet()) {
                    diffs.add(new VersionDiff(
                        UUID.randomUUID(),
                        versionA, versionB,
                        parts.length > 0 ? parts[0] : "",
                        parts.length > 1 ? parts[1] : "",
                        "REMOVED",
                        entry.getKey(),
                        entry.getValue() != null ? entry.getValue().toString() : null,
                        null
                    ));
                }
            }
        }

        // Find ADDED and MODIFIED artifacts
        for (String key : bMap.keySet()) {
            String[] parts = key.split(":", 2);
            if (!aMap.containsKey(key)) {
                // ADDED
                Map<String, Object> newAttrs = bMap.get(key);
                for (Map.Entry<String, Object> entry : newAttrs.entrySet()) {
                    diffs.add(new VersionDiff(
                        UUID.randomUUID(),
                        versionA, versionB,
                        parts.length > 0 ? parts[0] : "",
                        parts.length > 1 ? parts[1] : "",
                        "ADDED",
                        entry.getKey(),
                        null,
                        entry.getValue() != null ? entry.getValue().toString() : null
                    ));
                }
            } else {
                // Compare attribute-level changes for MODIFIED
                Map<String, Object> oldAttrs = aMap.get(key);
                Map<String, Object> newAttrs = bMap.get(key);
                Set<String> attrKeys = new HashSet<>();
                attrKeys.addAll(oldAttrs.keySet());
                attrKeys.addAll(newAttrs.keySet());
                for (String attr : attrKeys) {
                    Object oldVal = oldAttrs.get(attr);
                    Object newVal = newAttrs.get(attr);
                    if (!Objects.equals(oldVal, newVal)) {
                        diffs.add(new VersionDiff(
                            UUID.randomUUID(),
                            versionA, versionB,
                            parts.length > 0 ? parts[0] : "",
                            parts.length > 1 ? parts[1] : "",
                            "MODIFIED",
                            attr,
                            oldVal != null ? oldVal.toString() : null,
                            newVal != null ? newVal.toString() : null
                        ));
                    }
                }
            }
        }

        return new DiffResponse(
            versionAMeta,
            versionBMeta,
            diffs,
            diffs.size(),
            Instant.now()
        );
    }

    private Map<String, Object> parseJsonb(Object jsonb) {
        if (jsonb == null) return Map.of();
        if (jsonb instanceof Map) return (Map<String, Object>) jsonb;
        try {
            return mapper.readValue(jsonb.toString(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
