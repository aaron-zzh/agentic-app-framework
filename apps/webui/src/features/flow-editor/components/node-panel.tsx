/**
 * 左侧节点选择面板——拖拽添加节点
 * @author AaronZZH & Kiro
 */

"use client"

import { type DragEvent, useCallback } from "react"
import { categoryLabels, getAllCategories, getNodesByCategory } from "../lib/registry"
import type { NodeTypeRegistry } from "../types"

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
    <div className="w-48 overflow-y-auto border-r bg-muted/30 p-3">
      <h3 className="mb-3 font-semibold text-muted-foreground text-xs uppercase">节点</h3>
      {categories.map((cat) => (
        <div key={cat} className="mb-3">
          <p className="mb-1 text-muted-foreground text-xs">{categoryLabels[cat]}</p>
          <div className="space-y-1">
            {getNodesByCategory(registry, cat).map(({ type, def }) => (
              // biome-ignore lint/a11y/useSemanticElements: 拖拽元素需要 div
              <div
                key={type}
                role="button"
                tabIndex={0}
                draggable
                onDragStart={(e) => onDragStart(e, type)}
                className="flex cursor-grab items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-accent active:cursor-grabbing"
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
