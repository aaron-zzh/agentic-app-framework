# 路线图

版本里程碑计划。详细任务见 [backlog.md](../task/backlog.md)。

> 规划版本时请先参考 [产品概述](../explanation/product-overview.md)，确保版本目标与产品方向一致。

## v0.1.0 — AI 协作开发基础设施（传统 MVC 过渡期）

> 目标：以传统 MVC 架构搭建文档管理系统 + 聊天协作界面 + 用户权限模块 + 协作控制台界面，验证并优化 AI 与人类对等协作的开发流程，为后续引擎化打地基。
>
> **阶段定位**：v0.1.x 与 v1.0 都保持"传统 MVC + AI 协作"定位；**v2.0 开始引擎化**；**v3.0+ 转向元引擎无代码**。本版本不追求元引擎抽象。
>
> 周期：2026-05-03 ~ 2026-05-30（4 周）
>
> 详细计划：[aaf-v0.1.0.md](../task/aaf-v0.1.0.md)

| 里程碑 | 内容 | 状态 |
|--------|------|------|
| 项目模块结构 | Maven 多模块（aaf-common/framework/modules/server）、Flyway 初始化、环境配置分离 | 进行中 |
| 文档管理系统 | PostgreSQL + Neo4j 块状文档存储、CRUD、版本快照、全文检索、本地双向同步 | 待开始 |
| 聊天协作界面 | Spring AI 流式对话、AI Tool 修改文档、WebSocket/SSE 推送、Next.js 聊天界面 + Lexical 编辑器 | 待开始 |
| Auto Dev 监控 | 多 Agent 代码生成 + kiro-cli 上报 + SSE 实时展示 + 监控面板 | 待开始 |
| 用户与权限模块 | 注册/登录（JWT）、RBAC、Spring Security、Next.js 登录页 | 待开始 |
| 开源授权控制 | 启动时 JWT 校验 + 分散式权限耦合，零运行时开销的离线 Premium 权限管理 | 待开始 |
| 协作控制台界面（基础版） | Web 仪表板 + Task Timeline + 审核 Inbox 三页面，文件扫描 + git log 驱动；**只读**、不建 DB、不引 WebSocket/Autopilot | 待开始 |

---

## v1.0 — 传统 MVC 完善版（待规划）

> 目标：在 v0.1 过渡期验证基础上，把"AI + 人类 MVC 协作"做成稳定可交付的产品形态；**仍保持传统 MVC 架构**，不引入专项引擎。
>
> 预计周期：待定（v0.1 发布后规划）

候选方向（待 product 细化）：

- v0.1 业务需求的完整实现与稳定化（文档系统 / 聊天 / Auto Dev / 用户权限 / 控制台 Phase 1 达到可发布）
- **协作控制台 Phase 2**：DB 持久化 + WebSocket + 审核闭环（[详见设计](../design/framework/engine/auto-dev.md#phase-2v10--结构化存储--审核闭环)）
- AI 协作流程的完整规范落地（改进意见中"待评估"条目批量评估后的采纳项）
- 发布与使用文档、快速开始示例
- CI/CD 与质量门控自动化
- Polymorphic Actor + 任务生命周期状态机的设计稿产出（为 v2.0 落地打地基）

---

## v2.0 — 引擎化（愿景）

> 目标：把 v0.1/v1.0 的传统 MVC 实现逐步引擎化，引入专项引擎并建立共享包基础，让协作能力从"页面 + 文件"升级到"调度 + 事件流"。
>
> 预计周期：待定（v1.0 发布后启动规划）

候选里程碑（待 product 细化）：

| 候选方向 | 对应改进意见 |
|---------|-------------|
| 工作流引擎（Flowable）落地 | meta-engine.md 占位，v2.0 实现 |
| 向量库 / 语义检索（PgVector） | 知识库引擎 v2.0 启用 |
| Autopilot 触发机制（cron + webhook + API） | 框架 Autopilot 能力 |
| 协作控制台 Phase 3（Autopilot + Skill 晋升 UI） | 与上一条天然配合 |
| Polymorphic Actor 数据模型落地 | improvements.md 框架能力区锚点项 |
| 任务生命周期状态机 + 并发策略 | improvements.md 框架能力区 |
| packages/ 首个共享包 + ArchUnit | P2.3 |
| MCP server 标准化 | 框架层能力 |

---

## v3.0+ — 元引擎 / 无代码（愿景）

> 目标：转向纯元引擎架构——DSL 驱动执行、意图直接转为执行、普通用户可视化搭建工作流/技能/知识库，无需写代码。
>
> 前置：v2.0 引擎化完成，各专项引擎稳定；Polymorphic Actor 贯穿业务实体；协作控制台完成 Skill 晋升与 Autopilot。
>
> 规划时机：v2.0 中期启动 v3.0 规划。

候选方向：

- DSL 多范式 / 分层 / 分域完整落地，成为面向用户的意图表达层
- 元引擎执行调度器、状态管理器、置信度门控器、自进化引擎上线
- 协作控制台 Phase 4（多 agent 并行，Worktree + 共享 PostgreSQL P3.1 方案）可选
- 无代码可视化编排（工作流 / 技能 / 知识库 / 组件）
