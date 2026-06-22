package io.hashmatrix.security.domain;

import java.util.Set;

/**
 * 数据访问裁决输入：主体（角色集）× 对象（{@link AssetRef}）× 动作。
 *
 * <p>供数据权限拦截链 ② {@link io.hashmatrix.security.domain.port.AssetAccessPolicy SC-6 接入点}
 * 消费。租户维度不在此显式出现——由链 ① 行级兜底（{@code starter-tenant} 当前上下文）隐式界定，
 * 裁决永远发生在「当前租户隔离边界内」。
 *
 * @param subjectRoles 主体角色集（网关 {@code X-Roles} 透传，去 {@code ROLE_} 前缀的原始角色名）
 * @param asset        被访问的数据资产
 * @param action       动作（如 {@code read}）
 */
public record AccessRequest(Set<String> subjectRoles, AssetRef asset, String action) {

    public AccessRequest {
        // 角色集归一化为不可变副本；null 视作空集（无角色 = 不可见，由策略 fail-closed 判定）
        subjectRoles = subjectRoles == null ? Set.of() : Set.copyOf(subjectRoles);
        if (asset == null) {
            throw new IllegalArgumentException("asset must not be null");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
    }
}
