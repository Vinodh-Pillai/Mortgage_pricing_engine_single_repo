package com.wcpe.adjustment.gridloader;

import org.springframework.stereotype.Component;

@Component
public final class FhlmcLlpaCsvParser extends GseLlpaCsvParser {
    @Override
    String investorCode() {
        return "FHLMC";
    }
}
