---
---
level: Practice
layer: Product
purpose: 跟踪 AAF v0.13 的 AIGC 项目重构与对话式协作业务范围
status: draft
version: "v0.13"
date: 2026-09-04
author: AaronZZH & Kiro
scope:
  includes:
    - "v0.13 AIGC 项目重构与对话式协作业务需求"
gains:
  - 能了解 v0.13 先项目功能、后对话协作的交付顺序与阶段门
---

# AAF v0.13 迭代计划

> **目标**：先把 AIGC 项目的创建、对象创作、素材版本、审核交付闭环做成可独立使用的项目工作台，再让用户现有助理以内容创作者角色调用同一组应用服务完成对话式协作。
>
> **周期**：2026-09-07 ~ 2026-10-02（4 周）
>
> **状态**：业务需求草案，待人类确认后由 product 细化并分配 AAF-112 起的一级用户故事编号。

## 方案边界

- 复用当前用户已有 Assistant 和统一 `/agui/run`，进入 AIGC 项目上下文时默认使用 `system.role.content-creator`；不新增 Assistant、Agent、Team 或项目专属运行时。
- 只优化内容创作者 Role，并新增或优化项目协作 Skill、Tool；Skill 负责意图收敛，Tool 只能调用 `AigcProjectApi`、`AigcExecutionApi`、`AigcMediaApi` 等既有应用边界。
- 项目 UI 与对话共用 `AigcProject`、ProjectGraph、ExecutionRun、ObjectVersion、MediaVersion、Work/Publication 真理源；消息、ToolUI 和 AG-UI 事件只做交互投影。
- 不恢复 `module.content`、`/api/content/**`、旧类型别名、双写、fallback 或兼容层。
- 对话阶段不得反向阻塞项目工作台：关闭 Chatter 后，用户仍能通过 UI 完成同一项目任务。

## 实施顺序与阶段门

```text
项目基础闭环
  → 对象创作与素材版本闭环
    → 审核交付与项目验收
      → 现有助理的只读项目对话
        → 受控项目写工具
          → 富媒体与素材协作验收
```

项目阶段全部验收通过前，不启动对话写工具开发；只读项目对话通过权限和隔离验收前，不开放任何项目写 Tool。

## 实时事件与双通道边界

v0.13 采用“一个业务执行链、两条交互事件流”，不允许项目 UI 与 Assistant 各自实现生成链路。无论动作来自项目界面、普通对话、项目内对话或工作流，最终都进入统一的 `ExecutionRun → AigcTask → MediaVersion → ProjectMediaRef` 应用链，并记录动作来源与关联上下文。

| 事件流 | 职责 | 生命周期 |
|--------|------|----------|
| AG-UI Run SSE | 对话文本增量、Tool Call、审批中断与恢复、后台任务受理结果 | 单次 Assistant Run |
| 用户级 AIGC Activity SSE | 生成任务创建、进度、完成、失败、媒体入库及项目状态变化 | 用户或工作区级全局长连接 |

全局活动流目标端点为 `/api/aigc/events/stream`，它不是项目专属 SSE。前端应用只维护一条共享连接，项目界面按 `projectId` 消费事件，对话框按 `conversationId` 消费事件；项目内对话生成的事件同时携带两个标识，从而同步刷新消息和项目视图。普通对话生成允许 `projectId=null`，但必须携带 `conversationId`；项目界面直接生成允许 `conversationId=null`。

```json
{
  "eventType": "media.generated",
  "projectId": 1001,
  "conversationId": "conversation-1",
  "assistantRunId": "assistant-run-1",
  "toolCallId": "tool-call-1",
  "executionRunId": 2001,
  "taskId": 3001,
  "mediaVersionId": 4001,
  "status": "SUCCESS",
  "occurredAt": "2026-09-04T15:00:00+08:00"
}
```

Assistant 发起长时间生成时，AG-UI 只返回“任务已受理”及 `executionRunId/taskId`，不得为了等待图片或视频完成而保持 Run。生成终态只产生一次内部领域事件，由 Activity SSE 投影给所有界面；若 AG-UI Run 仍在线，可额外投影 `CUSTOM` 事件改善即时体验，但不得将其作为唯一完成通知，也不得由生成服务手工向两个通道重复发送业务副作用。

当前 `/api/aigc/tasks/stream` 及 `useAigcTaskStream` 直接演进为上述全局活动流，补齐 `conversationId`、`assistantRunId`、`toolCallId`、`executionRunId` 等稳定关联，不保留并行兼容端点。SSE 只承担变化通知，消息中的媒体引用、ProjectGraph、ExecutionRun、MediaVersion 和项目引用仍以数据库为唯一真理源；前端收到事件或重连后必须重新查询权威状态，并通过事件游标或补查避免断线丢失。

**业务验收结果**：项目界面、普通对话和项目内对话发起的同一类生成均走同一执行链；项目内对话生成完成后消息与项目素材同步更新，普通对话生成无需项目也能更新正确会话；关闭 AG-UI、切换页面或断线重连不影响后台生成与结果恢复。

## 业务需求

### 项目创建与工作台闭环

收敛项目创建、配置物化、结构视图与图谱视图，使用户无需依赖对话即可从项目类型和 Brief 建立项目骨架，查看缺口、焦点对象、关系、进度与成本。结构/图谱只投影同一 ProjectGraph，默认视图、焦点、筛选和只读状态保持一致。

