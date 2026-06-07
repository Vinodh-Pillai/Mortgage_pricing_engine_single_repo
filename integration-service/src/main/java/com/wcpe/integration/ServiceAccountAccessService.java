package com.wcpe.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ServiceAccountAccessService {
  public static final String BASE_PATH = "/api/v1/tenants/{tenantId}/service-credentials";
  public static final String WRITE_PERMISSION = "integrations.credentials.write";
  public static final String READ_PERMISSION = "integrations.credentials.read";
  public static final String ACCOUNT_CREATED_EVENT_TYPE = "integration.service-account.created.v1";
  public static final String ACCOUNT_UPDATED_EVENT_TYPE = "integration.service-account.updated.v1";
  public static final String ACCOUNT_REVOKED_EVENT_TYPE = "integration.service-account.revoked.v1";
  public static final String CREDENTIAL_CREATED_EVENT_TYPE = "integration.credential.created.v1";
  public static final String CREDENTIAL_ROTATED_EVENT_TYPE = "integration.credential.rotated.v1";
  public static final String CREDENTIAL_REVOKED_EVENT_TYPE = "integration.credential.revoked.v1";
  public static final String ACCOUNT_CREATED_AUDIT_ACTION = "SERVICE_ACCOUNT_CREATED";
  public static final String CREDENTIAL_CREATED_AUDIT_ACTION = "SERVICE_ACCOUNT_CREDENTIAL_CREATED";
  public static final String CREDENTIAL_ROTATED_AUDIT_ACTION = "SERVICE_ACCOUNT_CREDENTIAL_ROTATED";
  public static final String CREDENTIAL_REVOKED_AUDIT_ACTION = "SERVICE_ACCOUNT_CREDENTIAL_REVOKED";

  private final Clock clock;
  private final SecretProvider secretProvider;
  private final Map<String, ServiceAccount> accounts = new HashMap<>();
  private final Map<String, CredentialReference> credentialReferences = new HashMap<>();
  private final Map<String, String> activeCredentialNameIndex = new HashMap<>();
  private final Map<String, IdempotencyEntry<ServiceAccountResponse>> accountIdempotency = new HashMap<>();
  private final Map<String, IdempotencyEntry<CredentialResponse>> credentialIdempotency = new HashMap<>();
  private final List<IntegrationOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<IntegrationAuditRecord> auditRecords = new ArrayList<>();
  private final List<CredentialUsageAudit> usageAudits = new ArrayList<>();
  private final Map<String, Long> metrics = new HashMap<>();
  private Set<String> allowedScopes = Set.of();
  private Set<String> allowedChannels = Set.of();
  private boolean separationOfDutiesRequired = true;

  public ServiceAccountAccessService(SecretProvider secretProvider) {
    this(Clock.systemUTC(), secretProvider);
  }

  public ServiceAccountAccessService(Clock clock, SecretProvider secretProvider) {
    this.clock = clock;
    this.secretProvider = secretProvider;
  }

  public void configureScopePolicy(Set<String> scopes, Set<String> channels) {
    this.allowedScopes = Set.copyOf(scopes);
    this.allowedChannels = Set.copyOf(channels);
  }

  public void requireSeparationOfDuties(boolean required) {
    this.separationOfDutiesRequired = required;
  }

  public IntegrationResult<ServiceAccountResponse> createServiceAccount(CreateServiceAccountCommand command) {
    IntegrationError validation = validateAccountCommand(command);
    if (validation != null) {
      return IntegrationResult.failure(validation);
    }
    String requestHash = hash(canonicalAccount(command));
    String idempotencyKey = command.tenantId() + ":service-account:create:" + command.idempotencyKey();
    IntegrationResult<ServiceAccountResponse> replay = replayOrConflict(accountIdempotency, idempotencyKey, requestHash, command.correlationId());
    if (replay != null) {
      return replay;
    }

    Instant now = clock.instant();
    String accountId = deterministicId(command.tenantId(), command.displayName(), command.actorId());
    ServiceAccount account =
        new ServiceAccount(
            command.tenantId(),
            accountId,
            command.displayName().trim(),
            command.principalType(),
            List.copyOf(command.scopes()),
            List.copyOf(command.allowedChannels()),
            command.status(),
            command.expiresAt(),
            1,
            command.actorId(),
            now,
            now,
            command.correlationId());
    accounts.put(accountKey(account.tenantId(), account.accountId()), account);
    writeOutbox(ACCOUNT_CREATED_EVENT_TYPE, account.tenantId(), account.accountId(), command.actorId(), command.idempotencyKey(), command.correlationId(), Map.of("status", account.status().name(), "scopes", String.join(",", account.scopes())));
    writeAudit(ACCOUNT_CREATED_AUDIT_ACTION, command.actorId(), account.tenantId(), account.accountId(), "created", command.correlationId(), Map.of("status", account.status().name()));
    recordMetric("integration_service_accounts_total");
    ServiceAccountResponse response = accountResponse(account, "created");
    accountIdempotency.put(idempotencyKey, new IdempotencyEntry<>(requestHash, response));
    return IntegrationResult.success(response);
  }

  public IntegrationResult<CredentialResponse> registerCredential(RegisterCredentialCommand command) {
    IntegrationError validation = validateCredentialCommand(command);
    if (validation != null) {
      return IntegrationResult.failure(validation);
    }
    String requestHash = hash(canonicalCredential(command));
    String idempotencyKey = command.tenantId() + ":credential:register:" + command.idempotencyKey();
    IntegrationResult<CredentialResponse> replay = replayOrConflict(credentialIdempotency, idempotencyKey, requestHash, command.correlationId());
    if (replay != null) {
      return replay;
    }
    ServiceAccount account = accounts.get(accountKey(command.tenantId(), command.accountId()));
    if (account == null || account.status() != ServiceAccountStatus.ACTIVE) {
      return IntegrationResult.failure(error("422", "POLICY_NOT_SATISFIED", "Active service account is required", command.correlationId(), false));
    }
    String nameIndexKey = activeCredentialNameKey(command.tenantId(), command.accountId(), command.credentialName());
    if (activeCredentialNameIndex.containsKey(nameIndexKey)) {
      return IntegrationResult.failure(error("409", "VERSION_CONFLICT", "An active credential with this name already exists for the account", command.correlationId(), false));
    }

    SecretCreation creation;
    if (command.generateSecret()) {
      creation = secretProvider.generate(command.tenantId(), command.accountId(), command.credentialType(), command.actorId(), command.correlationId());
    } else {
      if (isBlank(command.providedSecretRef())) {
        return IntegrationResult.failure(error("400", "VALIDATION_FAILED", "providedSecretRef is required when generateSecret is false", command.correlationId(), false));
      }
      SecretPointer pointer = secretProvider.reference(command.providedSecretRef(), command.tenantId(), command.accountId(), command.credentialType(), command.correlationId());
      if (!pointer.available()) {
        return IntegrationResult.failure(error("503", "DEPENDENCY_UNAVAILABLE", "Secret provider reference is unavailable", command.correlationId(), true));
      }
      creation = new SecretCreation(pointer, Optional.empty());
    }

    Instant now = clock.instant();
    String credentialId = deterministicId(command.tenantId(), command.accountId(), command.credentialName(), creation.pointer().secretRef());
    CredentialReference credential =
        new CredentialReference(
            command.tenantId(),
            credentialId,
            command.accountId(),
            command.credentialName().trim(),
            command.credentialType(),
            creation.pointer().secretRef(),
            creation.pointer().secretVersion(),
            CredentialStatus.ACTIVE,
            now,
            command.expiresAt(),
            safeMetadata(command.metadata()),
            1,
            command.actorId(),
            now,
            now,
            command.correlationId());
    credentialReferences.put(credentialKey(credential.tenantId(), credential.credentialId()), credential);
    activeCredentialNameIndex.put(nameIndexKey, credential.credentialId());
    writeCredentialOutboxAndAudit(CREDENTIAL_CREATED_EVENT_TYPE, CREDENTIAL_CREATED_AUDIT_ACTION, command.actorId(), command.idempotencyKey(), credential, null, command.correlationId(), Map.of("status", credential.status().name(), "secretVersion", credential.secretVersion()));
    recordMetric("integration_credentials_total");
    CredentialResponse response = credentialResponse(credential, "created", creation.oneTimeSecret());
    credentialIdempotency.put(idempotencyKey, new IdempotencyEntry<>(requestHash, response));
    return IntegrationResult.success(response);
  }

  public IntegrationResult<CredentialResponse> rotateCredential(RotateCredentialCommand command) {
    IntegrationError common = validateCredentialMutation(command.tenantId(), command.credentialId(), command.idempotencyKey(), command.actorId(), command.correlationId(), command.expectedVersion());
    if (common != null) {
      return IntegrationResult.failure(common);
    }
    if (separationOfDutiesRequired && (isBlank(command.approvedBy()) || command.actorId().equals(command.approvedBy()))) {
      return IntegrationResult.failure(error("422", "POLICY_NOT_SATISFIED", "Rotation requires separation of duties approval", command.correlationId(), false));
    }
    String requestHash = hash(String.join("|", command.tenantId(), command.credentialId(), command.actorId(), command.approvedBy(), String.valueOf(command.generateSecret()), nullToEmpty(command.newSecretRef())));
    String idempotencyKey = command.tenantId() + ":credential:rotate:" + command.credentialId() + ":" + command.idempotencyKey();
    IntegrationResult<CredentialResponse> replay = replayOrConflict(credentialIdempotency, idempotencyKey, requestHash, command.correlationId());
    if (replay != null) {
      return replay;
    }
    CredentialReference existing = credentialReferences.get(credentialKey(command.tenantId(), command.credentialId()));
    if (existing == null) {
      return IntegrationResult.failure(error("404", "NOT_FOUND", "Credential was not found", command.correlationId(), false));
    }
    if (existing.status() != CredentialStatus.ACTIVE) {
      return IntegrationResult.failure(error("422", "POLICY_NOT_SATISFIED", "Only active credentials can be rotated", command.correlationId(), false));
    }
    SecretCreation creation;
    if (command.generateSecret()) {
      creation = secretProvider.rotate(existing.secretRef(), command.tenantId(), existing.accountId(), existing.credentialType(), command.actorId(), command.correlationId());
    } else {
      if (isBlank(command.newSecretRef())) {
        return IntegrationResult.failure(error("400", "VALIDATION_FAILED", "newSecretRef is required when generateSecret is false", command.correlationId(), false));
      }
      SecretPointer pointer = secretProvider.reference(command.newSecretRef(), command.tenantId(), existing.accountId(), existing.credentialType(), command.correlationId());
      if (!pointer.available()) {
        return IntegrationResult.failure(error("503", "DEPENDENCY_UNAVAILABLE", "Secret provider reference is unavailable", command.correlationId(), true));
      }
      creation = new SecretCreation(pointer, Optional.empty());
    }
    CredentialReference rotated =
        existing.withSecret(creation.pointer().secretRef(), creation.pointer().secretVersion(), command.actorId(), clock.instant(), command.correlationId());
    credentialReferences.put(credentialKey(rotated.tenantId(), rotated.credentialId()), rotated);
    writeCredentialOutboxAndAudit(CREDENTIAL_ROTATED_EVENT_TYPE, CREDENTIAL_ROTATED_AUDIT_ACTION, command.actorId(), command.idempotencyKey(), rotated, existing, command.correlationId(), Map.of("secretVersion", rotated.secretVersion(), "approvedBy", command.approvedBy()));
    recordMetric("integration_credential_rotations_total");
    CredentialResponse response = credentialResponse(rotated, "rotated", creation.oneTimeSecret());
    credentialIdempotency.put(idempotencyKey, new IdempotencyEntry<>(requestHash, response));
    return IntegrationResult.success(response);
  }

  public IntegrationResult<CredentialResponse> revokeCredential(RevokeCredentialCommand command) {
    IntegrationError common = validateCredentialMutation(command.tenantId(), command.credentialId(), command.idempotencyKey(), command.actorId(), command.correlationId(), command.expectedVersion());
    if (common != null) {
      return IntegrationResult.failure(common);
    }
    if (separationOfDutiesRequired && (isBlank(command.approvedBy()) || command.actorId().equals(command.approvedBy()))) {
      return IntegrationResult.failure(error("422", "POLICY_NOT_SATISFIED", "Revocation requires separation of duties approval", command.correlationId(), false));
    }
    CredentialReference existing = credentialReferences.get(credentialKey(command.tenantId(), command.credentialId()));
    if (existing == null) {
      return IntegrationResult.failure(error("404", "NOT_FOUND", "Credential was not found", command.correlationId(), false));
    }
    CredentialReference revoked = existing.withStatus(CredentialStatus.REVOKED, command.actorId(), clock.instant(), command.correlationId());
    credentialReferences.put(credentialKey(revoked.tenantId(), revoked.credentialId()), revoked);
    activeCredentialNameIndex.remove(activeCredentialNameKey(revoked.tenantId(), revoked.accountId(), revoked.credentialName()));
    writeCredentialOutboxAndAudit(CREDENTIAL_REVOKED_EVENT_TYPE, CREDENTIAL_REVOKED_AUDIT_ACTION, command.actorId(), command.idempotencyKey(), revoked, existing, command.correlationId(), Map.of("reason", command.reason()));
    recordMetric("integration_credential_revocations_total");
    return IntegrationResult.success(credentialResponse(revoked, "revoked", Optional.empty()));
  }

  public IntegrationResult<CredentialResponse> fetchCredentialMetadata(String tenantId, String credentialId, String correlationId) {
    if (isBlank(tenantId) || isBlank(credentialId)) {
      return IntegrationResult.failure(error("400", "VALIDATION_FAILED", "tenantId and credentialId are required", correlationId, false));
    }
    CredentialReference credential = credentialReferences.get(credentialKey(tenantId, credentialId));
    if (credential == null) {
      return IntegrationResult.failure(error("404", "NOT_FOUND", "Credential was not found", correlationId, false));
    }
    return IntegrationResult.success(credentialResponse(credential, "metadata", Optional.empty()));
  }

  public IntegrationResult<SecretResolution> resolveForAdapter(ResolveCredentialCommand command) {
    if (isBlank(command.tenantId()) || isBlank(command.credentialId()) || isBlank(command.usedBy()) || isBlank(command.purpose()) || isBlank(command.correlationId())) {
      return IntegrationResult.failure(error("400", "VALIDATION_FAILED", "tenantId, credentialId, usedBy, purpose, and correlationId are required", command.correlationId(), false));
    }
    CredentialReference credential = credentialReferences.get(credentialKey(command.tenantId(), command.credentialId()));
    if (credential == null) {
      return IntegrationResult.failure(error("404", "NOT_FOUND", "Credential was not found", command.correlationId(), false));
    }
    boolean success = credential.status() == CredentialStatus.ACTIVE;
    usageAudits.add(new CredentialUsageAudit(command.tenantId(), command.credentialId(), command.purpose(), command.usedBy(), success, clock.instant(), command.correlationId()));
    if (!success) {
      recordMetric("integration_credential_usage_failures_total");
      return IntegrationResult.failure(error("422", "POLICY_NOT_SATISFIED", "Credential is not active", command.correlationId(), false));
    }
    return IntegrationResult.success(new SecretResolution(credential.secretRef(), credential.secretVersion(), credential.status(), command.correlationId()));
  }

  public List<ServiceAccountResponse> serviceAccountsForTenant(String tenantId) {
    return accounts.values().stream().filter(account -> account.tenantId().equals(tenantId)).sorted(Comparator.comparing(ServiceAccount::accountId)).map(account -> accountResponse(account, "metadata")).toList();
  }

  public List<CredentialResponse> credentialsForTenant(String tenantId) {
    return credentialReferences.values().stream().filter(credential -> credential.tenantId().equals(tenantId)).sorted(Comparator.comparing(CredentialReference::credentialId)).map(credential -> credentialResponse(credential, "metadata", Optional.empty())).toList();
  }

  public List<IntegrationOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<IntegrationAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  public List<CredentialUsageAudit> usageAudits() {
    return List.copyOf(usageAudits);
  }

  public Map<String, Long> metrics() {
    return Map.copyOf(metrics);
  }

  private IntegrationError validateAccountCommand(CreateServiceAccountCommand command) {
    if (command == null || isBlank(command.tenantId()) || isBlank(command.idempotencyKey()) || isBlank(command.actorId()) || isBlank(command.displayName()) || isBlank(command.correlationId())) {
      return error("400", "VALIDATION_FAILED", "tenantId, idempotencyKey, actorId, displayName, and correlationId are required", command == null ? "" : command.correlationId(), false);
    }
    if (allowedScopes.isEmpty() || allowedChannels.isEmpty()) {
      return error("422", "POLICY_NOT_SATISFIED", "Scope and channel policy configuration is required", command.correlationId(), false);
    }
    if (command.scopes() == null || command.scopes().isEmpty() || !allowedScopes.containsAll(command.scopes())) {
      return error("422", "POLICY_NOT_SATISFIED", "Service account scopes are not allowed", command.correlationId(), false);
    }
    if (command.allowedChannels() == null || command.allowedChannels().isEmpty() || !allowedChannels.containsAll(command.allowedChannels())) {
      return error("422", "POLICY_NOT_SATISFIED", "Service account channels are not allowed", command.correlationId(), false);
    }
    return null;
  }

  private IntegrationError validateCredentialCommand(RegisterCredentialCommand command) {
    if (command == null || isBlank(command.tenantId()) || isBlank(command.accountId()) || isBlank(command.idempotencyKey()) || isBlank(command.actorId()) || isBlank(command.credentialName()) || isBlank(command.correlationId())) {
      return error("400", "VALIDATION_FAILED", "tenantId, accountId, idempotencyKey, actorId, credentialName, and correlationId are required", command == null ? "" : command.correlationId(), false);
    }
    return null;
  }

  private IntegrationError validateCredentialMutation(String tenantId, String credentialId, String idempotencyKey, String actorId, String correlationId, int expectedVersion) {
    if (isBlank(tenantId) || isBlank(credentialId) || isBlank(idempotencyKey) || isBlank(actorId) || isBlank(correlationId)) {
      return error("400", "VALIDATION_FAILED", "tenantId, credentialId, idempotencyKey, actorId, and correlationId are required", correlationId, false);
    }
    CredentialReference existing = credentialReferences.get(credentialKey(tenantId, credentialId));
    if (existing != null && existing.version() != expectedVersion) {
      return error("409", "VERSION_CONFLICT", "Credential version does not match", correlationId, false);
    }
    return null;
  }

  private <T> IntegrationResult<T> replayOrConflict(Map<String, IdempotencyEntry<T>> entries, String idempotencyKey, String requestHash, String correlationId) {
    IdempotencyEntry<T> entry = entries.get(idempotencyKey);
    if (entry == null) {
      return null;
    }
    if (entry.requestHash().equals(requestHash)) {
      return IntegrationResult.success(entry.response());
    }
    return IntegrationResult.failure(error("409", "IDEMPOTENCY_CONFLICT", "Idempotency key was already used for a different request", correlationId, false));
  }

  private ServiceAccountResponse accountResponse(ServiceAccount account, String summary) {
    return new ServiceAccountResponse(account.accountId(), account.status(), account.version(), Map.of("summary", summary, "principalType", account.principalType(), "scopes", String.join(",", account.scopes())), List.of(), deterministicId(account.tenantId(), account.accountId(), "audit", String.valueOf(account.version())), account.correlationId());
  }

  private CredentialResponse credentialResponse(CredentialReference credential, String summary, Optional<String> oneTimeSecret) {
    return new CredentialResponse(credential.credentialId(), credential.accountId(), credential.credentialType(), credential.credentialName(), credential.status(), credential.secretRef(), credential.secretVersion(), credential.version(), Map.of("summary", summary, "metadata", credential.metadata().toString()), List.of(), deterministicId(credential.tenantId(), credential.credentialId(), "audit", String.valueOf(credential.version())), credential.correlationId(), oneTimeSecret);
  }

  private void writeCredentialOutboxAndAudit(String eventType, String auditAction, String actorId, String idempotencyKey, CredentialReference after, CredentialReference before, String correlationId, Map<String, String> summary) {
    writeOutbox(eventType, after.tenantId(), after.credentialId(), actorId, idempotencyKey, correlationId, Map.of("credentialId", after.credentialId(), "accountId", after.accountId(), "status", after.status().name(), "secretVersion", after.secretVersion(), "summary", summary.toString()));
    writeAudit(auditAction, actorId, after.tenantId(), after.credentialId(), before == null ? "created" : before.status().name() + "->" + after.status().name(), correlationId, summary);
  }

  private void writeOutbox(String eventType, String tenantId, String entityId, String actorId, String idempotencyKey, String correlationId, Map<String, String> payload) {
    outboxEvents.add(new IntegrationOutboxEvent(tenantId + ":" + entityId, eventType, "1", "integration-service", actorId, correlationId, idempotencyKey, clock.instant(), Map.copyOf(payload)));
  }

  private void writeAudit(String action, String actorId, String tenantId, String entityId, String beforeAfterSummary, String correlationId, Map<String, String> summary) {
    auditRecords.add(new IntegrationAuditRecord(action, actorId, tenantId, entityId, beforeAfterSummary, correlationId, hash(action + tenantId + entityId + summary), Map.copyOf(summary)));
  }

  private void recordMetric(String name) {
    metrics.merge(name, 1L, Long::sum);
  }

  private static Map<String, String> safeMetadata(Map<String, String> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return Map.of();
    }
    Map<String, String> safe = new HashMap<>();
    for (Map.Entry<String, String> entry : metadata.entrySet()) {
      String key = entry.getKey().toLowerCase(Locale.ROOT);
      if (key.contains("secret") || key.contains("password") || key.contains("privatekey") || key.contains("apikey") || key.contains("api_key")) {
        safe.put(entry.getKey(), "<redacted>");
      } else {
        safe.put(entry.getKey(), entry.getValue());
      }
    }
    return Map.copyOf(safe);
  }

  private static String canonicalAccount(CreateServiceAccountCommand command) {
    return String.join("|", command.tenantId(), command.actorId(), command.displayName(), command.principalType(), String.join(",", command.scopes()), String.join(",", command.allowedChannels()), command.status().name(), nullToEmpty(command.expiresAt()));
  }

  private static String canonicalCredential(RegisterCredentialCommand command) {
    return String.join("|", command.tenantId(), command.accountId(), command.actorId(), command.credentialName(), command.credentialType().name(), String.valueOf(command.generateSecret()), nullToEmpty(command.providedSecretRef()), command.metadata().toString());
  }

  private static String accountKey(String tenantId, String accountId) {
    return tenantId + ":" + accountId;
  }

  private static String credentialKey(String tenantId, String credentialId) {
    return tenantId + ":" + credentialId;
  }

  private static String activeCredentialNameKey(String tenantId, String accountId, String credentialName) {
    return tenantId + ":" + accountId + ":" + credentialName.toLowerCase(Locale.ROOT).trim();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String nullToEmpty(Object value) {
    return value == null ? "" : value.toString();
  }

  private static String deterministicId(String... parts) {
    return UUID.nameUUIDFromBytes(String.join("|", parts).getBytes(StandardCharsets.UTF_8)).toString();
  }

  private static String hash(String value) {
    return hash(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String hash(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required", ex);
    }
  }

  private static IntegrationError error(String code, String reason, String message, String correlationId, boolean retryable) {
    return new IntegrationError(code, reason, message, correlationId, retryable);
  }

  public interface SecretProvider {
    SecretCreation generate(String tenantId, String accountId, CredentialType credentialType, String actorId, String correlationId);

    SecretCreation rotate(String existingSecretRef, String tenantId, String accountId, CredentialType credentialType, String actorId, String correlationId);

    SecretPointer reference(String secretRef, String tenantId, String accountId, CredentialType credentialType, String correlationId);
  }

  public static final class LocalDevSecretProvider implements SecretProvider {
    private final Map<String, SecretPointer> pointers = new HashMap<>();

    @Override
    public SecretCreation generate(String tenantId, String accountId, CredentialType credentialType, String actorId, String correlationId) {
      String secret = "local-dev-" + UUID.randomUUID();
      String ref = "local-dev://" + tenantId + "/" + accountId + "/" + credentialType.name().toLowerCase(Locale.ROOT) + "/" + hash(secret).substring(0, 16);
      SecretPointer pointer = new SecretPointer(ref, "v1", hash(secret), true);
      pointers.put(ref, pointer);
      return new SecretCreation(pointer, Optional.of(secret));
    }

    @Override
    public SecretCreation rotate(String existingSecretRef, String tenantId, String accountId, CredentialType credentialType, String actorId, String correlationId) {
      String secret = "local-dev-" + UUID.randomUUID();
      int version = Optional.ofNullable(pointers.get(existingSecretRef)).map(pointer -> Integer.parseInt(pointer.secretVersion().substring(1)) + 1).orElse(1);
      String ref = "local-dev://" + tenantId + "/" + accountId + "/" + credentialType.name().toLowerCase(Locale.ROOT) + "/" + hash(secret).substring(0, 16);
      SecretPointer pointer = new SecretPointer(ref, "v" + version, hash(secret), true);
      pointers.put(ref, pointer);
      return new SecretCreation(pointer, Optional.of(secret));
    }

    @Override
    public SecretPointer reference(String secretRef, String tenantId, String accountId, CredentialType credentialType, String correlationId) {
      SecretPointer existing = pointers.get(secretRef);
      if (existing != null) {
        return existing;
      }
      return new SecretPointer(secretRef, "external", "", !isBlank(secretRef));
    }
  }

  public enum ServiceAccountStatus {
    ACTIVE,
    SUSPENDED,
    REVOKED
  }

  public enum CredentialType {
    API_KEY,
    OAUTH_CLIENT_SECRET,
    WEBHOOK_HMAC,
    SFTP_PASSWORD,
    SFTP_PRIVATE_KEY
  }

  public enum CredentialStatus {
    ACTIVE,
    ROTATED,
    REVOKED,
    EXPIRED
  }

  public record CreateServiceAccountCommand(String tenantId, String idempotencyKey, String actorId, String displayName, String principalType, List<String> scopes, List<String> allowedChannels, ServiceAccountStatus status, String expiresAt, String correlationId) {}

  public record RegisterCredentialCommand(String tenantId, String accountId, String idempotencyKey, String actorId, String credentialName, CredentialType credentialType, boolean generateSecret, String providedSecretRef, String expiresAt, Map<String, String> metadata, String correlationId) {}

  public record RotateCredentialCommand(String tenantId, String credentialId, String idempotencyKey, String actorId, int expectedVersion, boolean generateSecret, String newSecretRef, String approvedBy, String correlationId) {}

  public record RevokeCredentialCommand(String tenantId, String credentialId, String idempotencyKey, String actorId, int expectedVersion, String approvedBy, String reason, String correlationId) {}

  public record ResolveCredentialCommand(String tenantId, String credentialId, String purpose, String usedBy, String correlationId) {}

  public record ServiceAccount(String tenantId, String accountId, String displayName, String principalType, List<String> scopes, List<String> allowedChannels, ServiceAccountStatus status, String expiresAt, int version, String updatedBy, Instant createdAt, Instant updatedAt, String correlationId) {}

  public record CredentialReference(String tenantId, String credentialId, String accountId, String credentialName, CredentialType credentialType, String secretRef, String secretVersion, CredentialStatus status, Instant lastRotatedAt, String expiresAt, Map<String, String> metadata, int version, String updatedBy, Instant createdAt, Instant updatedAt, String correlationId) {
    CredentialReference withSecret(String secretRef, String secretVersion, String actorId, Instant now, String correlationId) {
      return new CredentialReference(tenantId, credentialId, accountId, credentialName, credentialType, secretRef, secretVersion, CredentialStatus.ACTIVE, now, expiresAt, metadata, version + 1, actorId, createdAt, now, correlationId);
    }

    CredentialReference withStatus(CredentialStatus status, String actorId, Instant now, String correlationId) {
      return new CredentialReference(tenantId, credentialId, accountId, credentialName, credentialType, secretRef, secretVersion, status, lastRotatedAt, expiresAt, metadata, version + 1, actorId, createdAt, now, correlationId);
    }
  }

  public record ServiceAccountResponse(String id, ServiceAccountStatus status, int version, Map<String, String> resultSummary, List<String> validationMessages, String auditRef, String correlationId) {}

  public record CredentialResponse(String id, String accountId, CredentialType credentialType, String credentialName, CredentialStatus status, String secretRef, String secretVersion, int version, Map<String, String> resultSummary, List<String> validationMessages, String auditRef, String correlationId, Optional<String> oneTimeSecret) {}

  public record SecretPointer(String secretRef, String secretVersion, String secretFingerprint, boolean available) {}

  public record SecretCreation(SecretPointer pointer, Optional<String> oneTimeSecret) {}

  public record SecretResolution(String secretRef, String secretVersion, CredentialStatus status, String correlationId) {}

  public record IntegrationOutboxEvent(String eventKey, String eventType, String eventVersion, String sourceService, String actorId, String correlationId, String idempotencyKey, Instant occurredAt, Map<String, String> payload) {}

  public record IntegrationAuditRecord(String action, String actorId, String tenantId, String entityId, String beforeAfterSummary, String correlationId, String replayHash, Map<String, String> summary) {}

  public record CredentialUsageAudit(String tenantId, String credentialId, String purpose, String usedBy, boolean success, Instant occurredAt, String correlationId) {}

  private record IdempotencyEntry<T>(String requestHash, T response) {}

  public record IntegrationError(String code, String reason, String message, String correlationId, boolean retryable) {}

  public record IntegrationResult<T>(boolean valid, Optional<T> value, Optional<IntegrationError> error) {
    public static <T> IntegrationResult<T> success(T value) {
      return new IntegrationResult<>(true, Optional.of(value), Optional.empty());
    }

    public static <T> IntegrationResult<T> failure(IntegrationError error) {
      return new IntegrationResult<>(false, Optional.empty(), Optional.of(error));
    }
  }
}
