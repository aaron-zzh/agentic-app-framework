---
level: Practice
layer: Model
purpose: AI 产出异步审查：统一审查队列、风险提醒、快速反馈
status: published
version: 1.0.0
date: 2026-05-29
author: AaronZZH
---

# AI 产出审查与追溯

> 助理产出即时生效，人类异步审查，可调整可回退。

## 设计理念

- **产出即生效**：助理完成任务后产出立即生效，不等待人类审批
- **异步审查通知**：高风险产出推送通知提醒用户关注，用户自行决定是否查看
- **可调整可回退**：用户发现问题可以调整（修改）或回退（撤销），而非事前阻塞
- **全量可追溯**：所有 AI 产出统一归档，用户随时查看完整工作历史

## 两个核心场景

### 场景一：审查消息（异步通知 + 可回退）

```text
助理完成任务
  → 产出生效（代码已写入/实体已创建/文档已生成）
  → 创建 AiOutput 记录
  → 高风险项 → 推送通知消息给用户
  → 用户异步查看：
      ├── 忽略（默认，产出保持生效）
      ├── 调整（修改产出内容，助理协助）
      └── 回退（撤销操作，恢复到变更前状态）
```

### 场景二：AI 工作产出总览（随时查看）

```text
用户打开"AI 产出"页面
  → 时间线展示所有助理工作成果
  → 筛选：按来源/类型/风险/时间
  → 每条产出可展开查看详情（代码 diff / 实体变更 / 文档内容）
  → 高风险项标红，但不阻塞
  → 支持从任意产出发起"调整"或"回退"
```

## 架构总览

```text
AI 产出来源：
  ├── Auto-Dev（代码生成/文档生成）
  ├── 任务执行（业务实体 CRUD）
  └── 助理对话（知识库更新/文件创建）
         ↓ 统一记录
┌─────────────────────────────────────────────────────────────────┐
│  AiOutput（AI 产出记录）                                         │
│  来源 + 类型 + 风险等级 + 内容快照 + 回退信息                    │
│  状态：effective（生效中）/ adjusted（已调整）/ reverted（已回退） │
└──────────────────────────────┬──────────────────────────────────┘
         ↓                     ↓
┌──────────────────┐  ┌───────────────────────────────────────────┐
│  通知推送         │  │  AI 产出总览页面                           │
│  高风险 → 即时    │  │  时间线 + 筛选 + 详情展开                  │
│  中风险 → 摘要    │  │  代码 diff / 实体变更 / 文档预览           │
│  低风险 → 不推送  │  │  操作：调整 / 回退                        │
└──────────────────┘  └───────────────────────────────────────────┘
```

## 数据模型

### ai_output（AI 产出记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | 主键 |
| session_id | BIGINT | 关联会话 |
| task_id | BIGINT | 关联任务（可选） |
| execution_id | BIGINT | 关联执行实例（可选） |
| creator_id | BIGINT | 所属用户 |
| source_type | VARCHAR(30) | 来源：autodev / task / chat / tool |
| category | VARCHAR(30) | 类别：code / document / entity_change / config / file |
| risk_level | VARCHAR(10) | 风险：high / medium / low |
| title | VARCHAR(500) | 标题摘要 |
| description | TEXT | 描述（做了什么、为什么） |
| content_snapshot | JSONB | 内容快照 |
| revert_info | JSONB | 回退信息（变更前状态，用于撤销） |
| status | VARCHAR(20) | effective / adjusted / reverted |
| adjust_note | TEXT | 调整/回退说明 |

### content_snapshot 结构

```json
// 代码类
{
  "type": "code",
  "files": [
    {"path": "src/UserService.java", "diff": "...", "language": "java", "fullContent": "..."}
  ]
}

// 实体变更类
{
  "type": "entity_change",
  "entityType": "user",
  "entityId": 123,
  "operation": "UPDATE",
  "before": {"name": "旧值", "role": "user"},
  "after": {"name": "新值", "role": "admin"}
}

// 文档类
{
  "type": "document",
  "title": "用户模块设计文档",
  "format": "markdown",
  "content": "...",
  "path": "docs/design/user-module.md"
}
```

### revert_info 结构

```json
// 代码回退
{"type": "code", "files": [{"path": "src/UserService.java", "originalContent": "..."}]}

// 实体回退
{"type": "entity", "entityType": "user", "entityId": 123, "snapshot": {"name": "旧值", "role": "user"}}

// 文档回退
{"type": "document", "path": "docs/xxx.md", "originalContent": "..."}
```

## 风险分级（仅用于通知优先级，不阻塞）

| 风险 | 条件 | 通知方式 |
|------|------|---------|
| 🔴 高 | 删除 / 权限变更 / 配置修改 / ≥5 文件 | 即时推送 + 标红 |
| 🟡 中 | 新增接口 / 修改业务逻辑 / 实体字段变更 | 汇总摘要（每小时） |
| 🟢 低 | 加日志 / 修 typo / 文档补充 | 不推送，仅在总览中可见 |

## API 设计

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | /api/ai-outputs | 产出列表（分页，筛选） |
| GET | /api/ai-outputs/{id} | 产出详情（含完整 snapshot） |
| POST | /api/ai-outputs/{id}/adjust | 调整（传入修改内容，助理协助执行） |
| POST | /api/ai-outputs/{id}/revert | 回退（根据 revert_info 撤销） |
| GET | /api/ai-outputs/stats | 统计（今日产出数、各风险级别） |
| GET | /api/ai-outputs/recent | 最近产出（快速审查用，默认最近 24h） |

## 前端页面

### AI 产出总览

```text
┌─────────────────────────────────────────────────────────────────┐
│  🤖 AI 工作产出                    今日: 23 项 (🔴2 🟡8 🟢13)   │
├─────────────────────────────────────────────────────────────────┤
│  筛选: [全部] [代码] [文档] [实体变更] [高风险]   时间: [今天 ▼] │
├─────────────────────────────────────────────────────────────────┤
│  🔴 20:30  权限变更：用户 #123 角色 user → admin                │
│     来源: 任务 "批量更新用户权限" | [查看详情] [回退]            │
│                                                                  │
│  🟡 20:15  新增接口：POST /api/orders/refund (3 文件)           │
│     来源: Auto-Dev | [查看代码] [调整]                           │
│                                                                  │
│  🟢 19:50  文档更新：API 文档补充退款接口说明                    │
│     来源: 助理对话 | [查看]                                      │
│                                                                  │
│  🟢 19:30  ✅ 任务完成：生成周报                                 │
│     来源: 定时任务 | [查看]                                      │
└─────────────────────────────────────────────────────────────────┘
```

## 与现有架构整合

| 触发点 | 创建 AiOutput 的时机 |
|--------|---------------------|
| DurableTaskExecutor.execute() 完成 | 自动创建，category 根据任务类型判定 |
| Auto-Dev 代码生成完成 | 创建 code 类型，含 diff |
| 工具调用（实体 CRUD） | 创建 entity_change 类型，含 before/after |
| 文档生成/修改 | 创建 document 类型，含内容 |
| 通知系统 | 高风险项通过 NotificationWebSocket 推送 |

## 回退机制

```text
用户点击"回退"
  → 读取 revert_info
  → 根据 type 执行对应回退操作：
      code → 恢复文件原始内容
      entity → 恢复实体到变更前快照
      document → 恢复文档原始内容
  → 更新 AiOutput.status = 'reverted'
  → 记录回退事件到 TaskEvent
  → 通知助理（学习反馈）
```

## 相关文档

- [AI 长任务持久执行](task-durability.md)
- [五层智能架构](architecture.md)
