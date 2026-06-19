package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class FieldConsumerMappingPolicyTest {
  @Test
  void resolvesConsumerMappingToStableFieldLibraryMetadata() {
    FieldConsumerMappingResponse response = FieldConsumerMappingPolicy.normalize("Pipeline", new FieldConsumerMappingRequest(null,
        "tenant-active", null, List.of(
            new FieldConsumerMappingItem("field@pipeline-status", "pipeline.status", "pipeline_status", "display", Map.of("section", "summary")),
            new FieldConsumerMappingItem("dti", "calculation.dti", "calc_dti", "calculation-input", Map.of())), Map.of("scopeOwner", "tenant")),
        fields(), "catalog-version-7");

    assertThat(response.consumer()).isEqualTo("pipeline");
    assertThat(response.mappingScope()).isEqualTo("tenant-active");
    assertThat(response.activeVersion()).isEqualTo("catalog-version-7");
    assertThat(response.sourceScope()).isEqualTo("tenant:tenant-active");
    assertThat(response.fields()).extracting(FieldConsumerMappedFieldResponse::fieldId).containsExactly("field@pipeline-status", "field@dti");
    assertThat(response.fields()).extracting(FieldConsumerMappedFieldResponse::stableApiKey).containsExactly("field@pipeline-status", "field@dti");
    assertThat(response.fields().get(0).name()).isEqualTo("Pipeline Status");
    assertThat(response.fields().get(1).dataTableKey()).isEqualTo("calc_dti");
  }

  @Test
  void listsFieldReferencesAcrossMultipleConsumersBeforeEditing() {
    FieldConsumerMappingResponse pipeline = FieldConsumerMappingPolicy.normalize("pipeline", new FieldConsumerMappingRequest(null,
        "tenant-active", null, List.of(new FieldConsumerMappingItem("field@dti", "pipeline.dti", "pipeline_dti", "display", Map.of())), Map.of()),
        fields(), "catalog-version-7");
    FieldConsumerMappingResponse pricing = FieldConsumerMappingPolicy.normalize("pricing", new FieldConsumerMappingRequest(null,
        "tenant-active", null, List.of(new FieldConsumerMappingItem("field@dti", "pricing.dti", "calc_dti", "calculation-input", Map.of())), Map.of()),
        fields(), "catalog-version-8");

    FieldConsumerReferenceResponse references = FieldConsumerMappingPolicy.references("dti", List.of(pricing, pipeline));

    assertThat(references.fieldId()).isEqualTo("field@dti");
    assertThat(references.referenceCount()).isEqualTo(2);
    assertThat(references.references()).extracting(FieldConsumerReference::consumer).containsExactly("pipeline", "pricing");
    assertThat(references.references()).extracting(FieldConsumerReference::activeVersion).containsExactly("catalog-version-7", "catalog-version-8");
  }

  @Test
  void ignoresCallerSuppliedActiveVersion() {
    FieldConsumerMappingResponse response = FieldConsumerMappingPolicy.normalize("pipeline", new FieldConsumerMappingRequest(null,
        "tenant-active", "catalog-version-stale", List.of(new FieldConsumerMappingItem("field@dti", "pipeline.dti", "pipeline_dti", "display", Map.of())), Map.of()),
        fields(), "catalog-version-9");

    assertThat(response.activeVersion()).isEqualTo("catalog-version-9");
  }

  @Test
  void rejectsMappingsThatDoNotResolveToFieldLibraryStableIds() {
    assertThatThrownBy(() -> FieldConsumerMappingPolicy.normalize("pipeline", new FieldConsumerMappingRequest(null,
        "tenant-active", null, List.of(new FieldConsumerMappingItem("field@missing", "pipeline.missing", null, null, Map.of())), Map.of()),
        fields(), "catalog-version-7"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("FIELD_CONSUMER_FIELD_NOT_FOUND");
  }

  private static List<FieldMetadataResponse> fields() {
    return List.of(
        new FieldMetadataResponse("field@pipeline-status", null, "Pipeline Status", "Pipeline status", "pipeline", "enum", "pipelineOnlyFields", Map.of(), "native", "ReferenceFormfields.json"),
        new FieldMetadataResponse("field@dti", "legacy-dti", "Debt-to-income Ratio", "Calculated ratio", "calculation", "number", "rawFields", Map.of(), "native", "ReferenceFormfields.json"));
  }
}
