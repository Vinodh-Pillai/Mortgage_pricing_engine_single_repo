package com.wcpe.eligibility;

import com.wcpe.eligibility.domain.extension.UnsupportedProductFamilyException;
import com.wcpe.eligibility.domain.extension.UnsupportedProductFamilyPolicy;
import com.wcpe.eligibility.domain.models.BorrowerProfile;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.LoanProfile;
import com.wcpe.eligibility.domain.models.ProductCandidate;
import com.wcpe.eligibility.domain.models.ProductFamily;
import com.wcpe.eligibility.domain.models.PropertyProfile;
import com.wcpe.eligibility.domain.models.QuoteType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnsupportedProductFamilyPolicyTest {
    private final UnsupportedProductFamilyPolicy policy = new UnsupportedProductFamilyPolicy();

    @Test
    void allowsConventionalPurchaseAndDefaultsMissingExtensionFields() {
        EligibilityRequest legacyRequest = request(null, null);
        EligibilityRequest explicitConventional = request(ProductFamily.CONVENTIONAL, QuoteType.CONVENTIONAL_PURCHASE);

        assertDoesNotThrow(() -> policy.rejectUnsupportedForPii03(legacyRequest));
        assertDoesNotThrow(() -> policy.rejectUnsupportedForPii03(explicitConventional));
        assertEquals(ProductFamily.CONVENTIONAL, legacyRequest.resolvedProductFamily());
        assertEquals(QuoteType.CONVENTIONAL_PURCHASE, legacyRequest.resolvedQuoteType());
    }

    @Test
    void rejectsFhaPurchaseFailClosed() {
        UnsupportedProductFamilyException ex = assertThrows(
            UnsupportedProductFamilyException.class,
            () -> policy.rejectUnsupportedForPii03(request(ProductFamily.FHA, QuoteType.FHA_PURCHASE))
        );

        assertEquals(ProductFamily.FHA, ex.productFamily());
        assertEquals(QuoteType.FHA_PURCHASE, ex.quoteType());
    }

    private EligibilityRequest request(ProductFamily productFamily, QuoteType quoteType) {
        return new EligibilityRequest(
            new BorrowerProfile(742, new BigDecimal("8500"), new BigDecimal("1200")),
            new PropertyProfile("TX", "Austin", "78701", "SINGLE_FAMILY", 1, "PRIMARY_RESIDENCE", new BigDecimal("500000"), new BigDecimal("500000")),
            new LoanProfile("PURCHASE", new BigDecimal("400000"), BigDecimal.ZERO, 30, "FULL_DOC", "AUTOMATED", 1),
            new ProductCandidate(UUID.randomUUID(), "CONV30", "FNMA"),
            productFamily,
            quoteType
        );
    }
}
