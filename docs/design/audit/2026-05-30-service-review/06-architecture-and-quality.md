# 06 架构与质量（横切）

> 覆盖：分层与实体外泄、重复/并行抽象、命名与包结构、占位实现、通用工具、异常处理。

## 问题清单（2026-08-01 复核）

| 编号 | 级别 | 状态 | 位置 | 结论 |
|------|------|------|------|------|
| M6 | 🟠 | FIXED | `WebhookService`、`ChannelConfigService` | 核实（详见 03 区）：`listActive`/`listEnabled` 均已返回 VO，无 Entity 出参 |
| 占位 | 🟠 | OPEN | `engine/{space,evolution,semanticcalc,dsl,metadata,monitor}` | 投机性空接口，违反硬约束#5"禁占位/TODO 占位"、准则#2"简洁优先"，未在本轮处理 |
| 包结构 | 🟡 | OPEN | `module/ai/role`、`module/ai/skill`、`module/tool` 等 | 文件平铺（Service/VO/DTO 同包）与 controller/service/domain 分层混用，跨模块不一致，未在本轮处理 |
| 示例 | 🟡 | OPEN | `aaf-api/module/examples/**` | demo/示例代码混入主 api 模块并参与构建，未在本轮处理 |
| 兼容 | 🟡 | OPEN | `OperatorContext#currentUserId`(default 别名)、`ToolPermissionGuard` 多重载 | "兼容旧调用"别名与硬约束#5精神相悖，未在本轮处理 |
| 异常 | 🟡 | FIXED | `config/GlobalExceptionHandler` | 核实：已有 `handleAuthentication`（401）、`handleAccessDenied`/`handleAccessDeniedException`（403）专门处理器；`ConstraintViolationException` 已用 `leafProperty` 脱敏，不回传 `e.getMessage()` 完整方法签名信息 |

## 良好实践（架构层面）

- `BaseEntity` 统一 id/乐观锁 `@Version`/审计字段/软删除（`@SQLRestriction`+子类 `@SQLDelete`）/多租户字段/Actor（createByType/updateByType），抽象到位。
- `BaseCrudService`/`BaseCrudController` 泛型化 CRUD，减少模板代码，分页/异常一致。
- `Result<T>` 用 record + `@JsonIgnore isSuccess`，统一响应体清晰。
- 模块依赖方向（common ← framework ← api/auto-dev）总体遵守；未发现业务模块反向依赖 framework 的明显违例（抽样范围内）。

## 重复 / 并行抽象清单

| 重复项 | 位置 | 处置 |
|--------|------|------|
| 脚本执行 ×2 | `ScriptExecutor`(GraalVM/Process) 与 `ScriptSandbox`(子进程) | 收敛为 GraalVM 受限基线 |
| 多租户机制 ×2 | `TenantContext`/`TenantFilter`(org 过滤) 与 `engine/space SpaceEngine` | 明确单一隔离模型，避免双轨 |

## 通用工具（aaf-common）

- `Result`/`PageResult`/`PageParam`/`SpecificationBuilder` 结构合理。
- `ServletUtils.getClientIp` 盲信 XFF（见 01 区 m7）。


## 结论

- 整体分层与基类设计扎实，主要问题集中在**鉴权边界、敏感数据外泄、并发与幂等、占位/并行抽象**四类。
- 修复优先级建议：B 级（B1/B4/B5/B7/B8）→ 鉴权类 major（M1/M9）→ 其余。
- 🔴 高风险项（租户模型、资金安全、部署接口、路径穿越）须人类审核后再进入开发修复。
