package com.wcpe.margin.srp;

import java.math.BigDecimal;

public record SrpResult(
    String reasonCode,
    BigDecimal srpBps,
    BigDecimal srpPoints,
    BigDecimal srpDollars,
    BigDecimal noteRate,
    BigDecimal servicingRate) {}
