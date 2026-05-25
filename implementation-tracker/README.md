# Implementation Tracker

This tracker coordinates implementation outside the requirements package. Requirements remain under `world-class-pricing-engine`; runnable services live under `projects/`.

## Current Projects
| Project | PII | Status | Notes |
|---|---|---|---|
| `projects/scenario-service` | PII-01 Scenario Intake | Prototype / partial | In-memory Gradle service. Covers broad API surface but not complete production acceptance. |
| `projects/catalog-service` | PII-02 Product and Investor Catalog | Started baseline | In-memory Gradle service with product, investor, publish, active lookup, events, migration, and tests. |

## Validation Limitation
Local machine has Java 17 but no `gradle`, `gradlew`, or `mvn` executable available. Code-level validation is currently static only until Gradle wrapper scripts or Gradle are installed.
