# Audit Replay Service - Detailed Implementation Analysis 
  
## 1. Service Purpose and Capabilities 
The audit-replay-service is a Spring Boot 3.3.5 (Java 17) microservice that provides tamper-evident audit logging with deterministic replay capabilities for mortgage pricing events. It is designed for regulatory compliance, audit trails, and forensic investigation.  
  
### Core Capabilities:  
- Immutable Audit Recording: Append-only audit records with cryptographic integrity chaining (SHA-256 hash chain)  
- Outbox Pattern: Reliable event publishing to Kafka with idempotency, retry with exponential backoff, and dead-letter queue  
- Quote Replay: Deterministic re-execution of quote pricing logic against immutable audit snapshots (VERIFY/DIAGNOSE modes)  
- Lock Replay: Deterministic re-execution of lock decisions (eligibility, expiration, extension, cancellation) with market snapshot references  
- Evidence Export: Package audit records, replay runs, and diffs into regulator-ready evidence bundles (ZIP_JSON format)  
