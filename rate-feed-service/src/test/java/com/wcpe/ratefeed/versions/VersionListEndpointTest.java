package com.wcpe.ratefeed.domain;

import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests version listing and filtering from the version endpoint.
 */
@ExtendWith(MockitoExtension.class)
class VersionListEndpointTest {
  @Mock
  org.springframework.jdbc.core.JdbcTemplate mockJdbc;

  RateFeedService service;

  @BeforeEach
  void setup() {
    RequestContext.roles("RATE_FEED_VIEW,RATE_FEED_UPLOAD,RATE_FEED_ACTIVATE");
    lenient().when(mockJdbc.query(isA(String.class), isA(org.springframework.jdbc.core.RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
      return new ArrayList<>();
    });
    lenient().when(mockJdbc.queryForObject(isA(String.class), eq(Integer.class), any())).thenReturn(1);
    service = TestRateFeedServices.create(new RateFeedRepository(mockJdbc, new com.fasterxml.jackson.databind.ObjectMapper()), mockJdbc);
  }

  @AfterEach
  void cleanup() {
    RequestContext.clear();
  }

  @Test
  void listVersions_allParams() {
    // When roles are set, listVersions should execute
    assertDoesNotThrow(() -> service.listVersions(
        UUID.randomUUID(), UUID.randomUUID(), "PMK"));
  }

  @Test
  void listVersions_investorOnly() {
    assertDoesNotThrow(() -> service.listVersions(UUID.randomUUID(), null, null));
  }

  @Test
  void listVersions_channelOnly() {
    assertDoesNotThrow(() -> service.listVersions(null, UUID.randomUUID(), null));
  }

  @Test
  void listVersions_productOnly() {
    assertDoesNotThrow(() -> service.listVersions(null, null, "PMK"));
  }

  @Test
  void listVersions_noParams() {
    assertDoesNotThrow(() -> service.listVersions(null, null, null));
  }

  @Test
  void listVersions_requiresViewRole() {
    RequestContext.clear();
    RequestContext.roles("SOME_OTHER_ROLE");
    assertThrows(RateFeedException.class, () -> service.listVersions(null, null, null));
  }
}
