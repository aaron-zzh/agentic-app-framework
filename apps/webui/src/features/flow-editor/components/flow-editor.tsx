/**
 * 流程编辑器主组件——组装画布 + 节点面板 + 属性面板
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect, useRef } from "react"
import { ReactFlowProvider } from "@xyflow/react"
import type { FlowEditorProps } from "../types"
import { useFlowState } from "../hooks/use-flow-state"
import { FlowCanvas } from "./flow-canvas"
import { NodePanel } from "./node-panel"
import { InspectorPanel } from "./inspector-panel"
import { injectExecutionStatus } from "../nodes/_base/base-node"

export function FlowEditor({
  mode,
  nodeRegistry,
  initialData,
  onChange,
  readonly,
  executionState
}: FlowEditorProps) {
  const { init, nodes, edges, toDefinition } = useFlowState()
  const initialized = useRef(false)

  /** 初始化画布数据 */
  useEffect(() => {
    if (initialData && !initialized.current) {
      init(initialData)
      initialized.current = true
    }
  }, [initialData, init])

  /** 数据变更回调 */
  useEffect(() => {
    if (initialized.current) {
      onChange(toDefinition())
    }
  }, [nodes, edges, onChange, toDefinition])

  /** 注入执行状态 */
  useEffect(() => {
    if (executionState) {
      const { nodes: currentNodes } = useFlowState.getState()
      const updated = injectExecutionStatus(
        currentNodes.map((n) => ({ id: n.id, data: n.data as Record<string, unknown> })),
        executionState
      )
      // 仅更新 data 中的 executionStatus
      for (const u of updated) {
        useFlowState.getState().updateNodeData(u.id, { executionStatus: (u.data as Record<string, unknown>).executionStatus })
      }
    }
  }, [executionState])

  return (
    <ReactFlowProvider>
      <div className="flex h-full w-full overflow-hidden rounded-lg border">
        {!readonly && <NodePanel registry={nodeRegistry} />}
        <FlowCanvas registry={nodeRegistry} readonly={readonly} />
        {!readonly && <InspectorPanel registry={nodeRegistry} />}
      </div>
    </ReactFlowProvider>
  )
}
