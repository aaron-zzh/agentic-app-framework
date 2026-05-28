/**
 * 左侧节点选择面板——拖拽添加节点
 * @author AaronZZH & Kiro
 */

"use client"

import { type DragEvent, useCallback } from "react"
import type { NodeTypeRegistry } from "../types"
import { getAllCategories, getNodesByCategory, categoryLabels } from "../lib/registry"

interface NodePanelProps {
  registry: NodeTypeRegistry
}

export function NodePanel({ registry }: NodePanelProps) {
  const categories = getAllCategories(registry)

  const onDragStart = useCallback((event: DragEvent, nodeType: string) => {
    event.dataTransfer.setData("application/flow-node-type", nodeType)
    event.dataTransfer.effectAllowed = "move"
  }, [])

  return (
    <div className="border-r bg-muted/30 w-48 overflow-y-auto p-3">
      <h3 className="text-muted-foreground mb-3 text-xs font-semibold uppercase">节点</h3>
      {categories.map((cat) => (
        <div key={cat} className="mb-3">
          <p className="text-muted-foreground mb-1 text-xs">{categoryLabels[cat]}</p>
          <div className="space-y-1">
            {getNodesByCategory(registry, cat).map(({ type, def }) => (
              <div
                key={type}
                draggable
                onDragStart={(e) => onDragStart(e, type)}
                className="hover:bg-accent flex cursor-grab items-center gap-2 rounded-md px-2 py-1.5 text-sm active:cursor-grabbing"
              >
                <span>{def.icon}</span>
                <span>{def.label}</span>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
