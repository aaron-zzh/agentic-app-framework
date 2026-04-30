---
level: Practice
layer: Model
status: published
version: 2.0.0
date: 2026-03-30
author: AaronZZH
tags:
  - 文档规范
  - 文档路由
  - 目录结构
  - 索引页
purpose: 定义文档的定位决策、存放路径与目录索引规则
scope:
  includes:
    - 五度空间原则
    - 存放路径规则
    - 定位决策
    - 目录索引规范
    - 组合矩阵
  excludes:
    - 元数据字段定义（见 doc-meta-standard）
    - 文件命名规则（见 file-name-standard）
gains:
  - 能快速决定文档放在哪个目录
  - 能判断何时拆分文档
  - 能创建规范的目录索引
dependencies:
  - ./doc-meta-standard.md
related:
  - ./file-name-standard.md
---

# 文档路由规范

## 概述

本文档解决"文档该放哪、目录怎么组织"的问题。`doc-meta-standard` 定义"是什么"，本文定义"怎么选、放哪里、怎么索引"。

## 五度空间原则

> 核心约束：人类工作记忆容量 ≈ 5±2，超过即认知过载。

### 规则

任何目录 = 1 个 README（索引） + ≤ 5 个内容项（文件或子目录）

1. **同一目录最多 5 个内容项**：不含 README，文件和子目录合计 ≤ 5
2. **超过时拆分子目录**：按子主题建目录，每个子目录同样遵循此规则
3. **递归适用**：子目录内部、文档章节、列表枚举均适用
4. **README 是"1主"**：每个目录必须有 README.md 作为索引入口

### 实际示例：本目录

```text
content-system/                          ← 1 + 5 ✅
├── README.md                        ← 1（索引）
├── doc-meta-standard.md             ← 内容项1
├── doc-route-standard.md            ← 内容项2
├── file-name-standard.md            ← 内容项3
├── content-standard/                ← 内容项4（1 + 5 ✅）
│   ├── README.md
│   ├── reference-standard.md
│   ├── guide-standard.md
│   ├── tutorial-standard.md
│   ├── explanation-standard.md
│   └── glossary-standard.md
└── management-standard/             ← 内容项5（1 + 3 ✅）
    ├── README.md
    ├── validation-standard.md
    ├── evolution-standard.md
    └── glossary.md
```

### 反例

```
# ❌ 错误：超过 5 个内容项
some-topic/
├── README.md
├── doc1.md
├── doc2.md
├── doc3.md
├── doc4.md
├── doc5.md
└── doc6.md    ← 超出，应拆分为子目录
```

### 文档内部同理

| 信号 | 动作 |
| --- | --- |
| 目录下内容项 > 5 | 按子主题建子目录 |
| 文档一级章节 > 5 | 拆分为多篇文档 |
| 列表枚举项 > 5 | 分类归组 |
| 表格行 > 5 且无规律 | 按维度拆表 |

## 目录索引规范

### 核心原则

**极简主义**：README.md 只做两件事：

1. **定义边界** — 说明目录的 purpose、scope、gains
2. **列出内容** — 提供目录下所有内容项的清单

不需要：

- ❌ 使用指南（读者会自己判断）
- ❌ 更新历史（用 Git 管理）
- ❌ 相关资源（在具体文档中提供）
- ❌ 详细说明（与具体文档重复）

### README 必需结构

```yaml
---
level: 维度
layer: 层级
status: published
version: 1.0.0
date: YYYY-MM-DD
author: 作者
purpose: 目录的核心价值（一句话）
scope:
  includes:
    - 这个目录包含的内容
gains:
  - 使用这个目录后能获得什么
---
```

```markdown
# [目录名称]

> [一句话定位]

## 文档列表

1. [文档A](./doc-a.md) — 一句话说明
2. [文档B](./doc-b.md) — 一句话说明
3. [子目录](./sub-dir/) — 一句话说明
```

格式：`序号. [标题](路径) — 一句话说明`，按重要性排序。

### 维护

唯一要求：**保持文档清单的完整性**。添加内容项时加一行，删除时删一行。

## 存放路径规则

### 目录创建规则

