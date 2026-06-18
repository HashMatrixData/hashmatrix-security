package io.hashmatrix.security.api;

import io.hashmatrix.security.app.ConnectivityService;
import io.hashmatrix.security.domain.InfraStatus;
import io.hashmatrix.starter.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基础设施自检 API：演示分层骨架与公共能力复用——
 * starter-security 校验网关下发身份并做<strong>方法级授权</strong> → starter-tenant 透传租户
 * → starter-web 统一返回 → app/infra 探测 PG/Flowable → starter-audit 记审计。
 *
 * <p>方法级授权范式：默认链要求认证（{@code anyRequest().authenticated()}），叠加
 * {@code @PreAuthorize} 做基于角色（网关 {@code X-Roles} → {@code ROLE_*}）的细粒度授权。
 * 探针放行在 actuator，业务 API 一律需网关下发身份 + 相应角色。分类分级 / 标签 / 审批等业务 API
 * 在本基座之上沿此范式落地。
 */
@RestController
@RequestMapping("/api/security")
public class ProbeController {

    private final ConnectivityService connectivityService;

    public ProbeController(ConnectivityService connectivityService) {
        this.connectivityService = connectivityService;
    }

    /**
     * 在当前租户隔离边界内自检 PG/Flowable 连通。
     *
     * <p>示范方法级授权：需 {@code security-viewer} 角色（映射 Keycloak 角色，网关经 {@code X-Roles}
     * 下发）。无角色的已认证主体将被拒（403）——这是本仓「授权与管控」的最小可验证范式。
     */
    @GetMapping("/probe")
    @PreAuthorize("hasRole('security-viewer')")
    public ApiResponse<InfraStatus> probe() {
        return ApiResponse.ok(connectivityService.probe());
    }
}
