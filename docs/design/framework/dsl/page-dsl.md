---
level: Practice
layer: Model
purpose: PageDSL——Magic-DSL doc/layout 域的页面级声明语法
status: draft
version: 1.0.0
date: 2026-05-13
author: AaronZZH
---

# PageDSL 语法设计

> PageDSL 是 Magic-DSL 在 `doc/layout` 域的具体实现，用于声明式描述营销页/落地页结构。
> 所属体系：[Magic-DSL](magic-dsl.md) | 渲染引擎：[PageEngine](../../apps/webui/page-engine.md)

## 一、定位

```text
Magic-DSL
  └── doc 域
      └── layout 子域
          └── PageDSL（页面级声明）
```

遵循 Magic-DSL 核心原则：约定优先、标记即语义、缩进即层级、自然语言可达。

## 二、设计原则：约定即省略

组件类型由上层包（sectionComponents 注册表）约定，DSL 中只写**与默认不同的部分**：

| 约定 | 含义 | 省略效果 |
|------|------|---------|
| 区块名即类型 | `hero` = `@section type: hero` | 无需声明 type |
| 首行即标题 | 区块内第一个 `#` 即 title | 无需 `title:` |
| 列表即 items | `-` 开头自动收集为 items 数组 | 无需 `items:` |
| 链接即 CTA | `[文本](url)` 在区块顶层即 cta/buttons | 无需 `cta:` |
| 缩进即归属 | 子行属于最近的父级 | 无需闭合标签 |
| 管道即分隔 | `|` 分隔同行多字段 | 无需 JSON 对象 |

## 三、完整示例

```dsl
page landing
  title: AI 原生应用开发框架
  theme: dark: system

---

hero | gradient(#1e1b4b, #312e81)
  # AI 原生多智能体应用开发框架
  > 配置驱动 · 对话生成 · 一句话创建完整业务模块
  [快速开始](/docs)  [GitHub](https://github.com/...)

---

features | 3col
  - bot | 多智能体协作 | Agent 编排、记忆系统、对话式交互
  - layout | 配置驱动 | 注册 EntityDef 即生成完整 CRUD 应用
  - sparkles | AI 感知 | 结构化语义理解，主动辅助操作
  - workflow | 工作流引擎 | Flowable 集成，可视化流程编排
  - brain | 知识库 | 向量检索 + 知识图谱 + RAG
  - code | 无代码开发 | 对话生成业务模块，零代码上线

---

showcase | autoplay: 5s
  - 结构化视图 | /img/list.png | 配置驱动的列表、表单、看板
  - 对话式交互 | /img/chat.png | 自然语言操作数据和系统
  - 工作流编排 | /img/flow.png | 可视化设计审批和自动化流程

---

stats
  - 64+ | 内置功能模块
  - 14 | 预定义组件类型
  - 5 | 智能层级架构

---

pricing
  - 社区版 | 免费
    ✓ 完整框架功能
    ✓ 社区支持
    [开始使用](/docs)
  - 专业版 | ¥299/月 | *
    ✓ AI 感知增强
    ✓ 优先技术支持
    ✓ 私有部署
    [立即订阅](/pricing)
  - 企业版 | 联系销售
    ✓ 定制开发
    ✓ SLA 保障
    [联系我们](/contact)

---

faq
  - 什么是 AAF？ | AI 原生多智能体应用开发框架，支持配置驱动和无代码开发。
  - 是否免费？ | 社区版 MIT 开源，专业版和企业版提供增值服务。

---

cta | #6366f1
  ## 准备好开始了吗？
  > 一行配置，生成完整业务模块。
  [免费开始](/docs)  [预约演示](/demo)

---

footer
  - 产品 | 功能, 定价, 路线图
  - 开发者 | 文档, API, GitHub
  - 公司 | 关于, 博客, 联系
  social: github, twitter, discord
  © 2026 XuejiAI
```

## 四、语法规则

### 页面声明

```dsl
page {slug}
  {meta_key}: {value}
```

`page` 是唯一的顶层关键字，后跟 slug。缩进行为页面元数据。

### 区块声明

```dsl
{type} [| inline_props]
  {content}
```

- 区块名即类型（`hero`、`features`、`pricing`...），由注册表约定
- `|` 后为内联属性（简写），等价于缩进行的 `key: value`
- `---` 分隔区块（可选，提升可读性）

### 内容语法（Markdown 子集）

| 语法 | 语义 | 映射 |
|------|------|------|
| `# 文本` | 标题 | props.title |
| `> 文本` | 描述/副标题 | props.subtitle / props.description |
| `[文本](url)` | 按钮/链接 | props.cta[] / props.buttons[] |
| `- 内容` | 列表项 | props.items[] |
| `✓ 文本` / `✗ 文本` | 功能项 | props.features[] (included/excluded) |
| `普通文本` | 正文 | props.content |

### 列表项管道语法

```dsl
- field1 | field2 | field3
```

字段位置由区块类型约定：

| 区块 | 管道含义 |
|------|---------|
| features | `icon \| title \| description` |
| showcase | `label \| image \| description` |
| stats | `value \| label` |
| pricing | `name \| price \| modifier(* = highlighted)` |
| faq | `question \| answer`（单行简写） |

### 内联属性

```dsl
hero | gradient(#1e1b4b, #312e81)     -- backgroundType + colors
features | 3col                        -- columns: 3
showcase | autoplay: 5s                -- autoplay interval
cta | #6366f1                          -- backgroundColor
navbar | sticky                        -- sticky: true
```

内联属性是**语法糖**，由各 Section 类型自行定义解析规则。

### 条件与绑定（函数式）

```dsl
pricing | if: $user.locale == 'cn'     -- 条件渲染
stats
  - $api.users.count | 注册用户        -- 动态数据绑定
```

复用 Magic-DSL 的 `$` 表达式语法，支持上下文引用和 API 绑定。

## 五、与 JSON PageDef 的关系

```text
PageDSL（人类/AI 编写）
  ↓ 解析器（编译时，零运行时开销）
PageDef JSON（存储 + 渲染消费）
  ↓ PageEngine
HTML（SSG 静态输出）
```

| 用途 | 格式 |
|------|------|
| 人类编写/阅读/Diff | PageDSL |
| AI 生成（两者皆可） | PageDSL 或 JSON |
| 可视化编辑器内部 | JSON |
| 数据库存储 | JSON |
| 版本对比 | PageDSL（更易读） |

## 六、解析器

- 编译时解析，非运行时——零性能开销
- 基于缩进的块级解析（类 YAML）
- `---` 作为区块分隔符（类 Markdown front matter）
- `|` 管道解析由区块类型的 schema 驱动
- 错误提示：行号 + 区块上下文 + 期望 vs 实际
