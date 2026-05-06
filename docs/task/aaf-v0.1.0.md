---
level: Practice
layer: Product
purpose: AAF v0.1.0 版本迭代计划
status: active
version: "0.1.0"
date: 2026-05-03
author: AaronZZH
scope:
  includes:
    - v0.1.0 业务需求与进度
gains:
  - 能了解当前版本开发进度
---

# AAF v0.1.0 迭代计划

> **目标**：**以传统 MVC 架构**搭建自动化开发平台与初步文档系统，验证并优化 "AI 与人类对等协作" 的开发流程，为 `aaf-auto-dev` 全流程能力打地基，同时提供可视化的协作控制台让人类用户监督和指挥 kiro-cli 智能体。
>
> **阶段定位**：v0.1.0 是**过渡期**——传统 MVC + AI 能力增强的组合；**v2.0 才开始逐步引擎化**（引入 Flowable / 向量库 / MCP / Agent Sandbox 等专项引擎）；**v3.0+ 转向纯元引擎 / DSL 驱动 / 无代码开发**。v0.1.x 与 v1.0 都保持 "传统 MVC + AI 协作" 定位；本版本**不追求**元引擎级抽象，任何提前引入元引擎特性的建议都需要走"过渡期是否仍应保留传统实现"评估。
>
> **核心假设**：在传统 MVC 上验证"人 + AI 协作做 MVC 应用"可行后，后续才有条件把协作模式迁移到引擎层，最终过渡到元引擎。
>
> v0.1.0 是**元引擎骨架 + AI 协作开发基础设施**：五层架构全部建包占位，重点模块有传统实现；文档管理系统作为核心基础设施，支撑 AI 协作开发流程；聊天协作界面验证 AI 直接操作文档的可行性；Auto Dev 监控验证多 Agent 代码生成流水线；协作控制台让人类以 Web 方式监督 agent；用户权限和开源授权控制为后续商业化奠基。
>
> **周期**：2026-05-03 ~ 2026-05-30（4 周）
>
> **架构设计**：[后端技术选型](../design/apps/service/tech-stack.md)

## 业务需求

> 每条业务需求对应 backlog 中一个或多个用户故事（AAF-XXX），技术任务拆分在各用户故事目录下的 `tasks.md`。

### 项目基础框架搭建

搭建前后端开发骨架：后端建立 Maven 多模块结构（aaf-dependencies / aaf-common / aaf-framework / aaf-auto-dev / aaf-api），完成 Flyway 数据库迁移初始化和环境配置分离；前端初始化 Next.js 16 + TypeScript 项目（apps/webui），配置 Nx monorepo 集成。为后续所有模块开发提供基础，其他所有 Epic 依赖此项。

初始数据库表：sys_user、doc_document、autodev_request、autodev_generated_code、autodev_execution_log。

- 对应用户故事：[AAF-023](v0.1.0/AAF-023/) — [技术任务](v0.1.0/AAF-023/tasks.md)

### 文档管理系统

基于 PostgreSQL（内容）+ Neo4j（关系图谱）实现块状多层次网络文档存储，支持文档 CRUD、版本快照、全文检索、文档关系管理，以及与本地文件系统的双向同步。

- 对应用户故事：AAF-XXX（待 product 拆分并录入 backlog）

### 聊天协作界面

基于 Spring AI 实现流式对话接口，AI 可通过 Tool 直接修改文档，文档变更通过 WebSocket/SSE 实时推送。前端（Next.js）提供聊天界面和基于 Lexical 的文档编辑器，支持在线查看和编辑并同步到本地文件。

- 对应用户故事：AAF-XXX（待 product 拆分并录入 backlog）

### Auto Dev 监控

基于 Spring AI 实现多 Agent 代码生成（规划 → 编码 → 审查），支持 kiro-cli 上报执行日志，通过 SSE 实时推送执行状态。前端提供 Auto Dev 监控面板，展示触发生成、实时执行状态和历史记录。

- 对应用户故事：AAF-XXX（待 product 拆分并录入 backlog）

### 用户与权限模块

实现用户注册/登录（JWT）、角色权限管理（RBAC）、Spring Security 集成，以及 Next.js 登录页。作为 AI 协作开发流程的压力测试场景，验证从需求文档到代码的完整流水线。

- 对应用户故事：AAF-XXX（待 product 拆分并录入 backlog）

### 开源授权控制

框架开源，通过启动时一次性 JWT 校验（RS256，公钥内置）+ 分散式权限耦合实现零运行时开销的离线 Premium 权限管理。合法用户放置 JWT 文件即可开箱即用，无需联网、无设备绑定。

- 对应用户故事：AAF-XXX（待 product 拆分并录入 backlog）

### 协作控制台界面（基础版）

面向一人作者的协作控制台 Web 界面（`apps/webui` 下新增路由），让作者不必逐个打开 agent 会话，就能在一屏掌握：当前 Epic 进度 / 所有活跃任务的派发状态 / 等待审核的 🔴 高风险任务 / 所有 agent 上下文占用健康度。**仅实现只读仪表板 + Task Timeline + 审核 Inbox 三个页面**，数据来源是文件系统扫描（`docs/task/` + `.kiro/`）+ `git log`，不建数据库表、不引入 WebSocket、不引入 Autopilot。后续阶段：Phase 2（DB 持久化 + WebSocket + 审核闭环）在 **v1.0** 落地；Phase 3（Autopilot + Skill 晋升）与 **v2.0** 框架 Autopilot 能力同步；Phase 4（多 agent 并行）留 **v3.0+**。详细设计见 [协作控制台设计](../design/framework/engine/auto-dev.md)。

