---
level: Practice
layer: Product
purpose: AAF 记录变更历史设计（版本追踪 + Diff）
status: draft
version: 1.0.0
date: 2026-05-12
author: AaronZZH
---

# 记录变更历史设计

## 核心思路

一张表记录所有实体的字段级变更，支持历史查看、版本对比、回滚。

## 表设计

```sql
CREATE TABLE sys_change_log (
  id            BIGINT PRIMARY KEY,
  entity_type   VARCHAR(64) NOT NULL,     -- 'document' / 'user'
  entity_id     BIGINT NOT NULL,
  version       INT NOT NULL,             -- 递增版本号
  action        VARCHAR(16) NOT NULL,     -- 'create' / 'update' / 'delete' / 'rollback'
  changes       JSONB,                    -- { "title": { "old": "v1", "new": "v2" } }
  snapshot      JSONB,                    -- 全量快照（仅关键版本：创建/状态流转/每10版）
  operator_id   BIGINT NOT NULL,
  created_at    TIMESTAMP NOT NULL,
  remark        VARCHAR(256),
  INDEX idx_entity (entity_type, entity_id, version)
);
```

## changes 结构

```json
{ "title": { "old": "旧标题", "new": "新标题" },
  "status": { "old": "draft", "new": "published" } }
```

## 接口

```text
GET /api/{entity}/{id}/history              → 变更时间线
GET /api/{entity}/{id}/history/diff?v1=3&v2=5 → 两版本对比
POST /api/{entity}/{id}/history/rollback?version=5 → 回滚
```

## 实现

- 后端：JPA @PreUpdate 拦截器自动对比 old/new 生成 changes
- 全量 snapshot 仅在创建、状态流转、每 10 版时存储
- 前端：FormHeader [版本历史] → 侧边抽屉展示时间线 + Diff 视图


## 与操作日志的关系

两者是不同层次的记录，不互相替代：

| 维度 | 变更历史（sys_change_log） | 操作日志（sys_operation_log） |
|------|--------------------------|----------------------------|
| 记录什么 | **数据变了什么**（字段 old→new） | **谁做了什么操作**（接口调用） |
| 粒度 | 字段级 | 接口级 |
| 用途 | 版本对比、回滚、审计数据变更 | 安全审计、行为分析、问题排查 |
| 示例 | `title: "v1"→"v2"` | `用户A 调用 PUT /api/documents/123` |
| 触发 | 数据实际变更时 | 每次 API 调用（含查询） |
| 存储量 | 中（仅写操作） | 大（所有请求） |
| 是否需要 | 业务需要版本追踪的实体 | 所有接口（安全合规） |

```text
操作日志：谁在什么时间调了什么接口（入参/出参/IP/耗时）→ 安全审计
变更历史：这条记录的哪些字段从什么值变成了什么值 → 业务追溯
```

**AAF 两者都需要，但独立设计**：
- `sys_operation_log`：AOP 拦截所有 Controller 方法，记录请求元信息（通用，所有接口）
- `sys_change_log`：JPA 拦截器对比实体变更，记录字段 diff（按需，仅需要版本追踪的实体）


## 性能与清理策略

### 性能影响

| 环节 | 开销 | 影响 |
|------|------|------|
| 写入变更记录 | 每次保存多一次 INSERT | 极小（异步写入，不阻塞主事务） |
| 查询历史 | 按索引查 `(entity_type, entity_id)` | 无问题（单条记录历史通常 < 100 条） |
| 存储增长 | 取决于编辑频率 | 需要清理策略 |

核心原则：**写入异步化（不影响保存性能），存储定期清理（不无限增长）**。

### 按需开启

不是所有实体都需要变更历史。通过 EntityDef 配置：

```typescript
interface EntityDef {
  versions?: {
    enabled: boolean              // 默认 false，按需开启
    maxPerRecord?: number         // 每条记录最多保留版本数（默认 50）
    retentionDays?: number        // 保留天数（默认 180 天）
    snapshotInterval?: number     // 每 N 版存一次全量（默认 10）
  }
}
```

### 自动清理策略

```text
定时任务（每日凌晨）：
  1. 删除超过 retentionDays 的记录（保留最近 snapshot 不删）
  2. 单条记录超过 maxPerRecord 时，删除最早的（保留 snapshot 节点）
  3. 已删除实体的历史，保留 30 天后彻底清除
```

### 典型配置

| 实体 | 是否开启 | 理由 |
|------|---------|------|
| 文档 | ✅ | 核心业务，需要版本追溯 |
| 工作流 | ✅ | 配置变更需审计 |
| 用户 | ✅ | 权限变更需审计 |
| 字典 | ❌ | 变更少，无需追踪 |
| 操作日志 | ❌ | 本身就是日志，不需要日志的日志 |
