---
level: Practice
layer: Product
purpose: AAF 统一流程图编辑器设计——一个组件支撑审批流/AI 工作流/聊天机器人三种场景
status: draft
version: 2.0.0
date: 2026-05-13
author: AaronZZH
changelog:
  - 2026-05-13 | v2.0 合并 Dify 分析文档，补充节点分类体系、变量系统、自动布局、运行追踪等设计
  - 2026-05-12 | v1.0 初版
---

# 统一流程图编辑器（FlowEditor）

> 基于 @xyflow/react，一个编辑器组件 + 三套节点注册表，覆盖审批工作流、AI 工作流、聊天机器人脚本三种场景。
> 所属体系：[结构化视图模式](./interaction-mode-structured-view.md) | 生成式交互模式（待建） | [聊天模块](./chat-livechat-module.md) | [Inspector 面板](./inspector-panel.md)
> DSL 集成（条件表达式/变量模板）：[DSL 运行时](../../framework/dsl/dsl-runtime.md)

## 一、设计理念

三种场景的 UI 交互完全一致（画布拖拽 + 连线 + 属性面板 + 条件分支 + 运行时高亮），区别仅在节点类型和后端引擎。统一为一个组件，避免并行抽象。

```text
┌─────────────────────────────────────────────────────┐
│          FlowEditor（统一流程图编辑器）                │
│  @xyflow/react 画布 + 节点注册表 + Inspector 面板    │
├─────────────────────────────────────────────────────┤
│  节点注册表（按场景注入）：                            │
│  ┌─────────────┐ ┌─────────────┐ ┌───────────────┐ │
│  │ 审批节点集    │ │ AI 工作流    │ │ 聊天机器人     │ │
│  │ Approver    │ │ LLM         │ │ Text          │ │
│  │ Condition   │ │ Agent       │ │ Selection     │ │
│  │ Notify      │ │ Code        │ │ Input         │ │
│  │ End         │ │ HTTP        │ │ ForwardOp     │ │
│  │             │ │ Condition   │ │ AIAnswer      │ │
│  └─────────────┘ └─────────────┘ └───────────────┘ │
├─────────────────────────────────────────────────────┤
│  后端引擎（按场景不同）：                             │
│  Flowable      │ 自研调度器     │ ChatbotEngine    │
└─────────────────────────────────────────────────────┘
```

### 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 画布引擎 | @xyflow/react（React Flow v12） | 生产级验证（Dify/n8n 均基于此），不自研 |
| 自动布局 | ELK.js（elkjs） | 层次化 DAG 布局算法，用户点击"整理"自动排列 |
| 状态管理 | TanStack Query（流程定义持久化）+ Zustand（画布 UI 状态） | 遵循 AAF 状态边界规则 |
| 节点配置面板 | 复用 Inspector 面板架构 | 与 [inspector-panel.md](./inspector-panel.md) 统一 |
| 节点间数据传递 | 变量引用系统（路径式） | 借鉴 Dify，用户体验关键 |

## 二、核心接口

```typescript
// ─── 编辑器入口 Props ───
interface FlowEditorProps {
  mode: 'approval' | 'workflow' | 'chatbot'
  nodeRegistry: NodeTypeRegistry
  initialData?: FlowDefinition
  onChange: (flow: FlowDefinition) => void
  readonly?: boolean
  executionState?: ExecutionState
}

// ─── 流程定义（统一数据结构，三种场景共用）───
interface FlowDefinition {
  nodes: FlowNode[]
  edges: FlowEdge[]
  viewport?: { x: number; y: number; zoom: number }
  variables?: VariableDef[]          // 全局变量定义
}

interface FlowNode {
  id: string
  type: string                       // 从 NodeTypeRegistry 查找
  position: { x: number; y: number }
  data: Record<string, unknown>      // 节点配置数据
}

interface FlowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string              // 条件分支输出端口
  label?: string
  condition?: string                 // 条件表达式
}
```

## 三、节点注册表

与实体引擎的组件注册表同一思想——注册表模式，按场景注入不同节点集。

```typescript
interface NodeTypeRegistry {
  [type: string]: NodeTypeDef
}

interface NodeTypeDef {
  component: React.ComponentType<NodeProps>       // 画布上的节点渲染
  inspector: React.ComponentType<InspectorProps>  // 右侧属性面板
  icon: string
  label: string
  category: NodeCategory              // 节点面板分组
  ports: PortDef[]                    // 输入/输出端口
  defaultData?: Record<string, unknown>
  validate?: (data: unknown) => string[]
  outputs?: OutputDef[]               // 节点输出变量声明
}

// 节点分类（借鉴 Dify 六分类，对用户认知友好）
type NodeCategory =
  | 'trigger'   // 触发/入口
  | 'ai'        // AI/推理
  | 'logic'     // 逻辑控制
  | 'data'      // 数据处理
  | 'tool'      // 工具/集成
  | 'output'    // 输出/结束
  | 'interact'  // 交互（审批/人工/聊天）

interface PortDef {
  id: string
  type: 'input' | 'output'
  label?: string
  multiple?: boolean
}

interface OutputDef {
  name: string
  type: 'string' | 'number' | 'boolean' | 'object' | 'array'
  description?: string
}
```

