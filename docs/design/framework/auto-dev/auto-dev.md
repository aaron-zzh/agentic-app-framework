---
level: 2
version: 0.2.0
date: 2026-05-29
purpose: AI 协作开发——统一对话流驱动的全链路自动化开发 + 协作控制台
status: 草案
author: AaronZZH
changelog:
  - 2026-05-29 v0.2.0 | 重构：融合 AI 协作开发为核心主线，去掉冲突，统一两种开发模式
  - 2026-05-06 v0.1.0 | 初稿：协作控制台设计
---

# AI 协作开发设计（Auto-Dev）

> **一句话定位**：一个对话入口，AI 根据意图自动切换开发模式——对话式编码或 EntityDef 驱动的流程式开发。人 + AI 在同一个工作台上并肩工作。

## 定位与双重用户

Auto-Dev 同时服务两类用户，共享同一套实现：

| 用户 | 用它做什么 | 版本 |
|------|----------|------|
| AAF 作者 | 用 AAF 开发 AAF 自身（自举） | v0.1+ |
| AAF 框架用户 | 用 AAF 开发自己的业务应用 | v2.0+ |

核心思想：**AAF 用自己的能力开发自己**。作者的开发工作流就是框架用户未来的使用场景。

## 两种开发模式（统一入口，自动路由）

前端始终用同一个组件：

```tsx
<Chatter preset="kiro" layout="panel" />
```

**后端 Agent 根据意图自动路由**，用户无需手动切换：

```text
用户消息 → KiroAgent
  ↓
前注意分流（SkillMatchEngine，<50ms）
  │
  ├─ 匹配到结构化开发意图？
  │   触发词："创建模块/实体" "添加字段" "修改表结构" "生成代码"
  │   或：会话 state 中已有 currentEntityDef
  │   → Pipeline 模式（流程驱动，逐步确认）
  │
  └─ 其他意图
      "这个 bug 怎么修" "支付成功后触发积分入账" "优化性能"
      → 对话模式（Agent 自主规划+执行）
```

### 模式一：对话式开发

Agent 自主规划，适合复杂业务逻辑、bug 修复、性能优化：

```text
用户描述问题/需求
  → Agent 分析代码上下文
  → 生成/修改代码
  → 验证（编译+测试）
  → Git 提交到分支
  → 工作区显示 diff
```

### 模式二：EntityDef 驱动开发

Pipeline 自动执行，适合新建模块、CRUD 业务、表结构变更：

```text
用户描述实体需求
  → AI 生成 EntityDef JSON → 实时预览 → 确认
  → 自动生成迁移脚本 → 预览 SQL → 确认
  → 自动生成骨架代码 + AI 补充业务逻辑 → 预览 diff → 确认
  → 验证 + 部署
```

## EntityDef 三层模型

EntityDef 是视图抽象，不是表映射：

```text
视图层（config）         → "用户看到什么"：字段/表单/列表/子表
存储层（config.storage） → "数据怎么存"：表名/关联/索引
实现层（代码）           → "逻辑怎么跑"：校验/状态机/事件
```

一个 EntityDef 可以：
- 映射一张表的部分字段（视图裁剪）
- 映射多张表（主从关联）
- 不映射任何表（虚拟聚合视图）
- 映射到通用 JSONB 存储（无代码模式）

### storage 配置

```json
{
  "storage": {
    "mode": "typed",
    "table": "biz_order",
    "relations": [
      { "field": "items", "type": "oneToMany", "targetTable": "biz_order_item", "foreignKey": "order_id" }
    ],
    "indexes": [
      { "fields": ["user_id"], "condition": "deleted = FALSE" },
      { "fields": ["order_no"], "unique": true }
    ]
  }
}
```

| mode | 含义 | 适用场景 |
|------|------|---------|
| `typed` | 强类型 JPA 实体（需生成代码） | 复杂业务逻辑 |
| `generic` | 通用 JSONB（GenericEntityController） | 快速原型、配置类 |
| `virtual` | 不持久化，聚合多源只读视图 | 仪表盘、统计 |

## 全链路 Pipeline

### 阶段一：EntityDef 生成（AI 辅助）

输入：自然语言 / 已有表结构 / 参考文档

AI 推断字段（名称/类型/校验/关联）、视图配置（列表列/表单布局/子表）、存储映射建议。PreviewPanel 实时渲染表单效果，用户可在对话中修正。

### 阶段二：存储映射 & 迁移生成

MigrationGenerator 对比当前 DB schema（information_schema），生成增量 DDL。PreviewPanel 显示 SQL diff，确认后写入 `db/migration/`。

### 阶段三：代码生成 + AI 补充

CodegenService 模板生成骨架（Entity/Repo/Service/Controller），AI Enricher 分析字段语义补充校验、状态机、事件发布。PreviewPanel 显示代码 diff。

### 阶段四：验证 & 部署

编译 + 单测 + Lint。开发环境热加载，测试环境 CI，生产走 PR。失败时 AI 自动分析错误并修复。

## DevWorkspace 前端

```text
┌──────────────────┐  ┌──────────────────────────────┐
│  Chatter         │  │  PreviewPanel                │
│  (对话面板)       │  │  Tab: Preview | Code | Task  │
│                  │  │                              │
│  统一入口        │  │  · EntityDef 表单实时预览     │
│  AI 自动路由     │  │  · 生成代码 diff              │
│  + 确认/审批按钮  │  │  · 迁移脚本 SQL              │
│                  │  │  · Pipeline 进度条            │
└──────────────────┘  └──────────────────────────────┘
```

