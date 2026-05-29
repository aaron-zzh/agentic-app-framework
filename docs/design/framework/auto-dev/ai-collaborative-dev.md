---
level: Practice
layer: Model
purpose: AI 协作开发——EntityDef 驱动的全链路自动化开发设计
status: draft
version: 0.1.0
date: 2026-05-29
author: AaronZZH
---

# AI 协作开发设计

> EntityDef 是视图抽象，不是表映射。AI 参与每个环节：生成定义 → 映射存储 → 生成代码 → 补充业务 → 验证部署。两种模式统一到一个对话流中。

## 核心认知

### EntityDef 的三层含义

```text
┌─────────────────────────────────────────────────────────┐
│  视图层（EntityDef.config）                              │
│  "用户看到什么"——字段/表单/列表/看板/子表                │
│  一个 EntityDef 可以：                                   │
│  · 映射一张表的部分字段（视图裁剪）                      │
│  · 映射多张表（主从关联）                                │
│  · 不映射任何表（虚拟聚合视图）                          │
│  · 映射到通用 JSONB 存储（无代码模式）                   │
└──────────────────────┬──────────────────────────────────┘
                       │ storage 配置
┌──────────────────────▼──────────────────────────────────┐
│  存储层（EntityDef.config.storage）                      │
│  "数据怎么存"——表名/关联/外键/索引                       │
│  mode: typed | generic | virtual                        │
└──────────────────────┬──────────────────────────────────┘
                       │ 代码生成 / 通用 CRUD
┌──────────────────────▼──────────────────────────────────┐
│  实现层（Java 代码 / GenericEntityController）           │
│  "逻辑怎么跑"——校验/状态机/事件/权限                    │
└─────────────────────────────────────────────────────────┘
```

### storage 配置设计

```json
{
  "storage": {
    "mode": "typed",
    "table": "biz_order",
    "primaryKey": "id",
    "relations": [
      {
        "field": "items",
        "type": "oneToMany",
        "targetTable": "biz_order_item",
        "targetEntity": "biz_order_item",
        "foreignKey": "order_id",
        "cascade": ["persist", "remove"]
      }
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
| `typed` | 映射到强类型 JPA 实体（需生成代码） | 有复杂业务逻辑的核心模块 |
| `generic` | 映射到通用 JSONB 存储（GenericEntityController） | 快速原型、配置类实体 |
| `virtual` | 不持久化，聚合多源数据的只读视图 | 仪表盘、统计视图 |

## 两种开发模式的统一

### 统一对话流

两种模式不是割裂的，而是同一个 AI 对话流中的不同路径：

```text
用户发起对话（Chatter preset="kiro"）
  │
  ├─ "帮我创建一个订单模块"          → 模式一：流程驱动
  │   AI 引导：需要哪些字段？有子表吗？
  │   → 生成 EntityDef
  │   → 确认存储映射
  │   → 自动执行 Pipeline
  │   → 工作区实时预览
  │
  └─ "订单支付成功后要触发积分入账"   → 模式二：对话驱动
      AI 分析：这是业务逻辑补充
      → 定位到 RechargeService
      → 生成/修改代码
      → 提交到分支
      → 工作区显示 diff
```

### 前端组件架构

```text
┌─────────────────────────────────────────────────────────┐
│  DevWorkspace（开发工作区）                               │
│  ┌──────────────────┐  ┌──────────────────────────────┐ │
│  │  DevChatter      │  │  PreviewPanel                │ │
│  │  (对话面板)       │  │  (实时预览)                   │ │
│  │                  │  │                              │ │
│  │  preset="kiro"   │  │  · EntityDef 表单预览         │ │
│  │  agentRole=      │  │  · 生成代码 diff              │ │
│  │    "auto-dev"    │  │  · 迁移脚本预览               │ │
│  │                  │  │  · 任务状态看板               │ │
│  │  AI 消息流       │  │  · 部署日志                   │ │
│  │  + 工具调用可视化 │  │                              │ │
│  │  + 确认/审批按钮  │  │  Tab: Preview | Code | Task  │ │
│  └──────────────────┘  └──────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

基于已有的 `Chatter` 组件扩展：
- `preset="kiro"` + `agentRole="auto-dev"` 路由到开发 Agent
- `PreviewPanel` 是新组件，通过 AG-UI 事件流接收 Agent 产出物实时渲染
- 工作区布局参考 `(workspace)/dev/` 已有的开发工具页面

## 全链路 Pipeline

### 阶段一：EntityDef 生成（AI 辅助）

```text
输入：用户自然语言描述 / 已有表结构 / 参考文档
  ↓
AI 分析意图，生成 EntityDef JSON：
  · 字段推断（名称/类型/校验/关联）
  · 视图配置（列表列/表单布局/子表）
  · 存储映射建议（新建表 or 映射已有表）
  ↓
输出：EntityDef JSON（待确认）
  → PreviewPanel 实时渲染表单/列表效果
  → 用户可在对话中修正："把金额改成分为单位"
```

后端实现为 Kiro Skill：

```yaml
# .kiro/skills/entity-def-generator/SKILL.md
name: entity-def-generator
description: 根据自然语言描述生成 EntityDef JSON
triggers:
  - "创建实体"
  - "新建模块"
  - "定义表结构"
tools:
  - entity_def_api      # 读取已有实体定义
  - db_schema_reader    # 读取已有表结构
  - codegen_preview     # 预览生成效果
```

### 阶段二：存储映射 & 迁移生成