- 对应用户故事：AAF-XXX（待 product 拆分并录入 backlog，暂记为 AAF-025）

### 协作基础设施优化

把"真理源归一"和"流程落地性"两件事做透，消除当前规范驱动项目中的反向漂移：AGENTS.md / steering / docs/reference/team 三方文档真理源归一；Agent 派发触发条件按风险分级明确；architect 代码审查与 qa 过程审计边界分离；规范-代码一致性自动检查；ADR 目录建立让决策可追溯；规范文档 Front Matter 规范化。本条需求在迭代中期（2026-05-05）根据协作实践反思新增。

- 对应用户故事：[AAF-024](v0.1.0/AAF-024/) — [技术任务](v0.1.0/AAF-024/tasks.md)

## 迭代范围决策

### v0.1.0 不引入的技术

| 技术 | 原因 | 计划版本 |
|------|------|----------|
| Redis | 单机运行，JWT 无状态认证不需要 | v2.0（引擎化） |
| Flowable | 工作流引擎建包占位，本迭代无工作流场景 | v2.0（引擎化） |
| PgVector/向量库 | 知识库引擎建包占位，本迭代无语义检索场景 | v2.0（引擎化） |
| CRDT/Yjs | 实时协同建包占位，本迭代单用户编辑 | v3.0（元引擎化后评估） |

### 各模块实现范围

**aaf-framework / engine**

| 引擎 | v0.1.0 实现 |
|------|------------|
| 文档引擎（doc） | 块状存储（PostgreSQL）+ 关系图谱（Neo4j）+ 全文检索 + 本地双向同步 |
| 监控引擎（monitor） | 执行日志记录、SSE 事件推送 |
| 权限引擎（permission） | JWT 生成/验证、RBAC 角色权限、Spring Security 集成 |
| 授权引擎（license） | 启动时 RS256 JWT 校验、全局 LICENSE 对象、分散式权限锚点 |
| 聊天引擎（chat） | Spring AI 流式对话、Tool 注册与调用（文档读写 Tool） |
| 其余引擎 | 建包 + `package-info.java` 说明职责 |

**aaf-framework / intelligent**

| 层 | v0.1.0 实现 |
|----|------------|
| Core | Spring AI ChatClient 封装，支持 DeepSeek/OpenAI |
| Agent | `Agent` 接口 + `SequentialAgentExecutor` 顺序编排 |
| 其余层 | 建包占位 |

**aaf-auto-dev**：PlanningAgent / CodingAgent / ReviewAgent 顺序执行，kiro-cli 监控接口

**aaf-api/module**：用户（注册/登录/JWT/RBAC）、文档（CRUD/版本/Neo4j/检索/同步）、聊天（历史/Tool/SSE）

**apps/webui**：登录页、聊天协作界面（Lexical 编辑器 + SSE）、Auto Dev 监控面板

### 不做什么

- 不做 Redis 缓存
- 不做 Flowable 工作流执行
- 不做向量检索和语义搜索
- 不做实时多人协同编辑（CRDT）
- 不做多 Agent 并行执行
- 不做沙箱代码执行
- 不做移动端、微信端适配
- 不做自进化闭环

## 变更记录

| 日期 | 变更内容 | 原因 |
|------|---------|------|
| 2026-05-03 | 初始版本，确定迭代范围 | 版本规划讨论 |
| 2026-05-03 | 补充 Auto Dev 监控需求，完善数据库初始表清单 | 参考 v0 计划调整 |
| 2026-05-03 | 项目模块结构对齐 java-module-structure.md（aaf-api 替代 aaf-modules/apps/service） | 规范一致性 |
| 2026-05-05 | 新增业务需求"协作基础设施优化"（对应 AAF-024） | 迭代中期协作实践反思，登记真理源归一 / 派发规则 / 审查边界等改进 |
| 2026-05-05 | AAF-024 #12 完成：合并 architect 与 qa 审查边界，qa 不再查代码内容，产出 `process-audit.md` | 消除 architect code-review 与 qa process-audit 的规范合规重叠 |
| 2026-05-05 | AAF-024 #14 完成：建立 `docs/design/adr/` 目录，迁移 3 条决策为独立 ADR-001/002/003，3 份测试规范回链「起因：ADR-NNN」 | 决策真理源归一；支撑规则溯源（multica P1.5）|
| 2026-05-05 | AAF-024 #16 完成：技术任务迁出迭代文件，放入各用户故事的 `tasks.md`；同步修 README 笔误 | 修正违反任务管理规范"迭代文件不包含技术任务"的结构问题 |
| 2026-05-06 | ① 目标段补"传统 MVC 过渡期"阶段定位，明确 v0.1.x 与 v1.0 保持传统 MVC、v2.0 引擎化、v3.0+ 元引擎化 ② 新增业务需求"协作控制台界面（基础版）"，纳入 v0.1 范围 ③ 迭代范围决策里"v0.2/v0.3"计划版本号改为"v2.0/v3.0" ④ 保留"开源授权控制"业务需求 | 明确 v0.1 过渡期定位 + 协作控制台纳入本迭代 + 版本路线与 roadmap 对齐（主版本语义） |
