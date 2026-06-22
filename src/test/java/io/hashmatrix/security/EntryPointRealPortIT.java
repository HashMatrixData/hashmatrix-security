package io.hashmatrix.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.hashmatrix.security.app.RoleVisibilityCatalog;
import io.hashmatrix.security.domain.AccessRequest;
import io.hashmatrix.security.domain.AssetRef;
import io.hashmatrix.security.domain.port.AssetAccessPolicy;
import io.hashmatrix.test.fixtures.MockTenants;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 入口级集成回归（末端 · 真实端口 E2E）：汇聚 #4（端口/readiness）+ #6（数据权限骨架）的运行期保证。
 *
 * <p>与既有 {@link InfraConnectivityIT}（走 MockMvc，{@code management.server.port} 被对齐到业务端口、
 * 分离不生效）互补——本测试用 {@code RANDOM_PORT} + 独立管理端口（{@code management.server.port=0}）让
 * <b>业务口与管理口各自真实绑定到不同物理端口</b>，再用 {@link TestRestTemplate} 真实命中两个端口，守护
 * 无任何 MockMvc 测试能覆盖的跨边界不变量：
 *
 * <ul>
 *   <li>真实双端口分离：业务口出业务 API、管理口出 actuator，且依赖就绪时 readiness 经独立管理口
 *       <b>免认证可达且 UP</b>（readiness 组 curated=readinessState，结构上 deps-optional——「依赖宕机仍绿」
 *       的负向证据成本高，留待后续，不在本 IT 断言）；
 *   <li>功能 RBAC：{@code /api/security/probe} 匿名 → 401、缺 {@code security-viewer} 角色 → 403、具角色 → 200；
 *   <li>多租户行级隔离：具角色请求按 {@code X-Tenant-Id} 路由到 {@code sec_<tenant>}；
 *   <li>与 #6 数据权限骨架共存不破：SC-6 占位端口 + 角色可见库表映射在同一真实启动上下文内正常装配。
 * </ul>
 *
 * <p>端口数字说明：平台基线默认 8083/9083（见 {@code application.yml}），本测试用随机口绑定以避免 CI
 * 并行端口占用——E2E 验证的是「双端口<b>真实分离绑定</b>」这一不变量，而非字面端口号。
 *
 * <p>走 failsafe（{@code *IT}），{@code mvn verify} 触发、需 Docker（Testcontainers 起真实 PostgreSQL；
 * Flowable 复用同一 PG 自建 ACT_* 表）。网关下发的身份/角色/租户头以 {@code X-User}/{@code X-Roles}/
 * {@code X-Tenant-Id} 模拟（脱敏，红线合规）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@Testcontainers
class EntryPointRealPortIT {

    /** 网关已完成 OIDC 校验后下发的身份头占位（脱敏）。 */
    private static final String GATEWAY_USER = "svc-probe";
    private static final String VIEWER_ROLE = "security-viewer";

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

    @LocalServerPort
    private int appPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AssetAccessPolicy assetAccessPolicy;

    @Autowired
    private RoleVisibilityCatalog roleVisibilityCatalog;

    @Test
    void app_and_management_bind_distinct_real_ports() {
        assertThat(appPort).isPositive();
        assertThat(managementPort).isPositive();
        assertThat(managementPort)
                .as("管理端口须独立于业务端口真实绑定（非 MockMvc 折叠）")
                .isNotEqualTo(appPort);
    }

