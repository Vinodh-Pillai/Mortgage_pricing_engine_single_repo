# Scenario hash runbook

Use `ScenarioHashService.explain()` for diagnostics. The explanation exposes `scenarioHash`, `hashSchemaVersion`, `canonicalPayloadSha256`, `versionGraph`, cache eligibility, and safe metric/trace field names only.

Do not log raw canonical JSON. For cache miss or replay mismatch triage, compare hash schema version, version graph entries, and canonical payload digest. Roll forward with a new hash schema version; do not reinterpret historical hashes.
