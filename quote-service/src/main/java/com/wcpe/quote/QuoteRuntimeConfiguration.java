package com.wcpe.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.quote.parallel.ParallelPricingOrchestrator;
import java.util.Arrays;
import java.time.Clock;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(QuoteRuntimeConfiguration.QuotePersistenceProperties.class)
class QuoteRuntimeConfiguration {
    @Bean
    @ConditionalOnMissingBean
    Clock quoteClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "quote.persistence", name = "mode", havingValue = "postgres", matchIfMissing = true)
    QuoteRepository jdbcQuoteRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcQuoteRepository(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "quote.persistence", name = "mode", havingValue = "postgres", matchIfMissing = true)
    QuoteJobRepository jdbcQuoteJobRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcQuoteJobRepository(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "quote.persistence", name = "mode", havingValue = "postgres", matchIfMissing = true)
    QuoteSnapshotRepository jdbcQuoteSnapshotRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcQuoteSnapshotRepository(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "quote.persistence", name = "mode", havingValue = "postgres", matchIfMissing = true)
    LoanPassQuoteCatalogRepository jdbcLoanPassQuoteCatalogRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcLoanPassQuoteCatalogRepository(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    LoanPassWarmEvaluator loanPassWarmEvaluator() {
        return new LoanPassWarmEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean
    LoanPassQuoteService loanPassQuoteService(LoanPassQuoteCatalogRepository repository, LoanPassWarmEvaluator evaluator) {
        return new LoanPassQuoteService(repository, evaluator);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "quote.persistence", name = "mode", havingValue = "fail-closed")
    LoanPassQuoteCatalogRepository failClosedLoanPassQuoteCatalogRepository() {
        return new FailClosedLoanPassQuoteCatalogRepository();
    }

    @Bean
    @ConditionalOnProperty(prefix = "quote.loanpass.synthetic-loader", name = "enabled", havingValue = "true")
    LoanPassSyntheticCatalogLoader loanPassSyntheticCatalogLoader(
        LoanPassQuoteCatalogRepository repository,
        Clock clock,
        @Value("${quote.loanpass.synthetic-loader.tenant-id:11111111-1111-1111-1111-111111111111}") UUID tenantId,
        @Value("${quote.loanpass.synthetic-loader.product-count:1536}") int productCount,
        @Value("${quote.loanpass.synthetic-loader.seed:lpq-02-dev}") String seed
    ) {
        return new LoanPassSyntheticCatalogLoader(repository, clock, tenantId, productCount, seed);
    }

    @Bean
    @ConditionalOnMissingBean
    QuoteCache quoteCache() {
        return new LocalQuoteCache();
    }

    @Bean
    @ConditionalOnMissingBean
    BestExecutionRanker bestExecutionRanker() {
        return new BestExecutionRanker();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    ParallelPricingOrchestrator parallelPricingOrchestrator() {
        return new ParallelPricingOrchestrator();
    }

    @Bean
    @ConditionalOnMissingBean
    QuoteDependencies quoteDependencies() {
        return new FailClosedQuoteDependencies();
    }

    @Bean
    @ConditionalOnMissingBean
    QuoteApplicationService quoteApplicationService(
        QuoteRepository repository,
        QuoteJobRepository jobRepository,
        QuoteSnapshotRepository snapshotRepository,
        QuoteDependencies dependencies,
        QuoteCache cache,
        BestExecutionRanker ranker,
        Clock clock,
        ParallelPricingOrchestrator parallelPricingOrchestrator
    ) {
        return new QuoteApplicationService(repository, jobRepository, snapshotRepository, dependencies, cache, ranker, clock, parallelPricingOrchestrator);
    }

    @Bean
    @ConditionalOnProperty(prefix = "quote.persistence", name = "mode", havingValue = "postgres", matchIfMissing = true)
    @ConditionalOnMissingBean
    DataSource postgresDataSource(
        @Value("${spring.datasource.url:}") String url,
        @Value("${spring.datasource.username:}") String username,
        @Value("${spring.datasource.password:}") String password
    ) {
        if (url == null || url.isBlank() || username == null || username.isBlank()) {
            throw new IllegalStateException("QUOTE_POSTGRES_DATASOURCE_REQUIRED: SPRING_DATASOURCE_URL and SPRING_DATASOURCE_USERNAME must be set");
        }
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);
        dataSource.setUser(username);
        dataSource.setPassword(password == null ? "" : password);
        return dataSource;
    }

    @Bean
    @ConditionalOnMissingBean(Flyway.class)
    @ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true")
    Flyway quoteFlyway(
        DataSource dataSource,
        @Value("${spring.flyway.locations:classpath:db/migration}") String locations,
        @Value("${spring.flyway.schemas:}") String schemas,
        @Value("${spring.flyway.default-schema:}") String defaultSchema
    ) {
        FluentConfiguration configuration = Flyway.configure()
            .dataSource(dataSource)
            .locations(csv(locations))
            .baselineOnMigrate(true)
            .baselineVersion("0");
        String[] schemaList = csv(schemas);
        if (schemaList.length > 0) configuration.schemas(schemaList);
        if (defaultSchema != null && !defaultSchema.isBlank()) configuration.defaultSchema(defaultSchema.trim());
        Flyway flyway = configuration.load();
        flyway.migrate();
        return flyway;
    }

    private static String[] csv(String value) {
        if (value == null || value.isBlank()) return new String[0];
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .toArray(String[]::new);
    }

    @ConfigurationProperties(prefix = "quote.persistence")
    record QuotePersistenceProperties(String mode) {}

    private static final class FailClosedQuoteDependencies implements QuoteDependencies {
        @Override
        public Optional<RankingPolicy> rankingPolicyFor(QuoteCreateRequest request) {
            throw unconfigured();
        }

        @Override
        public List<QuoteCandidate> candidatesFor(QuoteCreateRequest request) {
            throw unconfigured();
        }

        @Override
        public String eligibilityVersion() {
            throw unconfigured();
        }

        @Override
        public String pricingVersion() {
            throw unconfigured();
        }

        @Override
        public String adjustmentVersion() {
            throw unconfigured();
        }

        @Override
        public String marginVersion() {
            throw unconfigured();
        }

        private QuoteCreateException unconfigured() {
            return new QuoteCreateException(
                "QUOTE_DEPENDENCIES_UNCONFIGURED",
                "Quote runtime dependencies are not configured; refusing to fabricate pricing or ranking data"
            );
        }
    }

    private static final class FailClosedLoanPassQuoteCatalogRepository implements LoanPassQuoteCatalogRepository {
        @Override
        public Optional<LoanPassQuoteModels.CatalogSnapshot> activeSnapshot(UUID tenantId) {
            return Optional.empty();
        }

        @Override
        public LoanPassQuoteModels.CatalogSnapshot saveSnapshot(LoanPassQuoteModels.CatalogSnapshot snapshot) {
            throw new IllegalStateException("LOANPASS_CATALOG_POSTGRES_REQUIRED: durable LoanPass catalog snapshots require PostgreSQL");
        }
    }
}
