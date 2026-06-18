package io.hashmatrix.security.infra;

import io.hashmatrix.security.domain.InfraStatus;
import io.hashmatrix.security.domain.TenantSchema;
import io.hashmatrix.security.domain.port.InfraConnectivityPort;
import io.hashmatrix.starter.tenant.TenantContextHolder;
import org.flowable.engine.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link InfraConnectivityPort} 的 PostgreSQL + Flowable 审批引擎适配实现。
 *
 * <p>多租户路由：按当前 {@link TenantContextHolder} 派生 {@link TenantSchema}，在<strong>单个事务内</strong>
 * 用 {@code SET LOCAL search_path} 切到租户 schema 后再查询——
 * <ul>
 *   <li>{@code LOCAL} 使 search_path 仅在本事务生效，提交/回滚即自动复位，
 *       池化连接归还复用时<strong>不会继承上一租户的 search_path</strong>（杜绝跨租户串读）；</li>
 *   <li>同一事务保证 {@code SET} 与后续查询落在同一物理连接上。</li>
 * </ul>
 * 这是安全业务（分级/标签/审批落库）应复用的租户路由范式。schema 名已在 {@link TenantSchema} 归一化（无注入）。
 *
 * <p>Flowable 探针经 {@link RepositoryService} 计数流程定义，验证审批引擎已起且其表结构可达。
 * 本基座仅验证连通，不建业务表、不部署流程定义。
 */
@Component
public class JdbcFlowableConnectivityAdapter implements InfraConnectivityPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcFlowableConnectivityAdapter.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final RepositoryService repositoryService;

    public JdbcFlowableConnectivityAdapter(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            RepositoryService repositoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.repositoryService = repositoryService;
    }

    @Override
    public InfraStatus probe() {
        TenantSchema schema =
                TenantSchema.forTenant(TenantContextHolder.getTenantId().orElse("public"));
        return new InfraStatus(schema.name(), postgresReachable(schema), workflowReachable());
    }

    private boolean postgresReachable(TenantSchema schema) {
        try {
            Boolean ok =
                    transactionTemplate.execute(
                            status -> {
                                // SET LOCAL：仅本事务生效，事务结束自动复位，池连接复用不泄漏
                                jdbcTemplate.execute(
                                        "SET LOCAL search_path TO \"" + schema.name() + "\", public");
                                // 探针：验证连通 + JSONB 能力（安全标签/策略以 JSONB 存，业务表留待落地）
                                jdbcTemplate.queryForObject("SELECT '{}'::jsonb", String.class);
                                return true;
                            });
            return Boolean.TRUE.equals(ok);
        } catch (RuntimeException ex) {
            log.warn("PostgreSQL 连通探测失败: {}", ex.getMessage());
            return false;
        }
    }

    private boolean workflowReachable() {
        try {
            // 计数流程定义即触达 Flowable 表结构（ACT_RE_PROCDEF），验证引擎已起且 DB 可达
            repositoryService.createProcessDefinitionQuery().count();
            return true;
        } catch (Exception ex) {
            log.warn("Flowable 审批引擎连通探测失败: {}", ex.getMessage());
            return false;
        }
    }
}
