---
level: Practice
layer: Model
purpose: 协作开发功能需求规格（文档编辑 + Kiro Agent 交互）
status: active
version: 2.0.0
date: 2026-05-22
author: AaronZZH
changelog:
  - 2026-05-22 | v2.0 重写：从"聊天协作界面"调整为"协作开发功能"，整合文档管理（AAF-019）+ Kiro Agent 运行时
  - 2026-05-03 | v1.0 初始版本（聊天协作界面）
---

# 协作开发功能

任务编号：AAF-020

## 背景

AAF-019 已完成文档管理系统（`doc_document` / `doc_link` 表，`/workspace/docs` 页面，React Flow 关系图谱）。本功能在此基础上扩展两个核心能力：

1. **文档协作编辑**：在 `/workspace/docs` 页面增强为"左侧大纲层级 + 右侧 React Flow 图谱"的多层级可视化编辑界面，支持实时文档变更通知，支持本地文档同步（双向：本地 → 系统、系统 → 本地）。
2. **Kiro Agent 运行时**：通过 AG-UI 协议（live-chatter 已有基础设施）与 kiro-cli 交互，用户在弹窗聊天界面发送指令，后端通过脚本调用 kiro-cli 执行，实时将输出推送给用户。支持指定 agent 角色执行。

## 用户故事

### US-1：多层级文档可视化编辑

**作为** 框架开发者，**我希望** 在文档页面左侧看到大纲层级树、右侧看到文档关系图谱，并能在线编辑文档内容，修改后同步到本地文件，**以便** 在一个界面内完成文档的浏览、编辑和关系理解。

#### 验收标准

```gherkin
Feature: 多层级文档可视化编辑

  Scenario: 左侧大纲层级展示
    Given 打开 /workspace/docs 页面
    When 页面加载完成
    Then 左侧展示文档目录树（按 spec/design/task/guide/reference/explanation 分类）
    And 支持多级折叠展开
    And 点击文档节点在右侧展示内容

  Scenario: 右侧 React Flow 关系图谱
    Given 选中一篇文档
    When 切换到"关系图"Tab
    Then 右侧展示以该文档为中心的 React Flow 关系图（深度 ≤ 2）
    And 点击图中节点切换到对应文档

  Scenario: 弹窗编辑文档
    Given 正在查看一篇文档
    When 点击"编辑"按钮
    Then 弹出编辑弹窗，展示 Markdown 内容
    And 保存后内容更新到数据库并同步写回本地 docs/ 对应文件

  Scenario: 新建文档
    Given 在文档树中选择一个目录
    When 点击"新建文档"按钮
    Then 弹出新建弹窗，填写标题和路径
    And 创建后在本地 docs/ 目录生成对应 .md 文件
    And 文档树自动刷新显示新文档

  Scenario: 实时文档变更通知
    Given 用户 A 正在查看某文档
    When 后端（或 Kiro Agent）修改了该文档
    Then 前端通过 SSE 接收变更事件
    And 文档内容自动刷新，提示"文档已更新"
```

### US-2：Kiro Agent 运行时交互

**作为** 框架开发者，**我希望** 通过聊天弹窗向 kiro-cli 发送开发指令，指定 agent 角色执行，并实时看到执行输出，**以便** 在 Web 界面直接驱动 AI 完成开发任务。

#### 验收标准

```gherkin
Feature: Kiro Agent 运行时交互

  Scenario: 打开 Kiro Agent 聊天弹窗
    Given 用户在 /workspace/docs 页面
    When 点击右下角"Kiro Agent"悬浮按钮
    Then 弹出 live-chatter 聊天弹窗（drawer 模式，基于 AgUiChatProvider）
    And 弹窗顶部显示当前 agent 角色选择器

  Scenario: 发送指令给 Kiro Agent
    Given 聊天弹窗已打开，选择了 agent 角色（如 developer-service）
    When 用户输入"帮我在 AAF-020 中新增一个用户故事"并发送
    Then 后端通过脚本调用 kiro-cli，传入指令和 agent 角色
    And 执行输出通过 AG-UI SSE 事件流实时推送到前端
    And 前端逐字渲染 kiro-cli 的响应内容

  Scenario: 指定 agent 角色执行
    Given 聊天弹窗顶部有 agent 角色下拉选择器
    When 用户选择"architect"角色
    Then 后续发送的指令以 architect 角色调用 kiro-cli
    And 响应内容体现 architect 的专业视角

  Scenario: Kiro Agent 修改文档后通知
    Given Kiro Agent 执行了修改文档的操作
    When 文档内容被更新
    Then 后端发送文档变更 SSE 事件
    And 文档编辑区自动刷新显示最新内容
    And 聊天窗口显示"已更新文档：{文档标题}"
```

