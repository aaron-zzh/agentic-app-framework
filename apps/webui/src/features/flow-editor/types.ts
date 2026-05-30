/**
 * 流程编辑器类型定义
 * @author AaronZZH & Kiro
 */

import type { Edge, Node, NodeProps as XYNodeProps } from "@xyflow/react"
import type { ComponentType } from "react"

/** 节点分类 */
export type NodeCategory = "trigger" | "ai" | "logic" | "data" | "tool" | "output" | "interact"

/** 编辑器模式 */
export type FlowMode = "approval" | "workflow" | "chatbot"

/** 端口方向 */
export type PortDirection = "input" | "output"

/** 端口定义 */
export interface PortDef {
  id: string
  direction: PortDirection
  label?: string
  maxConnections?: number
}

/** 输出定义 */
export interface OutputDef {
  name: string
  type: string
  label?: string
}

/** 变量定义 */
export interface VariableDef {
  name: string
  type: "string" | "number" | "boolean" | "object" | "array"
  defaultValue?: unknown
  description?: string
}

/** 节点属性面板 Props */
export interface InspectorProps {
  nodeId: string
  data: Record<string, unknown>
  onChange: (data: Record<string, unknown>) => void
}

/** 节点类型定义 */
export interface NodeTypeDef {
  component: ComponentType<XYNodeProps>
  inspector: ComponentType<InspectorProps>
  icon: string
  label: string
  category: NodeCategory
  ports: PortDef[]
  defaultData?: Record<string, unknown>
  validate?: (data: unknown) => string[]
  outputs?: OutputDef[]
}

/** 节点类型注册表 */
export interface NodeTypeRegistry {
  [type: string]: NodeTypeDef
}

/** 流程节点 */
export interface FlowNode {
  id: string
  type: string
  position: { x: number; y: number }
  data: Record<string, unknown>
}

/** 流程边 */
export interface FlowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string
  label?: string
  condition?: string
}

/** 流程定义 */
export interface FlowDefinition {
  nodes: FlowNode[]
  edges: FlowEdge[]
  viewport?: { x: number; y: number; zoom: number }
  variables?: VariableDef[]
}

/** 执行状态 */
export interface ExecutionState {
  status: "idle" | "running" | "completed" | "failed"
  currentNodeId?: string
  completedNodes: string[]
  failedNodes: string[]
  nodeOutputs?: Record<string, unknown>
  nodeTimings?: Record<string, number>
}

/** 编辑器主组件 Props */
export interface FlowEditorProps {
  mode: FlowMode
  nodeRegistry: NodeTypeRegistry
  initialData?: FlowDefinition
  onChange: (flow: FlowDefinition) => void
  readonly?: boolean
  executionState?: ExecutionState
}

/** 流程模板 */
export interface FlowTemplate {
  id: string
  name: string
  description: string
  mode: FlowMode
  definition: FlowDefinition
  createdAt?: string
}

/** 历史操作记录 */
export interface HistoryEntry {
  nodes: Node[]
  edges: Edge[]
}
