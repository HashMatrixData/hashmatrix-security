package io.hashmatrix.security.app;

import io.hashmatrix.security.domain.AssetRef;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 「角色 → 可见库表」映射（库表级角色门 · M1 脱敏示例占位）。
 *
 * <p>回答数据权限拦截链中「某角色在当前租户库（{@code sec_<tenant>}）内能看到哪些表」——是「库表级」
 * 粒度的可见性闸门，<b>不等于</b>功能权限（功能权限走 {@code api} 层 {@code @PreAuthorize}）。多角色取
 * <b>并集</b>；未知/空角色 → <b>空集（默认不可见，fail-closed）</b>。
 *
 * <p>M1 为脱敏静态示例（{@code security-viewer → sec_*.classification / sec_*.label}）；SC-6 落地后由
 * 持久化的「角色-资产绑定」替代，本类随之退役。详见 {@code docs/data-permission/拦截链与边界.md}。
 *
 * <p>层位说明：本类不实现任何出站端口，是 M1 期的应用级示例策略 Bean（故置于 {@code app}，非
 * {@code infra}）；SC-6 落地为「角色-资产绑定」持久化后，读取端口移入 {@code domain/port} + {@code infra}
 * 适配，本类退役——届时一并归位，无需现在为占位数据预建端口。
 */
@Component
public class RoleVisibilityCatalog {

    /** 脱敏示例映射（不可变）：角色 → 该角色在当前租户库内可见的库表。 */
    private static final Map<String, Set<AssetRef>> DEFAULTS =
            Map.of(
                    "security-viewer",
                    Set.of(AssetRef.tenantTable("classification"), AssetRef.tenantTable("label")));

    private final Map<String, Set<AssetRef>> byRole;

    /** 以脱敏默认映射装配（Spring 注入用）。 */
    public RoleVisibilityCatalog() {
        this(DEFAULTS);
    }

    /** 以自定义映射装配（包内可见，供同包测试注入确定性数据；Spring 装配走上面的无参构造）。 */
    RoleVisibilityCatalog(Map<String, Set<AssetRef>> byRole) {
        this.byRole = Map.copyOf(byRole);
    }

    /**
     * 给定主体角色集，返回其在当前租户库内可见库表的并集。
     *
     * @param roles 主体角色集（去 {@code ROLE_} 前缀的原始角色名）；{@code null}/空 → 空集
     * @return 可见库表的不可变去重并集（迭代顺序不作保证）；未匹配任何角色 → 空集（默认不可见）
     */
    public Set<AssetRef> visibleAssets(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        Set<AssetRef> union = new LinkedHashSet<>();
        for (String role : roles) {
            union.addAll(byRole.getOrDefault(role, Set.of()));
        }
        return Set.copyOf(union);
    }
}
