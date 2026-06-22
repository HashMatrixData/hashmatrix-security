package io.hashmatrix.security.infra;

import static org.assertj.core.api.Assertions.assertThat;

import io.hashmatrix.security.domain.AccessDecision;
import io.hashmatrix.security.domain.AccessRequest;
import io.hashmatrix.security.domain.AssetRef;
import io.hashmatrix.security.domain.port.AssetAccessPolicy;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * SC-6 占位接入点单测：守护「干净 pass-through」不变量——无论主体/对象/动作如何，恒放行，
 * 不掺杂任何行/列约束语义。这是「占位不得悄改放行语义」的正向证据（行级兜底之上默认放行）。
 */
class PassThroughAssetAccessPolicyAdapterTest {

    private final AssetAccessPolicy policy = new PassThroughAssetAccessPolicyAdapter();

    @Test
    void allows_known_role_reading_visible_table() {
        AccessDecision decision =
                policy.decide(
                        new AccessRequest(
                                Set.of("security-viewer"), AssetRef.tenantTable("classification"), "read"));

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void allows_regardless_of_subject_object_action() {
        // pass-through：即便无角色、访问任意表、任意动作，占位也一律放行（语义不随输入变化）。
        AccessDecision noRole =
                policy.decide(new AccessRequest(Set.of(), AssetRef.tenantTable("payroll"), "export"));
        AccessDecision otherRole =
                policy.decide(
                        new AccessRequest(Set.of("some-other-role"), AssetRef.tenantTable("label"), "read"));

        assertThat(noRole.allowed()).isTrue();
        assertThat(otherRole.allowed()).isTrue();
    }

    @Test
    void decision_carries_reason_for_audit() {
        AccessDecision decision =
                policy.decide(new AccessRequest(Set.of("security-viewer"), AssetRef.tenantTable("label"), "read"));

        assertThat(decision.reason()).isNotBlank();
    }
}
