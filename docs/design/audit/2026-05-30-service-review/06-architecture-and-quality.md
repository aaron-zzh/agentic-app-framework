# 06 架构与质量（横切）

> 覆盖：分层与实体外泄、重复/并行抽象、命名与包结构、占位实现、通用工具、异常处理。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B6 | 🔴 | `framework/security/PermissionCacheService` 与 `framework/security/cache/PermissionCacheService` | 两个同名类均 `@Service`→默认 Bean 名冲突，启动 `ConflictingBeanDefinitionException`；且共用 key 前缀 `permission:user:` 但分别用 String/Set 存储→`WRONGTYPE` | 删除其一（保留带 PermissionLoader SPI 版本），调用方统一，验证启动恢复 |
| M6 | 🟠 | `WebhookService`、`ChannelConfigService`、`EntitlementService.listUserQuotas` 等 | service 层向 controller 返回 Entity，违反架构约束"service 禁止返回 Entity" | 统一 VO/DTO 出参；至少 controller 出参非实体 |
| 占位 | 🟠 | `engine/{space,evolution,semanticcalc,dsl,metadata,monitor}`、`KnowledgeBaseService` 的 import | 投机性空接口 + TODO 假实现，违反硬约束#5"禁占位/TODO 占位"、准则#2"简洁优先" | 未到实现阶段不声明；假成功改为显式未实现 |
| 包结构 | 🟡 | `module/ai/role`、`module/ai/skill`、`module/tool` 等 | 文件平铺（Service/VO/DTO 同包）与 controller/service/domain 分层混用，跨模块不一致 | 统一模块内分层结构 |
| 示例 | 🟡 | `aaf-api/module/examples/**` | demo/示例代码混入主 api 模块并参与构建 | 隔离到独立 profile 或移除 |
| 兼容 | 🟡 | `OperatorContext#currentUserId`(default 别名)、`ToolPermissionGuard` 多重载 | "兼容旧调用"别名与硬约束#5精神相悖 | 统一调用方后删除别名 |
| 异常 | 🟡 | `config/GlobalExceptionHandler` | 未显式处理 `AuthenticationException/AccessDeniedException`；`ConstraintViolationException` 直接回传 `e.getMessage()` 可能泄露内部信息 | 补 401/403；约束信息脱敏 |

## 良好实践（架构层面）

- `BaseEntity` 统一 id/乐观锁 `@Version`/审计字段/软删除（`@SQLRestriction`+子类 `@SQLDelete`）/多租户字段/Actor（createByType/updateByType），抽象到位。
- `BaseCrudService`/`BaseCrudController` 泛型化 CRUD，减少模板代码，分页/异常一致。
- `Result<T>` 用 record + `@JsonIgnore isSuccess`，统一响应体清晰。
- 模块依赖方向（common ← framework ← api/auto-dev）总体遵守；未发现业务模块反向依赖 framework 的明显违例（抽样范围内）。

## 重复 / 并行抽象清单

| 重复项 | 位置 | 处置 |
|--------|------|------|
| PermissionCacheService ×2 | security / security.cache | 删一份（B6） |
| 脚本执行 ×2 | `ScriptExecutor`(GraalVM/Process) 与 `ScriptSandbox`(子进程) | 收敛为 GraalVM 受限基线 |
| 多租户机制 ×2 | `TenantContext`/`TenantFilter`(org 过滤) 与 `engine/space SpaceEngine` | 明确单一隔离模型，避免双轨 |

## 通用工具（aaf-common）

- `Result`/`PageResult`/`PageParam`/`SpecificationBuilder` 结构合理。
- `ServletUtils.getClientIp` 盲信 XFF（见 01 区 m7）。
- 枚举体系（`enums/{pay,billing,livechat,channel,sys,stats,knowledge}`）齐全，但业务代码存在用魔法串绕过枚举的情况（见 02 区 M-str），应统一。

## 结论

- 整体分层与基类设计扎实，主要问题集中在**鉴权缺口、敏感数据外泄、并发与幂等、占位/重复抽象**四类。
- 修复优先级建议：B 级（B1/B2/B3/B4/B5/B6/B7/B8）→ 鉴权与并发类 major（M1/M4/M9/M12/M14）→ 其余。
- 🔴 高风险项（租户模型、资金安全、部署接口、路径穿越）须人类审核后再进入开发修复。
