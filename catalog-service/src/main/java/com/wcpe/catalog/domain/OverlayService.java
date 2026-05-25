package com.wcpe.catalog.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OverlayService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    OverlayService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    OverlayCreateResponse createOverlay(UUID tenantId, OverlayCreateRequest req, String actorId) {
        if (req.productCode() == null || req.productCode().isBlank()) {
            throw new IllegalArgumentException("PRODUCT_CODE_REQUIRED");
        }
        if (req.attribute() == null || req.attribute().isBlank()) {
            throw new IllegalArgumentException("ATTRIBUTE_REQUIRED");
        }
        if (req.overrideValue() == null || req.overrideValue().isBlank()) {
            throw new IllegalArgumentException("OVERRIDE_VALUE_REQUIRED");
        }
        if (req.effectiveDate() == null) {
            throw new IllegalArgumentException("EFFECTIVE_DATE_REQUIRED");
        }
        if (req.expiryDate() != null && !req.expiryDate().isAfter(req.effectiveDate())) {
            throw new IllegalArgumentException("EXPIRY_DATE_INVALID");
        }
        if (req.reason() == null || req.reason().isBlank()) {
            throw new IllegalArgumentException("REASON_REQUIRED");
        }

        // Idempotency check
        String idempKey = req.idempotencyKey();
        if (idempKey != null) {
            List<Map<String, Object>> existing = jdbc.queryForList(
                "select overlay_id from catalog.product_overlay where tenant_id = ? and " +
                "product_code = ? and attribute = ? and effective_date = ? and created_by = ? limit 1",
                tenantId, req.productCode(), req.attribute(), java.sql.Date.valueOf(req.effectiveDate()), actorId);
            if (!existing.isEmpty()) {
                UUID existingId = (UUID) existing.get(0).get("overlay_id");
                return getOverlayResponse(existingId, tenantId, req);
            }
        }

        UUID overlayId = UUID.randomUUID();
        String createdBy = actorId != null ? actorId : "SYSTEM";
        java.sql.Date expiry = req.expiryDate() != null ? java.sql.Date.valueOf(req.expiryDate()) : null;

        int inserted = jdbc.update(
            "insert into catalog.product_overlay (overlay_id, tenant_id, product_code, attribute, override_value, effective_date, expiry_date, reason, status, created_by) " +
            "values (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?) " +
            "on conflict (tenant_id, product_code, attribute, effective_date, created_by) do nothing",
            overlayId, tenantId, req.productCode(), req.attribute(), req.overrideValue(),
            java.sql.Date.valueOf(req.effectiveDate()), expiry, req.reason(), createdBy);

        if (inserted == 0) {
            throw new IllegalArgumentException("OVERLAY_CONFLICT");
        }

        return getOverlayResponse(overlayId, tenantId, req);
    }

    OverlayResolveResponse resolveOverlay(UUID tenantId, OverlayResolveRequest req) {
        LocalDate asOf = req.asOfDate() != null ? req.asOfDate() : LocalDate.now();

        // Load base attributes from product_definition
        Map<String, String> baseAttributes = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> products = jdbc.queryForList(
                "select product_code, product_name, product_family, allowed_channels, allowed_states, effective_from, effective_to " +
                "from catalog.product_definition where tenant_id = ? and product_code = ? order by effective_from desc limit 1",
                tenantId, req.productCode());
            if (!products.isEmpty()) {
                Map<String, Object> p = products.get(0);
                baseAttributes.put("product_code", Objects.toString(p.get("product_code"), ""));
                baseAttributes.put("product_name", Objects.toString(p.get("product_name"), ""));
                baseAttributes.put("product_family", Objects.toString(p.get("product_family"), ""));
                Object ch = p.get("allowed_channels");
                if (ch != null) baseAttributes.put("allowed_channels", ch.toString());
                Object st = p.get("allowed_states");
                if (st != null) baseAttributes.put("allowed_states", st.toString());
                Object ef = p.get("effective_from");
                if (ef instanceof java.sql.Date) baseAttributes.put("effective_from", ef.toString());
                Object et = p.get("effective_to");
                if (et instanceof java.sql.Date) baseAttributes.put("effective_to", et.toString());
            }
        } catch (Exception e) {
            // No base product found, continue with empty base
        }

        // Load active overlays
        List<AppliedOverlay> appliedOverlays = new ArrayList<>();
        List<Map<String, Object>> overlayRows = jdbc.queryForList(
            "select overlay_id, attribute, override_value, effective_date, reason from catalog.product_overlay " +
            "where tenant_id = ? and product_code = ? and status = 'ACTIVE' " +
            "and effective_date <= ? " +
            "and (expiry_date IS NULL OR expiry_date > ?) " +
            "order by effective_date desc",
            tenantId, req.productCode(), java.sql.Date.valueOf(asOf), java.sql.Date.valueOf(asOf));

        for (Map<String, Object> row : overlayRows) {
            String attr = (String) row.get("attribute");
            String override = (String) row.get("override_value");
            String original = baseAttributes.getOrDefault(attr, "");
            appliedOverlays.add(new AppliedOverlay(
                (UUID) row.get("overlay_id"),
                attr,
                original,
                override,
                ((java.sql.Date) row.get("effective_date")).toLocalDate(),
                (String) row.get("reason")
            ));
        }

        // Apply overlays to base attributes
        Map<String, String> resolvedAttributes = new LinkedHashMap<>(baseAttributes);
        for (AppliedOverlay ao : appliedOverlays) {
            resolvedAttributes.put(ao.attribute(), ao.overrideValue());
        }

        return new OverlayResolveResponse(
            req.productCode(),
            req.investorCode(),
            req.channelCode(),
            baseAttributes,
            appliedOverlays,
            resolvedAttributes,
            Instant.now()
        );
    }

    private OverlayCreateResponse getOverlayResponse(UUID overlayId, UUID tenantId, OverlayCreateRequest req) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "select overlay_id, product_code, attribute, override_value, effective_date, expiry_date, status, created_by, created_at " +
            "from catalog.product_overlay where overlay_id = ? and tenant_id = ?",
            overlayId, tenantId);
        if (rows.isEmpty()) {
            return new OverlayCreateResponse(
                overlayId, req.productCode(), req.attribute(), req.overrideValue(),
                req.effectiveDate(), req.expiryDate(), "ACTIVE", "SYSTEM", Instant.now()
            );
        }
        Map<String, Object> r = rows.get(0);
        return new OverlayCreateResponse(
            (UUID) r.get("overlay_id"),
            (String) r.get("product_code"),
            (String) r.get("attribute"),
            (String) r.get("override_value"),
            ((java.sql.Date) r.get("effective_date")).toLocalDate(),
            r.get("expiry_date") != null ? ((java.sql.Date) r.get("expiry_date")).toLocalDate() : null,
            (String) r.get("status"),
            (String) r.get("created_by"),
            ((java.sql.Timestamp) r.get("created_at")).toInstant()
        );
    }
}