```text
输入：确认后的 EntityDef（含 storage 配置）
  ↓
MigrationGenerator：
  · 对比当前 DB schema（通过 information_schema）
  · 生成增量 DDL（CREATE TABLE / ALTER TABLE）
  · 处理关联表（子表外键/索引）
  · 生成 Flyway 版本号
  ↓
输出：SQL 迁移脚本（待确认）
  → PreviewPanel 显示 SQL diff
  → 确认后写入 db/migration/
```

### 阶段三：代码生成（模板 + AI 补充）

```text
输入：EntityDef + storage mapping
  ↓
CodegenService（模板生成骨架）：
  · Entity.java（含 @OneToMany 子表关联）
  · Repository.java
  · Service.java（CRUD 骨架）
  · Controller.java
  · VO/DTO
  ↓
AI Enricher（补充业务逻辑）：
  · 分析字段语义 → 补充校验注解
  · 分析关联关系 → 补充级联操作
  · 分析业务上下文 → 补充状态机/事件发布
  · 参考已有模块模式 → 保持一致性
  ↓
输出：完整代码文件（待确认）
  → PreviewPanel 显示代码 diff
  → 确认后写入源码目录
```

### 阶段四：验证 & 部署

```text
输入：生成的代码 + 迁移脚本
  ↓
SandboxValidator：
  · 编译检查（mvn compile）
  · 单测生成 & 执行
  · Lint 检查
  ↓
部署策略（按环境）：
  · 开发环境：热加载（Spring DevTools / JRebel）
  · 测试环境：Git commit → CI → 自动部署
  · 生产环境：Git → PR → 人工审核 → 合并 → CD
  ↓
输出：部署结果
  → PreviewPanel 显示任务状态
  → 失败时 AI 自动分析错误并修复
```

## Kiro Skills 定义

每个阶段对应一个 Kiro Skill，后端 Agent 按需调用：

| Skill | 触发条件 | 输入 | 输出 |
|-------|---------|------|------|
| `entity-def-generator` | "创建实体/模块" | 自然语言描述 | EntityDef JSON |
| `migration-generator` | EntityDef 确认后 | EntityDef + 当前 schema | SQL 迁移脚本 |
| `code-generator` | 迁移确认后 | EntityDef + storage | Java 代码文件 |
| `ai-enricher` | 骨架代码生成后 | 骨架代码 + 业务上下文 | 补充后的完整代码 |
| `sandbox-validator` | 代码确认后 | 代码文件列表 | 编译/测试结果 |
| `hot-deployer` | 验证通过后 | 变更文件列表 | 部署状态 |

## 工作区实时预览

### AG-UI 事件流扩展

利用已有的 AG-UI 协议（SSE 事件流），扩展开发专用事件：

```typescript
// 开发事件类型
type DevEvent =
  | { type: "ENTITY_DEF_PREVIEW"; data: EntityDef }
  | { type: "MIGRATION_PREVIEW"; data: { sql: string; tables: string[] } }
  | { type: "CODE_PREVIEW"; data: { files: GeneratedFile[] } }
  | { type: "TASK_STATUS"; data: { phase: string; status: string; progress: number } }
  | { type: "DEPLOY_LOG"; data: { line: string; level: string } }
  | { type: "CONFIRM_REQUIRED"; data: { id: string; title: string; options: string[] } }
```

### PreviewPanel 组件

```typescript
interface PreviewPanelProps {
  /** 当前活跃的预览 Tab */
  activeTab: "preview" | "code" | "migration" | "task"
  /** AG-UI 事件流中的开发事件 */
  events: DevEvent[]
}
```

Tab 内容：
- **Preview**：EntityDef 驱动的 FormView/ListView 实时渲染
- **Code**：生成代码的 Monaco Editor diff 视图
- **Migration**：SQL 语法高亮 + 表结构变更图
- **Task**：Pipeline 阶段进度条 + 日志流

## 与已有系统的集成点

| 已有组件 | 集成方式 |
|---------|---------|
| `Chatter`（对话组件） | 新增 `agentRole="auto-dev"` 路由 |
| `EntityDefService`（后端） | 扩展 storage 配置段 |
| `GenericEntityController` | generic 模式的运行时 CRUD |
| `CodegenService` | 扩展子表/关联模板 |
| `KiroAgentController` | 注册开发 Skills |
| `AG-UI 协议` | 扩展 DevEvent 事件类型 |
| `ViewEngine`（前端） | PreviewPanel 复用 FormView/ListView |

## 实现优先级

| 阶段 | 内容 | 版本 |
|------|------|------|
| P0 | EntityDef storage 配置 + MigrationGenerator | v0.2 |
| P1 | CodegenService 子表支持 + AI Enricher Skill | v0.3 |
| P2 | DevWorkspace 前端（Chatter + PreviewPanel） | v0.4 |
| P3 | 热部署 + 自动修复循环 | v0.5 |
| P4 | 完整 Pipeline 自动化（无人值守模式） | v0.6 |

## 与协作控制台的关系

协作控制台（已有设计）是**观察和审核**入口，DevWorkspace 是**创作和执行**入口：

```text
协作控制台：看进度 / 审核决策 / 沉淀经验（只读为主）
DevWorkspace：对话创作 / 实时预览 / 确认执行（读写）
```

两者共享：
- 任务状态数据（`agent_run` / `dispatch_log`）
- AG-UI 事件流
- 置信度门控机制（🔴 高风险操作统一进审核队列）
