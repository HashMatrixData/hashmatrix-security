package io.hashmatrix.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hashmatrix.test.fixtures.MockTenants;
import org.junit.jupiter.api.Test;

class TenantSchemaTest {

    @Test
    void derivesPrefixedSchemaFromTenant() {
        assertThat(TenantSchema.forTenant(MockTenants.ACME).name()).isEqualTo("sec_acme");
        assertThat(TenantSchema.forTenant(MockTenants.TENANT_DEMO).name()).isEqualTo("sec_tenant_demo");
    }

    @Test
    void sanitizesUnsafeCharactersToUnderscore() {
        // 大小写归一 + 非 [a-z0-9_] 折叠，确保可安全用于 SET search_path（杜绝注入）
        assertThat(TenantSchema.forTenant("Acme Corp").name()).isEqualTo("sec_acme_corp");
        assertThat(TenantSchema.forTenant("t1; DROP").name()).isEqualTo("sec_t1__drop");
    }

    @Test
    void rejectsBlankTenant() {
        assertThatThrownBy(() -> TenantSchema.forTenant("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
