/**
 * 属性面板——节点选中后右侧展示配置项
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback } from "react"
import type { NodeTypeRegistry } from "../types"
import { useFlowState } from "../hooks/use-flow-state"

interface InspectorPanelProps {
  registry: NodeTypeRegistry
}

export function InspectorPanel({ registry }: InspectorPanelProps) {
  const { selectedNodeId, nodes, updateNodeData } = useFlowState()

  const selectedNode = nodes.find((n) => n.id === selectedNodeId)
  const nodeDef = selectedNode?.type ? registry[selectedNode.type] : undefined

  const handleChange = useCallback(
    (data: Record<string, unknown>) => {
      if (selectedNodeId) {
        updateNodeData(selectedNodeId, data)
      }
    },
    [selectedNodeId, updateNodeData]
  )

  if (!selectedNode || !nodeDef) {
    return (
      <div className="border-l bg-muted/30 flex w-64 items-center justify-center p-4">
        <p className="text-muted-foreground text-sm">选中节点查看属性</p>
      </div>
    )
  }

  const Inspector = nodeDef.inspector

  return (
    <div className="border-l bg-muted/30 w-64 overflow-y-auto p-4">
      <div className="mb-4 flex items-center gap-2">
        <span>{nodeDef.icon}</span>
        <h3 className="text-sm font-semibold">{nodeDef.label}</h3>
      </div>

      {/* 通用名称编辑 */}
      <div className="mb-4">
        <label htmlFor="node-label" className="text-sm font-medium">名称</label>
        <input
          id="node-label"
          className="border-input mt-1 w-full rounded-md border px-3 py-1.5 text-sm"
          value={(selectedNode.data.label as string) ?? ""}
          onChange={(e) => handleChange({ ...selectedNode.data as Record<string, unknown>, label: e.target.value })}
        />
      </div>

      {/* 节点类型特定属性 */}
      <Inspector
        nodeId={selectedNode.id}
        data={selectedNode.data as Record<string, unknown>}
        onChange={handleChange}
      />

      {/* 节点 ID（只读） */}
      <div className="mt-4 border-t pt-3">
        <p className="text-muted-foreground text-xs">
          ID: <span className="font-mono">{selectedNode.id}</span>
        </p>
      </div>
    </div>
  )
}
