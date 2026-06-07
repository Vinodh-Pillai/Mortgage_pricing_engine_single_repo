package com.wcpe.observability.database;

import java.util.List;
import java.util.Objects;

public record DatabaseIndexDefinition(
    String tableName,
    String indexName,
    List<String> orderedColumns,
    boolean unique,
    String queryClass,
    String ownerStory,
    String rollbackNote) {
  public DatabaseIndexDefinition {
    tableName = requireToken(tableName, "tableName");
    indexName = requireToken(indexName, "indexName");
    orderedColumns = List.copyOf(Objects.requireNonNull(orderedColumns, "orderedColumns is required"));
    if (orderedColumns.isEmpty()) {
      throw new IllegalArgumentException("orderedColumns is required");
    }
    queryClass = requireText(queryClass, "queryClass");
    ownerStory = requireText(ownerStory, "ownerStory");
    rollbackNote = requireText(rollbackNote, "rollbackNote");
  }

  private static String requireToken(String value, String field) {
    String normalized = requireText(value, field);
    if (!normalized.matches("[a-z0-9_]+")) {
      throw new IllegalArgumentException(field + " must be a lowercase database token");
    }
    return normalized;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value.trim();
  }
}
