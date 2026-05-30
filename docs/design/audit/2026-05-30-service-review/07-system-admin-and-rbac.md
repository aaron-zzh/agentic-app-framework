# 07 系统管理与 RBAC

> 覆盖：用户、角色、权限点、行级数据权限、访问策略；以及贯穿全局的"方法级鉴权缺失"系统级问题。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B9 | 🔴 | `system/user/controller/UserController`、`tool/ToolController`、`company/controller/CompanyController`、`stats/StatsController`、`pay/*Controller`、`channel/*` 等大量管理接口 | **系统性缺失方法级鉴权**：增删用户、重置任意用户密码（含 admin id=1→账号接管）、改状态、导入导出、删/禁用工具、生成并注册可执行工具、导出全员行为报表等均无 `@PreAuthorize`/`@AccessControl`。框架已有 `@AccessControl` 切面却几乎未被业务接口使用 | 制定"写操作/管理端点默认需鉴权"基线；对 user/role/permission/tool/stats/pay/channel 管理端点逐一加角色或权限校验；CI 加规则检测无鉴权的非公开写接口 |
| B10 | 🔴 | `module/tool/ToolController#invoke`/`generate`/`confirmGenerate`/`viewSource`/`share` | 统一工具调用入口"Agent/用户/外部系统均可调用"，REST 直调可能绕过 `ToolPermissionGuard`（其只包装 Agent 内的 ToolCallback）；AI 生成并注册可执行工具、查看源码均无鉴权→任意用户可执行/生成代码、读他人工具源码 | `/invoke` 必须复用与 Agent 同一套权限/风险门控；生成/注册/共享/查看源码加鉴权与审计 |
| M15 | 🟠 | `CompanyController`（createPlan/createObjective/createTask/recordMetric）、`WebhookService.create`、`ChannelConfigService.create` 等 | 直接以 JPA 实体作 `@RequestBody`→**Mass Assignment**：客户端可注入 id/orgId/ownerId/createBy/deleted/version 等系统字段 | 用 Create/Update DTO 接收，仅映射允许字段 |
| M16 | 🟠 | `system/user/domain/User`（password 无 `@JsonIgnore`）、`channel/WebhookConfig.secret`、`ChannelConfig.appSecret/token` | 敏感字段缺 `@JsonIgnore`，违反架构约束"password/secret 必须 `@JsonIgnore`"，依赖 VO 转换做唯一防线 | 敏感字段统一加 `@JsonIgnore` 做纵深防御 |
| M17 | 🟠 | `permission/service/PermissionService#assignRolesToUser` vs `assignPermissionsToRole` | 语义不对称：分配权限给角色是"删后重建"，分配角色给用户是"只增不删"（取消勾选不生效） | 统一为"全量覆盖"或明确文档化差异 |
| 重复 | 🟠 | `role/service/PermissionService` 与 `permission/service/PermissionService`（后者 `@Service("menuPermissionService")`）；`role/controller/PermissionController` 与 `permission/controller/PermissionController` | 两套 PermissionService/Controller 职责重叠（均涉及角色-权限），并行抽象 | 合并为单一权限服务，消除重叠 |
| m11 | 🟡 | `UserService#importUsers` | 默认密码 `123456` 来自配置，弱口令 | 强制首登改密或随机初始密码 |
| m12 | 🟡 | `DataAccessService#buildPredicate` | rule 的 `field/operator` 未对实体元数据校验，非法字段运行时抛错 | 校验字段白名单 |

## 良好实践

- `DataAccessService.buildSpecification` 无匹配规则时返回 `cb.disjunction()`（拒绝所有），行级权限 **fail-closed**，是正确范式（与 B1 租户过滤的 fail-open 形成对比，应以此为准）。
- `DataAccessService` 用 Criteria API 构建谓词，参数化、无 SQL 注入。
- `User` 充血模型（checkPassword/changePassword/isLocked/recordLoginFail/recordLoginSuccess）封装良好，密码仅以编码存储。
- `PermissionService.tree`/`buildTree` 递归构树清晰；版本化（permission 软删除 + 唯一 code 校验）到位。
- `UserController` 批量删除 >100 转异步任务，考虑了规模化（但异步循环 `catch(Exception ignored)` 吞异常，见下）。

## 对称性 / 一致性提示

- 认证 vs 鉴权（清单#8）：B9/B10 是本轮最严重的系统级缺口。
- 创建 vs 删除（清单#2）：`assignRolesToUser` 只增不删（M17）。
- `UserController.deleteBatch` 异步分支 `catch (Exception ignored) {}` 静默吞异常，失败项无记录（违反"不吞异常"）——建议累计失败清单返回。
