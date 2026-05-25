package com.wcpe.eligibility.domain.rules;

public enum RuleType {
    R01("R01", "FICO_MINIMUM", "Representative FICO must be at least 620"),
    R02("R02", "LTV_MAXIMUM", "Loan-to-value ratio must not exceed 97%"),
    R03("R03", "DTI_MAXIMUM", "Debt-to-income ratio must not exceed 43%"),
    R04("R04", "PROPERTY_TYPE", "Property type must be acceptable"),
    R05("R05", "OCCUPANCY_TYPE", "Occupancy type must be acceptable"),
    R06("R06", "LOAN_PURPOSE", "Loan purpose must be acceptable"),
    R07("R07", "INVESTOR_STATUS", "Investor must be active"),
    R08("R08", "PRODUCT_INVESTOR", "Product must support the investor"),
    R09("R09", "CHANNEL_ALLOWED", "Channel must be allowed by product"),
    R10("R10", "STATE_ALLOWED", "State must be allowed by product"),
    R11("R11", "LOAN_AMOUNT_LIMIT", "Loan amount must be within conforming limit"),
    R12("R12", "DOCUMENTATION_TYPE", "Documentation type must be acceptable");

    private final String code;
    private final String name;
    private final String description;

    RuleType(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }

    public static RuleType fromCode(String code) {
        for (RuleType rt : values()) {
            if (rt.getCode().equals(code)) return rt;
        }
        throw new IllegalArgumentException("Unknown rule code: " + code);
    }
}