    @Test
    void readiness_is_up_and_deps_optional_on_management_port() {
        // readiness 组 curated 为 readinessState：不硬门外部依赖（PG/Flowable），deps-optional 绿。
        // 关键：管理端口真实独立绑定下，探针仍免认证可达（permit-paths /actuator/health/**）——这是
        // MockMvc 测试覆盖不到、容器探针赖以摘流量的真实路径。
        ResponseEntity<String> resp =
                rest.getForEntity(url(managementPort, "/actuator/health/readiness"), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void actuator_not_served_on_app_port() {
        // 端口分离的反向证据：actuator 仅在管理口可达，业务口不暴露 /actuator/**。
        // 注意：业务口 permit-paths 同样放行 /actuator/health/**，故仅断「非 2xx」不足以区分「未分离但被拦」
        // 与「真分离」——此处叠加断「响应体不含 UP 健康体」：若 actuator 误留业务口，readiness 会 200+UP，
        // 两条断言同时失败；端口已分离时则为路由未命中（无 UP 体）。
        ResponseEntity<String> resp =
                rest.getForEntity(url(appPort, "/actuator/health/readiness"), String.class);

        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("actuator readiness 不应在业务口被成功服务（仅管理口可达）")
                .isFalse();
        assertThat(resp.getBody() == null || !resp.getBody().contains("\"status\":\"UP\""))
                .as("业务口不应返回 actuator 健康体（UP）——否则即 actuator 泄漏到业务口")
                .isTrue();
    }

    @Test
    void probe_requires_gateway_identity() {
        // 认证：业务 API 无网关下发身份（匿名）→ 401（默认链 anyRequest().authenticated()，starter-security 钉死）。
        ResponseEntity<String> resp =
                rest.getForEntity(url(appPort, "/api/security/probe"), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void probe_forbidden_without_required_role() {
        // 授权：已认证但缺 security-viewer 角色 → 403（方法级 @PreAuthorize 真生效）。
        ResponseEntity<String> resp =
                rest.exchange(
                        url(appPort, "/api/security/probe"),
                        HttpMethod.GET,
                        new HttpEntity<>(gatewayHeaders(GATEWAY_USER, "some-other-role", MockTenants.ACME)),
                        String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void probe_succeeds_for_viewer_routed_to_tenant_schema() {
        // RBAC 放行 + 行级兜底：具 security-viewer 角色、带 X-Tenant-Id=acme → 200，路由到 sec_acme，PG/Flowable 可达。
        ResponseEntity<String> resp =
                rest.exchange(
                        url(appPort, "/api/security/probe"),
                        HttpMethod.GET,
                        new HttpEntity<>(gatewayHeaders(GATEWAY_USER, VIEWER_ROLE, MockTenants.ACME)),
                        String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody())
                .contains("\"code\":\"0\"")
                .contains("\"tenantSchema\":\"sec_acme\"")
                .contains("\"postgres\":true")
                .contains("\"workflow\":true");
    }

    @Test
    void coexists_with_data_permission_skeleton() {
        // 与 #6 共存：SC-6 占位端口 + 角色可见库表映射在同一真实启动上下文内正常装配且行为正确，
        // 不破坏既有 probe/RBAC/端口分离（本类其余用例在同一上下文通过即为佐证）。
        assertThat(assetAccessPolicy.decide(
                        new AccessRequest(Set.of(VIEWER_ROLE), AssetRef.tenantTable("classification"), "read"))
                .allowed())
                .isTrue();
        assertThat(roleVisibilityCatalog.visibleAssets(Set.of(VIEWER_ROLE)))
                .extracting(AssetRef::qualifiedName)
                .containsExactlyInAnyOrder("sec_*.classification", "sec_*.label");
    }

    /** 模拟网关下发的预认证头（starter-security 信任 X-User/X-Roles；starter-tenant 信任 X-Tenant-Id）。 */
    private static HttpHeaders gatewayHeaders(String user, String roles, String tenant) {
        HttpHeaders headers = new HttpHeaders();
        if (user != null) {
            headers.add("X-User", user);
        }
        if (roles != null) {
            headers.add("X-Roles", roles);
        }
        if (tenant != null) {
            headers.add("X-Tenant-Id", tenant);
        }
        return headers;
    }

    private static String url(int port, String path) {
        return "http://localhost:" + port + path;
    }
}
