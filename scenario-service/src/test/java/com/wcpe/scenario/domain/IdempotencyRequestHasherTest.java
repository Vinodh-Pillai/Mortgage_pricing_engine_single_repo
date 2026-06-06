package com.wcpe.scenario.domain;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class IdempotencyRequestHasherTest {
  private final ObjectMapper mapper = new ObjectMapper()
      .findAndRegisterModules()
      .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  @Test
  void sameLogicalPayloadSameHash() {
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("borrowers", List.of(Map.of("borrowerRole", "PRIMARY", "creditScore", 742)));
    first.put("scenarioVersion", 1);
    Map<String, Object> second = new LinkedHashMap<>();
    second.put("scenarioVersion", 1);
    second.put("borrowers", List.of(Map.of("creditScore", 742, "borrowerRole", "PRIMARY")));

    assertThat(IdempotencyRequestHasher.hash(mapper, "tenant:scenario:borrowers", first))
        .isEqualTo(IdempotencyRequestHasher.hash(mapper, "tenant:scenario:borrowers", second));
  }

  @Test
  void differentScenarioPathChangesHash() {
    BorrowerCreditRequest request = new BorrowerCreditRequest(1, List.of(
        new BorrowerCredit("B1", "PRIMARY", true, "AVAILABLE", 742, "TRI_MERGE", LocalDate.now())));

    String first = IdempotencyRequestHasher.hash(mapper, "tenant:scenario-a:borrowers", request);
    String second = IdempotencyRequestHasher.hash(mapper, "tenant:scenario-b:borrowers", request);

    assertThat(second).isNotEqualTo(first);
  }
}
