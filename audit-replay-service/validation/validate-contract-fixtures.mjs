import { readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("..", import.meta.url));
const fixtures = JSON.parse(
  readFileSync(join(root, "validation", "contract-fixtures.json"), "utf8")
);
const eventSchema = JSON.parse(
  readFileSync(join(root, "..", "contracts", "audit", "audit-event-envelope.schema.json"), "utf8")
);
const manifestSchema = JSON.parse(
  readFileSync(join(root, "..", "contracts", "audit", "audit-replay-manifest.schema.json"), "utf8")
);

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function assertRequiredFields(schema, fixture, label) {
  for (const field of schema.required ?? []) {
    assert(Object.hasOwn(fixture, field), `${label} missing required field: ${field}`);
  }
}

assert(eventSchema.title === "Audit Event Envelope", "Unexpected audit event schema title");
assert(manifestSchema.title === "Audit Replay Manifest", "Unexpected replay manifest schema title");
assertRequiredFields(eventSchema, fixtures.auditEventEnvelope, "auditEventEnvelope");
assertRequiredFields(manifestSchema, fixtures.auditReplayManifest, "auditReplayManifest");
assert(fixtures.auditEventEnvelope.payload && typeof fixtures.auditEventEnvelope.payload === "object", "auditEventEnvelope payload must be an object");
assert(fixtures.auditReplayManifest.scope && typeof fixtures.auditReplayManifest.scope === "object", "auditReplayManifest scope must be an object");
assert(fixtures.auditReplayManifest.eventRange && typeof fixtures.auditReplayManifest.eventRange === "object", "auditReplayManifest eventRange must be an object");

console.log("audit replay contract fixtures satisfy required foundation schema fields");