## 四、变量引用系统

节点间通过路径式变量引用传递数据，是工作流编辑器的灵魂。

```typescript
// 变量引用格式：{{nodeId.outputName}}
// 示例：{{start.user_input}}、{{llm_1.text}}、{{code_1.result.items}}

interface VariableDef {
  name: string
  type: 'string' | 'number' | 'boolean' | 'object' | 'array'
  description?: string
  required?: boolean
  defaultValue?: unknown
}

interface VariableReference {
  nodeId: string
  outputName: string
  path?: string                      // 嵌套路径（如 result.items[0].name）
}
```

UI 实现：
- 输入框中输入 `{{` 触发变量选择器弹窗
- 变量选择器展示上游可用节点及其输出
- 选中后自动插入引用路径

## 五、三种场景的节点集

### 5.1 审批工作流节点

| 节点 | 类型 | 分类 | 说明 |
|------|------|------|------|
| 开始 | `start` | trigger | 流程发起 |
| 审批人 | `approver` | interact | 指定审批人/角色/部门 |
| 条件分支 | `condition` | logic | 根据表单字段值分支 |
| 会签 | `countersign` | interact | 多人同时审批 |
| 通知 | `notify` | output | 发送通知（不阻塞流程） |
| 子流程 | `subprocess` | logic | 嵌套子工作流（Flowable 子流程） |
| 结束 | `end` | output | 流程结束（通过/驳回） |

### 5.2 AI 工作流节点

| 节点 | 类型 | 分类 | 说明 |
|------|------|------|------|
| 开始 | `start` | trigger | 输入变量定义 |
| LLM | `llm` | ai | 大模型调用（prompt + model + 参数） |
| Agent | `agent` | ai | 智能体（LLM + 工具集，受控自主决策） |
| 知识检索 | `knowledge` | ai | RAG 检索 |
| 问题分类 | `question_classifier` | ai | LLM 分类路由 |
| 参数提取 | `parameter_extractor` | ai | 从文本提取结构化数据 |
| 代码 | `code` | data | Python/JS 沙箱执行 |
| HTTP | `http` | tool | 外部 API 调用 |
| 模板转换 | `template` | data | 模板渲染（变量插值） |
| 变量赋值 | `variable_assigner` | data | 变量赋值/聚合 |
| 条件 | `condition` | logic | IF/ELSE 分支 |
| 迭代 | `iteration` | logic | 数组逐项处理 |
| 人工介入 | `human` | interact | 暂停等待人类输入/审核 |
| 回答 | `answer` | output | 流式输出给用户（Advanced Chat 模式） |
| 结束 | `end` | output | 输出最终结果 |

> **Agent 节点 ≠ Agent 应用**：Agent 作为工作流节点时，是"受控的自主性"——在确定性流程中嵌入一段 LLM 自主决策。

### 5.3 聊天机器人节点

| 节点 | 类型 | 分类 | 说明 |
|------|------|------|------|
| 欢迎语 | `text` | output | 发送文本消息 |
| 选项选择 | `selection` | interact | 展示选项按钮 |
| 自由输入 | `input` | interact | 等待用户输入 |
| 邮箱收集 | `email` | interact | 收集并校验邮箱 |
| 电话收集 | `phone` | interact | 收集并校验电话 |
| AI 回答 | `ai_answer` | ai | 调用 Agent 回答 |
| 转人工 | `forward_operator` | interact | 分配客服 |
| 创建工单 | `create_ticket` | tool | 自动创建工单 |
| 结束 | `end` | output | 结束对话 |

## 六、共享能力

### 6.1 画布交互

| 能力 | 说明 |
|------|------|
| 缩放/平移/网格对齐 | @xyflow/react 内置 |
| 小地图 | MiniMap 组件 |
| 节点拖拽创建 | 从左侧节点面板拖入画布 |
| 连线时自动建议 | 拖出连线时弹出节点选择器（快速添加下游节点） |
| 框选/多选/批量操作 | 内置 |
| 撤销/重做 | Ctrl+Z / Ctrl+Shift+Z |
| 复制/粘贴节点组 | Ctrl+C / Ctrl+V |
| 对齐辅助线 | 拖拽时显示对齐参考线 |
| 自动布局 | ELK.js 一键整理（层次化 DAG） |
| 快捷键 | Delete 删除、Space 平移 |

