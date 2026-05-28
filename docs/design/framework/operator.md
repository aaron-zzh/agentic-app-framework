---
level: Practice
layer: Pattern
purpose: 定义 AAF 统一 Operator 抽象——让 Agent 和 Human 共享操作者接口
status: draft
version: 0.3.0
date: 2026-05-28
author: AaronZZH
changelog:
  - 2026-05-28 | 加入 BaseEntity 字段设计、OperatorContext 接口、AI 权限委托模型
  - 2026-05-28 | 重命名 Actor → Operator，避免与 Assistant 层 Actor（人格载体）冲突；移至 framework/ 顶层
  - 2026-05-06 | 初版，定义 Actor 抽象、类型枚举、审计协议
---

# Operator 模型设计（操作者抽象）

> AI 是一等公民，不是附加物。Agent 和 Human 在数据模型层共享统一的操作者抽象。

## 命名说明

- **Operator**（本文档）：系统级操作者标识，回答"谁执行了操作"，跨层通用（审计、权限、日志）
- **Actor**（`intelligent/agent.md` 中）：Assistant 层的人格载体，回答"助理以什么人格面对用户"

两者职责不同，包路径不同：
- `com.xuejiai.aaf.common.operator.Operator` — 操作者标识
- `com.xuejiai.aaf.framework.intelligent.assistant.actor.Actor` — 人格载体

## 核心抽象

```java
// com.xuejiai.aaf.common.operator.Operator
public record Operator(OperatorType type, Long id, String name) {
    public String toRef() { return type.name() + "/" + name; }
}

public enum OperatorType {
    HUMAN,  // 人类用户
    AI      // AI 智能体
}
```

## 设计原则

- **二元组标识**：`operator_type + operator_id` 唯一确定一个操作者
- **字符串引用**：`AI/architect` 或 `HUMAN/AaronZZH`，用于日志和文档
- **统一接口**：所有"谁做了什么"的场景使用同一 Operator 类型，不分叉

## 数据库约定

```sql
-- 所有需要记录操作者的表统一使用
operator_type  VARCHAR(16) NOT NULL,  -- 'HUMAN' | 'AI'
operator_id    BIGINT      NOT NULL   -- 指向 t_user 或 t_agent 的 ID
```

不建独立的 `t_operator` 表（避免多态关联的查询复杂度），而是通过类型+ID 指向具体表。

## 适用场景

| 场景 | 列名约定 | 示例 |
|------|---------|------|
| 创建者 | `creator_type + creator_id` | 谁创建了任务 |
| 修改者 | `modifier_type + modifier_id` | 谁最后修改 |
| 执行者 | `executor_type + executor_id` | 谁执行了操作 |
| 审批者 | `approver_type + approver_id` | 谁审批通过 |
| 分配对象 | `assignee_type + assignee_id` | 分配给谁 |

## 审计协议

所有状态变更事件携带 Operator 信息：

```java
public record AuditEvent(
    Operator operator,
    String action,      // CREATE / UPDATE / DELETE / APPROVE / REJECT
    String entityType,  // 操作的实体类型
    Long entityId,      // 操作的实体 ID
    Instant timestamp,
    Map<String, Object> changes  // 变更字段快照
) {}
```

## BaseEntity 字段设计

所有 JPA 实体继承 `BaseEntity`，审计字段支持区分 Human/AI 操作者：

```java
// com.xuejiai.aaf.common.model.BaseEntity
@CreatedBy
@Column(name = "create_by")
private Long createBy;              // 操作者 ID（user.id 或 assistant.id）

@Column(name = "create_by_type", length = 16)
private String createByType;        // "HUMAN" | "AI"（EntityListener 自动填充）

@Column(name = "owner_id")
private Long ownerId;               // 数据归属者（始终 user.id）
                                    // Human 操作：owner_id = create_by
                                    // AI 操作：owner_id = 委托者 user.id

@LastModifiedBy
@Column(name = "update_by")
private Long updateBy;

@Column(name = "update_by_type", length = 16)
private String updateByType;
```

**字段语义：**

| 字段 | 含义 | 值 |
|------|------|-----|
| `create_by` | 谁执行的操作 | user.id 或 assistant.id |
| `create_by_type` | 操作者类型 | HUMAN / AI |
| `owner_id` | 数据归属谁 | 始终 user.id（AI 操作时填委托者） |

**查询场景：**
- 查"我的数据"：`WHERE owner_id = ?`（不管谁创建的）
- 查"谁操作的"：`create_by + create_by_type`（审计用）

**迁移策略：**

```sql
ALTER TABLE xxx ADD COLUMN create_by_type VARCHAR(16) DEFAULT 'HUMAN';
ALTER TABLE xxx ADD COLUMN update_by_type VARCHAR(16) DEFAULT 'HUMAN';
ALTER TABLE xxx ADD COLUMN owner_id BIGINT;
UPDATE xxx SET owner_id = create_by WHERE owner_id IS NULL;
```

