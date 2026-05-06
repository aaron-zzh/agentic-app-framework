---
level: Practice
layer: Model
purpose: Auto Dev 在线开发监控需求规格
status: active
version: 1.0.0
date: 2026-05-03
author: AaronZZH
---

<!-- ⚠️ 早期需求，未经过六问分析。进入开发前由 product agent 补充需求分析章节 -->
<!-- scope_mode: hold -->

# Auto Dev 平台（AI 协作开发监控与管理）

任务编号：AAF-021

## 用户故事

### US-1：Auto Dev 在线开发监控

**作为** 框架开发者，**我希望** 通过 Web 页面实时监控 kiro-cli 的开发过程，并能触发多智能体代码生成，**以便** 与 kiro-cli 协作进行开发并实时掌握进度。

#### 验收标准

```gherkin
Feature: Auto Dev 在线开发监控

  Scenario: 触发多智能体代码生成
    Given 开发者在 Auto Dev 监控面板输入需求 "创建用户模块，字段：id、username、email、created_at"
    When 点击生成按钮
    Then 页面实时展示 Agent 执行状态（规划中 → 编码中 → 审查中 → 完成）
    And 规划 Agent 返回任务列表，包含实体、服务、控制器三个任务
    And 编码 Agent 生成 User.java、UserService.java、UserController.java
    And 审查 Agent 返回代码审查结果
    And 所有生成结果持久化到数据库

  Scenario: 监控 kiro-cli 执行过程
    Given kiro-cli 正在本地执行开发任务
    When kiro-cli 通过 API 上报执行事件和日志
    Then Web 页面通过 SSE 实时展示执行状态和日志内容

  Scenario: 查看执行记录
    Given 已有代码生成或 kiro-cli 执行记录
    When 打开 Auto Dev 监控面板
    Then 展示执行记录列表，包含输入、输出、状态、耗时
```

## 需求规格

### 功能描述

#### F1：多智能体代码生成

接收自然语言需求描述，依次调用三个 Agent 完成代码生成：

1. **规划 Agent**：需求 → 任务列表（模块名、实体字段、接口清单）
2. **编码 Agent**：单个任务 + 规范文档 → Java 代码（Entity / Service / Controller）
3. **审查 Agent**：生成代码 + 规范文档 → 问题列表 + 修改建议

三个 Agent 顺序执行，每步结果持久化，执行过程通过 SSE 实时推送。

#### F2：kiro-cli 执行监控

- kiro-cli 通过 REST API 上报执行事件（开始/进行中/完成/失败）和日志
- 后端持久化日志，通过 SSE 推送给 Web 前端
- Web 前端实时展示执行状态和日志流

### 数据模型

**autodev_request**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigserial PK | 主键 |
| requirement | text | 需求描述 |
| status | varchar(20) | pending / running / success / failed |
| planning_result | jsonb | 规划结果 |
| review_result | jsonb | 审查结果 |
| created_at | timestamp | 创建时间 |
| completed_at | timestamp | 完成时间 |

**autodev_generated_code**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigserial PK | 主键 |
| request_id | bigint FK | 关联生成请求 |
| file_path | varchar(500) | 文件路径 |
| content | text | 代码内容 |
| created_at | timestamp | 创建时间 |

**autodev_execution_log**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigserial PK | 主键 |
| request_id | bigint | 关联请求（kiro-cli 日志可为空） |
| source | varchar(50) | agent / kiro-cli |
| agent | varchar(50) | planning / coding / review |
| input | text | 输入内容 |
| output | text | 输出内容 |
| status | varchar(20) | running / success / failed |
| duration_ms | bigint | 耗时（毫秒） |
| created_at | timestamp | 创建时间 |

### 接口定义

**POST /api/auto-dev/generate** — 触发代码生成

**GET /api/auto-dev/requests** — 查询生成请求列表

**GET /api/auto-dev/requests/{id}** — 查询单次生成详情

**POST /api/monitor/events** — kiro-cli 上报执行事件

**POST /api/monitor/logs** — kiro-cli 上报执行日志

**GET /api/monitor/stream** — SSE 实时事件流

### 约束

- 需求描述长度：10 ~ 500 字符
- 单次生成最多 10 个代码文件
- LLM 调用超时：60 秒
- 不支持并发生成，同一时间只处理一个生成请求
- 生成代码仅供参考，需人工确认后手动复制到目标模块

## 相关设计

- 迭代架构设计：[后端技术选型](../../design/apps/service/tech-stack.md)（kiro-cli 协作接口、Auto Dev 模块结构）
- Actor 模型设计：[actor-model.md](../../design/framework/core/actor-model.md)

---

## 子故事

### US-3：任务调度引擎

**作为** 框架开发者，**我希望** Auto Dev 平台能管理编程智能体的任务队列，支持状态流转、并发控制和超时回收，**以便** 多个 kiro-cli 实例能被可靠地编排调度。

**范围**：

- 状态流转：queued → dispatched → running → completed / failed / cancelled
- 并发策略：skip（跳过）/ queue（排队）/ replace（替换）
- 超时孤儿回收：Sweeper 定时扫描，超时任务标记 failed
- Session Resumption：同一 agent+issue 自动复用 session_id + 工作目录
- Autopilot 触发：cron 定时 / webhook / API 三种触发 × create_issue / run_only 两种执行模式

### US-4：Agent Skill 知识沉淀

**作为** 框架开发者，**我希望** Agent 执行任务后能自动沉淀可复用的经验模式，下次遇到类似任务时自动注入，**以便** Agent 越用越聪明。

**范围**：

- 技能格式规范（输入/输出/前置条件/副作用）
- 经验提取：从执行历史中识别可复用模式
- 知识注入：按任务上下文动态加载相关技能
- 版本管理与淘汰策略

### US-5：多 Agent 并行隔离

**作为** 框架开发者，**我希望** 多个编程智能体能并行工作互不干扰，**以便** 提升开发效率。

**范围**：

- 每 worktree 独立 `.env` + 独立数据库名 + 独立端口
- 共享 PostgreSQL 容器
- 动态 Profile 命名（slug+hash）
- 工作空间自动创建与回收

### US-6：Assistant Skill 系统

**作为** 框架开发者，**我希望** Assistant 层能注册、发现和编排技能，根据用户意图自动匹配合适的 Skill 并调度 Agent 执行，**以便** 实现智能化的任务分发。

**范围**：

- Skill 定义规范（输入/输出/前置条件/副作用）
- Skill 注册表（按领域索引）
- 动态加载（按意图上下文匹配可用 Skill）
- Skill 组合（多 Skill 编排为复合能力）
- Agent 调度（Assistant 选定 Skill 后派发 Agent 执行）
- 多 host 适配（同一 Skill 定义跑遍多种编程 agent，v0.2+）
