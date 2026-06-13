package com.wcpe.quote;

public interface AdjustmentCalculationPort {
    AdjustmentCalculationResult calculate(AdjustmentCalculationRequest request);

    static AdjustmentCalculationPort preserveCandidateAdjustments() {
        return request -> null;
    }
}
