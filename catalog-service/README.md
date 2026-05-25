# Catalog Service

PII-02 Product and Investor Catalog implementation project.

## Scope
- Tenant-scoped product definitions.
- Investor programs.
- Channel availability.
- Effective-dated catalog publication.
- Active catalog lookup for downstream eligibility, rate ingestion, pricing, and governance.

## Run
```bash
./gradlew bootRun
```

## Test
```bash
./gradlew test
```

The included lightweight `gradlew` scripts delegate to a locally installed Gradle. For a self-contained wrapper, install Gradle once and run `gradle wrapper --gradle-version 8.10.2` to generate `gradle-wrapper.jar`.