- Chatter 复用已有组件，无需新 preset
- PreviewPanel 根据 AG-UI 事件流中的 DevEvent 动态展示
- 有 EntityDef 预览事件时自动弹出预览面板
- 确认操作通过 AG-UI 的 CONFIRM_REQUIRED 事件交互

### AG-UI 事件流扩展

```text
DevEvent:
  ENTITY_DEF_PREVIEW   → PreviewPanel 渲染 FormView
  MIGRATION_PREVIEW    → PreviewPanel 显示 SQL
  CODE_PREVIEW         → PreviewPanel 显示 diff
  TASK_STATUS          → 进度条更新
  DEPLOY_LOG           → 日志流
  CONFIRM_REQUIRED     → 对话中插入确认按钮
```

## 协作控制台（观察 + 审核）

DevWorkspace 是创作入口，协作控制台是观察入口。两者共享任务状态和事件流。

### 核心功能

| 功能 | 说明 |
|------|------|
| 仪表板 | Epic 进度、活跃 Agent、任务泳道、审核队列 |
| 任务时间线 | 单 Task 的 Dispatch/Artifact/Activity 混合时间线 |
| 审核 Inbox | 🔴 高风险任务审核入口，批准/退回/降级 |
| Agent 中心 | 11 个角色状态、Run 历史、上下文占用 |
| 经验沉淀 | dev-log 片段一键晋升为 .kiro/skills/ |
| 规范健康 | 规范-代码一致性检查结果 |

### 数据模型（核心实体）

| 实体 | 说明 |
|------|------|
| `epic` | 用户故事，映射 backlog.md |
| `task` | 技术任务 `#N` |
| `agent_run` | Agent 一次执行（状态机：queued→dispatched→running→completed/failed/blocked） |
| `dispatch_log` | 派发记录（风险等级、agent 链、耗时） |
| `artifact` | 产出物索引（requirement/design/dev-log/test-report/review/audit） |
| `inbox_item` | 审核请求/blocker/完工汇报 |
| `activity_log` | 审计日志（Polymorphic Actor 贯穿） |

### 设计范式

- **Polymorphic Actor**：`actor_type ∈ {human, agent, system}` + `actor_id`
- **Session Resumption**：同一 (agent, task) 复用上次 session
- **置信度门控**：>0.9 自动执行 / 0.7-0.9 确认 / <0.7 转人工

## 三级决策模型

| 级别 | 条件 | 动作 |
|------|------|------|
| Auto | 新增文件、<50 行、补缺依赖 | 直接执行，异步通知 |
| Prompt | 修改已有文件、改业务逻辑、新增接口 | 展示计划，等待确认 |
| Block | 删除文件、改权限、≥5 文件跨模块 | 拒绝执行，必须人类审核 |

## Kiro Skills

每个 Pipeline 阶段对应一个 Skill，Agent 按意图匹配调用：

| Skill | 触发条件 | 输出 |
|-------|---------|------|
| `entity-def-generator` | "创建实体/模块" | EntityDef JSON |
| `migration-generator` | EntityDef 确认后 | SQL 迁移脚本 |
| `code-generator` | 迁移确认后 | Java 代码文件 |
| `ai-enricher` | 骨架生成后 | 补充业务逻辑的完整代码 |
| `sandbox-validator` | 代码确认后 | 编译/测试结果 |
| `hot-deployer` | 验证通过后 | 部署状态 |

## 与已有系统的集成

| 已有组件 | 集成方式 |
|---------|---------|
| Chatter | 统一入口，agentRole 由后端自动路由 |
| SkillMatchEngine | 前注意分流，匹配开发 Skill |
| EntityDefService | 扩展 storage 配置段 |
| GenericEntityController | generic 模式运行时 CRUD |
| CodegenService | 扩展子表/关联模板 |
| KiroAgentController | 注册开发 Skills |
| AG-UI 协议 | 扩展 DevEvent 事件类型 |
| ViewEngine | PreviewPanel 复用 FormView/ListView |

## 渐进落地

| 阶段 | 内容 | 版本 | 前置条件 |
|------|------|------|---------|
| P0 | EntityDef storage 配置 + MigrationGenerator | v0.2 | EntityDefService 已有 |
| P1 | CodegenService 子表支持 + AI Enricher | v0.3 | P0 |
| P2 | DevWorkspace 前端（PreviewPanel + CONFIRM 交互） | v0.4 | P1 + AG-UI 扩展 |
| P3 | 协作控制台 Phase 1（只读仪表板） | v0.4 | 文件扫描 |
| P4 | 协作控制台 Phase 2（DB 持久化 + 审核闭环） | v1.0 | 14 张表 + 状态机 |
| P5 | 热部署 + 自动修复循环 | v1.0 | 沙箱验证 |
| P6 | Autopilot + 完整 Pipeline 无人值守 | v2.0 | 框架元引擎化 |

## 与协作控制台的关系

```text
DevWorkspace（/dev）    ← 创作 + 执行（对话 + Pipeline + 预览）
协作控制台（/console）  ← 观察 + 审核（仪表板 + 时间线 + Inbox）
共享：任务状态 / AG-UI 事件流 / 置信度门控 / Polymorphic Actor
```