顶层目录按**领域**或**目的**创建，每个顶层目录内部再按文档类型（guide/reference/tutorial/explanation...）组织。

#### 顶层目录

| 分类依据 | 说明 | 示例 |
| --- | --- | --- |
| **领域** | 围绕一个业务/技术领域的文档集合 | `agent/`、`auto-dev/`、`workflow/` |
| **目的** | 围绕一个横切关注点的文档集合 | `design/`、`content-system/`、`api/` |
| **辅助** | 非文档性质的管理目录 | `task/`、`temp/` |

#### 创建条件

| 条件 | 说明 |
| --- | --- |
| 该主题有 ≥ 2 篇文档 | 单篇文档直接放在最相关的已有目录中 |
| 该主题涉及 ≥ 2 种 type | 只有 guide 就放 guide/，同时有 guide + reference 才值得建顶层 |
| 五度空间约束 | 顶层目录总数同样遵循 ≤ 5 原则，超过时合并或分层 |

#### 顶层目录内部结构

每个顶层目录内部包含**子域**或 **type 子目录**，二者互斥：

```text
# 模式 A：有子域 → type 子目录在子域内
docs/{主题}/
├── README.md               ← 主题索引（必须）
├── {子域A}/                ← 子域目录
│   ├── README.md
│   ├── guide/             ← 子域内的操作指南（按需）
│   ├── reference/         ← 子域内的速查手册（按需）
│   └── ...
└── {子域B}/
    └── ...

# 模式 B：无子域 → type 子目录直接在主题下
docs/{主题}/
├── README.md               ← 主题索引（必须）
├── guide/                 ← 操作指南（按需）
├── reference/             ← 速查手册（按需）
├── explanation/           ← 原理解释（按需）
└── tutorial/              ← 入门教程（按需）
```

规则：

- **子域与 type 子目录互斥**：主题下有子域时，guide/reference 等只能在子域内创建，主题级不能同时有
- 子域内部可包含 guide/reference/explanation/tutorial 子目录
- 某 type 只有 1 篇文档时，直接放在当前目录，无需建 type 子目录
- 某 type 有 ≥ 2 篇文档时，建对应 type 子目录
- 最大嵌套 3 层：`docs/{主题}/{子域}/{type}/` 或 `docs/{主题}/{type}/{文件}`

### 完整示例

```text
docs/
├── README.md                        ← 文档中心入口
│
├── agent/                           ← 领域：智能体（有子域 → type 在子域内）
│   ├── README.md
│   ├── memory/                      ← 子域：记忆系统
│   │   ├── README.md
│   │   ├── memory-architecture.md
│   │   └── guide/               ← 子域内的 guide
│   │       └── how-to-config-memory.md
│   └── orchestration/               ← 子域：编排协作
│       ├── README.md
│       └── multi-agent.md
│
├── auto-dev/                        ← 领域：AI 自动开发（有子域）
│   ├── README.md
│   └── codegen/                     ← 子域：代码生成
│       ├── README.md
│       └── how-to-generate-code.md
│
├── design/                          ← 目的：架构设计（无子域 → type 在主题下）
│   ├── README.md
│   ├── architecture.md
│   └── explanation/                 ← 主题级 explanation
│       └── design-principles.md
│
├── content-system/                  ← 目的：内容/文档管理（有子域）
│   ├── README.md
│   ├── content-standard/            ← 子域：写作规范
│   │   └── ...
│   └── management-standard/         ← 子域：管理规范
│       └── ...
│
├── api/                             ← 目的：接口文档
│   └── README.md
│
├── task/                            ← 辅助：任务跟踪
│   └── aaf-v1.0.0.md
│
└── temp/                            ← 辅助：未归档内容
```

### 路径决策规则

| 优先级 | 规则 | 说明 |
| --- | --- | --- |
| 1 | **确定主题** | 文档属于哪个领域或目的？→ 对应顶层目录 |
| 2 | **确定 type** | guide/reference/tutorial/explanation？→ 对应子目录 |
| 3 | **判断是否建子目录** | 同 type ≥ 2 篇 → 建 type 子目录；否则直接放主题根目录 |
| 4 | **五度空间约束** | 超过 5 个内容项则拆分子目录 |
| 5 | **最大嵌套 3 层** | `docs/{主题}/{type或子域}/{文件}` |

