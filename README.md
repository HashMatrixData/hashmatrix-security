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

## 工程基座（本地构建与运行）

> 本仓已具备**可独立开发/编译/调试/运行**的 Spring Boot 工程基座（security#1）。
> 本基座只打通基础设施、授权链与多租户路由，**不实现安全业务**（分级/标签/审批/审计业务在其上落地）。

**分层骨架**（依赖自上而下，内层不依赖外层）：`api`（REST/统一返回/方法级授权）→ `app`（用例编排/审计）→ `domain`（领域模型+出站端口）→ `infra`（PG/Flowable 适配+租户 schema 路由）。

**公共能力复用**（经 Maven 坐标引用 `libs-java`，非 submodule 路径）：
`starter-tenant`（`X-Tenant-Id` → `TenantContext`）、`starter-web`（统一返回/异常）、`starter-security`（信任网关下发身份/角色头、方法级授权）、`starter-audit`（结构化审计·自动加盖租户）、`starter-observability`（actuator + `/actuator/prometheus`）、`starter-test`（JUnit5/AssertJ/Mockito/Testcontainers + 脱敏 fixtures）。

**审批工作流**：Flowable BPMN 引擎已接线、本地可起（基座不部署业务流程定义）。

```bash
# 1) 纯打包（跳过所有测试，无需 Docker）—— 对应 DoD 验收
mvn -q -DskipTests package

# 2a) 构建 + 单测（surefire，无需 Docker）
mvn -B package

# 2b) + 集成切片（failsafe，Testcontainers 起 PG，需 Docker）
mvn -B verify

# 3) 本地起栈 + 运行，验证健康检查
docker compose -f docker-compose.local.yml up -d
bash scripts/run-local.sh          # 或 mvn spring-boot:run -Dspring-boot.run.profiles=local
curl -s localhost:8080/actuator/health
# 业务 API 需网关下发身份（应用不二次校验 token）：
curl -s localhost:8080/api/security/probe -H 'X-User: svc-probe' -H 'X-Tenant-Id: tenant-demo'
```

> 公共依赖在 GitHub Packages，首次构建需配 `~/.m2/settings.xml`（`server id=github` + `read:packages` PAT）。
> 内网/信创交付：制品镜像到内网 Nexus/Artifactory，指向私服或用 `-Pxinchuang`，见主仓 `libs-java/README`。

## 说明

本仓库作为 `hashmatrix` 主仓的 git submodule，挂载于 `services/security`。架构背景见主仓 `docs/architecture/`。

## License

Apache-2.0
