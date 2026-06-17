# hashmatrix-security

> hashmatrix 数据中台子模块 · 所属：应用服务层 · 数据安全分系统
>
> 主仓：[HashMatrixData/hashmatrix](https://github.com/HashMatrixData/hashmatrix)

## 角色与位置（一眼看懂）

- **所属**：应用服务层 · 数据安全分系统（无状态 Spring Boot 应用）。
- **一句话**：平台的"安全闸门"——分类分级 / 标签 / 审批 / 审计 / 资源监控，为数据资产定密并管控访问。
- **调用流**：governance（元数据）→ **security（分级·打标·审批·审计）** → 授权结果供网关 / 各服务消费。

## 职责与边界

- **做**：数据分类分级（五层目录）、标签规则、工作流审批、安全审计、资源监控。
- **不做（边界）**：身份认证由 Keycloak / 网关负责（本仓只做授权与管控）；不做治理元数据建模（`governance`）。

## 骨架技术选型（首选 · 待逐仓细化）

| 维度 | 选型 |
|--|--|
| 运行时 | Spring Boot（Java） |
| 审批工作流 | **Flowable** |
| 资源监控 | **Prometheus** |
| 业务库 | PostgreSQL |

> 前端审批/分级界面在 `webui`；权限粒度路由级 + 按钮级，映射 Keycloak 角色。

## 产品形态与多租户（北极星）

**双模交付**：公网 SaaS（我们运营 · 统一**我们品牌** · 租户=企业）／私有化部署（客户环境 · **客户品牌**部署级 · 租户=客户部门）。品牌**部署级**、不按租户运行期换肤。多租户走 **C 分层桥接**：控制平面共享 + 数据平面按租户隔离（Keycloak Organizations 单 realm · schema/db-per-tenant · namespace-per-tenant），由 `control-plane` 编排开通。

**本仓视角**：安全能力以租户为边界、权限租户内生效；多租户隔离是底线。

> 详见主仓 `docs/00-主仓初始化-spec.md`、`docs/architecture/05-多租户与控制平面.md`。

## 说明

本仓库作为 `hashmatrix` 主仓的 git submodule，挂载于 `services/security`。架构背景见主仓 `docs/architecture/`。

## License

Apache-2.0
