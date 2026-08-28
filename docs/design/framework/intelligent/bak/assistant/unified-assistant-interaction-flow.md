---
level: Practice
layer: Model
purpose: 以统一流程图说明通用智能助理如何在对话中完成任务，并标记任务式交互的关键差异
status: draft
version: 1.0.0
date: 2026-08-20
author: Kiro
tags:
  - Assistant
  - 对话式交互
  - 任务执行
  - Mermaid
dependencies:
  - ../architecture.md
  - ./task-oriented-assistant-execution-design.md
related:
  - ./skill-progressive-loading-design.md
scope:
  includes:
    - 对话式与任务式 Assistant 的统一端到端交互流程
    - Role、Skill、上下文、Agent Loop、工具、HITL、事件与学习的关键节点
    - 任务式交互相对通用对话的差异节点
  excludes:
    - 具体接口、类、字段和数据库结构
    - 页面布局与视觉组件设计
    - 单个 Skill、工具或业务场景的内部步骤
gains:
  - 能沿一张图解释通用对话如何完成从问答到复杂任务的完整闭环
  - 能识别任务式交互在哪些节点收紧路由、澄清、执行和完成约束
  - 能将十项 Assistant 运行能力映射到现行统一执行模型
---

# 通用智能助理一体化交互流程

> 对话式是通用智能助理的默认交互表面，普通问答、内容生成、计算、检索和复杂协作任务都从同一会话进入。任务式不是另一套 Assistant 或运行时，而是在同一主链上采用更固定的路由、更少的非阻塞追问、更明确的执行计划、完成合同和产物呈现。

