package io.hashmatrix.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 数据权限领域模型校验单测：守护各 record 紧凑构造的不变量（fail-closed 的底座）+ 裁决两态。
 * 这些校验是「拒绝非法输入而非静默放行」的第一道，须有测试兜底防重构悄改。
 */
class DataPermissionModelTest {

    @Test
    void asset_ref_rejects_blank_schema_or_table() {
        assertThatThrownBy(() -> new AssetRef(" ", "classification"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AssetRef("sec_*", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void asset_ref_tenant_table_uses_tenant_schema_template() {
        AssetRef ref = AssetRef.tenantTable("classification");

        assertThat(ref.schema()).isEqualTo(AssetRef.TENANT_SCHEMA_TEMPLATE);
        assertThat(ref.qualifiedName()).isEqualTo("sec_*.classification");
    }

    @Test
    void access_request_normalizes_null_roles_to_empty_and_validates() {
        AccessRequest request = new AccessRequest(null, AssetRef.tenantTable("label"), "read");
        assertThat(request.subjectRoles()).isEmpty();

        assertThatThrownBy(
                        () -> new AccessRequest(Set.of("security-viewer"), null, "read"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new AccessRequest(Set.of("security-viewer"), AssetRef.tenantTable("label"), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void access_request_roles_are_immutable() {
        AccessRequest request =
                new AccessRequest(Set.of("security-viewer"), AssetRef.tenantTable("label"), "read");

        assertThatThrownBy(() -> request.subjectRoles().add("intruder"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void access_decision_allow_and_deny_carry_state_and_reason() {
        AccessDecision allow = AccessDecision.allow("ok");
        AccessDecision deny = AccessDecision.deny("forbidden");

        assertThat(allow.allowed()).isTrue();
        assertThat(deny.allowed()).isFalse();
        assertThat(deny.reason()).isEqualTo("forbidden");
        assertThatThrownBy(() -> AccessDecision.allow(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
