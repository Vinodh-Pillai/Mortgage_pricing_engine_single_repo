package com.wcpe.auditreplay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "audit_snapshots")
public class AuditSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "content_hash", length = 128, nullable = false, updatable = false)
    private String contentHash;

    @Column(name = "encryption_key_ref", length = 180, nullable = false, updatable = false)
    private String encryptionKeyRef;

    @Column(name = "encrypted_snapshot_json", columnDefinition = "jsonb", nullable = false, updatable = false)
    private byte[] encryptedSnapshotJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditSnapshot() {
        // JPA only.
    }

    public static AuditSnapshot create(UUID tenantId, String contentHash, String encryptionKeyRef, byte[] encryptedSnapshotJson) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        requireText(contentHash, "contentHash is required");
        requireText(encryptionKeyRef, "encryptionKeyRef is required");
        Objects.requireNonNull(encryptedSnapshotJson, "encryptedSnapshotJson is required");
        AuditSnapshot snapshot = new AuditSnapshot();
        snapshot.id = UUID.randomUUID();
        snapshot.tenantId = tenantId;
        snapshot.contentHash = contentHash;
        snapshot.encryptionKeyRef = encryptionKeyRef;
        snapshot.encryptedSnapshotJson = encryptedSnapshotJson.clone();
        snapshot.createdAt = Instant.now();
        return snapshot;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getEncryptionKeyRef() {
        return encryptionKeyRef;
    }

    public byte[] getEncryptedSnapshotJson() {
        return encryptedSnapshotJson == null ? null : encryptedSnapshotJson.clone();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
