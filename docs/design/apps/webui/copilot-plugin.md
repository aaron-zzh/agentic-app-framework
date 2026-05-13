---
level: Practice
layer: Product
purpose: AAF Copilot 高级插件设计——全业务全流程 AI 辅助
status: draft
version: 1.0.0
date: 2026-05-12
author: AaronZZH
---

# AAF Copilot 插件设计

> 全业务、全流程的 AI 辅助系统。不是独立产品，而是 AAF 已有能力的**高层编排**——将 assistant-ui、AI 感知、知识库、工作流引擎、实体引擎统一暴露为"Copilot"体验。
> 依赖：[assistant-ui 选型](./tech-design/ai-frontend-component-selection.md) | [AI 感知](./interaction-mode-structured-view.md#三十五ai-感知能力ai-context-awareness) | [聊天模块](./chat-livechat-module.md) | [统一流程图编辑器](./flow-editor.md) | [实时数据方案](./tech-design/realtime-data-strategy.md)

## 一、定位

```text
AAF Copilot ≠ 独立聊天机器人
AAF Copilot = AI 感知 + 对话交互 + 工具调用 + 知识检索 + 工作流编排
            = 贯穿所有页面的智能助理层
```

| 特性   | AAF Copilot |
|------|-------------|
| UI 基础 | assistant-ui（与客服/IM 共享） | 
| AI 感知 | AIPageContext 全页面感知 |
| 工具调用 | AG-UI Tool Call + MCP | 
| 工作流  | FlowEditor 可视化编排 |
| 知识库  | PgVector + Neo4j 语义检索 | 
| 多模型  | Spring AI 统一抽象 | 
| 多渠道  | 网页 + UniApp + 外部整合 | 

## 二、架构

```text
┌─────────────────────────────────────────────────────────────┐
│                    Copilot 体验层                            │
│  ┌───────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐  │
│  │ 对话面板    │ │ 内联建议  │ │ 命令面板   │ │ 通知/提醒    │  │
│  │(Thread)   │ │(Inline)  │ │(⌘K)      │ │(Toast/Badge) │  │
│  └─────┬─────┘ └────┬─────┘ └────┬─────┘ └──────┬───────┘  │
├────────┼────────────┼────────────┼───────────────┼──────────┤
│        └────────────┴────────────┴───────────────┘          │
│                    CopilotService（前端核心）                  │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ AIAwarenessService │ ToolRegistry │ PromptTemplates │    │
│  └─────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────┤
│                    AG-UI 协议（SSE）                          │
├─────────────────────────────────────────────────────────────┤
│                    后端 Agent 服务                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐  │
│  │ Copilot  │ │ 知识库    │ │ 工具执行  │ │ 工作流引擎    │  │
│  │ Agent    │ │ RAG      │ │ MCP/Tool │ │ Flowable     │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## 三、能力矩阵

### 3.1 按用户角色

| 角色 | 核心能力 | 示例 |
|------|---------|------|
| **普通用户** | 操作指引、表单辅助、知识问答 | "怎么提交报销？" → 引导到表单 + 预填 |
| **业务人员** | 数据洞察、流程优化、内容生成 | "本月销售趋势如何？" → 生成图表 |
| **客服人员** | 智能回复、知识检索、情绪分析 | 见 [聊天模块 · AI 感知融合](./chat-livechat-module.md#十一ai-agent-集成--ai-感知融合) |
| **管理员** | 系统配置、权限管理、监控告警 | "给销售部开通审批权限" → 执行配置 |
| **开发者** | 代码生成、API 查询、调试辅助 | "生成一个客户管理模块的 EntityDef" |

### 3.2 按功能域

| 功能域 | 能力 | 依赖的 AAF 基础设施 |
|--------|------|-------------------|
| **智能客服** | 自动应答、转人工、满意度 | 聊天模块 + LivechatRuntime |
| **知识问答** | 企业知识库语义检索 | PgVector + Neo4j + Spring AI |
| **业务处理** | 工具调用执行操作 | AG-UI Tool Call + MCP Server |
| **办公助理** | 操作指引、表单预填、流程引导 | AI 感知 + EntityDef 元数据 |
| **BI 分析** | 数据洞察、图表生成、趋势分析 | TanStack Query + ECharts + Agent SQL |
| **内容生成** | 文档/邮件/报告/产品描述 | Lexical 编辑器 + Agent 生成 |
| **流程优化** | 操作习惯分析、优化建议 | AI 感知 recentActions + 后端分析 |
| **工作流编排** | 可视化 Agent 工作流 | FlowEditor（AI 工作流节点集） |
| **代码生成** | EntityDef/API/组件生成 | Auto Dev 模块 + Agent |
| **多渠道** | 微信/企微/UniApp | 外部整合服务 + 统一消息协议 |

## 四、交互形态

Copilot 不是单一聊天窗口，而是**四种交互形态**协同：

### 4.1 对话面板（主交互）

```text
工作区右侧可展开的 Copilot 面板（assistant-ui Thread）：
┌──────────────────────────────────┐
│ 🤖 AAF Copilot          [─] [×] │
├──────────────────────────────────┤
│ 用户：本月销售数据怎么样？        │
│                                  │
│ Copilot：根据系统数据分析：       │
│ ┌────────────────────────────┐  │
│ │ [ECharts 图表组件]          │  │ ← ToolUI 渲染
│ │ 本月销售额 ¥128万，环比+12% │  │
│ └────────────────────────────┘  │
│ 建议关注华东区增长放缓趋势。     │
│                                  │
│ [导出报告] [查看详情] [追问]      │ ← Action 按钮
├──────────────────────────────────┤
│ [📎] 输入消息...          [发送] │
│ 快捷：[本月报表] [待办] [帮助]   │ ← Prompt 模板
└──────────────────────────────────┘
```

基于 assistant-ui，与客服/IM 共享 Thread 组件，runtime 为 AgUiRuntime。

### 4.2 内联建议（AI 感知驱动）

直接在当前页面上下文中展示建议，无需打开对话面板：

```text
表单字段旁：
  [客户名称: ________] 💡 建议：根据邮箱域名，可能是"腾讯科技"

列表工具栏：
  💡 "检测到 15 条过期未跟进的线索，是否批量标记？" [执行] [忽略]

审批表单顶部：
  💡 "此报销金额超出部门月均 200%，建议审核附件" [查看分析]
```

实现：复用第三十五章 AI 感知的 `AISuggestion` 机制，Copilot Agent 作为 suggestion provider。

### 4.3 命令面板（⌘K）

```text
⌘K → 输入自然语言或命令：
  "创建一个客户记录"     → 打开客户表单 + 预填
  "本周我的待办"         → 展示待办列表
  "给张三发消息"         → 打开 IM 对话
  "分析上季度退货原因"   → 触发 BI Agent
```

命令面板已在结构化视图中设计（第八章），Copilot 扩展其能力：除了导航/搜索，还支持自然语言意图执行。

### 4.4 通知与提醒

```text
Copilot 主动推送：
  🔔 "您有 3 个审批待处理，最早的已等待 2 天"
  🔔 "检测到库存预警：SKU-001 低于安全库存"
  🔔 "本周目标完成率 65%，建议关注..."
```

通过 WebSocket 推送 + sonner Toast 展示。

## 五、工具调用（Tool Call）

Copilot 通过 AG-UI Tool Call 执行业务操作，前端通过 ToolUI 渲染确认/结果：

### 5.1 内置工具

| 工具 | 功能 | 示例 |
|------|------|------|
| `search_entity` | 搜索实体记录 | "查找张三的订单" |
| `create_record` | 创建记录 | "新建一个客户" |
| `update_record` | 更新记录 | "把这个订单标记为已发货" |
| `run_query` | 执行数据查询 | "本月销售额多少" |
| `generate_chart` | 生成图表 | "画一个销售趋势图" |
| `send_message` | 发送消息 | "通知张三审批通过" |
| `start_workflow` | 启动工作流 | "发起采购审批" |
| `search_knowledge` | 知识库检索 | "公司差旅报销标准是什么" |
| `generate_content` | 内容生成 | "写一封催款邮件" |
| `navigate` | 页面导航 | "打开客户列表" |

### 5.2 工具注册机制

```typescript
// 后端：Spring AI Tool 注册
@Tool(name = "search_entity", description = "搜索业务实体记录")
public List<Record> searchEntity(String entity, String query, Map<String, Object> filters) {
    // 基于 EntityDef 元数据动态查询
}

// 前端：ToolUI 渲染（assistant-ui makeAssistantToolUI）
makeAssistantToolUI({
  toolName: "create_record",
  render: ({ args, result }) => (
    <RecordCreatedCard entity={args.entity} record={result} />
  ),
});
```

### 5.3 人工确认（高风险操作）

```text
用户："删除所有过期订单"
Copilot：
  ⚠️ 即将删除 47 条过期订单，此操作不可撤销。
  [确认删除] [取消] [先预览列表]
```

通过 AG-UI `INTERRUPT` 事件实现，对应 assistant-ui 的 Human-in-the-Loop 模式。

## 六、知识库集成

### 6.1 知识来源

| 来源 | 入库方式 | 检索方式 |
|------|---------|---------|
| 企业文档（制度/手册/FAQ） | 管理员上传 → 向量化 | 语义检索（PgVector） |
| 业务数据（客户/订单/产品） | 实时同步 EntityDef 元数据 | 结构化查询 + 语义 |
| 操作日志（用户行为） | 自动采集 | 模式分析 |
| 外部知识（行业/法规） | 定时抓取 | 语义检索 |
| 知识图谱（概念关系） | Neo4j 存储 | 图遍历 + 语义 |

### 6.2 RAG 流程

```text
用户提问
  → 意图分类（问答/操作/分析/生成）
  → 知识检索（向量相似度 + 图谱关联 + 关键词）
  → 上下文组装（检索结果 + 页面上下文 + 用户角色）
  → LLM 生成回答
  → 引用标注（标明来源文档）
```

## 七、BI 分析能力

### 7.1 自然语言查询

```text
用户："上季度各区域销售对比"
  → Agent 解析意图 → 生成 SQL/GraphQL
  → 执行查询 → 格式化结果
  → 选择图表类型（柱状图）
  → 通过 ToolUI 渲染 ECharts 组件
```

### 7.2 主动洞察

AI 感知 + 定时分析，主动推送业务洞察：

```text
Copilot 通知：
  📊 "发现异常：华东区本周退货率较上周上升 35%，主要集中在 SKU-003"
  [查看详情] [生成报告] [忽略]
```

### 7.3 报表生成

```text
用户："生成本月销售周报"
  → Agent 收集数据（销售额/增长率/TOP 客户/问题）
  → 生成结构化报告（Markdown）
  → 渲染为文档（Lexical 编辑器）
  → 用户可编辑后导出 PDF
```

## 八、内容生成（AIGC）

| 场景 | 输入 | 输出 |
|------|------|------|
| 产品描述 | 产品基本信息 | 营销文案 |
| 邮件草稿 | 收件人 + 意图 | 邮件正文 |
| 会议纪要 | 会议录音/笔记 | 结构化纪要 |
| 工作总结 | 时间范围 | 周报/月报 |
| 通知公告 | 事项描述 | 正式通知文本 |

实现：Agent 调用 LLM 生成 → 通过 ToolUI 渲染预览 → 用户确认/编辑 → 插入到 Lexical 编辑器或发送。

## 九、流程优化建议

基于 AI 感知的 `recentActions` 历史数据，分析用户操作模式：

```typescript
// 后端定期分析
interface WorkflowOptimization {
  userId: string
  pattern: string              // "用户每次创建订单后都手动通知仓库"
  suggestion: string           // "建议配置自动通知工作流"
  impact: 'high' | 'medium' | 'low'
  automatable: boolean         // 是否可自动化
  workflowTemplate?: string   // 推荐的工作流模板 ID
}
```

展示方式：
- 周期性推送优化建议（通知形态）
- 设置页面展示"效率报告"
- 操作时实时提示"检测到重复操作，是否自动化？"

## 十、Prompt 模板

### 10.1 快捷模板

```typescript
interface PromptTemplate {
  id: string
  label: string
  icon: string
  category: 'query' | 'action' | 'generate' | 'analyze'
  template: string             // 含变量的 prompt
  variables?: { name: string; source: 'input' | 'context' | 'entity' }[]
  visibleFor?: string[]        // 角色过滤
}
```

示例：
```text
[📊 本月报表]  → "生成{当前模块}的本月数据报表"
[📝 写邮件]    → "帮我给{选中客户}写一封{目的}的邮件"
[🔍 查数据]    → "查询{实体}中{条件}的记录"
[⚡ 批量操作]  → "对选中的{N}条记录执行{操作}"
```

### 10.2 用户自定义

用户可保存常用 prompt 为个人模板，支持分享给团队。

## 十一、多渠道支持

```text
┌─────────────────────────────────────────┐
│           Copilot Agent（后端统一）       │
├─────────┬─────────┬─────────┬───────────┤
│ Web     │ UniApp  │ 企业微信 │ 公众号    │
│ (AG-UI) │ (AG-UI) │ (Webhook)│ (Webhook) │
└─────────┴─────────┴─────────┴───────────┘
```

| 渠道 | 接入方式 | 能力范围 |
|------|---------|---------|
| Web 工作区 | assistant-ui + AG-UI | 全能力 |
| UniApp | assistant-ui + AG-UI | 全能力（移动适配） |
| 企业微信 | Webhook → Agent → 回复 | 文本问答 + 工具调用 |
| 微信公众号 | Webhook → Agent → 回复 | 文本问答 + 知识检索 |
| API（OpenAPI/GraphQL） | REST/GraphQL 接口 | 程序化调用 |

外部渠道通过 AAF 外部整合服务接入，消息统一路由到 Copilot Agent。

## 十二、持续学习

```text
用户交互 → 反馈收集 → 效果评估 → 模型优化
    ↑                                  |
    └────────── 体验提升 ←─────────────┘
```

| 机制 | 说明 |
|------|------|
| 显式反馈 | 回答后 👍👎 按钮 |
| 隐式反馈 | 用户是否采纳建议、是否追问 |
| 使用日志 | 哪些工具被频繁调用、哪些建议被忽略 |
| A/B 测试 | 不同 prompt 策略对比 |
| 知识库更新 | 新文档入库后自动重新索引 |

## 十三、与现有模块的关系

```text
Copilot 不新建基础设施，而是编排已有能力：

assistant-ui ──→ Copilot 对话 UI（Thread/Composer/ToolUI）
AI 感知 ───────→ Copilot 上下文感知（页面状态/用户操作）
聊天模块 ─────→ Copilot 客服场景（LivechatRuntime）
FlowEditor ───→ Copilot 工作流编排（Agent 工作流节点集）
实体引擎 ─────→ Copilot 业务操作（CRUD 工具调用）
知识库 ────────→ Copilot 知识问答（RAG 检索）
Spring AI ────→ Copilot 后端推理（多模型统一抽象）
MCP ──────────→ Copilot 外部工具（标准化工具协议）
```

**Copilot 是 AAF 智能层的用户态入口，不是独立系统。**

## 十四、实现路径

| 阶段 | 能力 | 依赖 |
|------|------|------|
| v0.1 | 对话面板 + 知识问答 + 基础工具调用 | assistant-ui + Spring AI + PgVector |
| v0.2 | AI 感知内联建议 + BI 分析 + 内容生成 | AI 感知服务 + ECharts |
| v0.3 | 工作流编排 + 流程优化 + Prompt 模板 | FlowEditor + 行为分析 |
| v1.0 | 多渠道 + 持续学习 + 自定义 Agent | 外部整合 + 反馈系统 |