本文是整体场景流程视图，不重复定义底层合同。运行时、身份、事件及权限细节以[五层智能架构](../../architecture.md#统一智能任务运行流程)和[任务式 Assistant 统一执行路径设计](task-oriented-assistant-execution-design.md)为准。

## 图例

- **蓝色节点**：对话式常用路径或对话式交互特征。
- **橙色虚线节点**：任务式在该节点的差异，不代表独立运行时。
- **红色节点**：授权、人工介入或执行控制点。
- **紫色节点**：贯穿流程的事件与学习能力。
- ①–⑩ 对应 Assistant 的十项核心运行能力。

## 一体化交互与处理流程

```mermaid
flowchart TD
    USER([用户表达问题、目标或后续反馈])
    MODE{选择交互呈现方式}
    CHAT["对话式 CHAT<br/>默认通用入口，可逐轮完成各种任务"]:::chat
    TASK["任务式 EXECUTION<br/>一次提交，持续观察，明确交付"]:::task

    USER --> MODE
    MODE -->|多轮对话| CHAT
    MODE -->|任务提交| TASK
    CHAT --> INGRESS
    TASK --> INGRESS

    INGRESS["① 统一入口与预处理<br/>身份、输入、策略、幂等与执行意图"]:::common
    BUFFER["② 输入缓冲与合并<br/>不阻塞新输入，控制处理节奏"]:::common
    PRE["③ 前注意与协调分析<br/>理解目标、判断复杂度、澄清与规划"]:::common
    READY{目标和关键输入是否充分}

    INGRESS --> BUFFER --> PRE --> READY
    READY -->|充分| ROUTE
    READY -->|对话式信息不足| CHAT_ASK["自然追问或提供选项<br/>下一轮继续理解"]:::chat
    CHAT_ASK --> USER
    READY -->|任务式存在硬阻塞| TASK_WAIT["任务式差异<br/>仅请求不可替代的输入或授权"]:::task
    TASK_WAIT --> USER
    READY -->|任务式无硬阻塞| TASK_DEFAULT["任务式差异<br/>采用安全默认值并记录假设"]:::task
    TASK_DEFAULT --> ROUTE

    ROUTE{Role 与 Skill 路由约束}
    AUTO_ROUTE["AUTO<br/>无工具 LLM 动态单选 Role"]:::chat
    FIXED_ROUTE["任务式常用 FIXED<br/>校验指定 Role 与 ON_DEMAND Skill"]:::task
    SKILLS["分层激活 Skill<br/>ALWAYS 自动激活，AI 从 ON_DEMAND 中选择 0..N"]:::common

    ROUTE -->|对话式默认 AUTO| AUTO_ROUTE
    ROUTE -->|任务式可固定能力方向| FIXED_ROUTE
    AUTO_ROUTE --> SKILLS
    FIXED_ROUTE --> SKILLS

    RETRIEVE["④ 受控混合检索<br/>按需获取知识与记忆"]:::common
    CONTEXT["⑤ 最小上下文与执行画像<br/>装配提示、Persona、Role、ActivatedSkill、偏好与检索摘要<br/>冻结 Scope、激活方式、模型、计划和工具上限"]:::common
    PLAN{选择执行形态}
    DIRECT["对话式简单任务<br/>直接回复或单执行单元"]:::chat
    TASK_PLAN["任务式差异<br/>冻结计划、完成合同、产物与恢复边界"]:::task
    BOARD["复杂或委派任务<br/>TaskBoard 组织串行、并行与有界循环"]:::common

    SKILLS --> RETRIEVE --> CONTEXT --> PLAN
    PLAN -->|简单且可直接完成| DIRECT
    PLAN -->|复杂、委派或协作| BOARD
    PLAN -->|任务式| TASK_PLAN
    TASK_PLAN --> BOARD

    HARNESS["⑥ Harness Agent Loop<br/>Assistant、Coordinator 与 Executor 按需循环执行"]:::common
    MODEL["⑨ 模型推理与消息处理<br/>推理、结构化输出、流式增量与用量记录"]:::common
    ACTION{是否需要调用工具或外部动作}
    TOOLSET["仅按最终 ActivatedSkill 解析 EffectiveTools<br/>未激活的 Skill 不暴露其工具"]:::common
    GATE["⑦ ToolGateway 与 HITL 门控<br/>策略、授权、参数、预算与人工确认"]:::control
    AUTH{是否已满足动作条件}
    CALL["执行受治理工具<br/>返回可审计结果"]:::common
    HUMAN["人工干预<br/>确认、补参、暂停、继续、重试或取消"]:::control
    AGGREGATE["聚合结果与完成验证<br/>检查目标、证据、产物和终态"]:::common
    COMPLETE{完成条件是否满足}
    RECOVER["恢复、调整计划或有界重试"]:::common

    DIRECT --> HARNESS
    BOARD --> HARNESS
    HARNESS --> MODEL --> ACTION
    ACTION -->|否| AGGREGATE
    ACTION -->|是| TOOLSET --> GATE --> AUTH
    AUTH -->|已授权且参数充分| CALL
    AUTH -->|需确认、补参或过程控制| HUMAN
    HUMAN -->|批准、补全或继续| CALL
    HUMAN -->|拒绝、取消或暂停| AGGREGATE
    CALL --> HARNESS
    AGGREGATE --> COMPLETE
    COMPLETE -->|未满足且可恢复| RECOVER --> HARNESS
    COMPLETE -->|满足或形成规范终态| EVENTS

    EVENTS["⑩ 统一事件与投影<br/>状态、正文、工具、授权、子任务、产物与终态"]:::cross
    PRESENT{按交互方式呈现}
    CHAT_OUT["对话式输出<br/>消息、流式内容、工具卡片与可继续追问的结果"]:::chat
    TASK_OUT["任务式差异<br/>进度、步骤、阻塞、产物、完成证据与最终结果"]:::task
    LEARN["⑧ 学习管线<br/>沉淀知识、记忆与版本化优化候选，不直接改写运行时"]:::cross

    EVENTS --> PRESENT
    PRESENT -->|CHAT| CHAT_OUT --> USER
    PRESENT -->|EXECUTION| TASK_OUT --> USER
    EVENTS --> LEARN

    INGRESS -.执行事实.-> EVENTS
    PRE -.分析与决策.-> EVENTS
    HARNESS -.过程与增量.-> EVENTS
    GATE -.授权与工具.-> EVENTS

    classDef common fill:#eef2ff,stroke:#4f46e5,color:#1e1b4b,stroke-width:1.5px;
    classDef chat fill:#e0f2fe,stroke:#0284c7,color:#0c4a6e,stroke-width:1.5px;
    classDef task fill:#fff7ed,stroke:#f97316,color:#7c2d12,stroke-width:2px,stroke-dasharray:5 5;
    classDef control fill:#fef2f2,stroke:#dc2626,color:#7f1d1d,stroke-width:2px;
    classDef cross fill:#f5f3ff,stroke:#7c3aed,color:#4c1d95,stroke-width:1.5px;
```

## 任务式差异落点

| 关键节点 | 通用对话 | 任务式差异 |
|---|---|---|
| 入口与身份 | 在既有会话中持续接收消息，每轮形成独立执行 | 同一入口提交明确任务；以本轮任务身份持续跟踪，不创建第二套运行时 |
| 澄清 | 信息不足时可自然追问，并在后续轮次继续理解 | 只在关键输入、凭证或授权不可替代时阻塞；其他情况采用安全默认值 |
| Role 与 Skill | 默认动态单选 Role，再自动激活 ALWAYS 并从 ON_DEMAND 中选择 0..N | 常用固定 Role 与目标 ON_DEMAND Skill；越界组合直接拒绝，但 ALWAYS 仍自动激活 |
| 计划与协调 | 简单任务可直接完成，复杂任务再进入委派和 TaskBoard | 开始时冻结执行策略、完成合同、产物与恢复边界，过程更可预测、可审计 |
| 工具与动作 | 根据当前 ActivatedSkill 暴露最小工具集，敏感动作按需询问 | 同样经过 ToolGateway；可使用任务级窄授权，但不能扩大 Assistant、Role 或用户权限 |
| 完成判断 | 一轮有效回复即可完成当前轮次，用户可继续追问 | 必须验证结果、产物和完成证据，形成明确成功、失败、暂停或待处理终态 |
| 结果呈现 | 以消息和流式正文为主，可穿插工具及交互卡片 | 以状态、步骤、阻塞、产物和最终结果为主；正文仍使用统一投影 |

## 十项能力在现行流程中的映射

| 编号 | 核心能力 | 在统一流程中的职责 |
|---|---|---|
| ① | 预处理 | 将对话或任务输入转换为受约束的执行意图，完成身份、授权上限和策略准备 |
| ② | 输入缓冲 | 合并连续输入、避免阻塞并控制处理节奏，为可中断交互提供稳定入口 |
| ③ | 前注意与协调 | 理解目标、评估复杂度、澄清、选择 Role/Skill 并形成协调计划 |
| ④ | 混合检索 | 通过 Cognition 按授权获取知识库和记忆摘要，不把数据源直接暴露为开放工具 |
| ⑤ | 上下文管理 | 按任务最小化装配上下文，冻结 ActivatedSkill 的 Scope 与激活方式，并由最终激活结果计算工具 |
| ⑥ | Harness Agent Loop | 让协调者和执行者在各自边界内进行规划、执行、检查和有界循环 |
| ⑦ | HITL | 在授权、关键参数或过程控制处切换给人，并支持确认、暂停、继续、重试和取消 |
| ⑧ | 自学习 | 从完成过程提取知识、记忆和优化建议，只形成可审核、可版本化候选 |
| ⑨ | 模型推理 | 为理解、规划、生成和结构化输出提供横切推理能力，并记录流式消息与资源使用 |
| ⑩ | 事件消息 | 将对话与非对话任务投影为统一、可重放、可恢复和可演进的执行事实 |

## 设计边界

- **统一主链**：`CHAT` 与 `EXECUTION` 共享 Assistant、Cognition、Agent、TaskBoard、ToolGateway、HITL、完成验证和事件事实源。
- **对话承载任务**：用户不必先判断任务类型；普通对话可以逐步演化为计算、创作、检索、产物生成或多智能体协作任务。
- **模式不授予权限**：选择任务式只改变交互和约束，不自动获得更多工具、写权限或外部动作权限。
- **能力按激活结果可见**：SYSTEM、ASSISTANT 和当前 Role 的 ALWAYS 自动激活；AI 只筛选 ON_DEMAND，且允许选择 0..N。工具仅从最终 ActivatedSkill 解析。
- **系统机制优先**：路由校验、Schema、权限交集、授权、预算、状态迁移、事件和完成验证由系统保证；模型只在这些边界内推理和选择。
- **学习不直改生产定义**：学习结果必须经过版本化、审核和发布后才能影响后续执行。
