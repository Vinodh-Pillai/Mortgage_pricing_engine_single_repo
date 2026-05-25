# Scenario Service Implementation

This is the executable PII-01 Scenario Intake vertical slice for `world-class-pricing-engine`.

Runtime target: Java 17+ for this standalone scaffold. The product standard remains Java 21; this code uses only Java 17-compatible language features so it can run in the current local environment.

## Covered Stories
- PII-01-S01 create draft scenario
- PII-01-S02 capture borrower credit
- PII-01-S03 capture loan structure and derived LTV/CLTV/HCLTV
- PII-01-S04 capture property attributes
- PII-01-S05 capture income and asset signals
- PII-01-S06 normalize derived fields and scenario hash
- PII-01-S07 idempotent scenario API behavior
- PII-01-S08 clone scenario variant
- PII-01-S09 batch scenario import and invalid scenario handling
- PII-01-S10 replay package API

## Run
```bash
./gradlew bootRun
```

## Test
```bash
./gradlew test
```

If wrapper scripts are not present yet, install Gradle once and run `gradle wrapper --gradle-version 8.10.2`, then use `./gradlew` going forward.

## Main Endpoints
- `POST /api/v1/tenants/{tenantId}/scenarios`
- `PATCH /api/v1/tenants/{tenantId}/scenarios/{scenarioId}/borrowers-credit`
- `PATCH /api/v1/tenants/{tenantId}/scenarios/{scenarioId}/loan-structure`
- `PATCH /api/v1/tenants/{tenantId}/scenarios/{scenarioId}/property`
- `PATCH /api/v1/tenants/{tenantId}/scenarios/{scenarioId}/income-assets`
- `POST /api/v1/tenants/{tenantId}/scenarios/{scenarioId}/normalize`
- `POST /api/v1/tenants/{tenantId}/scenarios/{scenarioId}/submit`
- `POST /api/v1/tenants/{tenantId}/scenarios/{scenarioId}/clone`
- `POST /api/v1/tenants/{tenantId}/scenario-imports`
- `GET /api/v1/tenants/{tenantId}/scenarios/{scenarioId}/replay-package`
- `GET /api/v1/tenants/{tenantId}/scenarios/{scenarioId}/events`

## Notes
- Persistence is currently in-memory so the slice can run inside the requirements workspace without a database.
- The service emits in-memory audit and outbox records on every accepted command.
- PostgreSQL/JPA migrations should replace `ScenarioRepository` when the full platform repository is created.
