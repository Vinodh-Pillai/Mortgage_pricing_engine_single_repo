package com.wcpe.adjustment.gridloader;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class GseGridLoader {
    public static final String FNMA_LLPA_URL = "https://www.fanniemae.com/content/guide/llpa-matrix.csv";
    public static final String FHLMC_LLPA_URL = "https://freddiemac.com/llpa-matrix.csv";
    private static final Logger LOGGER = LoggerFactory.getLogger(GseGridLoader.class);

    private final FnmaLlpaCsvParser fnmaParser;
    private final FhlmcLlpaCsvParser fhlmcParser;
    private final GseGridMapper mapper;
    private final GseGridOverlapValidator overlapValidator;
    private final GseGridRepository repository;
    private final RestTemplate restTemplate;
    private final UUID tenantId;

    @Autowired
    public GseGridLoader(
        FnmaLlpaCsvParser fnmaParser,
        FhlmcLlpaCsvParser fhlmcParser,
        GseGridMapper mapper,
        GseGridOverlapValidator overlapValidator,
        GseGridRepository repository,
        @Value("${wcpe.gse-grid-loader.tenant-id:00000000-0000-0000-0000-000000000001}") UUID tenantId
    ) {
        this(fnmaParser, fhlmcParser, mapper, overlapValidator, repository, new RestTemplate(), tenantId);
    }

    GseGridLoader(FnmaLlpaCsvParser fnmaParser, FhlmcLlpaCsvParser fhlmcParser, GseGridMapper mapper,
                  GseGridOverlapValidator overlapValidator, GseGridRepository repository, RestTemplate restTemplate, UUID tenantId) {
        this.fnmaParser = fnmaParser;
        this.fhlmcParser = fhlmcParser;
        this.mapper = mapper;
        this.overlapValidator = overlapValidator;
        this.repository = repository;
        this.restTemplate = restTemplate;
        this.tenantId = tenantId;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void loadFnmaGrids() {
        loadGrid("FNMA", FNMA_LLPA_URL);
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void loadFhlmcGrids() {
        loadGrid("FHLMC", FHLMC_LLPA_URL);
    }

    public GseGridMappedRules loadGrid(String investor, String url) {
        try {
            String csv = downloadWithRetry(url, 3);
            ParsedGseGrid parsed = parserFor(investor).parse(csv);
            GseGridMappedRules mapped = mapper.map(parsed, tenantId);
            overlapValidator.validate(mapped);
            repository.publish(mapped, url, parsed.warnings().size());
            LOGGER.info("GseGridLoaded.v1 investor={} version={} cellCount={} ruleBookHash={}", investor, mapped.ruleBookVersion(), mapped.cellCount(), mapped.ruleBookHash());
            return mapped;
        } catch (RuntimeException ex) {
            repository.recordFailure(investor, investor + "_UNKNOWN", url, ex.getMessage());
            LOGGER.warn("GseGridLoadFailed.v1 investor={} error={}", investor, ex.getMessage());
            throw ex;
        }
    }

    private GseLlpaCsvParser parserFor(String investor) {
        return switch (investor.toUpperCase()) {
            case "FNMA" -> fnmaParser;
            case "FHLMC" -> fhlmcParser;
            default -> throw new IllegalArgumentException("unsupported GSE investor " + investor);
        };
    }

    private String downloadWithRetry(String url, int maxAttempts) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && !response.getBody().isBlank()) return response.getBody();
                last = new IllegalStateException("empty or non-success grid response from " + url);
            } catch (RestClientException ex) {
                last = ex;
            }
            try {
                Thread.sleep(Duration.ofMillis(100L * attempt).toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while retrying GSE grid download", ex);
            }
        }
        throw last == null ? new IllegalStateException("download failed") : last;
    }
}
