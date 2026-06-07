package com.wcpe.tenantcontext.observability;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.TenantContextValidationException;
import org.junit.jupiter.api.Test;

class CorrelationIdGeneratorTest {
    @Test
    void generatesSafeCorrelationIdWhenMissing() {
        String generated = CorrelationIdGenerator.resolve(" ");

        assertThat(generated).startsWith("corr-");
        assertThat(generated).matches("^[A-Za-z0-9][A-Za-z0-9._:-]{1,127}$");
    }

    @Test
    void preservesProvidedCorrelationIdAndRejectsMalformedInput() {
        assertThat(CorrelationIdGenerator.resolve(" corr-tenant-123 ")).isEqualTo("corr-tenant-123");
        assertThatThrownBy(() -> CorrelationIdGenerator.resolve("bad id with spaces"))
            .isInstanceOf(TenantContextValidationException.class)
            .extracting(error -> ((TenantContextValidationException) error).code())
            .isEqualTo("TENANT_CONTEXT_MALFORMED");
    }
}
