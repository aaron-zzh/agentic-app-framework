# AI 业务动作网关技术设计

## 背景

AI 需要代表用户执行业务系统操作，但不应直接获得任意 REST 或数据库访问能力。直接暴露 REST 会让权限、风险确认、计费、审计和参数语义分散在多个入口，后续接入 AgentScope、MCP、工作流和内部代办时也容易出现绕过。

本设计提供一个统一的 AI 业务动作网关：AI 只调用受控函数接口，后端根据实体注册表和动作元数据执行权限、数据权限、计费、风险确认和审计。

## 目标

- AI 可以代表用户执行已开放实体的标准 CRUD 操作。
- 默认只开放显式注册的实体，不自动暴露所有 JPA Entity 或数据表。
- 标准实体复用 `BaseCrudService` 的查询窗口、字段集、数据权限和权限码设计。
- 复杂业务动作可在同一网关下注册专用 Action，不强行映射为 CRUD。
- 审计上区分真实操作者和权限主体：operator 是 AI/Assistant，owner 是被代表用户。

## 非目标

- 不提供一个可调用任意 URL 的通用 HTTP 工具作为生产默认能力。
- 不绕过 Controller/Service 上已有业务规则。
- 不让 AI 自动推断数据库表、字段和权限码。

## 总体结构

```text
AI / Agent
  -> AiBusinessActionTool
  -> AiBusinessActionExecutor
  -> EntityActionRegistry
  -> EntityActionAdapter
  -> BaseCrudService / 专用业务 Service
```

固定执行顺序：

```text
解析 action/entity/params
-> 查实体注册表
-> 解析标准动作
-> 计算权限码
-> AccessDecisionService.hasPermission
-> BaseCrudService 行级数据权限与字段权限
-> 执行业务
-> 返回结构化结果
```

计费和风险确认作为治理接入点：读操作可低风险自动执行，写操作、删除、发布、付款等动作应在 Action 元数据中声明风险等级并触发人工确认；需要消耗权益的动作在执行前调用权益/积分门控，成功后结算。无权限、等待确认、无额度和内容审查等待都必须返回结构化 JSON，AI 根据 `recoverable` 与 `resume.strategy` 判断是否等待并使用相同参数恢复执行。`sessionId` 用于人工确认后写入本会话授权；无法取得会话 ID 的内部调用应使用 `executeAsOwner` 并由调用方保证风险边界。

## 请求协议

```json
{
  "sessionId": "chat-session-id",
  "action": "entity.query",
  "entity": "system-role",
  "params": {
    "pageNo": 1,
    "pageSize": 20,
    "keyword": "销售",
    "fieldSet": "list"
  }
}
```

标准动作：

| 动作 | 权限动作 | 说明 |
| --- | --- | --- |
| `entity.query` | `read` | 调用 `queryWindow`，支持列表详情优化 |
| `entity.detail` | `read` | 调用详情，支持 `queryToken` 和 `fieldSet` |
| `entity.batchRead` | `read` | 批量读取 |
| `entity.options` | `read` | 选择器选项 |
| `entity.meta` | `read` | CRUD 元数据 |
| `entity.create` | `create` | 创建 |
| `entity.update` | `update` | 更新 |
| `entity.delete` | `delete` | 删除 |
| `entity.batchDelete` | `delete` | 批量删除 |
| `entity.export` | `export` | 导出 |
| `entity.validate` | `create` | 创建/更新前预校验 |
| `entity.archive` | `delete` | 归档 |
| `entity.restore` | `update` | 恢复 |

## 实体开放方式

标准 CRUD 实体通过 `BaseCrudEntityActionAdapter` 注册：

```java
@Component
public class RoleAiActionAdapter
        extends BaseCrudEntityActionAdapter<Role, RoleVO, RoleCreateDTO, RoleUpdateDTO, RolePageParam> {
    // 绑定 RoleService 与 DTO 类型
}
```

这表示 `system-role` 实体对 AI 开放标准动作。没有注册适配器的实体，即使存在 Controller 或 Repository，也不会被 AI 业务动作网关调用。

复杂业务动作可以实现 `EntityActionAdapter` 或后续的 `BusinessActionAdapter`，手写参数、权限码、风险等级和执行逻辑。

