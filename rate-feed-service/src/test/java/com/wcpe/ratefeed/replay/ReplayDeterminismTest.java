package com.wcpe.ratefeed.replay;

import com.wcpe.ratefeed.domain.*;
import com.wcpe.ratefeed.service.ReplayService;
import com.wcpe.ratefeed.service.ReplayRepository;
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
import static org.mockito.Mockito.*;

/**
 * Tests replay determinism: same input produces same outputHash.
 */
@ExtendWith(MockitoExtension.class)
class ReplayDeterminismTest {
  @Mock
  ReplayRepository mockRepo;

  ReplayService replayService;

  @BeforeEach
  void setup() {
    RequestContext.roles("RATE_FEED_VIEW,RATE_FEED_UPLOAD,RATE_FEED_ACTIVATE");
    replayService = new ReplayService(
        mock(org.springframework.jdbc.core.JdbcTemplate.class),
        mockRepo);
  }

  @AfterEach
  void cleanup() {
    RequestContext.clear();
  }

  @Test
  void sameInputProducesSameOutputHash() {
    UUID sheetId = UUID.randomUUID();
    UUID investorId = UUID.randomUUID();
    UUID channelId = UUID.randomUUID();
    String productCode = "PMK";
    Instant asOfDate = Instant.parse("2025-01-15T00:00:00Z");

    RateFeedModels.RateSheet sheet = new RateFeedModels.RateSheet(
        sheetId, UUID.randomUUID(), investorId, channelId, productCode, 1,
        RateFeedModels.RateSheetStatus.ACTIVE, asOfDate, null,
        "sha256:" + "ab".repeat(32),
        "sha256:" + "cd".repeat(32), null, 2,
        "sha256:" + "ef".repeat(32), asOfDate, "system", null, null, null, null, null, asOfDate);

    List<RatePricePoint> points = List.of(
        new RatePricePoint(sheetId, new BigDecimal("6.50"), 30, new BigDecimal("150.00"), new BigDecimal("0.50"), new BigDecimal("6.75"), 0),
        new RatePricePoint(sheetId, new BigDecimal("6.75"), 30, new BigDecimal("155.00"), new BigDecimal("0.25"), new BigDecimal("6.50"), 1)
    );

    UUID replayId = UUID.randomUUID();

    when(mockRepo.findEffectiveSheetByVersionAndDate(investorId, channelId, productCode, 1, asOfDate))
        .thenReturn(sheet);
    when(mockRepo.findPricePoints(sheetId)).thenReturn(points);
    when(mockRepo.saveReplay(eq(sheetId), eq(1), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(replayId);

    RateFeedModels.ReplayRequest req = new RateFeedModels.ReplayRequest(
        investorId, channelId, productCode, 30, asOfDate, 1);

    // First replay
    RateFeedModels.ReplayResult r1 = replayService.replay(req, "test-user", "corr-1");
    assertNotNull(r1);
    assertEquals("REPLAYED", r1.status());
    assertEquals(2, r1.pointCount());
    assertNotNull(r1.inputHash());
    assertNotNull(r1.outputHash());

    // Second replay with same input
    RateFeedModels.ReplayResult r2 = replayService.replay(req, "test-user", "corr-2");

    // Same input should produce same outputHash
    assertEquals(r1.outputHash(), r2.outputHash(), "Same input must produce same outputHash");
    assertEquals(r1.inputHash(), r2.inputHash(), "Same input must produce same inputHash");
  }
}
