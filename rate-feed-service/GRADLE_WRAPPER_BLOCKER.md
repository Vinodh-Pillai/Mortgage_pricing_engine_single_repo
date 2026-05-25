# Gradle Wrapper Blocker / Provenance Note

Task: `developer-wcpe-pii-04-repo-remediation`

The `rate-feed-service` Gradle wrapper was not repaired because a complete wrapper requires a reviewed `gradle-wrapper.jar`, and no in-repository wrapper JAR was available to copy with provenance.

Observed local provenance:

- `projects/catalog-service/gradlew` and `projects/catalog-service/gradlew.bat` are Gradle shims that require `gradle` on `PATH`; they are not complete Gradle wrappers.
- `projects/catalog-service/gradle/wrapper/gradle-wrapper.properties` and `projects/scenario-service/gradle/wrapper/gradle-wrapper.properties` point to `https://services.gradle.org/distributions/gradle-8.10.2-bin.zip`.
- No `gradle-wrapper.jar` was found under `projects/**/gradle-wrapper.jar` during repo-local inspection.
- `gradle -v` failed locally because Gradle is not installed or not on `PATH`.

Resolution required before wrapper-based build evidence:

1. Generate a complete wrapper from a reviewed local Gradle 8.10.2 installation with `gradle wrapper --gradle-version 8.10.2`, or
2. Supply an approved wrapper JAR from an internal/provenance-controlled artifact source.

No external binary download was performed for this remediation, and no build/package success is claimed from wrapper tooling.
