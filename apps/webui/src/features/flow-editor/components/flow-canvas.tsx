/**
 * 画布区域——ReactFlow 实例 + 拖拽放置 + 快捷键
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useEffect, useMemo, useRef, type DragEvent } from "react"
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  type ReactFlowInstance,
  type NodeTypes,
  type EdgeTypes
} from "@xyflow/react"
import "@xyflow/react/dist/style.css"
import type { NodeTypeRegistry } from "../types"
import { useFlowState } from "../hooks/use-flow-state"
import { CustomEdge } from "./custom-edge"

interface FlowCanvasProps {
  registry: NodeTypeRegistry
  readonly?: boolean
}

export function FlowCanvas({ registry, readonly }: FlowCanvasProps) {
  const {
    nodes,
    edges,
    onNodesChange,
    onEdgesChange,
    onConnect,
    selectNode,
    addNode,
    deleteSelected,
    undo,
    redo
  } = useFlowState()

  const rfInstance = useRef<ReactFlowInstance | null>(null)

  /** 从注册表构建 nodeTypes 映射 */
  const nodeTypes: NodeTypes = useMemo(() => {
    const types: Record<string, NodeTypeRegistry[string]["component"]> = {}
    for (const [type, def] of Object.entries(registry)) {
      types[type] = def.component
    }
    return types
  }, [registry])

  const edgeTypes: EdgeTypes = useMemo(() => ({ custom: CustomEdge }), [])

  /** 拖拽放置节点 */
  const onDrop = useCallback(
    (event: DragEvent) => {
      event.preventDefault()
      const nodeType = event.dataTransfer.getData("application/flow-node-type")
      if (!nodeType || !registry[nodeType]) return

      const bounds = (event.target as HTMLElement).closest(".react-flow")?.getBoundingClientRect()
      if (!bounds || !rfInstance.current) return

      const position = rfInstance.current.screenToFlowPosition({
        x: event.clientX - bounds.left,
        y: event.clientY - bounds.top
      })

      const def = registry[nodeType]
      const id = `${nodeType}_${Date.now()}`
      addNode({
        id,
        type: nodeType,
        position,
        data: { ...def.defaultData }
      })
    },
    [registry, addNode]
  )

  const onDragOver = useCallback((event: DragEvent) => {
    event.preventDefault()
    event.dataTransfer.dropEffect = "move"
  }, [])

  /** 键盘快捷键 */
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return
      if (e.key === "Delete" || e.key === "Backspace") {
        deleteSelected()
      }
      if ((e.ctrlKey || e.metaKey) && e.key === "z") {
        e.preventDefault()
        if (e.shiftKey) redo()
        else undo()
      }
    }
    document.addEventListener("keydown", handler)
    return () => document.removeEventListener("keydown", handler)
  }, [deleteSelected, undo, redo])

  return (
    <div className="flex-1" onDrop={onDrop} onDragOver={onDragOver}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodesChange={readonly ? undefined : onNodesChange}
        onEdgesChange={readonly ? undefined : onEdgesChange}
        onConnect={(conn) => onConnect(conn, registry)}
        onNodeClick={(_e, node) => selectNode(node.id)}
        onPaneClick={() => selectNode(null)}
        onInit={(instance) => { rfInstance.current = instance }}
        fitView
        proOptions={{ hideAttribution: true }}
        defaultEdgeOptions={{ type: "custom" }}
        nodesDraggable={!readonly}
        nodesConnectable={!readonly}
        elementsSelectable={!readonly}
      >
        <Background />
        <Controls />
        <MiniMap className="!bottom-4 !right-4" />
      </ReactFlow>
    </div>
  )
}
