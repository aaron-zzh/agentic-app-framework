---
level: Practice
layer: Model
purpose: 团队规范目录索引，团队架构与角色的唯一真理来源
status: published
version: 1.0.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 补充 Front Matter
---

# 团队管理（唯一真理来源）

价值驱动 敏捷开发 CMMI5 AI结对编程 规范驱动

> **本文档及其子文档是 AAF 人机协作团队的唯一真理来源。**
> 组织架构、角色分工、协作流程、过程规范均以此目录为准。
>
> `AGENTS.md` 和 `.kiro/steering/collaboration.md` 只提供**摘要 + 硬约束**，详细规范在此目录下的具体文件。两者发生冲突时以本目录为准并在 [改进意见](../../prd/improvements.md) 中记录。
>
> **修改约束**：本目录下的规范文档**只能由协调者修改**。其他 agent 发现问题在 `dev-log.md` 或 `improvements.md` 中提出，由协调者评估后更新。

## 团队定位

**核心理念**：价值驱动 · 敏捷开发 · 规范驱动 · AI 结对编程

**目标**：通过人机协作，以规范为共识、文档为沟通媒介，高效交付生产级 AI 原生应用。

## 组织架构

矩阵式研发团队（职能 + 项目），每个岗位由人类与 AI 智能体协同承担。

| 岗位 | 岗位职责 | AI 智能体 | AI 协作模式 | 详细说明 |
|------|---------|-----------|------------|---------|
| **协调者** | 任务拆分与派发、进度跟踪、集成验证、过程改进 | kiro_default | AI 执行拆分/派发/集成验证/提交；人类把控方向和决策 | [roles/coordinator.md](roles/coordinator.md) |
| **产品经理** | 需求分析、用户故事、验收标准、版本规划 | product | AI 结构化需求；人类提供业务判断和优先级 | [roles/product.md](roles/product.md) |
| **架构师** | 技术设计、接口定义、架构决策、规范制定、代码审查 | architect | AI 输出设计方案并执行代码审查；人类审核高风险设计 | [roles/architect.md](roles/architect.md) |
| **开发工程师** | 编码实现、验证（单元测试/自测）、代码重构 | developer-* (3个) | AI 按端并行编码；人类处理复杂逻辑和跨端协调 | [roles/developer.md](roles/developer.md) |
| **测试工程师** | 确认（验收测试/集成测试）、需求覆盖验证 | tester | AI 对照验收标准生成测试；人类设计策略和边界场景 | [roles/tester.md](roles/tester.md) |
| **质量工程师** | 过程审计、规范合规检查、度量分析、质量门控 | qa | AI 检查过程合规和规范遵守；人类审计关键节点 | [roles/qa.md](roles/qa.md) |
| **UI 设计师** | 界面与交互设计、设计规范 | designer | AI 辅助设计稿生成和规范检查；人类主导创意和交互决策 | [roles/designer.md](roles/designer.md) |

### 验证与确认

| | 验证（Verification） | 确认（Validation） |
|---|---|---|
| **问题** | 做对了吗？（符合设计规格） | 做的对吗？（满足用户需求） |
| **对照物** | design.md、接口规格 | requirement.md、验收标准 |
| **方法** | 单元测试、自测冒烟、静态分析 | 验收测试、集成测试、用户演示 |
| **执行** | **开发工程师** | **测试工程师** |
| **技术审查** | **架构师**（代码审查、设计符合性） | **架构师**（方案合理性） |
| **过程审查** | **质量工程师**（验证过程是否充分） | **质量工程师**（确认过程是否充分） |

> 三层分离：执行者做事、架构师审技术、质量工程师审过程。质量工程师独立于执行和管理，确保 CMMI5 PPQA 独立性。

### 开发团队分工（按架构分层 + 端划分）

| AI 智能体 | 技术栈 | 负责范围 |
|-----------|-------|---------|
| developer-webui | Next.js / React / TypeScript | 前端 Web 应用 |
| developer-uniapp | UniApp / Vue | 跨端移动应用 |
| developer-service | Java / Spring Boot / Spring AI | 后端全栈（L1-L5 后端） |

> 划分原则：按架构分层 + 端划分，层不随模块增长而变化。同层模块技术同质，一个 developer 处理。

## 迭代过程

五阶段迭代（准备 → 启动 → 执行 → 发布 → 总结），周期 2-4 周。融合敏捷开发 + CMMI5 过程域 + 人机协作

详见 → [过程规范](process-standard.md)

## 协作规范

AI 不等待人类审批，完成即提交；人类按风险等级按需审查（🟢跳过 / 🟡抽查 / 🔴必审）。

任务流水线：想法 → product → architect → developer → tester → qa → 质量门控 → 交付

详见 → [协作规范](collaboration-standard.md)

## 子文档索引

| 文档 | 说明 |
|------|------|
| [协作规范](collaboration-standard.md) | 人机协作流程、审核机制、质量门控、反馈方式 |
| [过程规范](process-standard.md) | 迭代开发四阶段、各阶段产出与负责人 |
| [沟通规范](communication-standard.md) | 沟通机制与渠道 |
| [度量规范](measurement-standard.md) | 需求数量、工时、缺陷、团队生产率等度量指标 |
| [基本原则](team-principle.md) | 团队协作基本原则 |
| [上下文管理](context-management-standard.md) | Kiro 四层上下文策略与配置方式 |
| [角色定义](roles/) | 各角色（product/architect/developer/tester/reviewer）的详细职责与输出要求 |

## 任务管理

[docs/task/backlog.md](../../task/backlog.md) 是所有待办的唯一来源。

- 任务编号格式 `AAF-{三位序号}`，全局递增
- 提交时在脚注中关联：`Task: AAF-XXX`
- 详见 [任务管理规范](../../task/Readme.md)

## 引用本文档的地方

以下文件中的团队协作内容应与本文档保持一致，后续将逐步改为引用链接：

- [AGENTS.md](../../../AGENTS.md) — 组织架构章节
- [.kiro/steering/collaboration.md](../../../.kiro/steering/collaboration.md) — 协作规则

## 更多参考

- [AI 开发指南](../../guide/ai-development-guide.md) — 完整指南
- [任务看板](../../task/backlog.md) — 所有待办的唯一来源