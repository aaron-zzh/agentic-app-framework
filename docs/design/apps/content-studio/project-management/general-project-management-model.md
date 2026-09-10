---
level: Theory
layer: Model
purpose: 定义 AAF 以元数据和领域概念为基础、以价值实现、可信交付与可运行能力为核心的通用 AI 原生项目管理模型
status: draft
version: 1.7.2
date: 2026-09-10
author: AaronZZH & Kiro
tags:
  - 通用项目管理
  - AI 原生
  - 项目工程
changelog:
  - 2026-09-10 | v1.7.2 将核心特性展开为逐项定义的无序列表，明确各特性的语义与职责边界
  - 2026-09-10 | v1.7.1 在文档开头增加价值主张和核心特性摘要，突出智能文档、对话生成、可信交付与可运行能力
  - 2026-09-10 | v1.7.0 新增项目实践域，定义其与项目工程主链、五阶段迭代循环及项目方法版本的正交关系
  - 2026-09-10 | v1.0.0 融合项目管理实践、AI 原生理念、领域概念治理和元数据优先设计，重写通用项目管理模型
dependencies:
  - ./ai-native-project-management.md
scope:
  includes:
    - 通用项目管理的领域边界和核心概念
    - 项目领域语义元数据及治理方式
    - 项目实践域及其与主链、阶段和方法版本的关系
    - 价值、规范、交付物、工作、证据和决策闭环
    - 工作流、文档、模板和智能体组合的能力组装、发布、运行引用与迭代升级
    - 人类、AI 智能体和确定性系统协作模型
    - 不同项目方法和业务领域的扩展机制
  excludes:
    - 数据库表、REST API 和具体类结构
    - 项目管理模块的实施任务拆分
    - Content Studio 或其他单一业务的产品细则
    - PMBOK、CMMI、RUP 等体系的完整教程
gains:
  - 能解释通用项目管理模型如何把复杂事项转化为可执行工程系统
  - 能定义项目核心领域对象、关系、不变量和真理源
  - 能定义跨阶段项目实践域，并映射到主链语义、方法版本、证据和质量门
  - 能设计供 AI 理解和操作项目数据的领域语义元数据
  - 能区分领域元数据、资源能力目录、执行元数据和 UI 元数据
  - 能判断业务领域应复用、扩展还是关联通用项目模型
  - 能设计交付物从版本化制品组合、质量门、发布、独立运行到迭代升级的追溯链
---

# 通用 AI 原生项目管理模型

**价值主张：** 以元数据和领域概念为语义基础，以价值目标为牵引、交付物契约为承诺核心、规范化智能文档为人机协作载体，通过对话式生成交互组织人、Agent 和确定性系统进行规范驱动、证据验证的持续迭代，将模糊、复杂、跨领域且充满不确定性的事项转化为可理解、可分解、可执行、可验证、可调整、可验收、可发布、可运行、可观测、可升级和可沉淀的工程能力。

**核心特性：**

- **语义原生**：以元数据和领域概念定义项目对象、关系、状态、动作和规则，让人和 AI 共享同一语义。
- **文档原生**：以规范化智能文档承载意图、规范、推理和协作上下文，并通过版本引用连接结构化项目契约。
- **对话生成**：用户通过对话表达意图，AI 生成和调整文档、交付图、工作计划、项目视图与操作候选。
- **价值驱动**：所有规范、交付物、工作和决策都必须追溯到价值目标或明确的治理目的。
- **交付物中心**：以交付物契约定义结果承诺、责任和验收边界，不以任务数量或活动完成代替可信交付。
- **规范驱动**：项目依据版本化规范和基线执行，正式变化必须形成变更请求、决策和新版本。
- **证据闭环**：工作完成、交付验收、能力发布和价值实现分别由可追溯证据证明。
- **人机协同**：人类负责价值判断和最终责任，AI 负责理解、生成与分析，确定性系统负责规则、状态和审计。
- **可运行交付**：将文档、模板、工作流、智能体和工具组合组装为不可变能力版本，交付后可由专业运行时直接执行。
- **运行验证**：使用真实运行观测验证稳定性、成本、质量和价值，并决定继续、升级、回退或退役。
- **迭代演进**：通过多轮五阶段迭代循环产生新基线和能力发布，持续升级但不覆盖历史版本。
- **通用扩展**：稳定项目内核通过实践域、方法版本和专业资源引用适配软件、AIGC、研究、营销等领域。

## 文档定位

> AAF 不是在简单开发一个“项目管理软件”，而是在建立一套 AI 原生项目工程系统。

传统项目管理软件主要将任务、计划、进度、资源、审批和报表数字化，帮助管理者了解“项目进行到哪里”。AI 原生项目工程系统则面向复杂事项的完整转化过程：让人类、AI 智能体和确定性系统在统一领域语义、版本化规范、可执行交付物和可信证据的基础上，共同完成理解、分解、执行、验证、调整和沉淀。

| 项目管理软件 | AI 原生项目工程系统 |
| --- | --- |
| 记录和展示项目活动 | 将复杂事项转化为可治理的工程系统 |
| 以任务、计划和进度为中心 | 以价值、交付能力和证据为中心 |
| 主要依赖人解释数据语义 | 通过领域概念和元数据让人和 AI 共享语义 |
| 人负责规划、协调、执行和汇报 | 人、AI 智能体与确定性系统按能力和决策权协作 |
| 交付文件或静态结果 | 交付可验收、可发布、可运行和可升级的能力 |
| 项目完成后归档结果 | 通过真实运行验证价值，并将成果沉淀为可复用能力 |
| AI 是附加的辅助工具 | AI 是受到权限、证据和质量门治理的工程参与者 |

本文由此定义一套把复杂事项工程化的通用机制：

> 将模糊意图转化为明确目标，将目标转化为带契约的交付物，将工作流、文档、模板、智能体组合等制品组装为可发布、可运行的能力，并通过证据、决策、运行观测和反馈持续降低不确定性，最终产生可验证价值。

软件是载体，项目工程能力才是目标。AAF 的本质不是让 AI 更高效地维护传统项目表格，而是建立一套能够理解复杂事项、组织可信执行、验证真实价值并持续沉淀能力的工程系统。

[AI 原生项目管理](ai-native-project-management.md)解释为什么项目管理从监督人完成活动转向治理可信价值实现系统；本文进一步回答这个系统由哪些领域概念组成、AI 如何理解这些概念，以及不同业务如何在稳定内核上扩展。

## 核心判断

传统项目管理通常将项目定义为：

> 项目是为创造独特的产品、服务或成果而开展的临时性工作。

这一定义明确了项目的临时性和结果独特性，但项目管理的意义不只是按时完成一组计划活动。它要解决的根本问题是：

> 如何把一个模糊、复杂、跨领域、充满不确定性的事情，转化为一个可理解、可分解、可执行、可验证、可调整、可沉淀的工程系统。

其中，“可理解”要求概念和边界清楚，“可分解”要求目标能够追溯到交付物和工作，“可执行”要求责任、能力与契约明确，“可验证”要求完成判断有独立证据，“可调整”要求变化受版本、风险和决策治理，“可沉淀”要求成果与经验能够成为后续可复用的能力。

