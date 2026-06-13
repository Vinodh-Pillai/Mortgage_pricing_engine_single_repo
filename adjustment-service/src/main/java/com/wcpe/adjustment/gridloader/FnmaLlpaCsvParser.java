package com.wcpe.adjustment.gridloader;

import org.springframework.stereotype.Component;

@Component
public final class FnmaLlpaCsvParser extends GseLlpaCsvParser {
    @Override
    String investorCode() {
        return "FNMA";
    }
}
