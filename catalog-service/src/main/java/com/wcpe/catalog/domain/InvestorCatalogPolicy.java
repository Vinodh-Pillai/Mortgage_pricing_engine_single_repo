package com.wcpe.catalog.domain;

import java.util.*;

final class InvestorCatalogPolicy {
  static final Set<String> INVESTOR_TYPES = Set.of("AGENCY", "AGGREGATOR", "PORTFOLIO", "WHOLESALE_INVESTOR");
  static final Set<String> AGENCIES = Set.of("FANNIE_MAE", "FREDDIE_MAC");
  static final Set<String> DELIVERY_TYPES = Set.of("BEST_EFFORTS", "MANDATORY");

  private InvestorCatalogPolicy() {}

  static void validateDraft(InvestorCatalogDraftRequest request, boolean duplicateInvestorCode) {
    if (request == null) throw new CatalogException("INVESTOR_REQUEST_REQUIRED");
    if (blank(request.investorCode())) throw new CatalogException("INVESTOR_CODE_REQUIRED");
    if (duplicateInvestorCode) throw new CatalogException("DUPLICATE_INVESTOR_CODE");
    if (blank(request.legalName())) throw new CatalogException("INVESTOR_LEGAL_NAME_REQUIRED");
    if (!INVESTOR_TYPES.contains(request.investorType())) throw new CatalogException("INVESTOR_TYPE_NOT_SUPPORTED");
    if ("AGENCY".equals(request.investorType()) && !AGENCIES.contains(request.agency())) throw new CatalogException("AGENCY_REQUIRED");
    if ("FANNIE_MAE".equals(request.agency()) && !"FNMA".equals(request.investorCode())) throw new CatalogException("AGENCY_CODE_MISMATCH");
    if ("FREDDIE_MAC".equals(request.agency()) && !"FHLMC".equals(request.investorCode())) throw new CatalogException("AGENCY_CODE_MISMATCH");
    if (request.effectiveStart() == null) throw new CatalogException("EFFECTIVE_START_REQUIRED");
    if (request.effectiveEnd() != null && !request.effectiveEnd().isAfter(request.effectiveStart())) throw new CatalogException("INVALID_EFFECTIVE_WINDOW");
    if (request.deliveryTypes() == null || request.deliveryTypes().isEmpty()) throw new CatalogException("DELIVERY_TYPE_REQUIRED");
    for (String deliveryType : request.deliveryTypes()) if (!DELIVERY_TYPES.contains(deliveryType)) throw new CatalogException("DELIVERY_TYPE_NOT_SUPPORTED");
    if (request.activeChannelCodes() == null || request.activeChannelCodes().isEmpty()) throw new CatalogException("ACTIVE_CHANNEL_REQUIRED");
    Map<String, Set<String>> sellerIdsByChannel = new LinkedHashMap<>();
    for (InvestorSellerServicerId seller : request.sellerServicerIds() == null ? List.<InvestorSellerServicerId>of() : request.sellerServicerIds()) {
      if (seller == null || blank(seller.channelCode()) || blank(seller.sellerId()) || blank(seller.servicerId())) throw new CatalogException("SELLER_ID_REQUIRED");
      if (!request.activeChannelCodes().contains(seller.channelCode())) throw new CatalogException("SELLER_CHANNEL_NOT_ACTIVE");
      if (!sellerIdsByChannel.computeIfAbsent(seller.channelCode(), ignored -> new LinkedHashSet<>()).add(seller.sellerId())) throw new CatalogException("DUPLICATE_SELLER_ID");
    }
    for (String channel : request.activeChannelCodes()) if (!sellerIdsByChannel.containsKey(channel)) throw new CatalogException("SELLER_ID_REQUIRED");
  }

  static String maskSellerId(String sellerId, boolean canViewSecret) {
    if (sellerId == null) return "";
    if (canViewSecret) return sellerId;
    if (sellerId.length() <= 3) return "***";
    return "***" + sellerId.substring(sellerId.length() - 3);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
