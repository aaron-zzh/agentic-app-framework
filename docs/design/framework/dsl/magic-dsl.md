---
level: Practice
layer: Model
purpose: Magic-DSL 领域语言总体设计——贯穿开发时与运行时的统一中间表示
status: draft
version: 2.0.0
date: 2026-05-13
author: AaronZZH
changelog:
  - 2026-05-13 | v2.0 精简重写：保留核心思想，去掉源码和学习笔记，拆分前后端实现到独立文档
  - 2026-05-06 | v1.0 初版
---

# Magic-DSL 领域语言设计

> Magic-DSL 是 AAF 的核心语言，贯穿开发时和运行时。它是人类的规范文档、AI 的生成目标、系统的执行程序。
> 前端运行时实现：[dsl-runtime.md](dsl-runtime.md) | 后端引擎实现：[dsl-engine.md](dsl-engine.md)
> 所属体系：[元引擎](../meta-engine.md) | [对话式交互](../../apps/webui/tmp/conversational-interaction.md)

## 一、定位

一种用于知识表示与人机协作的专用语言，介于编程语言与自然语言之间。目的：与系统进行高效交互，支持复杂任务处理和自定义工作流。

```text
自然语言 ←→ Magic-DSL ←→ 系统执行
  人类友好      三重身份      机器精确
```

**不是图灵完备语言**——在表达能力上做妥协，换取特定领域的高效。

### 三重身份

| 身份 | 面向 | 作用 |
|------|------|------|
| 规范文档 | 人类 | 描述业务逻辑、界面结构、工作流定义 |
| 生成目标 | AI | LLM 将自然语言意图转化为 DSL |
| 执行程序 | 系统 | 元引擎直接解析 DSL 驱动执行 |

### 本质模型

```text
用户意图（自然语言 / DSL 指令）
  → 意图理解层
  → DSL 中间表示
  → 元引擎执行
  → 语义组件动态组装
  → 用户感知
  → 新一轮意图
```

界面是对话的投影，不是预先设计的固定结构。DSL 是这个循环的中间表示。

## 二、设计原则

### 极简语法，约定优先

借鉴 Markdown（标记即结构）、Python（缩进即层级）、Cypher（模式即查询）的设计哲学：

| 原则 | 含义 | 示例 |
|------|------|------|
| 约定优先 | 省略即默认，只写与默认不同的部分 | `name: String` 默认 required、非 unique |
| 标记即语义 | 用最少符号表达结构关系 | `@required` `@unique` `Ref<Department>` |
| 缩进即层级 | 嵌套关系靠缩进，不靠冗余括号 | workflow 节点定义 |
| 可读即可写 | 读懂就能写，不需要学语法手册 | `columns: [name, email, role]` |
| 自然语言可达 | 任何 DSL 表达都可从自然语言生成 | `帮我建个用户表` → `entity User { ... }` |
| 渐进复杂度 | 简单场景一行搞定，复杂场景逐步展开 | `/create entity User` vs 完整 entity 块 |

**反面原则**：不追求图灵完备，不追求语法正交性，不追求覆盖所有边界情况。在表达力和易用性之间，选择易用性。

## 三、多范式设计

不强制单一语法风格，元引擎统一解析：

| 范式 | 适用场景 | 示例 |
|------|---------|------|
| **声明式** | 界面结构、实体定义、配置 | `entity User { name: String, email: Email }` |
| **命令式** | 用户操作、快捷指令 | `/create module user --fields name,email` |
| **函数式** | 工作流节点、数据转换 | `filter(tasks, t => t.status == 'open')` |
| **自然语言混合** | 模糊意图、探索性输入 | `帮我创建用户模块，字段是 name:String, email:Email` |

**核心价值**：降低表达门槛——普通用户用自然语言，开发者用命令式，工作流用声明式，同一引擎处理全部。

## 四、分层设计

三层保证：用户层降低门槛，系统层保证精确，中间层是 AI 和人类协作的共同语言。

```text
L1  用户层（宽松、多范式、可不精确）
    用户手写 / 对话输入 / 前端操作序列化
    容许：缺字段、模糊语义、自然语言混入
      ↓ 引擎解析 + AI 补全 + 规范推断

L2  协作层（结构化、语义明确、可读可生成）
    AI 生成 / 人工精确编写 / 元引擎中间表示
    要求：语义完整、字段明确、可校验、人类可读
      ↓ 编译 / 转换

L3  执行层（严格、标准、机器直接执行）
    元引擎内部 IR，不对外暴露
    要求：类型完备、无歧义、可直接路由到对应引擎
```

**层间转化责任在元引擎**，不在用户：
- 系统负责：范式识别、语法解析、L2→L3 编译
- AI 负责：自然语言理解、缺失字段补全、模糊语义推断

```text
L1 输入（任意范式 / 模糊表达）
  ↓ 范式识别 + AI 补全 + 规范推断
  ↓ 置信度评估（模糊则低置信度，触发澄清）
L2 结构化 DSL（语义校验 + 规范一致性检查）
  ↓ 置信度门控（>0.9 自动，0.7-0.9 确认，<0.7 人工）
L3 执行层 IR（路由到对应引擎执行）
```

