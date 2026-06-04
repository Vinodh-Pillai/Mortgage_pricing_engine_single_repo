package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuoteCreateFailsWithoutRankingPolicyTest {
    @Test
    void missingRankingPolicyFailsClosedWithoutHardCodedOrdering() {
        QuoteApplicationService service = QuoteTestSupport.service(
            new QuoteTestSupport.FixtureDependencies(false, java.util.List.of(QuoteTestSupport.candidate("A"))),
            new InMemoryQuoteCache()
        );

        assertThatThrownBy(() -> service.createQuote(QuoteTestSupport.request("idem-no-policy")))
            .isInstanceOf(QuoteCreateException.class)
            .hasMessageContaining("ranking policy")
            .satisfies(ex -> assertThat(((QuoteCreateException) ex).code()).isEqualTo("NO_ACTIVE_RANKING_POLICY"));
    }
}
