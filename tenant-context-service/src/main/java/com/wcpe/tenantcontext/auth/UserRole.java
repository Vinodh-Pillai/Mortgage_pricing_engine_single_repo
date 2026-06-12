package com.wcpe.tenantcontext.auth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

public enum UserRole {
  LOAN_OFFICER("loan_officer"),
  PRICING_ANALYST("pricing_analyst"),
  OPERATIONS_LEAD("operations_lead"),
  GOVERNANCE_REVIEWER("governance_reviewer"),
  ADMIN("admin"),
  PARTNER_MANAGER("partner_manager"),
  COMPLIANCE_OFFICER("compliance_officer"),
  BORROWER("borrower");

  private final String databaseValue;

  UserRole(String databaseValue) {
    this.databaseValue = databaseValue;
  }

  public String databaseValue() {
    return databaseValue;
  }

  public static UserRole fromDatabaseValue(String value) {
    String normalized = value == null ? "" : value.trim().toLowerCase().replace('-', '_');
    return Arrays.stream(values())
      .filter(role -> role.databaseValue.equals(normalized))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unsupported user role"));
  }
}

@Converter(autoApply = true)
class UserRoleConverter implements AttributeConverter<UserRole, String> {
  @Override
  public String convertToDatabaseColumn(UserRole role) {
    return role == null ? null : role.databaseValue();
  }

  @Override
  public UserRole convertToEntityAttribute(String value) {
    return value == null ? null : UserRole.fromDatabaseValue(value);
  }
}