## 动作目录策略

`ai_action_catalog` 是第二层开放策略。代码 Adapter 决定“系统最多能做什么”，SQL 目录决定“当前允许 AI 看见和执行什么”。

标准实体动作在 SQL 中存短动作名：

```text
entity_slug = system-role
action_key = query / detail / create / update / delete
```

专用业务动作后续可使用完整动作名，例如 `content.publish`、`workflow.approve`。

存在 SQL Provider 时采用 fail-closed 策略：没有配置或 `enabled=false` 的动作不会出现在 `listBusinessActions` 返回中，也不能通过 `executeBusinessAction` 执行。AI 看到的能力清单是代码注册表和 SQL 目录合并后的结果。

## 权限模型

AI 动作权限使用当前 owner 判断：

- 普通用户请求：owner 是当前登录用户。
- Assistant 代办：owner 是 delegator。
- 内部代办：通过 `PermissionExecutionService.runAsOwner` 指定 owner。

真实操作者用于审计，不用于扩大权限。AI 只能执行 owner 本身具备权限的动作。

标准 CRUD 动作复用 `BaseCrudService.resolvePermissionCode`，例如角色实体：

```text
entity.query  -> system:role:read
entity.create -> system:role:create
entity.update -> system:role:update
entity.delete -> system:role:delete
```

## 当前实现

- `AiBusinessActionTool`：暴露给 Agent 的函数接口。
- `AiBusinessActionExecutor`：统一执行入口。
- `EntityActionRegistry`：显式注册实体清单。
- `BaseCrudEntityActionAdapter`：标准 CRUD 实体适配基类。
- `RoleAiActionAdapter`：角色实体演示。
- `BaseCrudService` 新增公共元信息方法，供动作网关复用。
- `AiActionCatalogProvider`：第二层动作目录 SPI。启动模块可用 SQL 实现动作开关、风险、计费和 schema。
- `ai_action_catalog`：SQL 动作目录表。存在 SQL Provider 时，未配置或未启用的动作不会展示给 AI，也不能执行。
- `AiBusinessActionExecutor`：按具体动作执行 L1 权限、置信度门控、动作风险确认、权益/积分预检、业务执行和成功后扣费。行级/字段级数据权限由 `BaseCrudService` 在查询、详情、批量读取和写入前读取实体时统一生效。
- `HumanApprovalService`：统一承载 Action 风险确认和低置信确认。审批通过后 `HitlApprovalGrantListener` 按会话写入临时授权，AI 用相同 `sessionId` 和参数重试即可恢复。
- `AgentScopeToolGovernanceService`：AgentScope `Toolkit` 构建后包装 `AgentTool.callAsync`，适配进入 `ToolPermissionGuard`，避免 AgentScope/MCP/子 Agent 工具绕过统一治理。

## 统一返回协议

业务动作与工具调用共享同一类语义字段：

```json
{
  "success": false,
  "code": "FORBIDDEN",
  "message": "权限不足: system:role:update",
  "recoverable": true,
  "pendingApproval": false,
  "authorization": {
    "mode": "ADMIN_REQUIRED",
    "approvalId": null,
    "requiredBy": "管理员授权业务权限"
  },
  "resume": null,
  "data": null
}
```

- `FORBIDDEN` 表示 owner 没有永久业务权限，不能通过用户临时确认绕过。
- `PENDING_APPROVAL` 表示当前用户可即时确认，确认后同会话重试。
- `INSUFFICIENT_CREDITS` 表示额度不足，等待充值/套餐恢复后重试。
- `PENDING_CONTENT_REVIEW` 主要用于生成式 TOOL，审查通过后恢复。

## 后续补强

- 为 Action 增加风险等级、人工确认策略和幂等键。
- 接入 `AiCreditGuard` / `@Entitlement`，区分读、写、导出、批量操作的成本。
- 为 AgentScope 工具治理补充专项集成测试，覆盖白名单、权限不足、待确认、内容复审和额度不足。
- 增加动作审计表，记录 operator、owner、entity、action、params 摘要、结果和耗时。
- 为更多继承 `BaseCrudService` 的实体补充适配器，逐步开放 AI 可操作面。
