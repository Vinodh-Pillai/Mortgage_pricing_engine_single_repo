package com.wcpe.margin.srp;

import com.wcpe.margin.CompanyMarginPolicyService.MarginScope;
import java.math.BigDecimal;

public record SrpRule(
    int priority,
    String investorCode,
    String channelCode,
    String productFamily,
    BigDecimal servicingRateSpread,
    BigDecimal minSpread,
    BigDecimal maxSpread,
    int roundingScale,
    String reasonCode,
    MarginScope scope) {}
