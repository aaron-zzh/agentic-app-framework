---
level: Practice
layer: Pattern
purpose: 定义 AAF 统一 Operator 抽象——让 Agent 和 Human 共享操作者接口
status: draft
version: 0.2.0
date: 2026-05-28
author: AaronZZH
changelog:
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

## 与权限系统的关系

Spring Security 的 `Principal` 层面：
- Human → `UserPrincipal`（JWT 认证）
- AI → `AgentPrincipal`（API Key / 内部 token 认证）

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
- ⏳ `com.xuejiai.aaf.common.operator.Operator` 类（首个业务实体开发时）
- ⏳ `packages/types/operator.ts`（packages/ 落地后）
- ⏳ ArchUnit 约束（`createdBy` 必须为 Operator 类型）
