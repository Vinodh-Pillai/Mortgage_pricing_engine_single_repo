package com.wcpe.adjustment.overlay;

public enum OverlayPolicyType {
    ESCROW_WAIVER(WaterfallPosition.LLPA_ADJUSTMENT, "Escrow Waiver"),
    ARM_CAPS(WaterfallPosition.LLPA_ADJUSTMENT, "ARM Caps/Floors"),
    BUYDOWN(WaterfallPosition.LLPA_ADJUSTMENT, "Temporary Buydown"),
    HIGH_BALANCE(WaterfallPosition.LLPA_ADJUSTMENT, "High Balance"),
    JUMBO(WaterfallPosition.MARGIN_COMPONENT, "Jumbo/Portfolio"),
    ODD_TERM(WaterfallPosition.LLPA_ADJUSTMENT, "Odd Term/Short Duration");

    private final WaterfallPosition waterfallPosition;
    private final String displayName;

    OverlayPolicyType(WaterfallPosition waterfallPosition, String displayName) {
        this.waterfallPosition = waterfallPosition;
        this.displayName = displayName;
    }

    public WaterfallPosition waterfallPosition() {
        return waterfallPosition;
    }

    public String displayName() {
        return displayName;
    }

    public enum WaterfallPosition {
        LLPA_ADJUSTMENT,
        MARGIN_COMPONENT
    }
}