### 6.2 属性面板（Inspector）

点击节点 → 右侧 Inspector 面板展示该节点的配置表单。复用 [Inspector 面板](./inspector-panel.md) 架构。

```text
┌─────────────────────────────────────────────────┐
│ 节点面板 │ 画布                  │ Inspector 面板 │
│ ┌──────┐ │                      │ ┌───────────┐ │
│ │ LLM  │ │ [开始]→[LLM]→[结束]  │ │ 节点：LLM  │ │
│ │ Code │ │          ↑ 选中      │ │ 模型：...  │ │
│ │ HTTP │ │                      │ │ Prompt：...│ │
│ │ ...  │ │                      │ │ 变量：...  │ │
│ └──────┘ │                      │ └───────────┘ │
└─────────────────────────────────────────────────┘
```

Inspector 内容由 `NodeTypeDef.inspector` 组件决定，每种节点类型自定义配置面板。

### 6.3 运行时状态可视化

```typescript
interface ExecutionState {
  status: 'idle' | 'running' | 'completed' | 'failed'
  currentNodeId?: string
  completedNodes: string[]
  failedNodes: string[]
  nodeOutputs?: Record<string, unknown>  // 各节点输出数据
  nodeTimings?: Record<string, number>   // 各节点耗时（ms）
}
```

UI 表现：

| 状态 | 视觉效果 |
|------|---------|
| 已完成 | 绿色边框 + ✓ 图标 |
| 当前执行 | 蓝色脉冲动画 |
| 失败 | 红色边框 + ✗ 图标 |
| 已执行连线 | 渐变流动动画 |

运行追踪面板（Inspector 内）：
- 展示每个已执行节点的输入/输出/耗时
- 失败节点展示错误信息
- 条件分支展示决策路径

### 6.4 校验

保存前自动校验：
- 必须有开始节点和结束节点
- 所有节点必须可达（无孤立节点）
- 必填配置项不能为空
- 条件分支必须覆盖所有情况
- 变量引用必须指向有效的上游节点输出

### 6.5 导入/导出

```typescript
exportFlow(editor): FlowDefinition   // 导出 JSON DSL
importFlow(json: FlowDefinition)     // 导入 JSON DSL
// 审批场景额外支持导出为 Flowable BPMN XML（后端转换）
```

## 七、目录结构

```text
features/flow-editor/
├── components/
│   ├── flow-editor.tsx              → 编辑器主组件
│   ├── flow-canvas.tsx              → 画布区域（@xyflow/react 封装）
│   ├── node-panel.tsx               → 左侧节点选择面板
│   ├── custom-edge.tsx              → 自定义边（渐变动画/分支标签）
│   └── variable-selector.tsx        → 变量选择器弹窗
├── nodes/
│   ├── _base/                       → 节点基础组件（端口/错误/重试）
│   ├── approval/                    → 审批节点集
│   ├── workflow/                    → AI 工作流节点集
│   └── chatbot/                     → 聊天机器人节点集
├── hooks/
│   ├── use-flow-state.ts            → 画布状态管理（Zustand）
│   ├── use-flow-query.ts            → 流程定义 CRUD（TanStack Query）
│   ├── use-auto-layout.ts           → ELK 自动布局
│   ├── use-flow-validation.ts       → 校验逻辑
│   └── use-execution-state.ts       → 运行时状态订阅
├── lib/
│   ├── registry.ts                  → 节点注册表工厂
│   ├── variables.ts                 → 变量解析/引用工具
│   └── elk-layout.ts               → ELK 布局配置
└── types.ts                         → 类型定义
```

## 八、与现有设计的关系

| 引用方 | 使用方式 |
|--------|---------|
| 结构化视图 · 流程图视图 | `ViewEngine` 的 `graph` 视图类型 → 渲染 `<FlowEditor readonly />` |
| 结构化视图 · 审批工作流 | EntityDef.workflow 配置 → `<FlowEditor mode="approval" />` |
| 生成式交互 · 画板 | 文档 type='workflow' → 画板视图渲染 `<FlowEditor mode="workflow" />` |
| 聊天模块 · 机器人脚本 | ChatbotScript 配置 → `<FlowEditor mode="chatbot" />` |
| Inspector 面板 | 节点选中 → Inspector 展示配置/运行状态 |

## 九、实现路径

| 阶段 | 能力 |
|------|------|
| v0.1 | FlowEditor 骨架 + 基础画布交互 + 审批节点集（最小可用） |
| v0.2 | AI 工作流节点集 + 变量引用系统 + Inspector 面板 + 运行时状态可视化 |
| v0.3 | 聊天机器人节点集 + 校验 + 导入导出 + ELK 自动布局 |
| v1.0 | 子工作流嵌套 + 插件式节点注册 + 自定义节点开发 SDK |
