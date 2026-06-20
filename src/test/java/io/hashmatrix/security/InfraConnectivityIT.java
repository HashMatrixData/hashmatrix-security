package io.hashmatrix.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.hashmatrix.test.fixtures.MockTenants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成切片：用 Testcontainers 起真实 PostgreSQL，验证工程基座的基础设施接线、多租户 schema 路由、
 * Flowable 审批引擎连通、授权链与 actuator 健康——即 security#1 验收「mvn verify 起 PG → 自检绿」。
 *
 * <p>走 failsafe（{@code *IT}），{@code mvn package} 不触发；{@code mvn verify} 需本地/CI 有 Docker。
 * Flowable 复用同一 PG（启动期自建 ACT_* 表）；网关下发的身份/角色头以 {@code X-User}/{@code X-Roles} 模拟。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
// 默认配置将管理端口独立为 9083（运行期 K8s 探针/Prometheus 走独立端口）。但 MOCK web env 下，
// 独立管理端口会把 actuator 端点搬到一个 MockMvc 触达不到的管理子上下文（→ /actuator/** 404/401）。
// 此处把管理端口对齐业务端口，使 actuator 回到主 mock 上下文，可经 MockMvc 验证；真实双端口绑定
// （8083/9083 各自监听）留待末端 #4 的真实端口 E2E 覆盖。
@TestPropertySource(properties = "management.server.port=${server.port}")
class InfraConnectivityIT {

    /** 网关已完成 OIDC 校验后下发的身份头占位（脱敏，红线合规）。 */
    private static final String GATEWAY_USER = "svc-probe";
    private static final String GATEWAY_ROLES = "security-viewer";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("security")
                    .withUsername("security")
                    .withPassword("security");

    @DynamicPropertySource
    static void infraProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void probeReportsBothInfraReachableUnderTenantSchema() throws Exception {
        mvc.perform(
                        get("/api/security/probe")
                                .header("X-User", GATEWAY_USER)
                                .header("X-Roles", GATEWAY_ROLES)
                                .header("X-Tenant-Id", MockTenants.ACME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.tenantSchema").value("sec_acme"))
                .andExpect(jsonPath("$.data.postgres").value(true))
                .andExpect(jsonPath("$.data.workflow").value(true));
    }

    @Test
    void routesEachTenantToItsOwnSchemaAcrossPooledConnections() throws Exception {
        // 连续探测两个租户：验证 SET LOCAL 按租户正确路由、且池连接复用不串读（多租户隔离底线）
        mvc.perform(
                        get("/api/security/probe")
                                .header("X-User", GATEWAY_USER)
                                .header("X-Roles", GATEWAY_ROLES)
                                .header("X-Tenant-Id", MockTenants.ACME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantSchema").value("sec_acme"))
                .andExpect(jsonPath("$.data.postgres").value(true));
        mvc.perform(
                        get("/api/security/probe")
                                .header("X-User", GATEWAY_USER)
                                .header("X-Roles", GATEWAY_ROLES)
                                .header("X-Tenant-Id", MockTenants.TENANT_DEMO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantSchema").value("sec_tenant_demo"))
                .andExpect(jsonPath("$.data.postgres").value(true));
    }

    @Test
    void rejectsBusinessApiWithoutGatewayIdentity() throws Exception {
        // 认证：业务 API 无网关下发身份（匿名）→ 拒绝（默认链 anyRequest().authenticated()）
        mvc.perform(get("/api/security/probe").header("X-Tenant-Id", MockTenants.ACME))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void rejectsAuthenticatedUserLackingRequiredRole() throws Exception {
        // 授权：已认证但角色不足（无 security-viewer）→ 403（守护方法级授权 @PreAuthorize 真生效）
        mvc.perform(
                        get("/api/security/probe")
                                .header("X-User", GATEWAY_USER)
                                .header("X-Roles", "some-other-role")
                                .header("X-Tenant-Id", MockTenants.ACME))
                .andExpect(status().isForbidden());
    }

    @Test
    void actuatorHealthIsUpAndPermittedWithoutAuth() throws Exception {
        // 探针放行（permitPaths），无需网关身份即可被 K8s/Prometheus 采集
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessProbeIsUpAndDepsOptional() throws Exception {
        // readiness 组 curated 为 readinessState：外部依赖（PG/Flowable）不硬门 readiness（deps-optional 绿），
        // 单 namespace 内依赖未就绪不应使 Pod 被摘流量。真实「依赖宕机仍绿」的双端口验证留待末端 #4 的 E2E。
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
