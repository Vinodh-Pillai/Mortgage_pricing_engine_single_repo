package com.wcpe.ratefeed.service;

import com.wcpe.ratefeed.domain.*;
import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import com.wcpe.ratefeed.domain.RateFeedModels.RatePricePoint;
import com.wcpe.ratefeed.domain.RateFeedModels.ReplayRequest;
import com.wcpe.ratefeed.domain.RateFeedModels.ReplayResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * ReplayService: deterministic replay of a historical rate sheet for audit/validation.
 */
@Service
public class ReplayService {
  private final JdbcTemplate jdbc;
  private final ReplayRepository replayRepository;

  public ReplayService(JdbcTemplate jdbc, ReplayRepository replayRepository) {
    this.jdbc = jdbc;
    this.replayRepository = replayRepository;
  }

  @Transactional
  public ReplayResult replay(ReplayRequest request, String actor, String correlationId) {
    if (request.investorId() == null) throw new RateFeedException(
        org.springframework.http.HttpStatus.BAD_REQUEST, "INVESTOR_REQUIRED", "investorId is required for replay.");
    if (request.channelId() == null) throw new RateFeedException(
        org.springframework.http.HttpStatus.BAD_REQUEST, "CHANNEL_REQUIRED", "channelId is required for replay.");
    if (request.productCode() == null || request.productCode().isBlank()) throw new RateFeedException(
        org.springframework.http.HttpStatus.BAD_REQUEST, "PRODUCT_CODE_REQUIRED", "productCode is required for replay.");
    if (request.asOfDate() == null) throw new RateFeedException(
        org.springframework.http.HttpStatus.BAD_REQUEST, "AS_OF_DATE_REQUIRED", "asOfDate is required for replay.");
    if (request.sheetVersion() == null) throw new RateFeedException(
        org.springframework.http.HttpStatus.BAD_REQUEST, "SHEET_VERSION_REQUIRED", "sheetVersion is required for replay.");

    // Find the sheet by version and date
    RateFeedModels.RateSheet sheet = replayRepository.findEffectiveSheetByVersionAndDate(
        request.investorId(), request.channelId(), request.productCode(),
        request.sheetVersion(), request.asOfDate());

    if (sheet == null) {
      throw new RateFeedException(org.springframework.http.HttpStatus.NOT_FOUND, "SHEET_VERSION_NOT_FOUND",
          "No sheet found for version " + request.sheetVersion() + " at " + request.asOfDate());
    }

    // Load grid points
    List<RatePricePoint> points = replayRepository.findPricePoints(sheet.sheetId());

    // Compute input and output hashes
    String inputHash = Hashing.sha256(sheet.sheetId().toString() + ":" + sheet.version() + ":" +
        request.investorId() + ":" + request.channelId() + ":" + request.productCode() + ":" +
        request.asOfDate().toString());

    String outputHash = Hashing.gridHash(
        new com.fasterxml.jackson.databind.ObjectMapper(), points);

    // Persist replay record
    UUID replayId = replayRepository.saveReplay(
        sheet.sheetId(), sheet.version(), inputHash, outputHash,
        actor, correlationId);

    return new ReplayResult(
        replayId,
        sheet.sheetId(),
        sheet.version(),
        inputHash,
        outputHash,
        points,
        points.size(),
        "REPLAYED",
        Instant.now()
    );
  }
}
