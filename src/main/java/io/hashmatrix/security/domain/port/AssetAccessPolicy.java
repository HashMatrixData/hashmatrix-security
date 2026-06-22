package io.hashmatrix.security.domain.port;

import io.hashmatrix.security.domain.AccessDecision;
import io.hashmatrix.security.domain.AccessRequest;

/**
 * 数据资产访问策略（领域出站端口，六边形架构）——数据权限拦截链 <b>② SC-6 行/列裁决接入点</b>。
 *
 * <p>位置：在链 ① 行级兜底（{@code starter-tenant} 租户 schema 路由）<b>之上</b>、列管控/脱敏
 * （③④，后置）<b>之前</b>。对「主体×对象×动作」给出裁决。
 *
 * <p><b>M1</b>：默认出站适配 {@code infra.PassThroughAssetAccessPolicyAdapter} 恒放行；
 * SC-6 落地时<b>替换为真实裁决适配</b>（同一端口），链上下游与调用方均不改——见
 * {@code docs/data-permission/拦截链与边界.md}。
 */
public interface AssetAccessPolicy {

    /**
     * 在当前租户隔离边界内，对一次数据资产访问做裁决。
     *
     * @param request 主体×对象×动作
     * @return 裁决结果
     */
    AccessDecision decide(AccessRequest request);
}
