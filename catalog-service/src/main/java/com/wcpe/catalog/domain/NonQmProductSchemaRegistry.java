package com.wcpe.catalog.domain;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class NonQmProductSchemaRegistry {
  private static final List<String> NON_QM_CHANNELS = List.of("RETAIL", "CORRESPONDENT", "WHOLESALE");
  private final Map<NonQmProductType, NonQmProductSchema> schemas;

  NonQmProductSchemaRegistry() {
    Map<NonQmProductType, NonQmProductSchema> configured = new EnumMap<>(NonQmProductType.class);
    configured.put(NonQmProductType.DSCR, schema("DSCR", List.of(
        required("dscrRatio", "DECIMAL", "Minimum DSCR required by the product definition."),
        required("propertyType", "STRING", "Eligible collateral property type."),
        required("loanPurpose", "STRING", "Eligible loan purpose."),
        required("maxLtv", "DECIMAL", "Maximum LTV attribute supplied by product operations."),
        required("minFico", "INTEGER", "Minimum credit score attribute supplied by product operations."),
        optional("rentSchedule", "OBJECT", "Rent roll or rent schedule details."),
        optional("interestOnly", "BOOLEAN", "Whether interest-only terms are allowed."),
        optional("prepaymentPenalty", "STRING", "Prepayment penalty descriptor."))));
    configured.put(NonQmProductType.BANK_STATEMENT, schema("BANK_STATEMENT", List.of(
        required("monthsOfStatements", "INTEGER", "Number of bank statement months required."),
        required("statementType", "STRING", "Personal, business, or combined statement type."),
        required("expenseRatio", "DECIMAL", "Expense factor supplied by product operations."),
        required("depositTrend", "STRING", "Deposit trend descriptor."),
        required("businessType", "STRING", "Borrower business type descriptor."),
        required("maxLtv", "DECIMAL", "Maximum LTV attribute supplied by product operations."),
        required("minFico", "INTEGER", "Minimum credit score attribute supplied by product operations."))));
    configured.put(NonQmProductType.ASSET_DEPLETION, schema("ASSET_DEPLETION", List.of(
        required("assetType", "STRING", "Asset category used for qualifying income."),
        required("depletionRate", "DECIMAL", "Annualized depletion rate supplied by product operations."),
        required("minAssetBalance", "DECIMAL", "Minimum qualifying assets supplied by product operations."),
        required("maxLtv", "DECIMAL", "Maximum LTV attribute supplied by product operations."),
        required("seasoningMonths", "INTEGER", "Asset seasoning requirement in months."))));
    configured.put(NonQmProductType.NO_RATIO, schema("NO_RATIO", List.of(
        optional("collateralProfile", "OBJECT", "Collateral or compensating-factor metadata."),
        optional("maxLtv", "DECIMAL", "Maximum LTV attribute supplied by product operations."),
        optional("minFico", "INTEGER", "Minimum credit score attribute supplied by product operations."))));
    configured.put(NonQmProductType.FOREIGN_NATIONAL, schema("FOREIGN_NATIONAL", List.of(
        optional("citizenshipDocumentation", "STRING", "Documentation descriptor supplied by product operations."),
        optional("visaType", "STRING", "Visa or residency descriptor when applicable."),
        optional("maxLtv", "DECIMAL", "Maximum LTV attribute supplied by product operations."),
        optional("minFico", "INTEGER", "Minimum credit score attribute supplied by product operations."))));
    configured.put(NonQmProductType.ITIN, schema("ITIN", List.of(
        optional("taxIdentificationType", "STRING", "Tax identification descriptor."),
        optional("residencyDocumentation", "STRING", "Residency documentation descriptor."),
        optional("maxLtv", "DECIMAL", "Maximum LTV attribute supplied by product operations."),
        optional("minFico", "INTEGER", "Minimum credit score attribute supplied by product operations."))));
    configured.put(NonQmProductType._1099_ONLY, schema("1099_ONLY", List.of(
        required("incomeDocType", "STRING", "Income documentation type, such as 1099 Only."),
        required("yearsInBusiness", "INTEGER", "Minimum business history supplied by product operations."),
        required("taxReturnWaiver", "BOOLEAN", "Whether tax returns are waived."),
        optional("maxLtv", "DECIMAL", "Maximum LTV attribute supplied by product operations."))));
    schemas = Map.copyOf(configured);
  }

  List<String> supportedProductTypes() {
    return Arrays.stream(NonQmProductType.values()).map(NonQmProductType::externalCode).toList();
  }

  NonQmProductSchema schema(String productType) {
    NonQmProductType type = NonQmProductType.fromExternal(productType);
    return schema(type);
  }

  NonQmProductSchema schema(NonQmProductType type) {
    NonQmProductSchema schema = schemas.get(type);
    if (schema == null) throw new CatalogException("UNKNOWN_NON_QM_PRODUCT_TYPE");
    return schema;
  }

  NonQmValidationResult validate(String productType, Map<String, Object> attributes) {
    NonQmProductSchema schema = schema(productType);
    Map<String, Object> safeAttributes = attributes == null ? Map.of() : attributes;
    List<NonQmValidationError> errors = new ArrayList<>();
    for (NonQmAttributeDefinition attribute : schema.attributes()) {
      Object value = safeAttributes.get(attribute.name());
      if (attribute.required() && value == null) {
        errors.add(new NonQmValidationError(attribute.name(), "REQUIRED", attribute.name() + " is required for " + schema.productType()));
      } else if (value != null && !matchesType(attribute.type(), value)) {
        errors.add(new NonQmValidationError(attribute.name(), "TYPE_MISMATCH", attribute.name() + " must be " + attribute.type()));
      }
    }
    return new NonQmValidationResult(errors.isEmpty(), List.copyOf(errors));
  }

  void requireValid(String productType, Map<String, Object> attributes) {
    NonQmValidationResult result = validate(productType, attributes);
    if (!result.valid()) throw new CatalogException("NON_QM_ATTRIBUTES_INVALID");
  }

  void requireAllowedChannel(String channelCode) {
    if (channelCode == null || channelCode.isBlank()) throw new CatalogException("CHANNEL_CODE_REQUIRED");
    if (!NON_QM_CHANNELS.contains(channelCode.trim().toUpperCase(Locale.ROOT))) throw new CatalogException("NON_QM_CHANNEL_NOT_SUPPORTED");
  }

  private static NonQmProductSchema schema(String productType, List<NonQmAttributeDefinition> attributes) {
    return new NonQmProductSchema("NON_QM", productType, "v1", attributes, NON_QM_CHANNELS);
  }

  private static NonQmAttributeDefinition required(String name, String type, String description) {
    return new NonQmAttributeDefinition(name, type, true, description);
  }

  private static NonQmAttributeDefinition optional(String name, String type, String description) {
    return new NonQmAttributeDefinition(name, type, false, description);
  }

  private static boolean matchesType(String type, Object value) {
    return switch (type) {
      case "DECIMAL" -> value instanceof Number || value instanceof BigDecimal || parseableDecimal(value);
      case "INTEGER" -> value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte;
      case "BOOLEAN" -> value instanceof Boolean;
      case "OBJECT" -> value instanceof Map<?, ?> || value instanceof List<?>;
      default -> value instanceof String;
    };
  }

  private static boolean parseableDecimal(Object value) {
    if (!(value instanceof String text) || text.isBlank()) return false;
    try {
      new BigDecimal(text);
      return true;
    } catch (NumberFormatException ex) {
      return false;
    }
  }
}
