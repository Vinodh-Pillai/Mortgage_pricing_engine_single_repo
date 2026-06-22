package com.wcpe.ratefeed.normalization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NormalizationProfileRepository extends JpaRepository<NormalizationProfile, UUID> {

    List<NormalizationProfile> findByTenantIdAndStatus(UUID tenantId, String status);

    List<NormalizationProfile> findByTenantId(UUID tenantId);

    List<NormalizationProfile> findByTenantIdAndInvestorCodeAndProductCode(
            UUID tenantId, String investorCode, String productCode);

    @Query("SELECT p FROM NormalizationProfile p " +
           "WHERE p.tenantId = :tenantId " +
           "  AND p.formatType = :formatType " +
           "  AND p.investorCode = :investorCode " +
           "  AND p.productCode = :productCode " +
           "  AND p.status = 'PUBLISHED' " +
           "ORDER BY p.version DESC")
    List<NormalizationProfile> findPublishedByTenantFormatInvestorProduct(
            @Param("tenantId") UUID tenantId,
            @Param("formatType") String formatType,
            @Param("investorCode") String investorCode,
            @Param("productCode") String productCode);

    @Query("SELECT p FROM NormalizationProfile p " +
           "WHERE p.tenantId = :tenantId " +
           "  AND p.status = 'PUBLISHED' " +
           "ORDER BY p.investorCode, p.productCode, p.version DESC")
    List<NormalizationProfile> findAllPublishedByTenant(@Param("tenantId") UUID tenantId);

    Optional<NormalizationProfile> findByTenantIdAndName(UUID tenantId, String name);

    boolean existsByTenantIdAndName(UUID tenantId, String name);
}
