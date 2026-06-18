/**
 * 数据安全分系统 —— 分层骨架（依赖方向自上而下，内层不依赖外层）：
 *
 * <ul>
 *   <li>{@code api} —— 入站适配（REST 控制器）：透传租户、统一返回（starter-web）、方法级授权
 *       （starter-security），不含业务规则。</li>
 *   <li>{@code app} —— 应用服务：编排用例、事务边界、审计（starter-audit），依赖 domain 端口。</li>
 *   <li>{@code domain} —— 领域模型与出站端口（port）：纯业务，无框架依赖。</li>
 *   <li>{@code infra} —— 出站适配：实现 domain 端口，对接 PostgreSQL / Flowable、租户 schema 路由。</li>
 * </ul>
 *
 * <p>本基座只打通基础设施、授权链与多租户路由；分类分级 / 安全标签 / 工作流审批 / 安全审计等业务
 * 在本基座之上落地。身份认证由 Keycloak / 网关负责，本仓只做<strong>授权与管控</strong>。
 */
package io.hashmatrix.security;
