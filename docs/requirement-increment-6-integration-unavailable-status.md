# Requirement Increment 6: Integration Dependency Unavailable Status

`PartnerIntegrationWorkbenchService` uses `DependencyStatus.UNAVAILABLE` as an explicit non-acceptance state for partner workbench actions. The previous string form `DependencyStatus.unavailable("partner-quote-service")` is now normalized to the canonical `DependencyName.PARTNER_QUOTE_SERVICE.name()` key so response metadata uses the same dependency key shape as `DependencyStatus.available(...)`.

Current repository evidence shows no live partner quote dependency client/configuration in `projects/integration-service`; the deployed surface is still health-only per `projects/integration-service/DEPLOYMENT.md`. Because no repo-supported live status source exists to wire, the service keeps this as an explicit fail-safe status and test coverage verifies that a partner quote unavailable state blocks reprice with `DEPENDENCY_OR_POLICY_BLOCKED` rather than silently accepting work.
