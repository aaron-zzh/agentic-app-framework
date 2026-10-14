---
level: Practice
layer: Product
purpose: 定义 AAF-119 实时通道收敛：保留 WebSocket、一条全局 SSE、AG-UI Run SSE，并入异步任务事件，移除重复推送路径
status: draft
version: 1.0.0
date: 2026-10-14
author: AaronZZH & Kiro
tags:
  - AAF-119
  - SSE
  - WebSocket
  - 全局事件通道
  - Activity
related:
  - design.md
  - tasks.md
  - ../../aaf-v0.13.md
  - ../AAF-113/requirement.md
  - ../../../design/apps/webui/tech-design/realtime-data-strategy.md
---

# AAF-119 实时通道收敛与全局事件通道

## 背景

v0.13 已冻结"AG-UI Run SSE + 用户级 Activity SSE"双通道边界（见 [aaf-v0.13.md](../../aaf-v0.13.md#实时事件与双通道边界)）。落地后核对代码，发现通道数量已超出规划：

- 全局 Activity SSE 的端点、表、信封、前端 Provider 都带 AIGC 前缀，信封里写死 `projectId`、`executionRunId`、`mediaVersionId` 等 AIGC 关联字段，其他模块无法复用。
- 前端 `ProjectActivityProvider` 只挂在 `StudioLayout`，workspace 布局没有全局事件连接。
- `/api/async-tasks/events` 走 `SseSessionManager`：只存内存、没有补发、多实例会丢消息；前端 `EntityActions` 每次操作都新建一条 `EventSource`。
- 通知同时存在 WebSocket 与 `SseSessionManager` 两条传输（`InternalMessageSenderImpl` 的 `SSE_ONLY / ALL` 策略），属于并行抽象。
- 前端 `use-export-progress.ts` 请求 `/api/{entity}/export-progress/{taskId}`，后端未发现对应端点；`use-notification-ws.ts` 与 `NotificationDrawer` 重复，且无调用方。

## 目标

收敛为三类通道，每类职责单一：

| 通道 | 职责 | 生命周期 |
|------|------|----------|
| WebSocket | 通知推送、用户间对话（IM / 客服）、语音与实时多模态、画布协同、在线状态 | 按功能连接 |
| 全局事件 SSE | 服务端到当前用户的变化通知：AIGC 任务与项目、异步任务状态等；事件落库，支持游标补发 | 登录后全应用一条 |
| AG-UI Run SSE | 助理对话的单次 Run 正文与工具调用 | 单次 Run |

全局事件 SSE 使用中性命名，不带任何业务模块前缀；各业务模块通过统一发布入口往里发事件，用事件类型前缀区分来源。

## 用户故事

### 页面在任意布局都能感知后台变化

**作为**用户，**我希望**无论在 Studio 还是 workspace 页面，后台任务（生成、导入导出等）完成后页面都能自动刷新，**以便**不用手动刷新也不会错过结果。

### 断线后不丢事件

**作为**用户，**我希望**网络抖动或后端重启后，重连会补发错过的事件，**以便**页面状态始终与服务端一致。

### 业务模块用统一入口推送

**作为**开发者，**我希望**新模块只需调用统一的事件发布入口并在前端订阅事件类型前缀，**以便**不再为每个模块新建推送通道。

## 验收标准

```gherkin
Feature: 全局事件通道收敛

  Scenario: 全局事件通道使用中性端点
    Given 已登录用户
    When 前端连接 GET /api/events/stream
    Then 获得 text/event-stream 响应
    And 路径、类、表和前端模块名中不含 aigc 前缀
    And /api/aigc/events/stream 与 /api/async-tasks/events 不再存在

  Scenario: AIGC 事件经全局通道到达
    Given 用户在项目中发起一次图片生成
    When 任务完成并产生终态事件
    Then 全局通道收到类型以 aigc. 开头的事件
    And 事件通过 refs 携带 projectId、taskId、executionRunId 等关联
    And 项目页面按 projectId 失效并重新查询权威状态

  Scenario: 异步任务终态经全局通道到达
    Given 用户在列表页触发一个异步任务（如批量导入）
    When 任务在工作线程中完成
    Then 全局通道收到类型以 async-task. 开头的事件
    And 提交任务的页面据此刷新，无需再为该操作新建 EventSource

  Scenario: 异步任务事件不受提交时组织与工作区上下文影响
    Given 用户在组织 A 的工作区 W 下提交异步任务
    And 工作线程执行时没有组织上下文
    When 任务进入终态
    Then 用户在组织 A 工作区 W 下的连接收到该事件
    And 用户切换到其他组织后的连接不收到带组织范围的事件，但仍收到用户级事件

  Scenario: 断线后按游标补发
    Given 前端已处理到事件 id=12
    When 连接中断期间产生事件 13 到 15
    And 前端以 after=12 重连
    Then 依次收到事件 13 到 15
    And 已处理过的事件不会重复触发刷新

  Scenario: 单页面只建立一条全局连接
    Given 用户登录后打开 workspace 页面，再进入 Studio 页面
    When 查看网络请求
    Then 同一时刻只有一条全局事件连接
    And 异步任务操作不再新建 EventSource

  Scenario: 只能订阅自己的事件
    Given 用户 A 和用户 B 各有事件
    When 用户 A 连接全局通道
    Then 只收到属于 A 的事件
    And 请求中带不属于 A 的 orgId 或 workspaceId 被拒绝

  Scenario: 通知只走 WebSocket
    Given 系统发出一条审批通知
    When 通知被发送
    Then 通知写库并通过 /ws/notifications 推送
    And 不再经 SSE 推送，InternalMessage 不再有 SSE_ONLY 策略

  Scenario: 不保留重复通道与死代码
    Given 本故事完成
    When 搜索代码库
    And 不存在 AsyncTaskController 与 /api/async-tasks/events
    And SseSessionManager 只作为全局事件通道的 SSE 传输层存在，没有其他业务调用方
    And 不存在 use-notification-ws.ts
    And 不存在指向无后端端点的 export-progress 前端调用
    And 不存在 aigc_activity_event 表与 AigcActivityEvent* 类

  Scenario: WebSocket 与 AG-UI 行为不变
    Given 本故事完成
    When 使用通知、IM、语音识别、助理对话
    Then 行为与改动前一致
```

## 范围

- 全局事件通道泛化：中性端点、通用事件表与信封、统一发布入口、订阅范围语义、游标补发。
- AIGC 全部事件迁移到通用入口，事件类型加 `aigc.` 前缀。
- 异步任务状态事件并入全局通道，删除 `/api/async-tasks/events` 与 `AsyncTaskController`；`SseSessionManager` 改造为全局事件通道的 SSE 传输层，不再另起一套连接管理。
- 通知移除 SSE 传输分支，精简 `InternalMessage.PushStrategy`。
- 前端全局事件 Provider、按类型前缀订阅、挂载到 workspace 与 Studio 两个布局；`EntityActions` 改用全局订阅。
- 清理死代码：导出进度 Hook、重复的通知 WebSocket Hook。
- 同步受影响文档中的端点与命名。

## 不在范围

- WebSocket 端点与协议改动（`/ws/notifications`、`/ws/im`、`/ws/asr`、`/ws/omni-realtime`、画布、在线状态）。
- AG-UI Run SSE 与 `/api/agui/run` 合同。
- `/autodev/docs/events`（autodev 开发工具）与 OCR 流式识别、客服公开流：保持原样。
- 多实例之间的事件总线（沿用 heartbeat 从库补发的最终一致，延迟最多一个心跳周期）。
- 事件内容扩展、事件过期清理策略（另立故事）。

## 约束

- 项目未发布：直接替换，不保留旧端点、旧表、旧类，不加 fallback 或兼容层。
- 涉及跨模块、接口路径、数据模型和已冻结的 v0.13 文档，设计经人工审核通过后才能编码。
- SQL 直接修改现有 `v1__system_schema.sql`、`v7__aigc_schema.sql`、`v15__async_task.sql`（如需），不新增迁移文件。
- 事件只做变化通知，不携带业务权威数据；前端收到事件后重新查询，保持 TanStack Query 管理服务端状态，不复制进 Zustand。
- 排期：不以 AAF-112 #11206、AAF-113 为前提，设计已确认即可开工；AAF-113 后续新增的 Activity 事件由其 #11332 按本故事的新约定接入。