### 决策流程

```text
1. 这篇文档属于哪个主题（领域/目的）？
   → 找到或创建对应的顶层目录

2. 该顶层目录是否已存在？
   ├── 是 → 进入步骤 3
   └── 否 → 满足创建条件（≥ 2 篇 + ≥ 2 种 type）？
       ├── 是 → 创建顶层目录，迁移已有文档
       └── 否 → 放入最相关的已有顶层目录

3. 该 type 在此目录下已有 ≥ 2 篇文档？
   ├── 是 → 放入 docs/{主题}/{type}/
   └── 否 → 直接放入 docs/{主题}/
```

### 路径示例

```text
# 领域=agent（有子域）, 子域=memory, type=Guide
→ docs/agent/memory/guide/how-to-config-memory.md

# 领域=agent, 子域=memory, 只有 1 篇
→ docs/agent/memory/memory-architecture.md

# 目的=design（无子域）, type=Explanation
→ docs/design/explanation/design-principles.md

# 目的=design, 只有 1 篇
→ docs/design/architecture.md
```

## 定位决策树

### 第一步：确定主题 → 顶层目录

```
这篇文档属于哪个领域或目的？
→ 找到对应的顶层目录（已有或需新建）
→ 新建时检查创建条件（≥ 2 篇 + ≥ 2 种 type）
```

### 第二步：确定 type → 子目录位置

```
要教新手从零完成任务？     → Tutorial   → {主题}/tutorial/
要解释概念背后的原理？     → Explanation → {主题}/explanation/
要给出解决具体问题的步骤？ → Guide      → {主题}/guide/
要提供结构化速查信息？     → Reference  → {主题}/reference/
要呈现全局关联关系？       → Map        → design/
```

### 第三步：确定 layer → 内容深度

```
跨领域普适规律？   → Principle  （抽象度最高）
领域主流思维框架？ → Paradigm
可复用解决方案？   → Pattern
特定领域操作模型？ → Model
具体实现与产出物？ → Product    （抽象度最低）
```

### 第四步：确定 level → 认知角度

```
描述客观约束和场景？ → Reality
表达主观解读和洞见？ → Thought
建立系统性知识结构？ → Theory
转化为可执行行动？   → Practice
判断价值和意义？     → Meaning
```

### 第五步：验证

- [ ] 主题（领域/目的）判断正确？
- [ ] type 与子目录位置一致？
- [ ] 同目录下内容项 ≤ 5？（五度空间原则）
- [ ] 与已有文档无内容重叠？
- [ ] 文件名符合命名规范？

## 组合矩阵

### layer × type 的常见组合

| layer | 常见 type | 说明 |
| --- | --- | --- |
| Principle | Explanation | 解释底层原理 |
| Paradigm | Explanation, Map | 解释范式，呈现全景 |
| Pattern | Reference, Guide | 模式速查，应用指南 |
| Model | Reference | 规范手册 |
| Product | Tutorial, Guide | 入门教程，操作指南 |

### 需要警惕的组合

| 组合 | 问题 | 建议 |
| --- | --- | --- |
| Principle + Reference | 原理层不适合纯速查 | 改为 Explanation 或降到 Model 层 |
| Product + Map | 具体产出物不需要全景图 | 提升到 Pattern 或 Paradigm 层 |

## 审查清单

- [ ] 顶层目录按领域或目的划分？
- [ ] 新建顶层目录满足创建条件？（≥ 2 篇 + ≥ 2 种 type）
- [ ] type 子目录仅在同 type ≥ 2 篇时创建？
- [ ] 目录内容项 ≤ 5？（不含 README，五度空间原则）
- [ ] 每个目录有 README.md？
- [ ] README 包含 purpose、scope、gains？
- [ ] README 文档清单完整？
- [ ] 目录深度 ≤ 3 层？
- [ ] 文档超 300 行？→ 考虑拆分
- [ ] 一级章节超 5 个？→ 必须拆分

## 参考

- [文档元数据规范](./doc-meta-standard.md) — 知识框架定位定义
- [文件命名规范](./file-name-standard.md) — 文件命名规则