AAF 继承传统项目的临时性和独特结果，同时将项目管理对象从计划活动扩展为完整的 AI 原生项目工程系统，其稳定语义骨架和完整转换过程分别见[项目工程概要主链](#项目工程概要主链)与[项目工程主干链路](#项目工程主干链路)。

项目由此被定义为：

> 项目是为解决特定问题或实现阶段性价值目标，在明确边界内临时建立的 AI 原生工程系统。它依据版本化规范和交付承诺，组织人类、AI 智能体与确定性系统，将意图工程化为可验收、可发布、可运行、可观测和可演进的能力，并以独立证据和运行结果验证实际价值。

项目具有明确的开始和结束，但不要求其产物随项目结束而停止。项目可以通过多个 Iteration、Baseline 和交付版本持续升级交付物；项目完成后，已发布能力脱离项目生命周期独立运行，项目保留其定义、发布、证据、决策和价值观测的追溯关系，但不成为长期运行容器。

这个定义包含六个不可分割的维度：

- **价值维度**：为什么做，服务谁，怎样判断值得继续；
- **语义维度**：管理的领域对象是什么，各自有什么边界、关系和规则；
- **交付维度**：需要形成什么结果，怎样才算可接受、可发布；
- **工程维度**：如何把文档、模板、工作流、智能体和专业制品组装为完整能力；
- **运行维度**：交付能力如何被部署、调用、观测和安全演进；
- **治理维度**：如何用证据、风险、权限和决策控制变化。

真正的 AI 原生项目系统必须让 AI 能够准确理解项目对象、关系、状态、约束、能力和证据，并在授权范围内参与定义、规划、执行、验证、调整和学习。

## 项目工程总体模型

项目工程总体模型包含两类互补视图：项目工程主链回答“复杂事项依次发生什么语义转换”，五阶段迭代循环回答“这些转换如何被组织和反复推进”。主链又分为概要骨架和详细展开，两者是同一模型的不同抽象层级，不是两套流程。顶层模型优先使用中文领域名称，后文“核心领域对象”再给出对应的英文规范身份。

### 项目工程概要主链

概要主链是跨领域稳定、便于记忆和检查的项目工程语义骨架：

```text
意图 → 目标 → 规范 → 交付物 → 工作 → 执行 → 证据 → 决策 → 发布 → 运行观测 → 价值 → 学习
```

| 概要语义 | 详细环节 | 定义 | 主要领域模型 | 形成的结果 |
| --- | --- | --- | --- | --- |
| 意图 | 复杂意图 | 发起人通过自然语言、材料或事件表达的原始诉求，可能模糊、矛盾或不完整 | 原始意图、干系人、上下文资源 | 保留未经结构化改写的诉求、来源和初始背景 |
| 意图 | 问题定义 | 将原始意图转化为对现状、差距、影响、边界和假设的结构化描述 | 待解决问题、范围项、约束、假设 | 明确真正需要改变的现实状态及项目边界 |
| 目标 | 价值目标 | 定义项目希望为谁产生什么变化，以及如何判断变化是否发生 | 价值目标、结果指标 | 形成可验证的目标、指标口径、基准值和目标值 |
| 规范 | 项目规范 | 固定项目执行所依据的范围、方法、规则、约束和版本化承诺 | 项目规范、项目方法版本、项目基线版本、项目策略 | 形成经确认且不能被静默修改的共同执行依据 |
| 交付物 | 交付物契约 | 将价值目标转化为明确的结果承诺、责任、验收条件和证据要求 | 交付物、验收标准、责任分配 | 明确必须交付什么、由谁负责以及怎样才算被接受 |
| 工作 | 工作与能力编排 | 将交付物分解为工作、依赖和执行契约，并匹配人、Agent、Workflow、Tool 与外部服务 | 工作包、工作项、依赖、工作契约、参与者、能力 | 形成可执行、可调度、可追溯的项目交付图 |
| 执行 | 人 / Agent / 系统执行 | 各类参与者在授权和契约范围内完成工作，产生实际制品和运行事实 | 参与者分配、执行引用、制品引用、项目事件 | 产生候选成果、执行记录、成本和异常信息 |
| 证据 | 验收证据 | 使用独立、可追溯的事实判断交付结果是否满足强制标准 | 证据、质量门、门禁评估 | 形成通过、附条件通过、退回或终止结论 |
| 决策 | 决策与动态调整 | 根据证据、风险、偏差和新信息决定继续、调整、回退、重新基线或终止 | 风险、问题事项、决策、变更请求、项目基线版本 | 形成可审计决策和新的正式执行依据 |
| 发布 | 能力发布 | 将已验收制品组装并冻结为不可变能力版本，建立专业运行时绑定和回退关系 | 可运行能力、能力发布、运行绑定 | 形成可部署、可切换、可回退且可追溯的正式版本 |
| 运行观测 | 运行观测 | 由专业运行时执行已发布能力，采集行为、质量、成本、异常和上下文事实 | 执行引用、运行遥测、项目事件、证据 | 形成可复现的真实运行记录和价值验证输入 |
| 价值 | 价值验证 | 使用真实运行观测判断结果指标是否改善，区分交付完成与价值实现 | 结果指标、结果观测、证据、决策 | 形成价值已实现、未实现或需要继续观察的判断 |
| 学习 | 知识与能力沉淀 | 从项目事实中提炼经过验证、可审核和可复用的知识、模板、方法与能力 | 回顾、学习资产、改进行动、模板候选 | 将一次性交付转化为组织可复用能力和下一轮项目输入 |

这是一条语义主干，不是只能向右推进的固定工作流。“决策与动态调整”贯穿全程，任何环节出现新证据、风险或语义冲突，都可以形成变更请求和决策，返回问题定义、价值目标、项目规范、交付物契约或工作编排；返回上游后必须产生新基线或新版本，不能覆盖历史事实。

表中的“概要语义”列是两层主链映射的唯一结构化定义。同一概要语义可以展开为多个详细环节，例如“意图”依次展开为“复杂意图”和“问题定义”；详细链路可以继续细化领域对象和关系，但不能改变概要主链的稳定语义。

### 五阶段迭代循环

五阶段迭代循环是组织和推进主干链路的顶层框架。它位于具体方法、领域对象和管理活动之上，为项目规划、状态投影、AI 上下文装配和质量门提供统一坐标。

AAF 参考 RUP 的 Inception、Elaboration、Construction、Transition 四阶段，并增加 Operation 阶段；对应关系为 START→Inception、DESIGN→Elaboration、BUILD→Construction、DELIVER→Transition、RUN→Operation。面向用户和领域模型统一采用简单动作名称：

```text
START → DESIGN → BUILD → DELIVER → RUN
初始      细化       构建       交付       运行
```

| 阶段 | 核心目标 | 阶段里程碑 | 主要领域模型与活动 | 退出依据 |
| --- | --- | --- | --- | --- |
| `START` 初始 | 明确项目价值、范围与可行性 | 生命周期目标（Lifecycle Objectives） | 原始意图、待解决问题、价值目标、结果指标、范围项、干系人、假设 | 目标和边界可验证，关键干系人和继续决策明确 |
| `DESIGN` 细化 | 建立解决方案架构与项目基线，消除关键高风险 | 生命周期架构（Lifecycle Architecture） | 领域模型、项目规范、项目方法版本、交付物、验收标准、风险、项目基线版本 | 正式基线获确认，关键不确定性和高风险已有处置方案 |
| `BUILD` 构建 | 完成交付物构建、集成与验证，形成初始运行能力 | 初始运行能力（Initial Operational Capability） | 迭代、工作包、工作项、工作契约、制品引用、证据、可运行能力 | 候选交付物完整，强制标准已有证据，可以进入正式验收 |
| `DELIVER` 交付 | 完成验收、发布、部署和责任交接，确保版本可用 | 能力发布（Capability Release） | 门禁评估、能力发布、运行绑定、发布记录、回退方案、交接证据 | 不可变版本已发布，运行入口、责任人、观测和回退条件明确 |
| `RUN` 运行 | 验证真实运行的稳定性与价值，决定完成或迭代升级 | 运行价值验证（Operational Value Validation） | 执行引用、运行遥测、结果指标、结果观测、问题事项、回顾、决策 | 形成继续运行、迭代升级、回退、退役或完成项目的可追溯决策 |

阶段里程碑是 `Milestone` 的受控语义角色，用来触发阶段末的正式评估，不是一个到期即自动完成的日期。每次里程碑判断必须形成 `GateEvaluation`，基于退出依据和证据给出通过、附条件通过、退回或终止结论。

`RUN` 是有明确观察目标、周期和退出条件的运行验证阶段，不是由 Project 承担无限期运营。实际调度、执行、扩缩容、重试和内部运行状态仍由 Workflow、Agent、Tool 或专业业务运行时负责；Project 只管理运行版本、观测契约、证据、价值判断和演进决策。

### 迭代与反馈路径

一次完整的五阶段推进构成一轮宏观迭代循环，通常产生一个 CapabilityRelease、一次价值判断或一个终止决策。每个阶段内部又可以包含多个 Iteration：前者表示项目跨阶段的价值工程循环，后者表示为达到阶段目标而执行的短反馈周期。同一 Project 可以执行多轮五阶段迭代循环，以升级同一 Deliverable，但已经发布的 CapabilityRelease 保持不可变。

运行阶段根据证据决定下一步，不固定回到起点：

```text
RUN
├─ 问题、目标或范围变化 → START
├─ 方案、模型或契约变化 → DESIGN
├─ 能力实现需要升级     → BUILD
├─ 仅需重新发布或切换   → DELIVER
├─ 需要继续观察         → RUN
└─ 项目退出条件成立     → COMPLETED
```

阶段质量门也可以触发就近回退，例如 BUILD 返回 DESIGN 修正方案，DELIVER 返回 BUILD 修正交付物。所有回退、跳转和裁剪规则由 `ProjectMethodVersion` 声明并形成 ProjectEvent；项目模板可以调整各阶段活动、迭代数和门禁强度，但不能改变五个阶段的规范语义。

### 状态、阶段、迭代与发布

项目工程同时存在四个正交维度，不能用单一项目状态字段混合表达：

| 维度 | 回答的问题 | 权威表达 |
| --- | --- | --- |
| 项目状态 | 项目承诺当前是否生效，能否继续投入资源 | 框定、已基线、活动、验证、暂停、完成等治理状态及其规范代码 |
| 工程阶段 | 当前主要解决哪一类工程问题 | 初始、细化、构建、交付、运行及其规范代码 |
| 迭代 | 当前短反馈周期承诺完成和验证什么 | 阶段目标、交付范围、工作项和回顾结果 |
| 能力发布 | 当前发布和运行的是哪个不可变能力版本 | 制品组合、证据、运行绑定、切换和回退关系 |

项目可以在活动状态下经历细化、构建、交付和运行，也可以在任一阶段因治理决策进入暂停或取消。项目状态、工程阶段、迭代和能力发布分别演进，并通过领域事件保持追溯。

### 项目实践域

项目实践域是围绕一类稳定项目能力组织的版本化实践集合，兼容传统 CMMI“过程域”的管理意图，但不绑定某个 CMMI 版本或单一方法体系。它说明某类管理问题为什么存在、需要执行哪些实践、由谁负责、产生哪些制品、需要什么证据、如何度量，以及何时触发质量门和决策。

```text
项目实践域版本
├─ 稳定身份、名称、定义和目的
├─ 方法来源与规范引用
├─ 适用项目模式与裁剪条件
├─ 适用概要语义与详细链路环节
├─ 适用工程阶段
├─ 必需与可选实践
├─ 角色与责任
├─ 输入和输出制品
├─ 证据要求
├─ 指标、阈值与数据来源
├─ 质量门与决策规则
└─ 依赖的其他实践域
```

主链、阶段和实践域是三个正交维度：

| 维度 | 回答的问题 | 本质 |
| --- | --- | --- |
| 项目工程主链 | 什么被转化成什么 | 从意图到学习的价值与成果转换链 |
| 五阶段迭代循环 | 当前重点推进哪类转换 | 初始、细化、构建、交付和运行的生命周期框架 |
| 项目实践域 | 用什么成熟能力保证转换可控、可重复 | 跨链路、跨阶段的实践、责任、制品、证据和度量集合 |

```text
主链：意图 → 目标 → 规范 → 交付物 → 工作 → 执行 → 证据 → 决策 → 发布 → 运行观测 → 价值 → 学习
                                      ↑
                                      │ 项目实践域跨环节提供能力保障
                                      │
阶段：START → DESIGN → BUILD → DELIVER → RUN
```

主链环节不是实践域：例如“证据”是项目事实的语义分类，质量与验证实践域则规定如何产生、审查和接受证据。工程阶段也不是实践域：例如风险与机会管理贯穿五个阶段，不能被放入某一个独立阶段。一个主链环节通常受到多个实践域共同治理，一个实践域也通常跨越多个主链环节和工程阶段。

AAF 首批项目实践域建议如下：

| 项目实践域 | 重点作用的主链语义 | 重点覆盖阶段 |
| --- | --- | --- |
| 价值与干系人 | 意图、目标、价值 | 初始、运行，并贯穿全程 |
| 问题、需求与范围 | 意图、目标、规范、交付物 | 初始、细化 |
| 方案与架构 | 规范、交付物、发布 | 细化、构建、交付 |
| 规划与估算 | 规范、工作、执行 | 细化、构建 |
| 能力与协作 | 工作、执行 | 全阶段 |
| 风险与机会 | 目标、规范、工作、证据、决策、价值 | 全阶段 |
| 质量与验证 | 交付物、证据、发布、价值 | 细化、构建、交付、运行 |
| 配置与变更 | 规范、交付物、决策、发布 | 全阶段 |
| 度量与控制 | 执行、证据、决策、运行观测、价值 | 全阶段，重点在构建和运行 |
| 发布与运行 | 发布、运行观测、价值 | 交付、运行 |
| 决策与治理 | 证据、决策 | 全阶段 |
| 学习与改进 | 价值、学习、下一轮意图 | 运行及下一轮初始 |

这些名称表达跨方法稳定的能力关注面，不复制 CMMI、PMBOK 或 DevOps 的完整目录。不同方法来源通过元数据映射到项目实践域，组织可以增加专业实践域，但不得改变主链和五阶段的规范语义。

项目实践域通过项目方法版本进入具体项目，并在基线中固定实际裁剪结果：

```text
项目方法版本 组合 项目实践域版本
项目实践域版本 作用于 主链语义和工程阶段
项目实践域版本 要求 实践、角色、制品、证据、指标和质量门
项目基线版本 固定 本项目采用的实践域版本与裁剪结果
门禁评估 判断 实践要求和退出依据是否满足
```

项目实践域不是新的线性流程、组织部门或独立业务模块。它属于方法与模板层，负责组织可复用的管理能力；具体项目只保存所采用的版本、裁剪结果、执行事实和评估证据。

## 项目管理实践的统一吸收

通用模型不绑定单一方法，而是吸收不同体系解决的稳定问题，再用统一领域概念表达。

| 实践来源 | 主要贡献 | 在本模型中的吸收方式 |
| --- | --- | --- |
| PMBOK 过程与知识领域视角 | 整合、范围、进度、成本、质量、资源、沟通、风险、采购和干系人治理 | 作为项目治理关注面，不直接固化为十套独立流程 |
| PMBOK 原则与绩效域视角 | 团队、干系人、生命周期、规划、项目工作、交付、测量和不确定性共同作用于成果 | 转化为价值、协作、交付、测量和适应性治理机制 |
| CMMI | 能力建设、过程成熟度、数据管理、人员管理、虚拟交付和持续改进 | 将过程域或实践域映射为版本化项目实践域，再以实践、证据、质量门、度量和方法组合落地 |
| RUP | 用例驱动、架构优先、迭代增量、角色活动与制品协作 | 保留迭代、风险前置和制品契约，角色改为可替换 Actor |
| Agile / Scrum | 短反馈周期、价值优先、跨职能协作、持续适应 | 用 Goal、Iteration、Backlog、Review 和 Retrospective 表达 |
| Kanban / Lean | 限制在制品、优化价值流、消除浪费、基于流动改进 | 用工作流状态、WIP 策略、周期时间和阻塞分析表达 |
| DevOps | 自动化、可观测性、持续交付、快速恢复和共同责任 | 用 Execution、Evidence、Gate、Telemetry 和 Recovery 表达 |
| 系统工程 | 需求追溯、接口、验证与确认、配置和变更控制 | 用 ProjectDeliveryGraph、Baseline、Trace、Verification 和 Decision 表达 |
| 领域驱动设计 | 统一语言、限界上下文、聚合、不变量和领域事件 | 用概念注册、领域边界和跨域引用控制通用与专业模型 |

这些体系不应被简单拼接为更重的流程。统一原则是：

- 保留它们试图解决的管理问题；
- 删除依赖人工搬运和重复汇报的低价值活动；
- 将稳定规则转化为元数据、策略、质量门和自动化；
- 将专业方法作为可版本化的项目方法，而不是写死在核心模型中；
- 用证据和真实价值代替形式合规。

## 设计原则

### 元数据优先，而不是 Prompt 优先

AI 不应仅靠字段名、页面文案或临时 Prompt 猜测项目数据语义。每个核心概念、关系、状态和动作都必须具备可查询、可版本化、可追溯的语义元数据。

### 领域概念优先，而不是功能菜单优先

先定义 Project、Goal、Deliverable、Evidence、Decision 等对象的含义、边界和不变量，再设计列表、看板、甘特图和对话入口。界面只是领域模型的投影。

### 价值与交付物双中心

价值目标回答“为什么做”，交付物契约回答“必须产生什么”。任务和执行必须追溯到两者之一，不能成为脱离目标的活动清单。

### 规范驱动与版本化基线

项目正式执行所依据的目标、范围、方法、交付物和质量要求必须形成版本化基线。变化通过 ChangeRequest 和 Decision 产生新版本，不能静默覆盖历史。

### 证据驱动完成

生成、提交和自我声明都不等于完成。交付状态必须由验收标准和证据推导；项目完成还需要区分交付完成与价值实现。

### 人、AI、系统统一协作

Human、Agent、AgentTeam、Workflow、Tool 和 ExternalService 都是 Actor 或 Capability Provider，但权限、责任、成本、可靠性和适用风险不同。

### 图驱动而不是表单堆叠

复杂项目的核心是目标、交付物、工作、依赖、风险和证据之间的关系。系统应维护可计算的项目交付图，而不是依赖人从多张列表中自行理解全局。

### 渐进承诺与可恢复执行

AI 结果先作为候选，经过验证或确认后进入正式状态；重要执行必须支持暂停、取消、重试、回退和转人工。

### 稳定内核与领域扩展分离

通用模型只定义跨领域稳定语义。软件研发、AIGC、营销、知识工程等专业对象通过方法、类型、规则和资源引用扩展，不把专业字段塞入通用 Project。

## 元数据是 AI 理解项目的语义基础

### 为什么仅有数据不够

一条记录可能包含 `status=COMPLETED`，但 AI 仍然不知道：

- 这是工作完成、交付验收还是价值实现；
- 谁有权改变状态；
- 状态变化需要哪些证据；
- 完成后会触发什么动作；
- 它与目标和风险有什么关系；
- 当前字段是权威事实、派生值还是展示投影。

因此，AI 需要的不只是 Schema，而是完整的领域语义。

### 项目领域语义元数据

每个领域概念至少需要以下元数据：

```text
稳定身份      conceptKey / resourceKey
规范名称      canonicalName
别名与禁用词  aliases / deprecatedTerms
定义          definition
存在目的      purpose
所属限界上下文 boundedContext
适用与排除    scope.includes / scope.excludes
身份规则      identityRule
关键属性      attributes
关系          relations / cardinality / ownership
生命周期      states / transitions / terminalStates
允许动作      actions / preconditions / effects
业务不变量    invariants
权限与责任    authorization / accountability
证据要求      evidenceRequirements
度量语义      metrics / calculation / source
示例与反例    examples / counterExamples
知识来源      specRefs / knowledgeRefs
版本与状态    version / draft / published / deprecated
```

关系本身也必须有语义，不能只有两个外键：

```text
relationKey
sourceConcept
targetConcept
meaning
direction
cardinality
ownership
required
lifecycleEffect
constraints
examples
```

例如 `WorkItem PRODUCES Deliverable` 与 `Deliverable CONTRIBUTES_TO Goal` 的方向、责任和生命周期影响完全不同，不能都退化为无语义的 `related_to`。

### 元数据的分层边界

AAF 中与项目相关的元数据应明确分层：

| 元数据层 | 回答的问题 | 权威来源 |
| --- | --- | --- |
| 领域语义元数据 | 这个概念是什么，为什么存在，有哪些规则和关系 | 项目领域模型与领域规范 |
| 资源能力元数据 | 系统允许对资源执行哪些查询和动作 | 类型化代码、Resource Catalog、权限策略 |
| 执行能力元数据 | 哪个 Agent、Tool、Workflow 能做什么，输入输出和成本是什么 | 元数据引擎与能力注册表 |
| 展示交互元数据 | 字段如何显示，使用列表、表单、看板还是图视图 | EntityDef 和语义组件定义 |
| 运行观测元数据 | 本次执行使用了什么上下文、模型、规则和版本 | ExecutionRun、Event、Evidence |

必须遵守以下边界：

- 领域语义元数据供人和 AI 理解，不直接替代类型化领域代码；
- 状态机、跨字段规则、领域权限和副作用由领域模型与策略执行；
- Resource Catalog 描述服务端能力上限，不由 AI、客户端或配置扩大；
- EntityDef 只描述和收窄 UI，不建表、不增加 API、不承载业务不变量；
- AI 可以依据元数据选择和建议动作，但不能绕过服务端授权和领域校验。

### AI 的项目语义上下文

项目智能体每次工作前应按任务装配最小充分上下文：

```text
ProjectSemanticContext
├─ 相关概念与关系定义
├─ 当前项目方法和基线版本
├─ 目标、交付物和工作图子图
├─ 当前状态、风险和待决策事项
├─ 可用 Actor、Tool、Workflow 与权限
├─ 相关规范、知识和历史决策
└─ 验收标准、已有证据和缺失证据
```

AI 不应默认读取全部项目数据。上下文由目标对象、关系图、权限、任务契约和 Token 预算共同裁剪，并记录使用的元数据和基线版本，以支持复现和审计。

### 语义漂移治理

项目领域规范、元数据、代码行为和界面可能发生漂移。系统应持续比较：

```text
领域规范
↕
领域语义元数据
↕
类型化领域模型与资源能力
↕
运行事件和实际行为
↕
EntityDef / 语义界面
```

发现以下情况时应告警或阻断相关 AI 动作：

- 文档定义了系统不存在的状态或动作；
- Resource Catalog 能力与元数据描述不一致；
- AI 使用了已废弃概念或错误关系；
- 验收规则变化但项目基线未升级；
- 页面展示的状态含义与领域状态不一致；
- 实际执行产生了元数据声明之外的副作用。

## 领域概念治理

### 统一语言

项目模块必须维护统一领域词汇。每个词只有一个规范定义；同义词映射到规范名称，歧义词必须由限界上下文消解。

例如：

| 概念 | 规范含义 | 不是什么 |
| --- | --- | --- |
| Project | 为阶段性价值目标建立的临时工程与治理边界 | 持续运营职能、任意文件夹、长期运行容器 |
| Goal | 项目希望实现的价值结果 | 工作项标题、交付物名称 |
| Deliverable | 对结果作出承诺并接受验收的交付单元，可在多个迭代中产生版本 | 任意生成内容、执行日志 |
| Artifact | 交付过程中产生或引用的实际制品，如文档、模板、工作流定义或智能体配置 | 已验收承诺本身 |
| OperationalCapability | 由版本化 Artifact 按运行契约组装，经验证、发布后可独立运行的能力 | 单次运行实例、运行引擎、持续运营过程 |
| WorkItem | 为交付物、风险或治理目的执行的工作单元 | 定时任务、个人提醒、供应商异步任务 |
| Evidence | 支持或否定验收判断的可追溯事实 | Agent 的自我声明 |
| Decision | 对备选方案作出的有依据选择 | 普通状态更新 |
| Baseline | 经确认、可追溯的项目规范版本 | 随时覆盖的当前表单值 |
| Outcome | 项目交付后产生的业务或社会效果 | 交付物完成状态 |

### 概念发布生命周期

领域概念和关系定义也需要治理：

```text
DRAFT → REVIEWED → PUBLISHED → DEPRECATED → RETIRED
```

- 草稿概念可以用于讨论，不能驱动正式自动化；
- 发布概念必须有定义、边界、示例、不变量和责任人；
- 变更概念语义必须升级版本并进行影响分析；
- 废弃概念保留映射和迁移说明，但新项目不得继续使用；
- AI 只能依据当前项目基线允许的已发布概念工作。

### 通用核心与专业领域

通用项目模块只拥有管理语义，不拥有专业领域数据。它通过稳定资源身份和语义角色引用外部对象：

```text
ProjectDomainBinding
├─ projectId
├─ domainType
├─ resourceKey
├─ resourceId
├─ semanticRole
└─ metadataVersion
```

例如，文档、项目模板、WorkflowDefinition、AgentTeamDefinition、ToolBinding、KnowledgeBase 和 AIGC Work 都可以作为版本化 Artifact；其中可执行定义与必要的规范性文档可以进一步组装为 `OperationalCapability`。项目模块管理这些资源在项目中的语义角色、交付承诺、版本组合、发布关系、证据和迭代升级，但不复制其专业内容，也不接管运行引擎、运行实例或持续运营生命周期。

## 四层项目元模型

### 领域元模型层

定义所有项目共同使用的概念、关系和不变量：

```text
ProjectConceptDef
ProjectRelationDef
ProjectLifecycleDef
ProjectActionDef
ProjectPolicyDef
ProjectMetricDef
ActorCapabilityDef
ArtifactTypeDef
EvidenceTypeDef
```

这一层使 AI 知道“项目世界里有什么，以及它们意味着什么”。

### 方法与模板层

将 PMBOK、CMMI、RUP、Agile、Lean、DevOps 或组织实践沉淀为项目实践域版本，再组合为可采用的方法版本：

```text
ProjectPracticeAreaVersion
├─ 适用主链语义与工程阶段
├─ 必需和可选实践
├─ 角色、制品与证据要求
├─ 指标、质量门和决策规则
└─ 裁剪规则与来源映射

ProjectMethodVersion
├─ 适用项目模式与复杂度
├─ 采用的实践域版本及裁剪策略
├─ 生命周期与阶段策略
├─ 必需角色和责任
├─ 必需规范与文档角色
├─ 交付物类型和验收要求
├─ 质量门和决策门
├─ 风险、变更和度量策略
└─ 自动化与人工确认策略
```

项目方法版本负责形成实践域的可执行组合，项目基线版本固定本项目实际采用的实践域版本和裁剪结果。项目模板基于方法版本提供场景化初始结构，但不得改变核心概念、主链、阶段或实践域的规范语义。

### 项目实例层

承载一个具体项目当前的权威状态：

```text
Project
├─ Intent / Problem
├─ Goal / OutcomeMetric
├─ Scope / Constraint / Assumption
├─ Stakeholder / ActorAssignment
├─ Baseline
├─ Iteration / Milestone
├─ Deliverable / AcceptanceCriterion
├─ WorkPackage / WorkItem / Dependency
├─ Risk / Issue / Decision / ChangeRequest
├─ DomainBinding / ArtifactReference
└─ OperationalCapability / RuntimeBinding
```

这一层回答“当前项目具体是什么”。

### 运行与证据层

记录项目如何被执行、验证和学习：

```text
ExecutionReference
RuntimeBinding
ProjectEvent
Evidence
GateEvaluation
StateSnapshot
MetricObservation
Retrospective
LearningAsset
```

这一层回答“发生过什么、凭什么相信、学到了什么”。历史事实不可被当前状态覆盖。

## 核心领域对象

### 项目定义域

| 对象 | 定义 | 核心责任 |
| --- | --- | --- |
| Project | 阶段性价值目标的临时工程与治理边界 | 固定身份、归属、方法、生命周期和当前基线，管理多个迭代与交付版本 |
| Intent | 发起人尚未完全结构化的原始意图 | 保存问题起点和后续澄清轨迹 |
| Problem | 需要改变的现实状态及其影响 | 约束项目为什么存在 |
| Goal | 项目希望实现的价值结果 | 连接 OutcomeMetric 和 Deliverable |
| OutcomeMetric | 判断价值是否实现的指标 | 定义口径、数据来源、基准和目标值 |
| ScopeItem | 项目承诺包含或排除的边界项 | 控制范围和变更影响 |
| Constraint | 时间、预算、合规、技术或资源限制 | 限制规划和执行选择 |
| Assumption | 当前计划依赖但尚未证实的判断 | 被证伪时触发风险或重规划 |
| Stakeholder | 受项目影响或能影响项目的主体 | 表达诉求、影响力和沟通责任 |

### 规范与基线域

| 对象 | 定义 | 核心责任 |
| --- | --- | --- |
| ProjectSpecification | 描述项目目标、规则、交付和验收语义的规范集合 | 关联版本化文档与结构化契约 |
| ProjectBaselineVersion | 经授权确认的某一时点项目规范快照 | 固定执行依据并支持差异比较 |
| ProjectPracticeAreaVersion | 某类跨阶段项目能力的版本化实践、责任、制品、证据和度量集合 | 连接方法来源、主链语义、工程阶段和可执行治理要求 |
| ProjectMethodVersion | 项目采用的方法和治理策略版本 | 组合并裁剪实践域，决定生命周期、必需制品、质量门和度量 |
| ProjectPolicy | 可由系统检查或执行的治理规则 | 约束权限、预算、风险、WIP 和自动化 |

文档负责完整语义、背景和推理；结构化契约负责系统校验、关系和状态。二者通过版本引用连接，不复制同一事实。

### 交付域

| 对象 | 定义 | 核心责任 |
| --- | --- | --- |
| Deliverable | 对某个目标作出明确承诺的可验收结果单元 | 管理责任、期限、状态、标准和证据，并跨迭代维护稳定身份 |
| AcceptanceCriterion | 判断交付物是否满足要求的可验证条件 | 定义检查方式、阈值和强制性 |
| ArtifactReference | 项目实际产生或引用的版本化专业制品 | 引用文档、模板、工作流、智能体组合、代码、媒体、Work、知识库等领域资源 |
| OperationalCapability | 由一组 Artifact 和运行契约组装形成的可发布交付能力 | 固定组合版本、兼容性、运行契约、发布条件和回退依据 |
| CapabilityRelease | OperationalCapability 某次通过质量门的不可变发布快照 | 支持同一交付物多次发布、并行版本、升级、切换和退役 |
| Evidence | 支持或否定验收判断的事实记录 | 保存来源、结果、时间、执行者和可信等级 |
| QualityGate | 聚合多项条件后决定是否允许状态推进的控制点 | 执行自动检查、AI 审查或人工批准 |
| OutcomeObservation | 交付后对价值指标的实际观测 | 区分交付完成与价值实现 |

交付物状态建议保持通用：

```text
DEFINED → IN_PROGRESS → SUBMITTED → ACCEPTED
                              └──────→ RETURNED
DEFINED / IN_PROGRESS / RETURNED → CANCELLED
```

专业领域可以拥有更细生命周期，但必须映射为通用交付承诺状态，不得反向污染核心枚举。

当 Deliverable 表达可运行能力时，还需要独立的发布生命周期投影：

```text
DRAFT → CANDIDATE → VERIFIED → RELEASED → SUPERSEDED → RETIRED
                         └───────────────→ WITHDRAWN
```

同一 Deliverable 可以在多个 Iteration 中形成多个 `CapabilityRelease`。每个已发布版本及其 Artifact 组合、验收证据和 RuntimeBinding 都不可原地修改；升级必须产生新候选版本并重新通过适用质量门，旧版本在完成切换前可以继续运行，并保留回退能力。

### 计划与工作域

| 对象 | 定义 | 核心责任 |
| --- | --- | --- |
| Iteration | 围绕阶段性目标和反馈建立的时间或价值窗口 | 固定本轮目标、交付范围和回顾结果 |
| Milestone | 具有管理意义的阶段性检查点 | 聚合到期条件，不等同于交付物 |
| WorkPackage | 可独立规划、委派和控制的一组工作 | 连接交付物、能力和预算 |
| WorkItem | 可执行、可观察、可验证的最小管理工作单元 | 定义输入、输出、执行者、依赖和完成条件 |
| Dependency | 两个管理对象之间具有方向和约束的依赖 | 支撑关键路径、阻塞传播和影响分析 |
| BacklogItem | 尚未进入已确认基线的候选工作或需求 | 承载优先级，不代表执行承诺 |

WorkItem 不等于系统定时任务、个人 Todo 或 AIGC 供应商任务。它可以向这些执行系统派生动作或保存执行引用，但项目工作状态只有一个真理源。

### 协作与执行域

| 对象 | 定义 | 核心责任 |
| --- | --- | --- |
| Actor | 能承担项目责任或执行动作的主体 | 统一 Human、Agent、Team、Workflow、System |
| Capability | Actor 可以提供的有边界能力 | 描述输入输出、工具、成本、可靠性和限制 |
| ActorAssignment | Actor 在项目对象上的责任或执行关系 | 区分 accountable、responsible、consulted、informed |
| WorkContract | 一次委派的输入、输出、约束、权限和验收契约 | 防止只有标题没有边界的任务分派 |
| RuntimeBinding | 将 CapabilityRelease 绑定到 Workflow、Agent、Tool 或外部服务运行入口的版本化引用 | 管理部署目标、调用契约、权限和观测来源，不复制运行时状态 |
| ExecutionReference | 指向 AgentRun、WorkflowRun、TaskExecution 或外部执行的引用 | 不复制执行引擎内部状态 |
| CollaborationEvent | 沟通、委派、升级和主导权切换的事实 | 支撑审计与异步协作 |

Actor 不意味着所有执行者具有同等责任：AI 可以承担执行和建议，确定性系统承担规则执行，人类承担价值判断、授权和最终责任。

### 治理域

| 对象 | 定义 | 核心责任 |
| --- | --- | --- |
| Risk | 尚未发生但可能影响目标的未来不确定性 | 管理概率、影响、触发器、责任和应对 |
| Issue | 已经发生并影响项目的事实问题 | 管理处置、阻塞和恢复 |
| Decision | 对多个可行选项作出的有依据选择 | 保存方案、证据、决策人、影响和可逆性 |
| ChangeRequest | 对已确认基线提出的正式变化 | 计算影响并产生批准、拒绝或新基线 |
| GateEvaluation | 某次质量门评估的不可变记录 | 保存条件、证据、结论和例外批准 |
| ProjectEvent | 项目中已经发生的领域事实 | 驱动投影、通知、自动化和学习 |

### 学习域

| 对象 | 定义 | 核心责任 |
| --- | --- | --- |
| Retrospective | 对目标、交付、协作和系统表现的结构化复盘 | 区分事实、原因、判断和行动 |
| LearningAsset | 从项目提炼且通过验证的可复用知识 | 关联知识库、规范、模板、Skill 或测试 |
| ImprovementAction | 将经验转化为系统改进的行动 | 指向责任人、验证方式和目标版本 |
| TemplateCandidate | 从成功模式中提取但尚未发布的方法或模板候选 | 经审核后进入方法与模板层 |

## 项目交付图

通用项目不是对象列表的集合，而是一个具有语义的关系图：

```text
Intent CLARIFIES_TO Problem
Problem JUSTIFIES Project
Project ADOPTS ProjectMethodVersion
ProjectMethodVersion COMPOSED_OF ProjectPracticeAreaVersion
ProjectBaselineVersion FIXES_TAILORING_OF ProjectPracticeAreaVersion
Project BASELINED_BY ProjectBaselineVersion
Project HAS Goal
Goal MEASURED_BY OutcomeMetric
Goal REALIZED_BY Deliverable
Deliverable VERIFIED_BY AcceptanceCriterion
WorkPackage PRODUCES Deliverable
WorkItem PART_OF WorkPackage
ActorAssignment ASSIGNS Actor TO WorkItem
ExecutionReference EXECUTES WorkContract
ArtifactReference SATISFIES Deliverable
Deliverable REALIZED_AS OperationalCapability
OperationalCapability COMPOSED_OF ArtifactReference
OperationalCapability HAS_RELEASE CapabilityRelease
CapabilityRelease DEPLOYED_VIA RuntimeBinding
ExecutionReference RUNS CapabilityRelease VIA RuntimeBinding
Evidence VERIFIES AcceptanceCriterion
QualityGate EVALUATES Deliverable
Risk THREATENS Goal / Deliverable / WorkPackage
Decision RESOLVES Issue / Risk / ChangeRequest
ChangeRequest REVISES ProjectBaselineVersion
OutcomeObservation OBSERVES OutcomeMetric
Retrospective PRODUCES LearningAsset
```

所有关系必须使用已发布 `ProjectRelationDef`，明确方向、基数、所有权和生命周期影响。系统可以在此基础上进行：

- 目标到工作和证据的端到端追溯；
- 缺失交付物、验收标准或责任人的完整性检查；
- 依赖、关键路径和阻塞传播分析；
- 风险对目标和交付的影响分析；
- 变更对基线、预算、计划和证据的影响计算；
- 面向 AI 的相关子图检索和最小上下文装配。

## 项目治理状态与工程闭环

### 项目治理状态

核心生命周期只表达项目级管理承诺：

```text
FRAMING → BASELINED → ACTIVE ⇄ VALIDATING → COMPLETED → ARCHIVED
                    ↘ SUSPENDED ↗
FRAMING / BASELINED / ACTIVE / SUSPENDED → CANCELLED
```

- `FRAMING`：问题、目标和边界仍在澄清；
- `BASELINED`：项目规范已确认，可以正式承诺资源；
- `ACTIVE`：工作、交付和升级迭代持续推进；
- `VALIDATING`：当前候选交付版本正在完成验收、发布门禁或价值验证；通过后可以进入下一轮迭代，也可以在项目完成条件成立时结束项目；
- `COMPLETED`：项目级完成条件成立，不要求已发布能力停止运行；
- `SUSPENDED`：暂时停止但保留恢复条件；
- `CANCELLED`：项目终止并保存原因与影响，已发布能力是否停止由独立决策决定；
- `ARCHIVED`：项目记录冻结，学习资产已处理，运行能力及其观测仍由对应运行领域维护。

首次发布或某个 Deliverable 被接受，不自动完成 Project。项目可以在 `ACTIVE` 与 `VALIDATING` 之间循环，通过新的 Baseline、Iteration 和 CapabilityRelease 迭代升级交付物；Project 只在约定目标、必需交付和退出条件均成立时进入 `COMPLETED`。

交付物、工作项、执行和专业领域拥有各自生命周期，不能用一个 Project.status 代替所有状态。

### 工程闭环

五阶段迭代循环定义项目工程的宏观推进结构；下列工程活动可以跨阶段发生，是每轮循环都要保持的管理闭环，不构成另一套阶段枚举。

| 工程活动 | 核心问题 | 主要对象 | AI 主要作用 | 系统控制 |
| --- | --- | --- | --- | --- |
| 框定 | 真正问题和价值是什么 | Intent、Problem、Goal、Metric | 澄清、归纳、识别假设 | 完整性检查、权限 |
| 建模 | 管理哪些领域对象和关系 | Concept、Relation、Method | 概念对齐、语义消歧 | 元数据版本、漂移检测 |
| 基线 | 承诺什么，依据什么执行 | Specification、Baseline、Deliverable | 生成候选规范和影响分析 | 版本冻结、确认门 |
| 规划 | 如何形成可执行交付图 | Iteration、WorkPackage、Dependency | 分解、估算、能力匹配 | 规则校验、预算、WIP |
| 执行 | 谁或什么系统完成工作 | WorkContract、Actor、ExecutionRef | 动态调度、异常处理 | 授权、超时、取消、审计 |
| 验证 | 凭什么证明结果正确 | Criterion、Evidence、Gate | 语义审查、差异分析 | 确定性检查、职责分离 |
| 决策 | 继续、调整、退回、发布还是终止 | Risk、Issue、Decision、Change | 方案比较和建议 | 风险门、人工确认 |
| 发布 | 哪个不可变能力版本可以投入使用 | OperationalCapability、CapabilityRelease、RuntimeBinding | 生成发布候选、检查兼容性和回退条件 | 版本冻结、发布门、授权 |
| 运行观测 | 已发布能力实际如何运行 | ExecutionReference、Event、Telemetry | 异常解释、模式识别、升级建议 | 专业运行时、审计、告警 |
| 价值验证 | 运行是否产生预期效果 | OutcomeObservation、Metric | 归因分析、趋势解释 | 数据口径和来源校验 |
| 学习 | 如何让下一次更好 | Retrospective、LearningAsset | 模式提取、改进建议 | 审核后写入知识和方法 |

该闭环不是固定瀑布。探索可以多次往返框定、建模和验证；成熟交付可以使用稳定流水线；高风险项目增加质量门和人工确认，但共享同一领域内核。

## 自适应项目方法

通用不等于所有项目使用同一流程。项目应依据目标不确定性、方案不确定性、执行复杂度、影响、可逆性和监管要求选择治理模式。

| 模式 | 适用情形 | 管理重点 |
| --- | --- | --- |
| EXPLORE 探索型 | 问题或方案尚不明确 | 假设、研究、实验、候选和证据 |
| DELIVER 交付型 | 目标清晰，需要形成完整成果 | 范围、交付物、依赖、计划和验收 |
| PRODUCE 生产型 | 结构稳定，可批量重复 | 模板、吞吐、成本、异常和自动化 |
| ASSURE 高保障型 | 高风险、不可逆或强合规 | 强制规范、职责分离、证据、审计和回退 |

模式由 `ProjectMethodVersion` 和策略组合表达，不替代五阶段语义：探索型重点循环 START、DESIGN 与 RUN；交付型完整执行五阶段；生产型主要重复 BUILD、DELIVER 与 RUN；高保障型则在每个阶段增加更严格的证据和人工门禁。一个项目可以从探索型转为交付型，并将成熟工作转为生产型，但模式变化属于基线变更。

## 人类、AI 与系统协作

### 职责分配

| 主体 | 擅长 | 项目责任 |
| --- | --- | --- |
| 人类 | 价值判断、创造性突破、伦理责任、复杂关系 | 定义成功、批准高影响决策、承担最终责任 |
| AI 智能体 | 语义理解、分解规划、生成比较、模式识别、异常分析 | 形成候选、执行授权工作、提出风险和调整建议 |
| 确定性系统 | 规则执行、持久状态、权限、计算、规模化和审计 | 强制不变量、记录证据、运行门禁和自动化 |

### AI 参与角色

AI 能力按项目职责注册，而不是机械模拟固定岗位：

- `Framing`：澄清意图、问题和成功标准；
- `DomainModeling`：识别概念、关系、边界和语义冲突；
- `Planning`：形成交付图、依赖、估算和候选计划；
- `Execution`：执行有明确契约的研究、生成、分析和操作；
- `Coordination`：同步状态、发现阻塞、匹配能力和重新排序；
- `Verification`：检查规范覆盖、证据完整性和结果一致性；
- `RiskAnalysis`：识别风险、预测影响和提出应对方案；
- `Reporting`：基于真实状态和证据生成项目报告；
- `Learning`：提炼可复用模式和系统改进建议。

这些是 Capability，不要求每项能力对应一个长期存在的 Agent 实例。

### 动态决策权

项目动作根据影响、可逆性、可验证性和置信度分配决策权：

| 条件 | 主导方 |
| --- | --- |
| 低风险、可逆、规则明确、可自动验证 | 系统执行，AI 处理例外 |
| 中风险、方案可比较、结果可回退 | AI 提议或暂存，人类确认 |
| 高风险、不可逆、涉及价值取舍或对外承诺 | 人类决策，AI 提供证据和建议 |
| 元数据冲突、上下文不足、连续失败 | 强制停止并转人工 |

主导权切换必须形成 CollaborationEvent，不允许静默切换或因人类超时自动降低标准。

## 规范、文档与交付物

### 文档的角色

文档是语义、推理和规范的主要载体，可以承担：

- 项目章程；
- 需求和设计规范；
- 调研与方案；
- 会议与决策记录；
- 测试和验收报告；
- 发布说明和复盘。

项目模块通过 `ArtifactReference` 引用文档及其版本，不复制文档正文。正式基线必须固定所采用的文档版本。

### 交付物的角色

交付物不是文件类型，而是一个管理承诺。一个 Deliverable 可以由多个 Artifact 共同满足；当这些 Artifact 共同提供可执行入口时，还可以组装为版本化 `OperationalCapability`。例如：

```text
Deliverable：上线可用的知识问答能力
└─ OperationalCapability
   ├─ 业务与运行规范文档
   ├─ 项目模板
   ├─ KnowledgeBase 引用
   ├─ AgentTeamDefinition
   ├─ WorkflowDefinition
   ├─ Tool / Model Binding
   └─ CapabilityRelease
      ├─ RuntimeBinding
      └─ 自动测试与安全审查证据
```

Artifact 是事实载体，Deliverable 是承诺与验收边界，OperationalCapability 是经过组装后可发布和运行的能力，CapabilityRelease 是某次不可变发布快照。四者不能混为一谈。

文档和模板不一定由机器直接执行，但可以作为智能体指令、规则、上下文和实例化输入，成为运行能力的一部分。项目负责定义和升级这种组合；发布后的调用、调度、扩缩容和运行状态仍由对应 Agent、Workflow、Tool 或专业业务运行时负责。

### 规范到执行的链路

```text
ProjectSpecification
→ ProjectBaselineVersion
→ Deliverable + AcceptanceCriterion
→ WorkPackage + WorkContract
→ ExecutionReference
→ ArtifactReference + Evidence
→ OperationalCapability
→ GateEvaluation
→ CapabilityRelease + RuntimeBinding
→ 独立运行时的 ExecutionReference / Telemetry
→ OutcomeObservation
```

任何关键链路断裂都应成为项目健康度问题，并由 AI 主动提示。升级交付物时复用同一 Deliverable 身份，但必须建立新的基线、Artifact 版本、证据和 CapabilityRelease，不得覆盖已经验收或正在运行的版本。

## 进度、健康度与度量

项目不能再用一个手工百分比描述全貌。

| 维度 | 含义 | 典型指标 |
| --- | --- | --- |
| 执行进度 | 工作流动和完成情况 | WorkItem 完成率、周期时间、WIP、阻塞时长 |
| 交付成熟度 | 交付物距离可验收的程度 | 标准覆盖率、证据完整率、质量门通过率 |
| 价值实现度 | 目标指标是否改善 | 基准值、当前值、目标值、价值实现率 |
| 治理健康度 | 项目是否处于受控状态 | 高风险暴露、待决策时长、基线偏差、越权数 |
| 系统能力 | 人机协作和自动化是否有效 | 自动闭环率、人工介入率、首次通过率、恢复时间 |
| 综合成本 | 获得有效交付的真实代价 | 人工、模型、工具、返工和机会成本 |

所有派生指标必须声明计算公式、数据来源、刷新频率、适用范围和可信等级，避免 AI 对同名“进度”作出错误解释。

## 项目工程不变量

以下规则应由类型化领域模型、状态机、策略和质量门强制执行：

- 每个 Project 必须有 Problem 或 Goal；
- 每个正式 Goal 必须有 OutcomeMetric 或明确的可验证结果；
- 每个正式 Deliverable 必须追溯到 Goal；
- 每个执行型 WorkItem 必须服务于 Deliverable、Risk、Issue 或治理活动；
- 每个 Deliverable 必须有责任人和完成定义；
- 每条强制 AcceptanceCriterion 必须由有效 Evidence 覆盖；
- AI 生成结果默认是 Candidate，不得直接成为 Accepted Deliverable；
- 正式 ProjectMethodVersion 只能组合已发布的 ProjectPracticeAreaVersion；
- ProjectBaselineVersion 必须固定本项目采用的实践域版本和裁剪结果；
- 已发布 Baseline 不得原地修改；
- 基线变化必须经过 ChangeRequest 和 Decision；
- 已发布 CapabilityRelease 及其 Artifact 组合、证据和 RuntimeBinding 不得原地修改；
- 交付物升级必须产生新的候选版本并重新通过适用质量门，不能用新版本覆盖仍在运行的旧版本；
- 高风险或不可逆动作必须经过人工确认；
- 生成者不能成为高风险交付物的唯一验证者；
- 项目状态必须由领域事实推导，不能绕过迁移规则直接改值；
- Project 完成不自动等于 Outcome 已实现，也不要求已发布 OperationalCapability 停止运行；
- 首次交付或首次发布不自动完成 Project，项目完成条件必须在基线中明确；
- 领域对象、文档、执行记录和 UI 不得形成重复真理源；
- 未经审核的经验不得自动写入组织规范和公共知识。

## 与 AAF 其他能力的边界

| 能力 | 真理源 | 项目模块的关系 |
| --- | --- | --- |
| 用户、组织、工作区和权限 | system org / user / permission | 引用身份并执行项目级责任策略 |
| 文档正文与版本 | document | 引用规范和制品版本 |
| 个人待办 | system.todo | 可从 WorkItem 派生提醒，不成为项目工作真理源 |
| 定时和异步执行 | system.task / framework task | 保存 ExecutionReference，不复制运行状态 |
| Agent、Team、Tool 和 Skill | intelligent / metadata | 作为 Artifact、能力提供者或 RuntimeBinding 目标参与能力组合，项目不复制其定义和运行状态 |
| AI Workflow 与审批流 | workflow / approval | WorkflowDefinition 可进入 OperationalCapability，运行实例作为 ExecutionReference，不拥有项目状态 |
| 知识库和记忆 | knowledge / cognition | 提供项目上下文并接收审核后的 LearningAsset |
| 元引擎 | engine.meta | 执行项目编排、Checkpoint 和渐进提交 |
| 元数据引擎 | engine.metadata | 注册项目领域语义和可用能力，执行漂移检测 |
| Resource Catalog | framework.crud | 暴露类型化资源能力上限 |
| EntityDef / SenseUI | WebUI / semantic UI | 投影项目视图和交互，不承载领域规则 |
| AIGC、软件开发等专业领域 | 各业务模块 | 通过 DomainBinding、ArtifactReference、ExecutionReference 和事件协作 |

现有 AIGC Project 是否调整属于后续领域集成决策，不影响本模型成立。通用项目模型不以迁移专业项目为前提，也不把 AIGC 的对象图谱、媒体、执行、Work 或 Publication 复制到项目核心。

## 模块能力边界

未来项目管理业务模块应负责：

```text
project/
├─ definition   Project、Intent、Problem、Goal、Scope、Baseline
├─ delivery     Deliverable、Criterion、Artifact、Evidence、Gate
├─ capability   OperationalCapability、CapabilityRelease、RuntimeBinding
├─ planning     Iteration、Milestone、WorkPackage、WorkItem、Dependency
├─ governance   Risk、Issue、Decision、ChangeRequest、ProjectEvent
├─ collaboration ActorAssignment、WorkContract、ExecutionReference
└─ metadata     Concept、Relation、PracticeArea、Method、Policy、Metric 的领域注册与发布
```

学习结果可以由项目模块形成候选，但正式知识写入知识库、Skill、规范或模板前必须走对应领域的审核流程。

项目模块不负责：

- 实现 Agent 或 Workflow 运行时；
- 保存或投影专业运行时的内部状态、队列、调度和扩缩容数据；
- 用 Project 生命周期替代 CapabilityRelease 或运行实例生命周期；
- 保存专业领域制品正文；
- 复制组织、用户、文档、知识库和执行日志；
- 用动态元数据替代类型化核心领域模型；
- 以项目之名吞并持续运营、CRM、ERP 或专业生产系统。

## 最小可行闭环

首期目标不是实现全部传统项目管理功能，而是证明复杂意图能够通过语义明确的项目模型形成可信且可运行的交付。MVP 不需要自建 Agent 或 Workflow 运行时，但至少应打通一种现有专业能力从交付组合、质量门、发布到运行引用的端到端链路：

```text
自然语言提出复杂事项
→ AI 基于领域元数据澄清 Intent / Problem / Goal
→ 选择 ProjectMethodVersion
→ 生成 ProjectSpecification 和候选交付图
→ 人类确认 ProjectBaselineVersion
→ AI 拆分 WorkPackage / WorkItem 并匹配 Actor Capability
→ 人、Agent 和系统执行 WorkContract
→ 关联版本化 Artifact 和 Evidence
→ 组装 OperationalCapability
→ QualityGate 自动检查或转人工确认
→ 发布不可变 CapabilityRelease 并建立 RuntimeBinding
→ 由专业运行时直接执行并返回 ExecutionReference / Telemetry
→ OutcomeMetric 持续观测
→ 新 Iteration 按运行证据升级交付物，或结束项目并保留能力独立运行
→ Retrospective 形成 LearningAsset 候选
```

MVP 必须包含：

- 已发布的核心概念和关系元数据；
- 最小项目实践域目录，以及实践域与主链、阶段和项目方法版本的映射；
- Project、Goal、Baseline、Deliverable、Criterion、WorkItem、Evidence、Decision；
- OperationalCapability、CapabilityRelease 和 RuntimeBinding 的最小发布链；
- 项目交付图与 Goal→Deliverable→Artifact→CapabilityRelease→ExecutionReference 追溯检查；
- Human / Agent / Workflow / System 统一 Actor 引用；
- 版本化规范和变更记录；
- 基于证据的质量门；
- AI 项目助手所需的语义上下文装配；
- 对文档和至少一种专业领域资源的引用；
- 执行、交付、价值和治理四类状态投影。

MVP 暂不包含：

- 完整财务、采购和供应商系统；
- 跨项目 Portfolio 资源优化；
- 任意用户动态创建核心实体和状态机；
- 自动修改已发布项目方法；
- 为每个传统知识领域复制一套子系统；
- 强制迁移现有专业项目数据。

## 成功指标

| 目标 | 建议指标 |
| --- | --- |
| 语义完整 | 核心概念元数据完整率、关系语义覆盖率、未知概念调用数 |
| 追溯完整 | Goal→Deliverable→Work→Artifact→Evidence 完整链比例 |
| 可信交付 | 首次验收通过率、证据完整率、错误逃逸率 |
| AI 有效性 | 规划采纳率、错误动作拦截率、人工纠正率、上下文命中率 |
| 协作效率 | 待决策时长、阻塞恢复时间、人工搬运信息时间 |
| 价值实现 | Goal 达成率、OutcomeMetric 改善率、无价值交付比例 |
| 系统学习 | 改进项闭环率、模板复用率、同类问题复发率 |
| 治理安全 | 越权操作数、未审基线变更数、高风险自动执行数 |

## 反模式

### 让 AI 猜字段含义

依赖数据库列名、页面文案或长 Prompt 推断语义，容易产生同名异义和错误动作。必须提供版本化领域概念、关系和动作元数据。

### 把元数据变成万能业务解释器

将状态机、权限、副作用和复杂规则全部配置化，会失去类型安全和可验证性。元数据负责描述，领域代码和策略负责强制执行。

### 先做功能菜单再补领域模型

从甘特图、看板、报表和聊天入口倒推数据，最终会形成重复状态和弱语义对象。必须先确定领域语言和不变量。

### 把项目变成所有业务对象的容器

复制文档、媒体、知识库、执行日志和专业状态会制造平行真理源。项目只拥有管理关系、承诺、证据和决策。

### 用多个 Agent 模拟官僚组织

增加 Agent 数量不等于提高能力。只有职责边界清楚、上下文可封装、输出可验证且并行收益明确时才建立独立 Agent。

### 以任务完成代替价值实现

WorkItem 全部完成只说明执行结束；Deliverable 通过说明承诺兑现；OutcomeMetric 达标才说明价值实现。

### 静默重规划

AI 可以发现偏差和生成新方案，但不能绕过 ChangeRequest、Decision 和 BaselineVersion 修改正式承诺。

## 结论

AAF 的通用项目管理模型应成为一个面向复杂事项的项目工程操作系统：

- 以领域概念定义管理对象；
- 以元数据让 AI 理解语义、关系、规则和能力；
- 以规范和基线建立共同执行依据；
- 以价值和交付物组织工作；
- 将文档、模板、工作流、智能体和专业制品组装为版本化运行能力；
- 以项目交付图管理复杂依赖；
- 以人、AI 和系统统一协作完成执行；
- 以证据和质量门证明可信完成；
- 以不可变发布和 RuntimeBinding 支持交付后直接运行；
- 以新的基线、迭代和发布版本持续升级交付物；
- 以决策和变更机制适应不确定性；
- 以价值观测和学习闭环持续提升组织能力。

项目是有边界、可结束的工程系统；它产出的能力可以在项目结束后独立持续运行，并携带清晰语义、可执行结构和可信证据，成为下一轮项目工程的输入。