## 五、分域设计

按内容的职责归属和生命周期隔离：

```text
├── dev 开发域（定义系统「是什么」）
│   ├── schema    业务实体、数据模型、字段约束
│   ├── api       接口定义、请求响应契约
│   ├── spec      验收标准、关联关系、状态标记
│   └── flow      工作流定义、节点、条件
│
├── runtime 运行时域（描述系统「怎么跑」）
│   ├── flow      工作流执行、自动化触发、事件管道
│   ├── agent     智能体配置、角色、工具、记忆策略
│   └── policy    权限规则、访问控制
│
└── doc 文档域（一切皆文档的呈现与交互）
    ├── content   文档结构、块类型、层级关系
    ├── layout    组件组装、语义布局规则
    ├── style     设计 token、主题、视觉变量
    └── behavior  交互行为、事件绑定、状态转换
```

一段 DSL 输入的三维定位：
```text
├── 属于哪个域？   → dev / runtime / doc
├── 在哪一层？     → L1 用户 / L2 协作 / L3 执行
└── 用什么范式？   → 声明式 / 命令式 / 函数式 / 混合
```

## 六、DSL 覆盖范围

| 域 | 覆盖内容 |
|----|---------|
| dev | 业务实体定义、API 接口定义、工作流定义、规范元数据 |
| runtime | 工作流执行、智能体配置、权限规则 |
| doc | 文档结构、内容格式、界面布局、样式主题、交互行为、模板定义 |

## 七、生命周期

```text
意图输入（任意范式）
  ↓ AI 生成 / 用户编写 / 前端操作序列化
DSL 草稿
  ↓ 语法校验 + 语义校验 + 规范一致性检查
DSL 验证版
  ↓ 置信度门控
DSL 执行版
  ↓ 元引擎解析执行
运行时状态
  ↓ 效果评估
DSL 迭代版（自进化）
```

## 八、语法示例

### 实体定义（dev/schema，声明式）

```dsl
entity User {
  name: String @required
  email: Email @unique
  role: Enum["admin", "user", "guest"]
  department: Ref<Department>
}
```

### 工作流定义（dev/flow，声明式）

```dsl
workflow ApprovalFlow {
  start -> review -> approve | reject
  
  node review {
    assignee: role("manager")
    timeout: 24h -> auto_approve
  }
  
  node approve {
    action: update(record, { status: "approved" })
    notify: [applicant, hr]
  }
}
```

### 交互指令（命令式）

```dsl
/create entity Product --fields "name:String, price:Number, category:Ref<Category>"
/deploy workflow ApprovalFlow --env production
/query users where role == "admin" limit 10
```

### 界面布局（doc/layout，声明式）

```dsl
view UserList {
  layout: table
  columns: [name, email, role, department, createdAt]
  filters: [role, department]
  actions: [create, edit, delete]
  sort: createdAt:desc
}
```

### 自然语言混合（L1 输入）

```text
帮我创建一个请假审批流程，部门经理审批，超过3天需要总监审批
```

→ 引擎 + AI 转化为 L2 结构化 DSL → 编译为 L3 执行

## 九、执行架构

```text
输入层（对话意图 / DSL 指令 / API / 事件 / 前端操作）
  ↓
DSL 层（统一中间表示）
  ├── 解析：范式识别 + 语法解析 + 语义分析
  ├── 转化：L1→L2（AI 补全）→ L3（编译）
  ├── 校验：类型检查 + 规范一致性 + 置信度评估
  └── 版本：DSL 快照 + diff + 回滚
  ↓
路由层（按域分发）
  ├── dev/schema  → 实体运行时（动态建表 + 自动 CRUD）
  ├── dev/flow    → 工作流引擎（Flowable）
  ├── runtime/*   → 调度引擎（执行 + 状态管理）
  └── doc/*       → 前端 DSL 运行时（解析 + 渲染 + 交互）
```

## 十、与元引擎的关系

- DSL 是元引擎的输入语言和输出语言
- 元引擎将意图转化为 DSL，将 DSL 路由到各专项引擎执行
- 自进化机制：行为数据 → 效果评估 → DSL 迭代 → 人工确认 → 热部署
- 开发即运行，运行即开发——DSL 消除开发与运行的边界

## 十一、实现拆分

| 关注点 | 文档 | 职责 |
|--------|------|------|
| 语言设计（本文档） | magic-dsl.md | 语法、语义、分层、分域、范式 |
| 前端运行时 | [dsl-runtime.md](dsl-runtime.md) | DSL 解析→UI 渲染、命令面板、指令执行 |
| 后端引擎 | [dsl-engine.md](dsl-engine.md) | DSL 解析→执行、L1→L2→L3 转化、路由分发 |
| 页面级 DSL | [page-dsl.md](page-dsl.md) | doc/layout 域：营销页声明式语法 |
