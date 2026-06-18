package io.hashmatrix.security.domain;

/**
 * 基础设施连通快照：本仓工程基座对 PostgreSQL / Flowable 审批引擎的可达性自检结果。
 *
 * @param tenantSchema 当前租户隔离 schema（见 {@link TenantSchema}）
 * @param postgres     PostgreSQL 是否可达（含 JSONB 能力探针）
 * @param workflow     Flowable 审批工作流引擎是否可达
 */
public record InfraStatus(String tenantSchema, boolean postgres, boolean workflow) {

    /** 两路基础设施均可达。 */
    public boolean healthy() {
        return postgres && workflow;
    }
}