**业务验收结果**：用户可独立完成“创建项目 → 看见项目结构 → 定位待办对象 → 编辑项目基础信息”，刷新和切换视图不丢状态且不存在第二份项目数据。

### 对象创作、素材引用与版本闭环

补齐对象级文案、图像和视频动作的统一入口，支持上传素材、引用项目素材或资产版本、提交生成、查看执行状态、比较候选、采用/否决版本和局部重生成。所有按钮统一走 `ActionCommand → ExecutionRun → candidate`，媒体生成再按需关联 `AigcTask`。

**业务验收结果**：在不打开 Chatter 的情况下，用户可围绕一个焦点对象完成文案、图像或视频生成，并用已上传或项目已有素材作为参考；任务终态会刷新对象、图谱、摘要、版本与成本。

### 审核、作品与项目生命周期闭环

补齐从候选采用到送审、审核、收录 Work、可选 Publication、完成和归档的可视操作与状态约束；执行成功、候选、采用、审核、作品、发布和项目完成保持独立事实。

**业务验收结果**：至少一个图文内容项目可完全通过 UI 跑通“创建 → 生成 → 采用 → 审核 → 收录作品 → 完成 → 归档”，失败、取消和重试不移动采用指针、不重复扣费。

### 现有助理的项目上下文与内容创作者角色

项目页继续使用当前用户已有助理和统一 Chatter，在项目上下文中默认选择 `system.role.content-creator`，离开项目后恢复页面原有路由。项目会话按用户、工作区和项目隔离，服务端依据 `projectId` 与当前焦点重新加载项目摘要、对象、素材和允许动作，不信任客户端快照。

本阶段只开放只读项目协作：询问项目状态、缺口、候选、执行进度、素材和下一步建议，不注册项目写 Tool。

**业务验收结果**：助理能准确回答当前项目和焦点对象问题；切换项目、刷新、断线恢复或切换页面不会串话、串素材或污染默认角色；数据库项目写状态保持不变。

### 内容创作 Skill 与受控项目 Tool

优化内容创作者 Role 的项目协作说明，将现有内容策划/文案技能与新增项目协作 Skill 绑定到当前用户助理；按波次开放项目读取、对象动作、素材关联、候选决策和生命周期 Tool。所有写 Tool 复用既有应用服务、乐观锁、幂等、权限、预算、内容安全与人工确认，不直接访问 Repository 或绕过 ExecutionRun。

默认自动执行低风险只读操作；生成、修改、素材关联等可撤销写入展示动作摘要后执行；采用/否决、批量高成本生成、送审、完成、归档和发布必须显式确认。Tool 完成后只发送包含 projectId、revision 与 changed ids 的项目变更事件，由前端重新查询权威状态。

**业务验收结果**：用户可用自然语言完成与项目 UI 等价的受控操作；同一动作从 UI 或对话发起会产生相同 ExecutionRun、候选和项目修订，重试不会重复执行或扣费。

### 富媒体消息与项目素材协作

统一 Tool canonical name、输入输出和 ToolUI 注册，消息中展示文案草稿、图片、视频、执行状态、版本比较、确认卡片及“在项目中查看”入口。Composer 支持上传允许的文件，也支持从当前项目素材、资产和已采用版本中选择引用；消息历史恢复保留结构化 parts、工具状态和媒体引用，而不是退化成纯文本。

**业务验收结果**：用户可在对话中上传或选择项目素材发起文生图、图生图、文生视频或图生视频，生成过程可恢复，结果同时出现在消息与项目视图；删除聊天记录不影响项目事实。

## 关键质量门

- **项目先行门**：前三项业务需求全部通过 UI 验收后，才允许开发对话写 Tool。
- **智能层边界门**：数据库、seed、代码与接口中不新增 Assistant/Agent/Team；只允许 Role、Skill、Tool 和页面上下文接线变更。
- **单一真理源门**：Chatter state、消息历史和 ToolUI 不保存可独立修改的 ProjectGraph、版本、执行或生命周期副本。
- **权限隔离门**：项目上下文、线程、素材引用和 Tool 调用均校验 tenant/workspace/user/project；跨项目与跨用户访问全部拒绝。
- **可靠性门**：乐观锁、幂等、断线重连、事件重放、任务取消/完成竞争和失败退费通过测试；AG-UI 重试只产生一次业务副作用。
- **工程门**：`pnpm check`、`pnpm acceptance` 全绿，质量门满足 blocker=0 且 major≤2；新增项目 Tool 的权限与确认策略必须经安全审查。

## 不纳入本迭代

- 新建项目专属 Assistant、Agent、Team、A2A 协作或第二个对话端点。
- 用对话替代 ProjectGraph、ExecutionRun、Media、Work 等业务真理源。
- 自动发布、开放模板/工作流市场、专业 NLE、实时多人协同和跨项目批量自动化。
- 为兼容旧实现保留双 API、双 Tool 名称、双写或 fallback。

## 变更记录

| 日期 | 变更内容 | 原因 |
|------|---------|------|
| 2026-09-04 | 冻结“统一生成执行链 + AG-UI Run SSE + 用户级 AIGC Activity SSE”双通道边界 | 同时支持项目界面、普通对话和项目内对话接收异步生成结果 |
| 2026-09-04 | 创建 v0.13 业务需求草案，冻结“先项目、后对话”顺序与复用现有助理边界 | 响应 AIGC 项目重构及对话式协作规划 |

<!-- 状态标记（迭代整体）：draft 草案 | active 进行中 | completed 已完成 | cancelled 已取消 -->
