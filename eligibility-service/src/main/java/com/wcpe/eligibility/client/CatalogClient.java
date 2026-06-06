package com.wcpe.eligibility.client;

import com.wcpe.eligibility.domain.models.*;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CatalogClient {
    static final Map<String, Investor> INVESTORS = Map.of(
        "FNMA", new Investor("FNMA", "Federal National Mortgage Association", "ACTIVE", List.of("CONVENTIONAL"), LocalDate.of(2020, 1, 1), null),
        "FHLMC", new Investor("FHLMC", "Federal Home Loan Mortgage Corp", "ACTIVE", List.of("CONVENTIONAL"), LocalDate.of(2020, 1, 1), null),
        "FREMAC", new Investor("FREMAC", "Freddie Mac Suspended", "SUSPENDED", List.of("CONVENTIONAL"), LocalDate.of(2020, 1, 1), null)
    );

    static final Map<String, Product> PRODUCTS = Map.of(
        "CONV30", new Product("CONV30", "Conventional 30yr", "CONVENTIONAL", List.of("RETAIL", "BROKER"), List.of("CA", "NY", "TX", "FL"), LocalDate.of(2020, 1, 1), null),
        "CONV15", new Product("CONV15", "Conventional 15yr", "CONVENTIONAL", List.of("RETAIL", "BROKER"), List.of("CA", "NY", "TX", "FL"), LocalDate.of(2020, 1, 1), null)
    );

    static final Map<String, Channel> CHANNELS = Map.of(
        "RETAIL", new Channel("RETAIL", "Retail", "ACTIVE", LocalDate.of(2020, 1, 1), null),
        "BROKER", new Channel("BROKER", "Broker", "ACTIVE", LocalDate.of(2020, 1, 1), null)
    );

    static final Map<String, Integer> CONFORMING_LOAN_LIMITS = Map.of(
        "CA", 768875, "NY", 768875, "TX", 768875, "FL", 768875, "HI", 1153300
    );

    public String getInvestorStatus(String investorCode) {
        Investor investor = INVESTORS.get(investorCode);
        return investor != null ? investor.status() : null;
    }

    public boolean doesProductSupportInvestor(String productCode, String investorCode) {
        Product product = PRODUCTS.get(productCode);
        Investor investor = INVESTORS.get(investorCode);
        if (product == null || investor == null) return false;
        return investor.productFamilies().contains(product.productFamily());
    }

    public boolean isChannelAllowed(String productCode, String channelCode) {
        Product product = PRODUCTS.get(productCode);
        if (product == null) return false;
        return product.allowedChannels().contains(channelCode);
    }

    public String resolveChannel(EligibilityRequest request) {
        return "RETAIL";
    }

    public boolean isStateAllowed(String productCode, String state) {
        Product product = PRODUCTS.get(productCode);
        if (product == null) return false;
        return product.allowedStates().contains(state);
    }

    public List<String> getAllowedStates(String productCode) {
        Product product = PRODUCTS.get(productCode);
        return product != null ? product.allowedStates() : List.of();
    }

    public Integer getConformingLimit(String state) {
        return CONFORMING_LOAN_LIMITS.get(state);
    }

    public List<ProductCandidate> publishedConventionalPurchaseCandidates(String channelCode, String state) {
        return PRODUCTS.values().stream()
            .filter(product -> "CONVENTIONAL".equals(product.productFamily()))
            .filter(product -> product.allowedChannels().contains(channelCode))
            .filter(product -> product.allowedStates().contains(state))
            .flatMap(product -> INVESTORS.values().stream()
                .filter(investor -> "ACTIVE".equals(investor.status()))
                .filter(investor -> investor.productFamilies().contains(product.productFamily()))
                .map(investor -> new ProductCandidate(
                    UUID.nameUUIDFromBytes((product.productCode() + ":" + investor.investorCode()).getBytes(StandardCharsets.UTF_8)),
                    product.productCode(),
                    investor.investorCode()
                )))
            .toList();
    }
}