## 需求规格

### 功能描述

**文档协作编辑**：
- 复用 AAF-019 的 `doc_document` 表和 `DocumentController` 接口
- 新增 `POST /api/docs` 接口（新建文档，写入本地文件 + 数据库）
- 新增 `GET /api/docs/events` SSE 端点（文档变更实时推送）
- 前端 `/workspace/docs` 页面重构：左侧大纲树 + 右侧内容/图谱 Tab

**Kiro Agent 运行时**：
- 后端在 `aaf-auto-dev` 模块新建 `KiroAgentController`，实现 AG-UI 协议端点 `/api/autodev/kiro/run`
- 通过 `ProcessBuilder` 调用本地 kiro-cli 脚本，将 stdout 转为 AG-UI SSE 事件流
- 新建 `autodev_session` 表记录 kiro 会话状态（sessionId、agent 角色、状态、创建时间）
- 前端复用 `AgUiChatProvider`，配置端点为 `/api/autodev/kiro/run`，以 drawer 模式嵌入文档页面

### 数据模型

**autodev_session**（新增，在 `aaf-auto-dev` 模块管理）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | 主键 |
| session_id | varchar(64) | kiro-cli 会话标识 |
| agent_role | varchar(50) | agent 角色（kiro_default/product/architect/developer-service 等） |
| status | varchar(20) | active/completed/failed |
| user_id | bigint | 操作用户 ID |
| create_time | timestamp | 创建时间 |
| update_time | timestamp | 更新时间 |

> 说明：不复用 `doc_document` 表，职责不同。`autodev_session` 记录 kiro 执行会话，`doc_document` 记录文档内容。

### 接口定义

**文档协作编辑（复用 AAF-019 + 新增）**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/docs/tree | 获取文档树（已有） |
| GET | /api/docs/{id} | 获取文档详情（已有） |
| PUT | /api/docs/{id} | 更新文档（已有，同步写本地文件） |
| POST | /api/docs | 新建文档（新增，写本地文件 + 数据库） |
| POST | /api/docs/import | 触发全量导入（已有） |
| GET | /api/docs/{id}/relations | 获取关系图数据（已有） |
| GET | /api/docs/search | 全文检索（已有） |
| GET | /api/docs/events | SSE 文档变更事件流（新增） |

**Kiro Agent 运行时（新增，在 aaf-auto-dev 模块）**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/autodev/kiro/run | AG-UI 协议端点，启动 kiro-cli 执行（SSE 流式响应） |
| GET | /api/autodev/kiro/agents | 获取可用 agent 角色列表 |
| GET | /api/autodev/kiro/sessions | 获取历史会话列表 |

### 技术方案

**Kiro Agent 运行时实现方式**：

采用 AG-UI 协议（与现有 `AgUiChatController` 一致），在 `aaf-auto-dev` 模块实现：

```
前端 AgUiChatProvider（drawer 模式）
    ↓ POST /api/autodev/kiro/run（AG-UI 格式请求）
KiroAgentController（aaf-auto-dev）
    ↓ ProcessBuilder 调用 kiro-cli
    ↓ 读取 stdout/stderr
    ↓ 转为 AG-UI SSE 事件（TEXT_MESSAGE_CONTENT / RUN_FINISHED）
前端实时渲染
```

选择 AG-UI 而非模拟内部用户的理由：
- live-chatter 已有完整的 `AgUiChatProvider` 实现，直接复用
- AG-UI 协议标准化，前端无需额外适配
- 比 WebSocket 多用户聊天更适合单用户 AI 交互场景

**文档变更通知**：
- 后端维护一个 `SseEmitter` 注册表（按文档 ID 分组）
- 文档保存时（`DocumentService.update()`）广播变更事件
- Kiro Agent 修改文档后同样触发广播

### 约束

- Kiro Agent 运行时仅在本地开发环境使用，不对外暴露
- kiro-cli 调用超时：5 分钟
- 文档变更 SSE 仅推送给当前打开该文档的用户
- 新建文档路径必须在 `docs/` 目录下，防止路径穿越
- `autodev_session` 表在 `aaf-auto-dev` 模块管理，通过 Flyway 迁移创建

## 相关设计

- AAF-019 文档管理：[requirement.md](../AAF-019/requirement.md)
- 协作控制台设计：[auto-dev.md](../../../design/framework/auto-dev/auto-dev.md)
- Auto-Dev 模块设计：auto-dev-design.md（待建）
- live-chatter 实现：`apps/webui/src/features/livechat/`
