package com.wcpe.eligibility;

import com.wcpe.eligibility.domain.extension.EligibilityRuleChainResolver;
import com.wcpe.eligibility.domain.extension.ResolveEligibilityRuleChainCommand;
import com.wcpe.eligibility.domain.extension.UnsupportedProductFamilyException;
import com.wcpe.eligibility.domain.models.ProductFamily;
import com.wcpe.eligibility.domain.models.QuoteType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EligibilityRuleChainResolverTest {
    private final EligibilityRuleChainResolver resolver = new EligibilityRuleChainResolver();

    @Test
    void returnsConventionalChainForPii03() {
        var chain = resolver.resolve(new ResolveEligibilityRuleChainCommand(
            ProductFamily.CONVENTIONAL,
            QuoteType.CONVENTIONAL_PURCHASE,
            UUID.randomUUID(),
            LocalDate.parse("2026-06-03")
        ));

        assertEquals("loan-limit", chain.get(0));
        assertTrue(chain.contains("investor-overlays"));
        assertTrue(chain.contains("cache-explain-hooks"));
    }

    @Test
    void rejectsFutureFamilyChainResolution() {
        assertThrows(UnsupportedProductFamilyException.class, () -> resolver.resolve(
            new ResolveEligibilityRuleChainCommand(ProductFamily.VA, QuoteType.VA_PURCHASE, UUID.randomUUID(), LocalDate.now())
        ));
    }
}
