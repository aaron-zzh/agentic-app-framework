---
level: 2
version: 0.1.0
date: 2026-05-06
purpose: 设计协作控制台——既是 AAF 自开发工具也是框架产品能力，服务人机协作开发全流程
status: 草案
---

# 协作控制台设计（Collaboration Console）

> **一句话定位**：把人与 AI Agent 的隐形协作变成可视化的人机协作看板——**人 + AI 在同一个工作台上并肩工作**。
>
> 协作控制台是 AAF 框架的一个**产品能力**，归属 Layer 4 服务层的 "任务服务 / Auto Dev / 文档服务" 综合呈现，作为 Layer 5 对话与交互层的默认工作区之一。它体现 AAF 的核心设计思想：**AI 主动协作、规范驱动、分层智能、置信度门控、渐进提交、自进化**。

## 0. 双重定位

**协作控制台同时服务两类用户，共享同一套产品实现**：

| 用户 | 用它做什么 | 版本节点 |
|------|----------|---------|
| **AAF 作者**（当前阶段） | 在 AAF 自开发过程中监督 kiro-cli 内置智能体，验证 "AAF 用 AAF 自己开发 AAF" 的自举闭环（参见 [architecture-thought.md v0 目标](../../../explanation/architecture-thought.md#技术实现)） | **v0.1.0 纳入**（Phase 1 只读） |
| **AAF 框架用户** | 用 AAF 开发自己的业务应用时，监督他们配置的 Agent / Assistant / Team 工作进度 + 审核 + 沉淀经验 | **v2.0 开放**（Phase 3 时产品化） |

**为什么两类用户可以共用？**

因为 AAF 的核心设计范式是 **"用 AAF 的核心能力开发 AAF 本身"**（自举）：

- AAF 作者的开发工作流程，本质就是 "一个 AAF 用户用 AAF 做复杂应用" 的最小案例
- AAF 作者用的 kiro-cli agent，本质就是 AAF 用户未来用到的 Agent / Assistant 的前身
- 控制台今天服务 AAF 自开发，明天就服务 AAF 用户——同一套实现，没有二次开发

这是 **AAF 元引擎思想的第一个具体落地**：工具与产品同源，规范即共识。

## 1. 目标与场景

### 1.1 目标

对齐 [architecture-thought.md 分工协作原则](../../../explanation/architecture-thought.md#分工与协作)：

- **降低监督成本**（系统优先）：作者不需要逐个 agent 打开会话看进度，控制台聚合呈现
- **加速审核闭环**（主导权动态切换）：🔴 高风险任务触发置信度门控，人类审核入口统一
- **持续沉淀**（自进化闭环）：把每次 agent 派发的产出物、决策、反思自动归档到 skill / ADR / 规范，形成 "行为 → 评估 → 规范更新 → 重生成" 的闭环

### 1.2 核心用户场景

| 场景 | 当前痛点 | 目标体验 |
|------|---------|---------|
| 看进度 | 要手动翻 `docs/task/` 目录 + kiro-cli 各会话 | 一屏看到所有 Epic/Task 状态、谁在做、卡在哪 |
| 审核决策 | 🔴 风险任务需要在对话里打断 | 审核请求进 Inbox，一键批准/退回 |
| 介入阻塞 | 看不到 agent 请求的具体澄清点 | blocker 主动弹出，附带上下文和建议选项 |
| 沉淀经验 | 好经验散落在 dev-log 里 | 一键把解决方案晋升为 `.kiro/skills/` 条目 |
| 规划迭代 | Epic 拆分靠人工，依赖关系凭记忆 | 任务依赖图可视化，拖拽调整 |

### 1.3 非目标

- ❌ 替代 kiro-cli 的对话体验（对话仍在 kiro-cli TUI 内）
- ❌ 做多租户/企业级权限（单人场景）
- ❌ 做调度器（kiro-cli 和 subagent 自己负责调度，控制台只观察和注入决策）

---

## 2. 核心概念与数据模型

参考 multica 28 实体，按 AAF 单人 + kiro-cli 场景筛选，最终核心 14 张表 + 3 个设计范式。

### 2.0 概念词典

理解这些术语是理解控制台的前提。每个概念都对应一个数据表或设计范式。

| 概念 | 定义 | 对应数据表 / 范式 |
|------|------|------------------|
| **User 用户** | 人类账号。v0.1 是 AAF 作者一人；v2.0 开放多用户 | `user`（v0.1 单条记录） |
| **Epic** | 一个用户故事 / Epic，如 `AAF-025`。跨多个 Task，多个 Epic 组成 Iteration | `epic` |
| **Iteration 迭代** | 一次版本迭代，如 `v0.1.0`。是 Epic 的容器 | 映射 `docs/task/aaf-v*.md` |
| **Task 技术任务** | 一个可派发给 agent 的执行单元，编号 `#N`。挂靠在 Epic 下 | `task` |
| **Agent 智能体** | kiro-cli 内置的 11 个角色（kiro_default / product / architect / developer-* / designer / qa / tester / kiro_planner / kiro_guide） | `agent`（绑 `.kiro/agents/*.yaml`） |
| **Dispatch 派发** | 一次 "把 Task 派发给一个或多个 Agent" 的动作，带 🟢/🟡/🔴 风险等级 | `dispatch_log` |
| **Run 执行** | 一次 Agent 实际执行 Task 的运行，对应 multica `agent_task_queue`。带状态机（queued/dispatched/running/completed/failed/cancelled/blocked） | `agent_run` |
| **Session 会话** | 一次 kiro-cli 对话会话，带 session_id 和工作目录。多个 Run 可复用同一 Session（Session Resumption） | `chat_session` |
| **Artifact 产出物** | 一次 dispatch 产生的文档产物，6 种：requirement / design / dev-log / test-report / review / audit | `artifact` |
| **Skill 技能** | 可复用的经验说明文档，对应 `.kiro/skills/` 目录。支持从 dev-log 片段 "晋升" 而来 | `skill` |
| **ADR 架构决策记录** | 一次重要架构决策的独立记录。对应 `docs/design/adr/ADR-*.md` | `adr` |
| **Actor 行动者** | 设计范式：`actor_type ∈ {human, agent, system}` + `actor_id`，贯穿所有 "谁做了什么" 字段 | 跨所有表 |
| **Comment 评论** | 挂在 Task 上的讨论，支持 `@agent` 触发派发 | `comment` |
| **Inbox 收件箱** | 人类的通知中心。审核请求 / blocker / 完工汇报 / 上下文超限都进这里 | `inbox_item` |
| **Activity 时间线** | Task 详情页的时间线，混合 activity_log + comment | `activity_log` |
| **Autopilot 自动驾驶** | 定时/webhook/API 触发规则，自动创建 Task 或直接派发 Agent（v2.0+ 与框架能力同步） | `autopilot` / `autopilot_trigger` / `autopilot_run` |
| **Context Usage 上下文占用** | 每次 dispatch 记录当时 agent 的 token 占用百分比，steering 硬约束 ≤ 50% | `context_usage` |
| **Regulation Check 规范一致性检查** | 自动检测规范与代码是否一致（AAF-024 #13），结果展示在健康面板 | 由 Regulation Check Runner 产出，结果写 `activity_log` |
| **Session Resumption** | 设计范式：同一 `(agent, task)` 对的下次 Run 自动复用上次 session_id + 工作目录 | `agent_run.session_id + work_dir` |
| **Risk Level** | 🟢 低 / 🟡 中 / 🔴 高，决定派发链（AAF-024 #11） | `dispatch_log.risk_level` |

### 2.1 借鉴自 multica

| 实体 | 说明 | AAF 调整 |
|------|------|---------|
| `user` | 人类用户 | 单条记录，一人公司 |
| `agent` | kiro-cli 内置 agent 配置 | 绑定到 `.kiro/agents/*.yaml`，11 个 role（kiro_default / product / architect / developer-api/app/web / designer / qa / tester / kiro_planner / kiro_guide） |
| `agent_skill` · `skill` | agent 挂载的可复用说明文档 | 映射到 `.kiro/skills/*`，支持 "晋升" 机制（把 dev-log 片段变成 skill） |
| `epic` | 用户故事/史诗 | 映射到 `docs/task/backlog.md` + `docs/task/v*/AAF-*/` |
| `task` | 技术任务 | 映射到 `docs/task/v*/AAF-*/tasks.md` 的 `#N` 条目 |
| `comment` | 评论 | 附加在 task 上，支持 `@agent` 触发派发 |
| `agent_run` | Agent 一次执行 | 对应 multica `agent_task_queue`，带状态机（见 4.1） |
| `task_message` | 执行消息流水 | 对应 subagent 的 stage 输入输出、关键中间状态 |
| `chat_session` | kiro-cli 对话会话 | 链接到具体的 session_id（Session Resumption 用） |
| `autopilot` · `autopilot_trigger` | 定时/webhook/API 触发规则 | 如"每天早上 9 点自动跑 `check:affected`"、"PR 创建时自动派发 tester" |
| `inbox_item` | 通知中心 | 审核请求、blocker、完工汇报、上下文超限警告都进这里 |
| `activity_log` | 审计日志 | Polymorphic Actor 贯穿，所有 agent/human 操作都记 |

### 2.2 AAF 特有

| 实体 | 说明 | 为什么需要 |
|------|------|----------|
| `artifact` | 派发产出物索引 | requirement/design/dev-log/test-report/review/audit 6 种，每种一条记录，指向 `docs/task/*/*.md` 文件 |
| `dispatch_log` | 派发记录 | 记录 🟢/🟡/🔴 风险等级、派发链、预估 vs 实际耗时，配合 AAF-024 #11 的派发规则 |
| `context_usage` | 上下文占用统计 | steering 硬约束 "上下文 ≤ 50%" 要求度量，每 agent 派发记录当时的 token/percent |
| `adr` | 架构决策索引 | 映射到 `docs/design/adr/`，配合规则溯源 P1.5 的硬约束回链 |

### 2.3 设计范式（贯穿所有表）

- **Polymorphic Actor**：`actor_type` ∈ {human, agent, system} + `actor_id`，贯穿 comment / activity / run 等所有"谁做了什么"的字段。落地见 [Polymorphic Actor 改进意见](../../../prd/improvements.md)。
- **Session Resumption**：`(agent, task)` 对的下次派发自动复用上次 session_id + work_dir（kiro-cli 级支持前用 session 目录映射兜底）。
- **任务生命周期状态机**：`queued → dispatched → running → completed/failed/cancelled/blocked`，并发策略 `skip/queue/replace`，孤儿回收 Sweeper。详见 4.1。

---

## 3. 关键功能

### 3.1 仪表板（Dashboard）

一屏聚合：
- **当前 Epic 卡片**：v0.1.0 进度条、P0/P1 任务数、blocker 数
- **活跃 Agent 列表**：11 个 agent 当前状态（idle / working / blocked / offline），最近一次 run 时间
- **任务泳道**：按 `queued / running / waiting-review / completed` 分列，卡片拖拽调状态
- **审核请求队列**：🔴 等待人类决策的任务，一键进入审核页
- **上下文健康度**：所有 agent 最近一次派发的上下文占用，触发 50% 硬约束的标红

### 3.2 任务时间线（Task Timeline）

单个 task 内展示：
- Kick-off：用户故事链接 + 拆分人 + 拆分时间
- **Dispatch 记录**：每次派发的 risk 级别、agent 链、耗时
- **Artifact 链接**：requirement.md / design.md / dev-log.md / test-report.md / review.md / audit.md
- **Activity 事件**：状态变更、assignee 变更、关键评论
- **完工汇报**：`pnpm check:affected` 结果、质量门控结论
- 合并 comment 线与 activity 线（混合时间线，同 multica 做法）

### 3.3 审核工作台（Review Inbox）

- 🔴 风险任务在任意 agent 派发前会生成一条 `inbox_item{type: approval_request}`
- 审核页展示：任务上下文 + architect 设计稿 + 潜在影响评估 + "批准 / 退回 / 降级" 三个动作
- 批准后自动继续派发链；退回写 comment 要求重新设计
- 响应 SLA 超时（如 24h 未处理）自动标记为 blocker

### 3.4 经验沉淀（Skill Promotion）

- 任何 dev-log 里的 "解决方案" / "踩坑记录" 可以一键 "晋升"：
  - 结构化抽取 → 生成 `.kiro/skills/{slug}/SKILL.md` 草稿 → 人类审核发布
- 晋升后的 skill 自动被未来同类任务的 agent 注入
- 对应 multica 的 Skill → 工作记忆借鉴点

### 3.5 Autopilot 规则

支持三种触发：
- **Cron**：每日 9:00 跑 `check:affected`、每周五归档上一周的 dev-log
- **Webhook**：GitHub PR open/merge 触发 tester / code-reviewer
- **API**：外部脚本或其他 agent 通过 HTTP 触发派发

两种执行模式：`create_task`（创建任务进待办）/ `run_directly`（直接派发给指定 agent）

### 3.6 CLI 命令入口（AAF CLI）

Web UI 是主入口，CLI 是补充——"不想开浏览器" 场景、"脚本化批量操作" 场景、"在 kiro-cli 对话里快速触发" 场景用 CLI。

#### 3.6.1 定位与边界

- **Web UI** 负责：仪表板、时间线浏览、审核详情页、Skill 晋升编辑器等需要可视化 + 富文本的场景
- **AAF CLI** 负责：高频、原子、脚本化的状态流转（create / start / done / block / status）
- **不做**：完整的 multica 式 CLI（login/daemon/workspace/runtime 等），这些在 AAF 单机场景不适用

#### 3.6.2 核心命令矩阵（轻量版）

```bash
# Epic / Task 管理
aaf epic list [--version v0.1.0] [--status in_progress]
aaf epic status AAF-025
aaf task list [--epic AAF-025] [--assignee developer-api]
aaf task create "修复登录 bug" --epic AAF-025 --risk 🟡
aaf task start #42                 # queued → dispatched
aaf task done #42                  # running → completed（触发 check:affected）
aaf task block #42 --reason "..."  # * → blocked，写入 inbox
aaf task assign #42 --to tester

# 派发（Polymorphic Actor + 风险分级，对应 AAF-024 #11 规则）
aaf dispatch #42                   # 按 risk 自动选派发链
aaf dispatch #42 --agents developer-api,tester   # 手动指定

# 审核（🔴 任务）
aaf review                         # 列出 inbox 中待审核项
aaf review approve <inbox-id>
aaf review reject <inbox-id> --comment "..."

# 监控
aaf context                        # 所有 agent 最近一次派发的上下文占用
aaf runs --active                  # 正在 running 的 agent_run
aaf timeline #42                   # 任务完整时间线（等价 Web 的 Task Timeline）

# 沉淀
aaf skill promote <dev-log-snippet-id>   # 把 dev-log 片段晋升为 .kiro/skills/ 条目
aaf adr new "<title>"                    # 从 _template 创建 ADR 草稿
```

#### 3.6.3 与 pnpm nx / kiro-cli 的关系

| 工具 | 职责 | 不重叠处 |
|------|------|---------|
| `pnpm nx` | 构建 / 测试 / lint / format（`check` / `acceptance` / `build`） | 不做任务管理 |
| `kiro-cli chat` | agent 对话、subagent 派发实现 | 不做任务状态流转 |
| `aaf` | 任务 / Epic / 派发 / 审核 / 监控的命令行入口 | 不做构建也不做对话 |

CLI 内部实现：读写 `docs/task/` 文件 + PostgreSQL（Phase 2 后）+ 调用 `pnpm nx` / `kiro-cli` 作为底层。

#### 3.6.4 对 multica Daemon / Profile 机制的取舍

| multica 机制 | AAF 是否采纳 | 理由 |
|------------|------------|------|
| Daemon 后台进程（探测 / 注册 / 轮询 / 心跳） | ❌ 不做 | AAF 单机 + kiro-cli 前台对话，无跨机器调度需求；等 AAF 作为"被调度 Agent 框架"时另设计（P3.2 改进意见） |
| 8 种 AI CLI 自动探测 | ❌ 不做 | kiro-cli 是固定工具链，无多 provider 切换场景 |
| Profile 机制（prod/staging 并行） | ⚠️ 借鉴简化版 | 用 Worktree + 共享 PostgreSQL（P3.1 改进意见）替代，per-worktree `.env.worktree` 充当 profile |
| Runtime 心跳 + Sweeper | ❌ 不做 | 无后台进程，不需要心跳；孤儿 run 的回收由 Phase 2 DB 层 Sweeper job 做（不是 daemon） |
| 完整 CLI 子命令（login/auth/workspace/daemon/...） | ❌ 不做 | 本地单人场景不需要认证/工作区切换；只做任务流转子集 |

结论：**只采纳 "CLI 命令入口" 这一层，不采纳 Daemon / Profile / Runtime 机制**。

### 3.7 Agent 中心（列表 + 详情）

对应 [architecture-thought.md 分层智能架构](../../../explanation/architecture-thought.md#分层智能渐进决策) 的可视化：让作者看清每个 agent 在做什么、状态如何、最近用了多少上下文。

- **Agent 列表页**：11 个角色的卡片化展示——头像 / 当前状态（idle/working/blocked/offline）/ 最近一次 run 的 task 编号 / 最近一次 dispatch 的 risk 等级 / 上下文占用百分比（触发 50% 阈值标红）
- **Agent 详情页**：
  - **基本信息**：角色描述、绑定的 `.kiro/agents/*.yaml` 配置、挂载的 skill 列表
  - **最近 Run 历史**：按时间倒序，每行显示 task / status / duration / context_usage
  - **近 7 天上下文趋势**：折线图，检测是否逼近 50% 硬约束
  - **挂载 Skill**：当前激活的 skill（来自 agent 的 `resources` 配置 + 默认 agent 自动加载的全部 skill）
- **手动触发派发**：页面底部有"派发新 Task 给这个 agent"按钮，按 AAF-024 #11 风险分级走派发链

### 3.8 知识中心（Artifact + Skill + ADR）

对应 architecture-thought.md **"知识与能力一体"** 设计思想——把开发过程中沉淀的知识集中呈现，方便查找和复用。

三个 Tab：

| Tab | 数据源 | 核心动作 |
|-----|-------|---------|
| **Artifact 浏览** | `docs/task/v*/AAF-*/*.md` 6 种产物 | 按 Epic / Task / 类型 / 作者筛选；进 Timeline 查看产出上下文 |
| **Skill 库** | `.kiro/skills/*/SKILL.md` | 查看 / 从 dev-log 晋升新 skill / 编辑 description / 归档 |
| **ADR 列表** | `docs/design/adr/ADR-*.md` | 查看现有决策 / 从 `_template.md` 新建 ADR / 关联到硬规则（规则溯源 P1.5） |

**"知识反哺"流程**：Task 完工时，控制台会在 Review Inbox 推送一条"有哪些内容值得晋升为 skill / ADR"的建议——这是 architecture-thought.md "执行结果反哺知识" 原则的落地。

### 3.9 规范健康面板（Regulation Health）

专门展示 AAF-024 #13 规范-代码一致性检查脚本的结果，是"规范驱动开发"的健康指示器：

- **检查项**：
  - 规范里宣称的依赖（如 "用 Vitest"）是否在 `package.json` / `pom.xml` 实际存在
  - CI 引用的 Nx target 是否在对应 `project.json` 定义
  - 规范中的相对链接是否指向真实存在的文件
  - ArchUnit 分层规则（包边界）是否有违规（依赖 P2.3 落地）
- **风险等级**：每项检查失败带 severity（`action_required` / `attention` / `info`，对应 Inbox 分级）
- **趋势图**：近 30 天的总失败数，判断"规范腐蚀"是否在加剧
- **一键修复建议**：失败项关联具体补救动作（如"添加 vitest 依赖到 package.json"），可直接派发 🟡 中风险 Task 给 developer agent 修复

### 3.10 设置页（Console Settings）

控制台自身的配置，与 AAF 业务设置（`docs/reference/team/`）分开：

- **Dashboard 首屏偏好**：默认显示哪个 Epic、哪些泳道、是否开启未读徽标
- **通知偏好**：哪些事件进 Inbox、严重性阈值、是否启用浏览器原生通知
- **Autopilot 开关**（Phase 3+）：启用 / 禁用各条 Autopilot 规则，调整 cron 时间
- **Skill 晋升偏好**：一键晋升前是否强制人工审核（默认是，对齐置信度门控）
- **上下文健康度警告阈值**：默认 50%（steering 硬约束），可调低至 40% 做预警
- **CLI token**（v2.0+）：为 `aaf` CLI 生成 PAT，脚本/CI 场景使用

---

## 4. 技术架构

### 4.1 任务生命周期状态机（借鉴 multica，已登记改进意见）

```text
         ┌──────┐
 human → │queued│
         └───┬──┘
             ↓ 派发
         ┌──────────┐
         │dispatched│
         └────┬─────┘
              ↓ agent 认领
         ┌───────┐
         │running│────┐
         └───┬───┘    │
             ↓        ↓ 请求澄清
       ┌─────────┐  ┌───────┐
       │completed│  │blocked│←──── human 回复
       └─────────┘  └───────┘
             ↑        ↓
             │    ┌──────┐
             └────│failed│ (重试策略)
                  └──────┘
                    ↓
                ┌─────────┐
                │cancelled│
                └─────────┘
```

- **并发策略**：同一 (agent, task) 有 running 时再次派发 → `skip`（默认）/ `queue`（排队）/ `replace`（终止旧 run）
- **孤儿回收**：Sweeper（每 30s）扫描 `dispatched > 5min` 或 `running > 2h` 的 run 标记为 failed

### 4.2 数据流与存储

```text
┌──────────────────────────────────────────────────────────────┐
│  前端（webui，Next.js）                                        │
│  - Dashboard / Task Timeline / Review Inbox / Skill Promote   │
└────────────────────────┬──────────────────────────────────────┘
                         │ REST + WebSocket（分 session 房间）
                         ↓
┌──────────────────────────────────────────────────────────────┐
│  后端（aaf-api 新增 module: collab-console）                   │
│  - Task / Agent / Artifact / DispatchLog / Inbox CRUD        │
│  - 状态机驱动                                                  │
│  - Autopilot Scheduler（Flowable 或 轻量 Quartz）             │
└────────────────────────┬──────────────────────────────────────┘
                         │
        ┌────────────────┼─────────────────┐
        ↓                ↓                 ↓
┌───────────────┐ ┌────────────┐ ┌──────────────────┐
│ PostgreSQL    │ │ 文件系统     │ │  kiro-cli 会话    │
│ (结构化实体)   │ │ docs/task/  │ │  session 目录映射 │
│                │ │ .kiro/      │ │  stdout/stderr   │
└────────────────┘ └─────────────┘ └──────────────────┘
```

**数据源分工**：
- **PostgreSQL**：结构化实体（task/run/comment/activity/inbox）
- **文件系统**：artifact 原文仍在 `docs/task/v*/AAF-*/*.md`，DB 只存指针和元数据（配合 "文档是唯一真理" 硬约束）
- **kiro-cli session**：run 关联到具体 session 目录，支持回看对话历史和文件快照

### 4.3 事件流（WebSocket 分房间，借鉴 multica）

- 房间维度：`session:{session_id}` / `task:{task_id}` / `workspace`（全局）
- 事件类型枚举（15+）：
  - `task.status_changed` / `task.comment_added` / `task.assignee_changed`
  - `run.started` / `run.progress` / `run.completed` / `run.failed` / `run.blocked`
  - `inbox.new` / `inbox.resolved`
  - `artifact.created` / `artifact.updated`
  - `skill.promoted` / `adr.created`
  - `regulation.check_failed` / `regulation.check_recovered`
  - `autopilot.run_start` / `autopilot.run_done`（Phase 3+）
  - `context.warning`（上下文占用超阈值）
- **硬规则**（借鉴 multica）：WS 事件**只触发前端 query invalidate，不直接写客户端 store**
- **心跳**：server 每 54 秒 ping，client 60 秒内必须 pong（对齐 multica 参数）
- **事件分类处理**：即时更新事件（task / comment / run 这类需要高响应的）前端直接 patch 本地缓存；less-critical 事件触发 query invalidate 重拉——不是所有事件都走 invalidate 一种模式

### 4.4 AI / LLM 在哪里

**协作控制台本身不直接调 LLM API**。所有 LLM 调用都在 kiro-cli agent 子进程里发生（kiro-cli 自己管理 API 调用、模型选择、token 预算）。

控制台做的事是：

1. **观察** kiro-cli 的工作（通过文件系统 + session 目录 + git log + 后续可能的 hooks/MCP）
2. **记录** agent dispatch / run / artifact / context_usage
3. **聚合呈现** 给人类 Dashboard / Timeline / Inbox
4. **回注决策**（人类通过审核、@comment 触发派发、Autopilot 规则）

这与 architecture-thought.md 的 **"Core 层无状态 + Agent 层从 Cognition 拉上下文"** 原则一致——控制台位于 Layer 4 服务层，**不触碰 LLM 推理**，只做任务编排、事件广播、持久化。

### 4.5 后台任务清单

对标 multica Server 的三个 goroutine，AAF 控制台 Phase 2+ 启动以下后台 worker（放在 `aaf-api` 的 `collab-console` 模块）：

| Worker | 周期 | 职责 |
|--------|------|------|
| **File System Scanner** | 每 60s | 扫描 `docs/task/` 和 `.kiro/` 发现新的 Task / Artifact / Skill / ADR，同步到 DB 索引（Phase 1 可用 Next.js ISR 或前端轮询替代） |
| **Git Activity Logger** | 每 5min | 从 `git log` 抽取 commit 生成 activity 事件（如 "developer-api 完成 #42 的 dev-log"） |
| **Context Usage Monitor** | 每次 dispatch 结束触发 | 读取该 agent 当时的 context_usage，写入 `context_usage` 表；超过 50% 阈值发 `context.warning` 事件 |
| **Regulation Check Runner** | 每 6 小时 | 跑 AAF-024 #13 一致性检查脚本，结果写入 `activity_log` + 失败项入 Inbox |
| **Run Sweeper**（Phase 2+） | 每 30s | 扫描 `dispatched > 5min` 或 `running > 2h` 的 run，标记为 failed（借鉴 multica） |
| **Autopilot Scheduler**（Phase 3+） | 每 30s | 扫 `autopilot_trigger` 的 cron，到点触发派发（与 v2.0 框架 Autopilot 能力配合） |
| **Skill Auto-Suggest**（Phase 3+） | 每次 Task 完工触发 | 分析 dev-log 内容，主动推送 "有哪些值得晋升为 skill" 到 Inbox |

**Phase 1 简化实现**：不启动任何后台 worker，文件状态变化由前端访问时即时扫描。这满足 architecture-thought.md **"最小可行实现"** 原则。

### 4.6 kiro-cli 协作接口（集成契约）

控制台与 kiro-cli 的事件/日志对接采用 **HTTP 上报 + SSE 下推** 模式，属于 "kiro-cli → AAF 后端 → Web UI" 的单向观测通道：

```text
kiro-cli（本地执行）
  ├─ POST /api/monitor/events    上报执行事件（dispatch / run 状态变更 / artifact 创建 / blocker）
  ├─ POST /api/monitor/logs      上报执行日志（thinking / tool_call / output / error）
  ↓
AAF 后端（aaf-api: collab-console 模块）
  ↓ 持久化到 agent_run / task_message / activity_log / inbox_item
  ↓
SSE /api/monitor/stream → Web 前端实时展示（Dashboard / Timeline / Inbox）
```

**接口职责拆分**：

| 接口 | 方向 | 内容 | 对应数据表 |
|------|------|------|-----------|
| `POST /api/monitor/events` | kiro-cli → 后端 | 状态变更（dispatched/running/completed/blocked）· artifact 创建 · 审核请求 | `agent_run` / `activity_log` / `inbox_item` |
| `POST /api/monitor/logs` | kiro-cli → 后端 | Agent 执行流水（subagent stage 输入输出、关键中间状态） | `task_message` |
| `SSE /api/monitor/stream` | 后端 → Web | 广播聚合后的事件给前端 Dashboard / Timeline | 对应 §4.3 WebSocket 事件的 SSE 兜底通道 |

**为什么选 HTTP + SSE 而非 WebSocket**（与 [tech-stack.md §4 流式推送决策](../../apps/service/tech-stack.md#四关键架构决策) 一致）：

- kiro-cli 是**短生命周期子进程**，HTTP 比 WebSocket 连接管理简单，失败重试天然
- SSE 单向够用——Web UI 只需接收推送，交互事件走 §4.3 的 WebSocket 通道双通道并存
- 上报失败时 kiro-cli 可**本地缓存后异步重放**，不阻塞 agent 执行

**渐进落地（呼应 §9.2 外挂观察者策略）**：

| 阶段 | 实现 |
|------|------|
| **Phase 1** | kiro-cli 未提供 hooks，三接口不实现；控制台只读 `session 目录` + `git log` + `docs/task/` 文件 |
| **Phase 2** | 若 kiro-cli 仍未原生支持，实现 **shim 工具**（post-commit hook / 完工时 `curl` 手动触发）按本表契约上报 |
| **Phase 3+** | kiro-cli 原生支持 webhook / MCP server 后，直接对接原生事件；本接口退化为兼容层 |

**安全边界**：
- 接口仅监听 `127.0.0.1` 本地回环，v0.1 单人场景无需鉴权
- v2.0 开放给 AAF 框架用户时升级为 PAT Token 鉴权（对应 §3.10 设置页 CLI token）

---

## 5. 与现有体系的关系

### 5.1 复用现有资产

| 现有 | 复用方式 |
|------|---------|
| `docs/task/v*/AAF-*/*.md` | 所有 artifact 原文位置不变，控制台只建指针索引 |
| `.kiro/agents/*.yaml` | agent 配置直接读取，不重复定义 |
| `.kiro/skills/*` | skill 目录直接读取 + 晋升写入 |
| `docs/design/adr/*` | ADR 列表同步到 `adr` 表作为索引 |
| `docs/prd/backlog.md` | Epic 来源，支持双向同步（文件 → DB → 编辑后回写文件） |
| `docs/reference/team/**` | 规范文档作为只读导航，不进 DB |

### 5.2 依赖的改进意见（需先落地）

协作控制台 v1 的前置依赖（来自 [improvements.md](../../../prd/improvements.md)）：

| 依赖项 | 为什么必需 |
|--------|----------|
| Polymorphic Actor 落地 | comment / activity / run 都需要 `actor_type + actor_id` |
| 任务生命周期状态机 | run 表的核心逻辑 |
| Session Resumption | run 与 kiro-cli session 的绑定 |
| WebSocket 分房间规范 | 实时事件的基础 |
| AI 自验证循环强化 | 完工汇报事件的触发点 |
| 规范-代码一致性检查 | Artifact 元数据校验 |
| 包边界规则细化（ArchUnit） | `collab-console` 模块的依赖边界 |

### 5.3 产品路由地图

控制台路由全部挂在 `apps/webui` 的 `/console` 命名空间下，与 webui 现有的登录 / 聊天 / Auto Dev 监控等业务路由并列。

| 路由 | 页面 | Phase |
|------|------|-------|
| `/console` | Dashboard（§3.1 仪表板） | 1 |
| `/console/epics` | Epic 列表（v0.1.0 / 历史迭代） | 1 |
| `/console/epics/[id]` | Epic 详情（该 Epic 下所有 Task + 进度） | 1 |
| `/console/tasks` | Task 列表（跨 Epic 聚合视图，按状态/风险/assignee 筛选） | 1 |
| `/console/tasks/[id]` | Task Timeline（§3.2） | 1 |
| `/console/review` | 审核 Inbox（§3.3） | 1（只读）→ 2（一键批准退回） |
| `/console/agents` | Agent 列表（§3.7） | 1 |
| `/console/agents/[role]` | Agent 详情（§3.7） | 1 |
| `/console/knowledge` | 知识中心（§3.8 三 Tab）| 2 |
| `/console/knowledge/artifacts` | Artifact 浏览 | 2 |
| `/console/knowledge/skills` | Skill 库 + 晋升入口 | 2 |
| `/console/knowledge/adr` | ADR 列表 + 新建 | 2 |
| `/console/regulations` | 规范健康面板（§3.9） | 1（只读趋势）→ 2（一键派发修复） |
| `/console/autopilots` | Autopilot 列表 | 3 |
| `/console/autopilots/[id]` | Autopilot 详情 + run 历史 | 3 |
| `/console/settings` | 控制台设置（§3.10） | 1（基本偏好）→ 3（Autopilot 开关） |

**全局命令面板（Cmd+K）**：Phase 2+ 支持全局搜索跳转 task / artifact / skill / ADR / agent，对齐 multica Cmd+K 模式。

---

## 6. 渐进落地路径

### Phase 1（MVP · 只读仪表板 + 轻量 CLI）— **纳入 v0.1.0 迭代**

- 不引入新 DB 表，基于文件系统扫描 + git log 生成只读视图
- webui 加 `/console` 路由，展示：Epic 进度、Task 列表、Artifact 链接
- **AAF CLI 骨架**：`aaf epic/task list`、`aaf timeline`、`aaf context`（只读子集，读取 docs/task/ + git 即可）
- 数据源：`docs/task/` + `.kiro/agents/` + `git log`
- **成本**：1-2 周（Web UI）+ 3-5 天（CLI 只读命令）
- **价值**：作者不再需要手动翻目录；CLI 可在 kiro-cli 对话里快速调用

### Phase 2（v1.0 · 结构化存储 + 审核闭环）

- 建 14 张表 + 后端 CRUD + 状态机
- 引入 WebSocket 事件层
- 审核 Inbox 落地，🔴 任务派发前强制走审核
- **AAF CLI 写操作**：`aaf task create/start/done/block`、`aaf dispatch`、`aaf review approve/reject` 接入 DB
- **前置**：Polymorphic Actor + 任务生命周期状态机已定义（属于控制台自身需要，不依赖框架元引擎化）
- **成本**：3-4 周
- **价值**：高风险任务不再需要作者主动盯

### Phase 3（v2.0 · Autopilot + 经验沉淀）

- Autopilot 调度器（Cron + Webhook + API），与 v2.0 框架 Autopilot 能力配合
- Skill Promotion UI
- **前置**：Phase 2 完成 + 积累足够 dev-log 数据 + 框架 Autopilot 能力启用
- **成本**：2-3 周
- **价值**：常规任务自动化，经验自动沉淀

### Phase 4（v3.0+ · 多 agent 并行协调，可选）

- 依赖 Worktree + 共享 PostgreSQL P3.1 方案
- 支持多个 developer agent 在不同 worktree 并行
- Runtime 健康监测 / 孤儿回收 Sweeper
- **前置**：单机多 agent 场景真正出现瓶颈时再做

---

## 7. 未决议问题（待评估）

| 问题 | 选项 | 建议 |
|------|------|------|
| 控制台是 Web UI 还是 TUI？ | A. Web (复用 webui) / B. TUI (ink/blessed) / C. 两者都支持 | A——复用 webui 基础设施 |
| DB 独立还是复用 AAF 主 DB？ | A. 独立 `aaf_console` 库 / B. 复用 `aaf_db` 加前缀 | A——关注点分离，避免污染业务表 |
| 与 kiro-cli 如何集成？ | A. 完全外挂（只读 session 目录）/ B. kiro-cli 提供 webhook / C. 增加 MCP server | A 先行，B/C 看 kiro-cli 后续支持 |
| backlog / iteration 的 DB 化是否破坏 "文档是唯一真理"？ | A. DB 是缓存，文件优先 / B. DB 是权威，文件导出 | A——硬约束不动 |

---

## 8. 参考

- [AAF 改进意见 - 协作流程与智能体](../../../prd/improvements.md)：相关未实施条目
- [AAF AGENTS.md](../../../../AGENTS.md)：当前 agent 配置清单
- [.kiro/steering/collaboration.md](../../../../.kiro/steering/collaboration.md)：协作红线

---

## 9. 落地意见（务实收敛）

> 不追求做成下一个 multica，只借 multica 的灵活控制能力把 "人 + kiro-cli agent 协作" 这件事做顺。

### 9.1 技术定位（v0.1 定下来不再改，v2.0+ 重新评估）

- **唯一 UI = `apps/webui`**：v0.1/v1.0 不做 TUI、不做桌面端、不做独立 CLI。所有监督/审核/沉淀动作都在 Web 里。**AAF CLI 退化为可选补丁**（Phase 1 阶段优先不做，等 Web UI 稳定后再评估是否需要脚本化入口）。v2.0 开放给 AAF 框架用户时 UI 形态不变，数据源从"文件 + DB"升级为"DB + 文件"（DB 主，文件归档用）
- **唯一 Agent 运行时（v0.1-v1.0）= `kiro-cli`**：当前阶段不引入 multica 式 Daemon、不做 8 种 provider 探测、不做 runtime 注册。所有 agent 都是 `.kiro/agents/*.yaml` 里定义的 11 个 role，运行载体就是当前这个 kiro-cli 会话。**v2.0 引擎化后**：agent 运行时扩展为框架自己的 Agent / Assistant / Team（参见 [architecture-thought.md 分层智能架构](../../../explanation/architecture-thought.md#分层智能渐进决策)），kiro-cli 从"唯一载体"退化为"其中一种载体"
- **唯一数据源双真理**：v0.1/v1.0 文件系统（`docs/task/` / `.kiro/`）是权威真理，DB 是派生缓存。一旦两者冲突，**以文件为准并回填 DB**（硬约束不破）。v2.0 开放给框架用户后，该用户自己的业务域数据（他们的 Task / Run / Artifact）可以 DB 为主，但 AAF 项目自身开发产物仍保持"文件为真理"

### 9.2 借力 kiro 原生能力（不重复造轮子）

AAF 协作控制台的本质是 **"给 kiro-cli 的工作过程加一层可视化 + 审核闭环"**，而不是另起一个 agent 编排平台。依托 kiro 的原生资产：

| kiro 资产 | 控制台怎么用 | 避免重造 |
|----------|-------------|---------|
| `.kiro/agents/*.yaml` | agent 注册表直接读取 | 不重新定义 agent 元数据 |
| `.kiro/skills/*/SKILL.md` | Skill 目录作为 skill 表数据源 + 晋升写入 | 不建独立的 skill 存储 |
| `.kiro/steering/collaboration.md` | 硬约束规则表的唯一来源 | 不在 DB 冗余一份 |
| kiro-cli session 目录 | Session Resumption 靠 session_id 回溯对话历史 | 不建独立对话存储 |
| kiro-cli subagent 机制（`subagent` 工具）| dispatch 底层就是调用 subagent 派发 | 不建独立调度器 |
| kiro-cli hooks（如果后续支持）| Autopilot 的 webhook 触发点 | 不做自己的触发器 |

**关键假设**：kiro-cli 目前还没有统一的 webhook / MCP server 暴露对外能力。**如果 Phase 2 启动时 kiro-cli 仍未提供**：控制台就退化成 "外挂观察者"（只读 session 目录、只读文件产物、通过编辑 `docs/task/` 触发状态变更），不做主动注入。等 kiro-cli 原生能力补上再升级。

### 9.3 借力 multica 的"灵活控制"（只取精华，不取架构）

从 multica 借的**不是分布式架构、不是多 daemon、不是 8 种 provider**，而是 5 个经过验证的 UX 模式：

| multica 模式 | AAF 用来解决什么 |
|------------|----------------|
| Polymorphic Actor（`actor_type + actor_id`） | 让 kiro 的 11 个 agent 和作者本人共享同一个任务分配接口 |
| 任务生命周期状态机 | 让作者在任意时刻能看到 "这个任务卡在哪" |
| Inbox 审核闭环 | 🔴 风险任务集中审核，不用追在每个 kiro-cli 对话里打断 |
| Timeline 时间线 | 回看某个任务完整发生了什么，不用翻多个文件 |
| Autopilot（cron + webhook + API） | 常规巡检任务（每日 `check:affected`、每周归档 dev-log）自动化 |

Skill 晋升虽在 multica 文档里不算独立模式，但**对 AAF 最核心的价值正是这里**：每次高风险任务做完都沉淀一条可复用 skill，越用 kiro-cli 越聪明，这是规范驱动开发的"复利引擎"。

### 9.4 最小可行目标（Definition of Done）

Phase 1 做完的成功标准是一句话——**"作者可以不打开任何 IDE 或编辑器，只看 Web 页面就掌握当前 Epic 的进度、所有活跃任务的派发状态、以及谁卡在哪里需要我决策"**。

具体交付物（按重要性排序）：

1. **Dashboard 页面**：v0.1.0 进度条 + 活跃任务泳道 + 等待审核队列 + 上下文健康度一屏
2. **Task Timeline 页面**：选中某任务后看到完整 artifact 链接、dispatch 记录、activity 流水
3. **Review Inbox 页面**：🔴 任务审核请求列表 + 一键批准/退回
4. 以上 3 个页面基于文件系统扫描 + git log 即可（不建表）

**放进 Phase 1 但不硬要做**：
- Skill 晋升 UI（Phase 3 功能，但如果数据积累够了可以提前）
- AAF CLI（Phase 1 骨架命令，实现难度低就做，否则跳过）

**明确不做的**：
- 实时 WebSocket（Phase 2 才做，Phase 1 用轮询够了）
- 任何 DB 持久化（Phase 2 才做）
- Autopilot（Phase 3 才做）
- 多 worktree 并行（Phase 4 才做）

### 9.5 最大风险与对冲

| 风险 | 对冲 |
|------|------|
| kiro-cli 不提供 hooks/webhook，Phase 3 Autopilot 做不了 | Phase 1/2 不依赖 hooks，可先做；Autopilot 延后到 v2.0 与框架 Autopilot 能力一起落地，或用外部 Cron + 文件系统监听替代 |
| 作者同时要开发 AAF 框架 + 开发控制台，精力分散 | Phase 1 只做文件扫描 + Web UI 三个页面，成本 1-2 周可控；AAF-024 规范闭环与控制台 Phase 1 可并行，互为支撑 |
| Web UI 自己就是 AAF 开发任务之一（`apps/webui`），控制台复用它意味着先有鸡还是先有蛋 | webui 基础能力（登录页/聊天界面）在 v0.1 的 AAF-020/021 规划内，控制台作为 AAF-025 在同版本叠加路由，天然有序 |
| 设计文档写得太详细导致实际落地走样 | 本文档标记为**草案**，Phase 1 启动前 architect 先做一次可行性评审，必要时按实际简化 |
| Phase 2/3 依赖的 Polymorphic Actor + 任务状态机至今未落地，可能延后整个控制台后续进度 | Phase 2/3 不纳入 v0.1；这两个前置规范随 AAF 框架设计进度推进，控制台按自身节奏在具备前置条件时启动下一阶段 |

### 9.6 下一步动作

1. **本文档提交为 Phase 1 设计基线**（`status: 草案` → architect 评审后改为 `讨论中`）
2. **登记 AAF-025 Epic**：在 `docs/task/backlog.md` 新增 AAF-025 "协作控制台基础版"，纳入 v0.1.0 迭代
3. **与 AAF-020/021 协同**：控制台 Phase 1 在 webui 登录页 / 聊天界面的基础上叠加 `/console` 路由，前端 agent 派发时注意顺序（先 AAF-020/021 → 再 AAF-025）
4. **Phase 2 前置准备**：Polymorphic Actor 与任务生命周期状态机的设计稿在 v0.1 后半程由 architect 产出，为 v1.0 启动 Phase 2 打好地基
5. **Phase 3 绑定 v2.0**：v2.0 启动框架元引擎化时，Autopilot 能力作为专项引擎之一落地，控制台 Phase 3 同步接入


---

## 10. 附录：数据表字段速查

Phase 2+ 结构化存储的 14 张核心表 + 4 张 AAF 特有表。仅列关键字段，完整 DDL 由 architect 在 Phase 2 启动前产出。

### 10.1 借鉴自 multica（10 张）

| 表 | 关键字段 |
|----|---------|
| `user` | id · email · name · avatar_url · created_at |
| `agent` | id · role (enum: kiro_default/product/architect/developer-api/...) · yaml_path · resources_json · status (idle/working/blocked/offline/archived) · max_concurrent |
| `epic` | id · code (AAF-xxx) · iteration_version · title · status (backlog/in_progress/done) · priority · p_rank (P0/P1/P2) |
| `task` | id · epic_id · seq (#N) · title · status (queued/running/...) · assignee_type · assignee_id · creator_type · creator_id · parent_task_id · acceptance_criteria (JSONB) · risk_level (🟢🟡🔴) · due_date · position |
| `comment` | id · task_id · actor_type · actor_id · type (comment/status_change/system) · content · parent_id · created_at |
| `agent_run` | id · task_id · agent_id · status (queued→dispatched→running→completed/failed/cancelled/blocked) · session_id · work_dir · context · result · started_at · finished_at |
| `task_message` | id · run_id · seq · type (thinking/tool_call/output/error) · tool · input (JSONB) · output (JSONB) |
| `chat_session` | id · agent_id · session_id · work_dir · unread_since · archived |
| `inbox_item` | id · recipient_actor_type · recipient_actor_id · type (approval_request/blocker/completion_report/context_warning) · severity (action_required/attention/info) · task_id · read · archived |
| `activity_log` | id · actor_type · actor_id · action · target_type · target_id · details (JSONB) · created_at |

### 10.2 Autopilot 三件套（Phase 3+）

| 表 | 关键字段 |
|----|---------|
| `autopilot` | id · title · assignee_agent_id · execution_mode (create_task/run_directly) · task_title_template · concurrency_policy (skip/queue/replace) |
| `autopilot_trigger` | id · autopilot_id · kind (schedule/webhook/api) · cron_expression · timezone · webhook_token · next_run_at |
| `autopilot_run` | id · autopilot_id · trigger_id · status (pending→task_created→running→completed/failed/skipped) · task_id · started_at · finished_at |

### 10.3 Skill / ADR

| 表 | 关键字段 |
|----|---------|
| `skill` | id · slug · file_path (`.kiro/skills/{slug}/SKILL.md`) · name · description · source (manual/promoted_from_devlog) · source_task_id · created_at |
| `adr` | id · number (ADR-001) · title · status (proposed/accepted/superseded) · file_path (`docs/design/adr/ADR-xxx.md`) · linked_hard_rules (JSONB) · created_at |

### 10.4 AAF 特有（4 张）

| 表 | 关键字段 |
|----|---------|
| `artifact` | id · task_id · type (requirement/design/dev-log/test-report/review/audit) · file_path · author_actor_type · author_actor_id · version · created_at |
| `dispatch_log` | id · task_id · dispatcher_actor_id · risk_level (🟢🟡🔴) · agent_chain (JSONB: [product, architect, developer-api, tester, qa]) · dispatched_at · expected_duration · actual_duration |
| `context_usage` | id · agent_id · run_id · percent · token_used · token_limit · warned (bool, 是否超 50%) · recorded_at |
| `regulation_check` | id · check_type (dependency/ci_target/link/archunit) · rule_description · passed · failure_detail · severity · ran_at |

---

## 11. 尾声

协作控制台的设计可以归结为一句话：**把 "人 + AI Agent 并肩工作" 这件事，从隐形的对话变成可视的协作台**。

所有功能都是围绕这个核心展开：

- 为了让 AAF 作者和 kiro-cli 的 11 个 agent 共享同一个任务看板 → **Polymorphic Actor**
- 为了让作者在任意时刻能看到 "这个任务卡在哪" → **任务生命周期状态机**
- 为了让 🔴 风险任务集中审核不打断每个对话 → **Inbox 审核闭环**
- 为了回看某个任务完整发生了什么 → **Timeline 时间线**
- 为了常规巡检任务自动化 → **Autopilot**（v2.0+）
- 为了把每次成功经验沉淀为可复用知识 → **Skill 晋升**
- 为了让 AAF 今天的自开发工具，就是明天的产品能力 → **同一套实现服务两类用户**

这是 AAF 元引擎思想的首个具体落地：**"用 AAF 的核心能力开发 AAF 本身"**，工具与产品同源，规范即共识。

当 v2.0 开放给 AAF 框架用户时，他们看到的控制台界面 / 数据模型 / 交互模式，就是 AAF 作者过去一年用过的同一套——经过实战打磨过的工具，不需要从零设计产品。
