package io.hashmatrix.security.domain;

/**
 * 受管数据资产引用：租户库（{@link #TENANT_SCHEMA_TEMPLATE sec_*} 模板，运行期绑定为
 * {@code sec_<tenant>}，见 {@link TenantSchema}）下的一张表。
 *
 * <p>是数据权限拦截链的「对象」维度（主体×<b>对象</b>×动作），亦即「角色→可见库表」映射的值。
 * schema 用模板 {@code sec_*} 表达「当前租户库」，避免把具体租户写死进静态映射（隔离由链 ① 行级兜底
 * 保证）。
 *
 * @param schema 库名或库模板（如 {@code sec_*}）
 * @param table  表名（如 {@code classification}）
 */
public record AssetRef(String schema, String table) {

    /** 租户库模板：运行期由 {@link TenantSchema#forTenant} 绑定为 {@code sec_<tenant>}。 */
    public static final String TENANT_SCHEMA_TEMPLATE = "sec_*";

    public AssetRef {
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("schema must not be blank");
        }
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("table must not be blank");
        }
    }

    /** 当前租户库（{@code sec_*}）下的一张表。 */
    public static AssetRef tenantTable(String table) {
        return new AssetRef(TENANT_SCHEMA_TEMPLATE, table);
    }

    /** 限定名 {@code schema.table}（如 {@code sec_*.classification}）。 */
    public String qualifiedName() {
        return schema + "." + table;
    }
}
