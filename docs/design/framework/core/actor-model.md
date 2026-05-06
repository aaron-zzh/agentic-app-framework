---
level: Practice
layer: Pattern
purpose: 定义 AAF 统一 Actor 抽象——让 Agent 和 Human 共享操作接口
status: draft
version: 0.1.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 初版，定义 Actor 抽象、类型枚举、审计协议
---

# Actor 模型设计

> AI 是一等公民，不是附加物。Agent 和 Human 在数据模型层共享统一抽象。

## 核心抽象

```java
// com.xuejiai.aaf.common.actor.Actor
public record Actor(ActorType type, Long id, String name) {
    public String toRef() { return type.name() + "/" + name; }
}

public enum ActorType {
    HUMAN,  // 人类用户
    AI      // AI 智能体
}
```

## 设计原则

1. **二元组标识**：`actor_type + actor_id` 唯一确定一个操作者
2. **字符串引用**：`AI/architect` 或 `Human/AaronZZH`，用于日志和文档
3. **统一接口**：所有"谁做了什么"的场景使用同一 Actor 类型，不分叉

## 数据库约定

```sql
-- 所有需要记录操作者的表统一使用
actor_type  VARCHAR(16) NOT NULL,  -- 'HUMAN' | 'AI'
actor_id    BIGINT      NOT NULL   -- 指向 t_user 或 t_agent 的 ID
```

不建独立的 `t_actor` 表（避免多态关联的查询复杂度），而是通过类型+ID 指向具体表。

## 适用场景

| 场景 | 列名约定 | 示例 |
|------|---------|------|
| 创建者 | `creator_type + creator_id` | 谁创建了任务 |
| 修改者 | `modifier_type + modifier_id` | 谁最后修改 |
| 执行者 | `executor_type + executor_id` | 谁执行了操作 |
| 审批者 | `approver_type + approver_id` | 谁审批通过 |
| 分配对象 | `assignee_type + assignee_id` | 分配给谁 |

## 审计协议

所有状态变更事件携带 Actor 信息：

```java
public record AuditEvent(
    Actor actor,
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

两者都实现统一的 `ActorAware` 接口，业务代码通过 `ActorContext.current()` 获取当前 Actor，不关心底层认证方式。

## 前端类型（packages/ 落地后）

```typescript
// packages/types/actor.ts
export type ActorType = 'HUMAN' | 'AI'

export interface Actor {
  type: ActorType
  id: number
  name: string
}

export function actorRef(actor: Actor): string {
  return `${actor.type}/${actor.name}`
}
```

## 落地路径

1. ✅ 设计文档（本文档）
2. ✅ 领域建模规范中的 Polymorphic Actor 约定
3. ⏳ `com.xuejiai.aaf.common.actor.Actor` 类（首个业务实体开发时）
4. ⏳ `packages/types/actor.ts`（packages/ 落地后）
5. ⏳ ArchUnit 约束（`createdBy` 必须为 Actor 类型）
