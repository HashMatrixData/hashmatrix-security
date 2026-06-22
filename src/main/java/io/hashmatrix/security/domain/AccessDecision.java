package io.hashmatrix.security.domain;

/**
 * 数据访问裁决结果。
 *
 * <p><b>M1 边界</b>：只表达「是否放行」+ 理由。完整 SC-6 的<b>行计划</b>（行过滤谓词）与<b>列计划</b>
 * （列可见/脱敏）随 SC-6 落地再扩展字段——M1 刻意<b>不</b>预埋半成品字段（如 {@code Optional<RowPlan>}），
 * 避免引入「未实现」的临时兼容层；占位实现给出语义完整的放行（见
 * {@link io.hashmatrix.security.domain.port.AssetAccessPolicy}）。
 *
 * @param allowed 是否放行
 * @param reason  裁决理由（审计/排障可读）
 */
public record AccessDecision(boolean allowed, String reason) {

    public AccessDecision {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    /** 放行。 */
    public static AccessDecision allow(String reason) {
        return new AccessDecision(true, reason);
    }

    /** 拒绝。 */
    public static AccessDecision deny(String reason) {
        return new AccessDecision(false, reason);
    }
}
