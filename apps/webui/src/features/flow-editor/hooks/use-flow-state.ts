/**
 * 画布状态管理——Zustand store，管理节点/边/选中/历史
 * @author AaronZZH & Kiro
 */

import type { Connection, Edge, EdgeChange, Node, NodeChange } from "@xyflow/react"
import { addEdge, applyEdgeChanges, applyNodeChanges } from "@xyflow/react"
import { create } from "zustand"
import type { FlowDefinition, HistoryEntry, NodeTypeRegistry } from "../types"

/** 历史栈最大长度 */
const MAX_HISTORY = 50

interface FlowState {
  nodes: Node[]
  edges: Edge[]
  selectedNodeId: string | null
  /** 撤销栈 */
  past: HistoryEntry[]
  /** 重做栈 */
  future: HistoryEntry[]

  /** 初始化画布数据 */
  init: (definition: FlowDefinition) => void
  /** 应用节点变更（拖拽/选中等） */
  onNodesChange: (changes: NodeChange[]) => void
  /** 应用边变更 */
  onEdgesChange: (changes: EdgeChange[]) => void
  /** 新增连线 */
  onConnect: (connection: Connection, registry?: NodeTypeRegistry) => void
  /** 选中节点 */
  selectNode: (id: string | null) => void
  /** 添加节点 */
  addNode: (node: Node) => void
  /** 更新节点数据 */
  updateNodeData: (id: string, data: Record<string, unknown>) => void
  /** 删除选中元素 */
  deleteSelected: () => void
  /** 撤销 */
  undo: () => void
  /** 重做 */
  redo: () => void
  /** 保存当前状态到历史 */
  pushHistory: () => void
  /** 导出为 FlowDefinition */
  toDefinition: () => FlowDefinition
}

export const useFlowState = create<FlowState>((set, get) => ({
  nodes: [],
  edges: [],
  selectedNodeId: null,
  past: [],
  future: [],

  init: (definition) => {
    const nodes: Node[] = definition.nodes.map((n) => ({
      id: n.id,
      type: n.type,
      position: n.position,
      data: n.data
    }))
    const edges: Edge[] = definition.edges.map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      sourceHandle: e.sourceHandle,
      label: e.label,
      data: { condition: e.condition },
      type: "custom"
    }))
    set({ nodes, edges, selectedNodeId: null, past: [], future: [] })
  },

  onNodesChange: (changes) => {
    set((s) => ({ nodes: applyNodeChanges(changes, s.nodes) }))
  },

  onEdgesChange: (changes) => {
    set((s) => ({ edges: applyEdgeChanges(changes, s.edges) }))
  },

  onConnect: (connection, registry) => {
    // 连线规则校验
    if (registry) {
      const { nodes } = get()
      const targetNode = nodes.find((n) => n.id === connection.target)
      const sourceNode = nodes.find((n) => n.id === connection.source)
      // 开始节点只出不入
      if (targetNode?.type === "start") return
      // 结束节点只入不出
      if (sourceNode?.type === "end") return
    }
    get().pushHistory()
    set((s) => ({
      edges: addEdge({ ...connection, type: "custom" }, s.edges)
    }))
  },

  selectNode: (id) => set({ selectedNodeId: id }),

  addNode: (node) => {
    get().pushHistory()
    set((s) => ({ nodes: [...s.nodes, node] }))
  },

  updateNodeData: (id, data) => {
    set((s) => ({
      nodes: s.nodes.map((n) => (n.id === id ? { ...n, data: { ...n.data, ...data } } : n))
    }))
  },

  deleteSelected: () => {
    const { nodes, edges, selectedNodeId } = get()
    if (!selectedNodeId) return
    get().pushHistory()
    set({
      nodes: nodes.filter((n) => n.id !== selectedNodeId),
      edges: edges.filter((e) => e.source !== selectedNodeId && e.target !== selectedNodeId),
      selectedNodeId: null
    })
  },

  undo: () => {
    const { past, nodes, edges } = get()
    if (past.length === 0) return
    const prev = past[past.length - 1]
    set({
      past: past.slice(0, -1),
      future: [{ nodes, edges }, ...get().future],
      nodes: prev.nodes,
      edges: prev.edges
    })
  },

  redo: () => {
    const { future, nodes, edges } = get()
    if (future.length === 0) return
    const next = future[0]
    set({
      future: future.slice(1),
      past: [...get().past, { nodes, edges }],
      nodes: next.nodes,
      edges: next.edges
    })
  },

  pushHistory: () => {
    const { nodes, edges, past } = get()
    set({
      past: [...past.slice(-MAX_HISTORY + 1), { nodes, edges }],
      future: []
    })
  },

  toDefinition: () => {
    const { nodes, edges } = get()
    return {
      nodes: nodes.map((n) => ({
        id: n.id,
        type: n.type ?? "default",
        position: n.position,
        data: n.data as Record<string, unknown>
      })),
      edges: edges.map((e) => ({
        id: e.id,
        source: e.source,
        target: e.target,
        sourceHandle: e.sourceHandle ?? undefined,
        label: typeof e.label === "string" ? e.label : undefined,
        condition: (e.data as Record<string, unknown>)?.condition as string | undefined
      }))
    }
  }
}))
