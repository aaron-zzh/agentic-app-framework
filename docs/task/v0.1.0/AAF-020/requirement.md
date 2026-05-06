---
level: Practice
layer: Model
purpose: 聊天协作界面需求规格
status: active
version: 1.0.0
date: 2026-05-03
author: AaronZZH
---

<!-- ⚠️ 早期需求，未经过六问分析。进入开发前由 product agent 补充需求分析章节 -->
<!-- scope_mode: hold -->

# 聊天协作界面

任务编号：AAF-020

## 用户故事

### US-1：与 AI 对话协作

**作为** 框架开发者，**我希望** 通过聊天界面与 AI 进行多轮对话，AI 能直接读取和修改项目文档，**以便** 在对话中完成文档编写、需求梳理等协作任务。

#### 验收标准

```gherkin
Feature: AI 对话协作

  Scenario: 流式对话
    Given 用户在聊天界面输入消息
    When 发送消息
    Then AI 以流式方式逐字返回响应，无需等待完整回复

  Scenario: AI 读取文档
    Given 项目中存在文档 docs/design/architecture.md
    When 用户发送 "帮我总结一下架构设计文档"
    Then AI 通过 Tool 读取该文档内容
    And 返回基于文档内容的摘要

  Scenario: AI 修改文档
    Given 用户发送 "在需求文档中添加一个用户故事：作为管理员，我希望能批量导出用户数据"
    When AI 确认修改意图
    Then AI 通过 Tool 更新对应文档内容
    And 文档变更通过 SSE 实时推送到编辑器
    And 编辑器内容自动刷新展示最新内容
```

### US-2：文档实时协同编辑

**作为** 框架开发者，**我希望** 在聊天界面旁边实时查看和编辑文档，AI 的修改和我的修改都能即时同步，**以便** 在对话过程中直接看到文档变化结果。

#### 验收标准

```gherkin
Feature: 文档实时协同编辑

  Scenario: 查看 AI 修改结果
    Given 聊天界面右侧打开了一篇文档
    When AI 通过 Tool 修改了该文档
    Then 编辑器通过 SSE 接收变更事件
    And 文档内容实时更新，高亮显示变更部分

  Scenario: 手动编辑并保存
    Given 用户在 Lexical 编辑器中修改文档内容
    When 点击保存
    Then 内容保存到数据库并同步到本地文件
    And 聊天上下文感知到文档已更新
```

## 需求规格

### 功能描述

- **流式对话**：基于 Spring AI 的 SSE 流式输出，前端逐字渲染
- **AI Tool 调用**：AI 可调用文档读取、文档写入两个 Tool，直接操作文档系统
- **实时推送**：文档变更事件通过 WebSocket/SSE 推送到前端编辑器
- **Lexical 编辑器**：前端使用 Lexical 富文本编辑器，支持 Markdown 渲染和编辑

### 接口定义

**POST /api/chat/message** — 发送消息（SSE 流式响应）

**GET /api/chat/history** — 获取对话历史

**GET /api/docs/events** — SSE 文档变更事件流（编辑器订阅）

### 约束

- 单次对话上下文最多保留最近 20 条消息
- AI Tool 修改文档前需在对话中说明修改意图，不静默修改
- 文档变更推送仅针对当前打开的文档

## 相关设计

- 迭代架构设计：[后端技术选型](../../design/apps/service/tech-stack.md)