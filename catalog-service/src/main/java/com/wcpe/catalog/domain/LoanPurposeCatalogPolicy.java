package com.wcpe.catalog.domain;

import java.time.Instant;
import java.util.*;

final class LoanPurposeCatalogPolicy {
  private static final Set<String> MVP_CODES = Set.of("PURCHASE", "RATE_TERM_REFI", "CASH_OUT_REFI", "CONSTRUCTION_TO_PERMANENT");

  private LoanPurposeCatalogPolicy() {}

  static void validateDraft(LoanPurposeDraftRequest request, boolean duplicateCode) {
    if (request == null) throw new CatalogException("LOAN_PURPOSE_REQUEST_REQUIRED");
    String code = canonical(required(request.purposeCode(), "LOAN_PURPOSE_CODE_REQUIRED"));
    required(request.displayName(), "DISPLAY_NAME_REQUIRED");
    required(request.category(), "LOAN_PURPOSE_CATEGORY_REQUIRED");
    requiredInstant(request.effectiveStart());
    if (!MVP_CODES.contains(code)) throw new CatalogException("LOAN_PURPOSE_NOT_SUPPORTED");
    if (duplicateCode) throw new CatalogException("LOAN_PURPOSE_CODE_DUPLICATE");
    if (request.effectiveEnd() != null && !request.effectiveEnd().isAfter(requiredInstant(request.effectiveStart()))) throw new CatalogException("EFFECTIVE_WINDOW_INVALID");
    if (Boolean.TRUE.equals(request.isCashOut()) && !Boolean.TRUE.equals(request.isRefinance())) throw new CatalogException("CASH_OUT_REQUIRES_REFINANCE");
    if (("PURCHASE".equals(code) || "PURCHASE".equals(canonical(request.category()))) && Boolean.TRUE.equals(request.requiresExistingLien())) throw new CatalogException("PURCHASE_CANNOT_REQUIRE_EXISTING_LIEN");
    validateStoryCitedFlags(code, request);
  }

  static List<String> canonicalAliases(LoanPurposeDraftRequest request) {
    LinkedHashSet<String> aliases = new LinkedHashSet<>();
    aliases.add(canonical(request.purposeCode()));
    if (request.agencyAliases() != null) {
      for (String alias : request.agencyAliases()) if (alias != null && !alias.isBlank()) aliases.add(canonical(alias));
    }
    return List.copyOf(aliases);
  }

  private static void validateStoryCitedFlags(String code, LoanPurposeDraftRequest request) {
    switch (code) {
      case "PURCHASE" -> requireFlags(request, false, false, false, true);
      case "RATE_TERM_REFI" -> requireFlags(request, true, false, true, true);
      case "CASH_OUT_REFI" -> requireFlags(request, true, true, true, true);
      case "CONSTRUCTION_TO_PERMANENT" -> {
        if (Boolean.TRUE.equals(request.eligibleForConventional())) throw new CatalogException("CONSTRUCTION_TO_PERMANENT_DISABLED");
      }
      default -> throw new CatalogException("LOAN_PURPOSE_NOT_SUPPORTED");
    }
  }

  private static void requireFlags(LoanPurposeDraftRequest request, boolean refinance, boolean cashOut, boolean lien, boolean eligible) {
    if (Boolean.TRUE.equals(request.isRefinance()) != refinance) throw new CatalogException("LOAN_PURPOSE_FLAGS_INVALID");
    if (Boolean.TRUE.equals(request.isCashOut()) != cashOut) throw new CatalogException("LOAN_PURPOSE_FLAGS_INVALID");
    if (Boolean.TRUE.equals(request.requiresExistingLien()) != lien) throw new CatalogException("LOAN_PURPOSE_FLAGS_INVALID");
    if (Boolean.TRUE.equals(request.eligibleForConventional()) != eligible) throw new CatalogException("LOAN_PURPOSE_FLAGS_INVALID");
  }

  private static String required(String value, String code) { if (value == null || value.isBlank()) throw new CatalogException(code); return value; }
  private static Instant requiredInstant(Instant value) { if (value == null) throw new CatalogException("EFFECTIVE_START_REQUIRED"); return value; }
  private static String canonical(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
}
