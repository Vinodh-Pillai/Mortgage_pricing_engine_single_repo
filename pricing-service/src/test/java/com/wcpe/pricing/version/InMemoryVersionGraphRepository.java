package com.wcpe.pricing.version;

import com.wcpe.pricing.version.PricingVersionResolver.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryVersionGraphRepository implements VersionGraphRepository {
    private final Map<UUID, VersionGraphResult> graphs = new ConcurrentHashMap<>();
    private final List<ArtifactVersion> artifactVersions = Collections.synchronizedList(new ArrayList<>());
    private final List<VersionGraphEvent> events = Collections.synchronizedList(new ArrayList<>());
    private final List<VersionGraphAudit> audits = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> invalidatedCacheKeys = ConcurrentHashMap.newKeySet();

    @Override
    public void saveGraph(VersionGraphResult result) {
        graphs.put(result.versionGraphId(), result);
    }

    @Override
    public Optional<VersionGraphResult> findById(String tenantId, UUID graphId) {
        return Optional.ofNullable(graphs.get(graphId));
    }

    @Override
    public List<ArtifactVersion> findArtifactVersions(String tenantId, ArtifactType type, String productCode,
            String investorCode, String channelCode) {
        synchronized (artifactVersions) {
            return artifactVersions.stream()
                    .filter(version -> tenantId.equals(version.tenantId()))
                    .filter(version -> type == version.artifactType())
                    .filter(version -> Objects.equals(productCode, version.productCode()))
                    .filter(version -> Objects.equals(investorCode, version.investorCode()))
                    .filter(version -> Objects.equals(channelCode, version.channelCode()))
                    .toList();
        }
    }

    @Override
    public void saveEvent(VersionGraphEvent event) {
        events.add(event);
    }

    @Override
    public void saveAudit(VersionGraphAudit audit) {
        audits.add(audit);
    }

    @Override
    public void invalidateCache(String tenantId, String cacheKey) {
        invalidatedCacheKeys.add(cacheKey);
    }

    @Override
    public List<String> findEventsByTenant(String tenantId) {
        synchronized (events) {
            return events.stream()
                    .filter(event -> tenantId.equals(event.tenantId()))
                    .map(VersionGraphEvent::eventType)
                    .toList();
        }
    }

    public List<VersionGraphEvent> events() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    public List<VersionGraphAudit> audits() {
        synchronized (audits) {
            return List.copyOf(audits);
        }
    }

    public boolean wasCacheInvalidated(String key) {
        return invalidatedCacheKeys.contains(key);
    }

    public void addArtifactVersion(ArtifactVersion artifactVersion) {
        artifactVersions.add(artifactVersion);
    }
}
