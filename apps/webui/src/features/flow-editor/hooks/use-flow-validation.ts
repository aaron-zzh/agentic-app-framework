/**
 * 流程校验逻辑
 * @author AaronZZH & Kiro
 */

import { useMemo } from "react"
import type { NodeTypeRegistry } from "../types"
import { useFlowState } from "./use-flow-state"

export interface ValidationError {
  nodeId?: string
  message: string
}

/** 校验流程定义完整性 */
export function useFlowValidation(registry: NodeTypeRegistry) {
  const { nodes, edges } = useFlowState()

  const errors = useMemo(() => {
    const result: ValidationError[] = []

    // 必须有开始节点
    const startNodes = nodes.filter((n) => n.type === "start")
    if (startNodes.length === 0) {
      result.push({ message: "流程缺少开始节点" })
    }
    if (startNodes.length > 1) {
      result.push({ message: "流程只能有一个开始节点" })
    }

    // 必须有结束节点
    const endNodes = nodes.filter((n) => n.type === "end")
    if (endNodes.length === 0) {
      result.push({ message: "流程缺少结束节点" })
    }

    // 检查孤立节点（无入边也无出边）
    for (const node of nodes) {
      if (node.type === "start" || node.type === "end") continue
      const hasIncoming = edges.some((e) => e.target === node.id)
      const hasOutgoing = edges.some((e) => e.source === node.id)
      if (!hasIncoming && !hasOutgoing) {
        result.push({
          nodeId: node.id,
          message: `节点"${(node.data as Record<string, unknown>).label ?? node.id}"未连接`
        })
      }
    }

    // 调用节点自身的 validate
    for (const node of nodes) {
      const def = node.type ? registry[node.type] : undefined
      if (def?.validate) {
        const nodeErrors = def.validate(node.data)
        for (const msg of nodeErrors) {
          result.push({ nodeId: node.id, message: msg })
        }
      }
    }

    return result
  }, [nodes, edges, registry])

  return { errors, isValid: errors.length === 0 }
}
