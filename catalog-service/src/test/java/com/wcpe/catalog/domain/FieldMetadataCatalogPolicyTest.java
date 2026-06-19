package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class FieldMetadataCatalogPolicyTest {
  @Test
  void preservesReferenceFormfieldMetadataAcrossSourceGroups() {
    FieldMetadataImportRequest request = new FieldMetadataImportRequest("ReferenceFormfields.json",
        List.of(new FieldMetadataInput("loan.amount", "1001", "Loan Amount", "Requested loan amount", "product", "number", null, Map.of("requiredWhen", "pricing"), "native")),
        List.of(new FieldMetadataInput("borrower.state", "2001", "Borrower State", "Borrower property state", "creditApplication", "US_STATE", "creditApplicationFields", Map.of(), "inherited")),
        List.of(new FieldMetadataInput("pipeline.header", null, "Pipeline Header", null, "pipelineOnly", "header", null, Map.of("section", "pipeline"), "native")),
        List.of(new FieldMetadataInput("county.code", "3001", "County", "County selection", "system", "US county", "rawFieldBlocks", Map.of("dependsOn", "borrower.state"), "inherited")));

    List<FieldMetadataResponse> normalized = FieldMetadataCatalogPolicy.normalize(request);

    assertThat(normalized).extracting(FieldMetadataResponse::id).containsExactly("loan.amount", "borrower.state", "pipeline.header", "county.code");
    assertThat(normalized).extracting(FieldMetadataResponse::oldId).containsExactly("1001", "2001", null, "3001");
    assertThat(normalized).extracting(FieldMetadataResponse::valueType).containsExactly("number", "us-state", "header", "us-county");
    assertThat(normalized).extracting(FieldMetadataResponse::sourceGroup).containsExactly("productFields", "creditApplicationFields", "pipelineOnlyFields", "rawFieldBlocks");
    assertThat(normalized).extracting(FieldMetadataResponse::disposition).containsExactly("native", "inherited", "native", "inherited");
    assertThat(normalized.get(0).conditions()).containsEntry("requiredWhen", "pricing");
  }

  @Test
  void acceptsEverySupportedReferenceFormfieldValueType() {
    FieldMetadataImportRequest request = new FieldMetadataImportRequest("ReferenceFormfields.json",
        List.of(
            supportedValueTypeInput("fixture.header", "Header", "header"),
            supportedValueTypeInput("fixture.enum", "Enum", "enum"),
            supportedValueTypeInput("fixture.number", "Number", "number"),
            supportedValueTypeInput("fixture.string", "String", "string"),
            supportedValueTypeInput("fixture.date", "Date", "date"),
            supportedValueTypeInput("fixture.time", "Time", "time"),
            supportedValueTypeInput("fixture.duration", "Duration", "duration"),
            supportedValueTypeInput("fixture.us-state", "State", "US_STATE"),
            supportedValueTypeInput("fixture.us-county", "County", "US county")),
        List.of(), List.of(), List.of());

    List<FieldMetadataResponse> normalized = FieldMetadataCatalogPolicy.normalize(request);

    assertThat(normalized).extracting(FieldMetadataResponse::valueType)
        .containsExactly("header", "enum", "number", "string", "date", "time", "duration", "us-state", "us-county");
    assertThat(normalized).extracting(FieldMetadataResponse::source)
        .containsOnly("ReferenceFormfields.json");
  }

  @Test
  void importsRepresentativeReferenceFormfieldsFixtureSubset() {
    FieldMetadataImportRequest request = new FieldMetadataImportRequest("ReferenceFormfields.json",
        List.of(
            new FieldMetadataInput("field@product-channel", "field@product-channel", "Product Channel",
                "The channel of business through which a loan is originated.", "product", "enum", null,
                Map.of(), "inherited"),
            new FieldMetadataInput("field@preset-loan-term", "field@preset-loan-term", "Preset Loan Term",
                "The scheduled number of periods after which the loan will mature.", "product", "duration", null,
                Map.of("parentFieldId", "field@loan-term-type", "conditionId", "297c9b51-8da7-419a-8201-76f9e8a0ff82"), "inherited")),
        List.of(), List.of(), List.of());

    List<FieldMetadataResponse> normalized = FieldMetadataCatalogPolicy.normalize(request);

    assertThat(normalized).hasSize(2);
    assertThat(normalized.get(0)).isEqualTo(new FieldMetadataResponse("field@product-channel", "field@product-channel",
        "Product Channel", "The channel of business through which a loan is originated.", "product", "enum",
        "productFields", Map.of(), "inherited", "ReferenceFormfields.json"));
    assertThat(normalized.get(1).conditions())
        .containsEntry("parentFieldId", "field@loan-term-type")
        .containsEntry("conditionId", "297c9b51-8da7-419a-8201-76f9e8a0ff82");
  }

  @Test
  void exposesRepresentativeFieldMetadataResponsePayloadContract() {
    FieldMetadataResponse response = FieldMetadataCatalogPolicy.normalize(new FieldMetadataImportRequest("ReferenceFormfields.json",
        List.of(new FieldMetadataInput("field@community-lending-product-type", "field@community-lending-product-type",
            "Community Lending Product Type",
            "A value from a MISMO prescribed list that classifies the community lending product associated with the loan.",
            "product", "enum", null,
            Map.of("parentFieldId", "field@mortgage-type", "variantId", "conventional"), "inherited")),
        List.of(), List.of(), List.of())).get(0);

    Map<String, Object> payloadContract = new LinkedHashMap<>();
    payloadContract.put("id", response.id());
    payloadContract.put("oldId", response.oldId());
    payloadContract.put("name", response.name());
    payloadContract.put("description", response.description());
    payloadContract.put("category", response.category());
    payloadContract.put("valueType", response.valueType());
    payloadContract.put("sourceGroup", response.sourceGroup());
    payloadContract.put("conditions", response.conditions());
    payloadContract.put("disposition", response.disposition());
    payloadContract.put("source", response.source());

    assertThat(payloadContract).containsExactly(
        entry("id", "field@community-lending-product-type"),
        entry("oldId", "field@community-lending-product-type"),
        entry("name", "Community Lending Product Type"),
        entry("description", "A value from a MISMO prescribed list that classifies the community lending product associated with the loan."),
        entry("category", "product"),
        entry("valueType", "enum"),
        entry("sourceGroup", "productFields"),
        entry("conditions", Map.of("parentFieldId", "field@mortgage-type", "variantId", "conventional")),
        entry("disposition", "inherited"),
        entry("source", "ReferenceFormfields.json"));
  }

  @Test
  void rejectsDuplicateFieldIdsWithFieldLevelErrorCode() {
    FieldMetadataImportRequest request = new FieldMetadataImportRequest("ReferenceFormfields.json",
        List.of(new FieldMetadataInput("loan.amount", null, "Loan Amount", null, "product", "number", null, Map.of(), "native")),
        List.of(new FieldMetadataInput("loan.amount", null, "Loan Amount Duplicate", null, "creditApplication", "number", null, Map.of(), "inherited")),
        List.of(), List.of());

    assertThatThrownBy(() -> FieldMetadataCatalogPolicy.normalize(request))
        .isInstanceOf(CatalogException.class)
        .hasMessage("FIELD_ID_DUPLICATE");
    assertThat(CatalogController.catalogErrorStatus("FIELD_ID_DUPLICATE"))
        .isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void rejectsUnsupportedValueTypesInsteadOfInventingMetadata() {
    FieldMetadataImportRequest request = new FieldMetadataImportRequest("ReferenceFormfields.json",
        List.of(new FieldMetadataInput("loan.rate", null, "Loan Rate", null, "product", "pricing-rate", null, Map.of(), "native")),
        List.of(), List.of(), List.of());

    assertThatThrownBy(() -> FieldMetadataCatalogPolicy.normalize(request))
        .isInstanceOf(CatalogException.class)
        .hasMessage("FIELD_VALUE_TYPE_UNSUPPORTED");
  }

  private static FieldMetadataInput supportedValueTypeInput(String id, String name, String valueType) {
    return new FieldMetadataInput(id, id, name, name + " metadata", "fixture", valueType, null, Map.of(), "native");
  }
}
