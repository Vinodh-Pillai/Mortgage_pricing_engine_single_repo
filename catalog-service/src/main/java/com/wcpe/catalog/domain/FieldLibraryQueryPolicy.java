package com.wcpe.catalog.domain;

import java.util.*;

final class FieldLibraryQueryPolicy {
  private static final Map<String, String> CATEGORY_ALIASES = Map.ofEntries(
      Map.entry("application", "application"),
      Map.entry("creditapplication", "application"),
      Map.entry("creditapplicationfields", "application"),
      Map.entry("product", "product"),
      Map.entry("productfields", "product"),
      Map.entry("pipeline", "pipeline"),
      Map.entry("pipelineonly", "pipeline"),
      Map.entry("pipelineonlyfields", "pipeline"),
      Map.entry("client", "client"),
      Map.entry("clientsettings", "client-settings"),
      Map.entry("settings", "client-settings"),
      Map.entry("pricing", "pricing"),
      Map.entry("pricingnotification", "pricing-notification"),
      Map.entry("notification", "pricing-notification"),
      Map.entry("calculation", "calculation"),
      Map.entry("calculations", "calculation"));

  private FieldLibraryQueryPolicy() {}

  static FieldLibraryQueryResponse query(String requestedCategory, List<FieldMetadataResponse> fields,
                                         List<EnumerationTypeResponse> enumerations, boolean includeEnums,
                                         boolean tenantSpecific) {
    String category = canonicalCategory(requestedCategory);
    Map<String, EnumerationTypeResponse> enumByType = new LinkedHashMap<>();
    for (EnumerationTypeResponse enumeration : enumerations == null ? List.<EnumerationTypeResponse>of() : enumerations) {
      enumByType.put(normalizeKey(enumeration.enumTypeId()), enumeration);
    }

    List<FieldLibraryFieldResponse> boundedFields = new ArrayList<>();
    Map<String, EnumerationTypeResponse> usedEnums = new LinkedHashMap<>();
    for (FieldMetadataResponse field : fields == null ? List.<FieldMetadataResponse>of() : fields) {
      if (!category.equals(categoryFor(field))) continue;
      Map<String, Object> conditions = normalizedConditions(field.conditions());
      List<String> parentRefs = parentFieldReferences(conditions);
      String enumTypeId = enumTypeId(field, conditions);
      EnumerationTypeResponse enumeration = enumByType.get(normalizeKey(enumTypeId));
      if (enumeration != null) usedEnums.put(enumeration.enumTypeId(), enumeration);
      boundedFields.add(new FieldLibraryFieldResponse(field.id(), stableApiKey(field), field.oldId(), field.name(), field.description(), category,
          field.valueType(), field.sourceGroup(), sourceChip(field), typeChip(field.valueType()), boundedFields.size() + 1, true,
          conditions, parentRefs, field.disposition(), field.source(), enumTypeId,
          enumTypeId == null ? null : "/api/v1/product-catalog/enumerations/" + enumTypeId, includeEnums ? enumeration : null));
    }
    return new FieldLibraryQueryResponse(category, tenantSpecific ? "tenant" : "system/default", tenantSpecific,
        List.copyOf(boundedFields), includeEnums ? List.copyOf(usedEnums.values()) : List.of(), boundedFields.size());
  }

  private static String canonicalCategory(String value) {
    String key = normalizeKey(value);
    if (key.isBlank()) throw new CatalogException("FIELD_LIBRARY_CATEGORY_REQUIRED");
    String category = CATEGORY_ALIASES.get(key);
    if (category == null) throw new CatalogException("FIELD_LIBRARY_CATEGORY_UNSUPPORTED");
    return category;
  }

  private static String categoryFor(FieldMetadataResponse field) {
    String bySourceGroup = CATEGORY_ALIASES.get(normalizeKey(field.sourceGroup()));
    if (bySourceGroup != null) return bySourceGroup;
    String byCategory = CATEGORY_ALIASES.get(normalizeKey(field.category()));
    if (byCategory != null) return byCategory;
    return normalizeKey(field.category());
  }

  private static Map<String, Object> normalizedConditions(Map<String, Object> conditions) {
    if (conditions == null || conditions.isEmpty()) return Map.of();
    Map<String, Object> normalized = new LinkedHashMap<>();
    conditions.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> normalized.put(entry.getKey(), normalizeValue(entry.getValue())));
    return Collections.unmodifiableMap(normalized);
  }

  private static Object normalizeValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> nested = new LinkedHashMap<>();
      map.entrySet().stream()
          .sorted(Comparator.comparing(entry -> Objects.toString(entry.getKey(), "")))
          .forEach(entry -> nested.put(Objects.toString(entry.getKey(), ""), normalizeValue(entry.getValue())));
      return nested;
    }
    if (value instanceof List<?> list) return list.stream().map(FieldLibraryQueryPolicy::normalizeValue).toList();
    return value;
  }

  private static List<String> parentFieldReferences(Map<String, Object> conditions) {
    Set<String> refs = new LinkedHashSet<>();
    collectParentRefs(conditions, refs);
    return List.copyOf(refs);
  }

  @SuppressWarnings("unchecked")
  private static void collectParentRefs(Object value, Set<String> refs) {
    if (value instanceof Map<?, ?> map) {
      Object parent = first((Map<String, Object>) map, "parentFieldId", "parentField", "dependsOn");
      if (parent != null && !parent.toString().isBlank()) refs.add(parent.toString().trim());
      for (Object nested : map.values()) collectParentRefs(nested, refs);
    } else if (value instanceof List<?> list) {
      for (Object nested : list) collectParentRefs(nested, refs);
    }
  }

  private static String enumTypeId(FieldMetadataResponse field, Map<String, Object> conditions) {
    Object fromConditions = first(conditions, "enumTypeId", "enumType");
    if (fromConditions != null && !fromConditions.toString().isBlank()) return normalizeEnumType(fromConditions.toString());
    Object nested = findNested(conditions, Set.of("enumTypeId", "enumType"));
    if (nested != null && !nested.toString().isBlank()) return normalizeEnumType(nested.toString());
    if (!"enum".equals(field.valueType())) return null;
    return normalizeEnumType(field.id().replace("field@", ""));
  }

  @SuppressWarnings("unchecked")
  private static Object findNested(Object value, Set<String> keys) {
    if (value instanceof Map<?, ?> map) {
      for (String key : keys) if (((Map<String, Object>) map).containsKey(key)) return ((Map<String, Object>) map).get(key);
      for (Object nested : map.values()) {
        Object found = findNested(nested, keys);
        if (found != null) return found;
      }
    } else if (value instanceof List<?> list) {
      for (Object nested : list) {
        Object found = findNested(nested, keys);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static Object first(Map<String, Object> values, String... keys) {
    for (String key : keys) if (values.containsKey(key)) return values.get(key);
    return null;
  }

  private static String normalizeEnumType(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
  }

  private static String stableApiKey(FieldMetadataResponse field) {
    return field.id() == null ? "" : field.id().trim();
  }

  private static String sourceChip(FieldMetadataResponse field) {
    String status = field.disposition() == null || field.disposition().isBlank() ? "native" : field.disposition().trim().toLowerCase(Locale.ROOT);
    String category = categoryFor(field);
    return category + ":" + status;
  }

  private static String typeChip(String valueType) {
    String value = valueType == null || valueType.isBlank() ? "unknown" : valueType.trim().toLowerCase(Locale.ROOT);
    return "type:" + value;
  }

  private static String normalizeKey(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
  }
}
