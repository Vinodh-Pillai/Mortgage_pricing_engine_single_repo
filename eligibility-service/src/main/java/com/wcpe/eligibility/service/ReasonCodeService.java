package com.wcpe.eligibility.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.models.ReasonCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class ReasonCodeService {
    private final List<ReasonCode> reasonCodes;

    public ReasonCodeService(ObjectMapper objectMapper) throws IOException {
        try (var is = new ClassPathResource("reason-codes.json").getInputStream()) {
            Map<String, Object> data = objectMapper.readValue(is, new TypeReference<>() {});
            this.reasonCodes = objectMapper.convertValue(
                data.get("reasonCodes"), new TypeReference<List<ReasonCode>>() {}
            );
        }
    }

    public List<ReasonCode> getAll() {
        return reasonCodes;
    }

    public List<ReasonCode> getByRuleCode(String ruleCode) {
        return reasonCodes.stream()
            .filter(rc -> rc.ruleCode().equals(ruleCode))
            .toList();
    }
}
