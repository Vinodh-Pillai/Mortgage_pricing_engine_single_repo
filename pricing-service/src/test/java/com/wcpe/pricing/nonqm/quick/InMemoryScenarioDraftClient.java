package com.wcpe.pricing.nonqm.quick;

import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.QuickQuoteResult;
import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.ScenarioDraftClient;
import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.ScenarioReference;

public final class InMemoryScenarioDraftClient implements ScenarioDraftClient {
    @Override
    public ScenarioReference createFromQuickQuote(QuickQuoteResult result) {
        return new ScenarioReference("scenario-from-" + result.quickQuoteId(), result.quickQuoteId(), result.assumptions());
    }
}
