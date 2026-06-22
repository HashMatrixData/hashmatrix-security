package io.hashmatrix.security.infra;

import io.hashmatrix.security.domain.AccessDecision;
import io.hashmatrix.security.domain.AccessRequest;
import io.hashmatrix.security.domain.port.AssetAccessPolicy;
import org.springframework.stereotype.Component;

/**
 * {@link AssetAccessPolicy} 的 M1 默认出站适配（六边形 {@code infra} 层，与
 * {@link JdbcFlowableConnectivityAdapter} 同列）：<b>干净 pass-through</b>，恒放行。
 *
 * <p>数据权限拦截链 ② 的占位：链 ① 行级兜底（{@code starter-tenant} 租户 schema 路由）已保证租户隔离，
 * 本占位在其上<b>不附加任何行/列约束</b>、不改变放行语义。给出的是语义完整的「放行」（而非「未实现」的
 * 空值/开关），因此 SC-6 落地只需把本适配替换为真实裁决适配（同一端口）、调用方不改。
 */
@Component
public class PassThroughAssetAccessPolicyAdapter implements AssetAccessPolicy {

    @Override
    public AccessDecision decide(AccessRequest request) {
        // TODO SC-6: 接入主体×对象×动作×约束裁决引擎，产出行计划（行过滤）/ 列计划（列可见/脱敏）。
        // M1 占位：行级兜底已生效，此处默认放行，不附加行/列约束，亦不改变任何既有放行语义。
        return AccessDecision.allow("M1 pass-through：行级兜底已生效，SC-6 行/列裁决后置");
    }
}