历史数据全部默认 HUMAN，`owner_id` 回填为 `create_by`，无损兼容。

## OperatorContext 接口

当前 `ActorContext` 的演进方向，统一提供操作者和数据归属者信息：

```java
// com.xuejiai.aaf.framework.security.OperatorContext
public interface OperatorContext {
    /** 当前操作者 ID（user.id 或 assistant.id） */
    Optional<Long> currentOperatorId();

    /** 操作者类型 */
    OperatorType currentOperatorType();

    /** 数据归属者 ID（始终 user.id） */
    Optional<Long> currentOwnerId();

    /** 是否已认证 */
    boolean isAuthenticated();
}
```

**两种场景下的值：**

| 场景 | operatorId | operatorType | ownerId |
|------|-----------|--------------|---------|
| Human 直接操作 | user.id | HUMAN | user.id（同一个） |
| AI 代为操作 | assistant.id | AI | delegator.user.id |

**EntityListener 自动填充：**

```java
@PrePersist
public void prePersist() {
    var ctx = OperatorContext.current();
    this.createBy = ctx.currentOperatorId().orElse(null);
    this.createByType = ctx.currentOperatorType().name();
    this.ownerId = ctx.currentOwnerId().orElse(this.createBy);
}
```

## AI 权限委托模型

AI 不是独立的权限主体——它**代表用户**执行操作，权限继承自委托者并被 scope 收窄。

### 核心公式

```text
AI 实际权限 = 委托者权限 ∩ scope 白名单
```

### 委托关系存储

委托关系直接存在 `AssistantDefinition` 表中（1:1 归属，不需要独立表）：

```java
// AssistantDefinition 实体
@Column(name = "delegator_id")
private Long delegatorId;           // 委托者（user.id）

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "permission_scope", columnDefinition = "jsonb")
private PermissionScope permissionScope;  // 权限边界配置
```

```java
// 权限边界（JSON 存储）
public record PermissionScope(
    List<String> allowedTools,        // ["search", "code-gen", "file-write"]
    List<String> allowedResources,    // ["space:my-workspace/*", "document:*"]
    List<String> allowedOperations,   // ["read", "write", "execute"]
    RiskLevel maxAutoRiskLevel,       // LOW / MEDIUM / HIGH
    OverLimitAction overLimitAction   // ASK / SKIP / PAUSE
) {}

public enum RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }
public enum OverLimitAction { ASK, SKIP, PAUSE }
```

### 权限判定流程

```text
AI 请求执行操作：
  1. OperatorContext → 获取 assistant_id
  2. 查 AssistantDefinition → 获取 delegator_id + permissionScope
  3. 查委托者权限（RBAC + ReBAC + 记录规则）→ 权限集 A
  4. 从 permissionScope 获取白名单 → 边界 B
  5. 实际权限 = A ∩ B
  6. 判断当前操作是否在实际权限内：
     ├── 在 → 检查风险等级
     │     ├── ≤ maxAutoRiskLevel → 自动执行
     │     └── > maxAutoRiskLevel → 按 overLimitAction 处理
     └── 不在 → 权限不足，按 overLimitAction 处理
```

### 权限不足时的处理

| overLimitAction | 行为 |
|-----------------|------|
| ASK | 向委托者实时申请（WebSocket 推送），等待确认 |
| SKIP | 跳过该操作，继续执行后续任务 |
| PAUSE | 暂停整个任务，等待用户介入 |

详见 [access-control.md](security/access-control.md) 中的实时交互授权章节。

## 与权限系统的关系

Spring Security 的 `Principal` 层面：
- Human → `UserPrincipal`（JWT 认证）
- AI → `AgentPrincipal`（内部 token 认证，携带 assistant_id + delegator_id）

两者都实现统一的 `OperatorAware` 接口，业务代码通过 `OperatorContext.current()` 获取当前 Operator，不关心底层认证方式。

## 前端类型（packages/ 落地后）

```typescript
// packages/types/operator.ts
export type OperatorType = 'HUMAN' | 'AI'

export interface Operator {
  type: OperatorType
  id: number
  name: string
}

export function operatorRef(op: Operator): string {
  return `${op.type}/${op.name}`
}
```

## 落地路径

- ✅ 设计文档（本文档）
- ✅ 领域建模规范中的 Polymorphic Operator 约定
- ⏳ BaseEntity 加 `create_by_type` / `update_by_type` / `owner_id` 字段
- ⏳ `ActorContext` → `OperatorContext` 重构（扩展接口，保持向后兼容）
- ⏳ `com.xuejiai.aaf.common.operator.Operator` 类
- ⏳ `packages/types/operator.ts`（packages/ 落地后）
- ⏳ ArchUnit 约束（`createdBy` 必须为 Operator 类型）
