/**
 * 竖向设计器——单个节点卡片渲染
 * @author AaronZZH
 */

"use client"

import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils/cn"
import type { ApprovalFlowNode } from "./types"

/** 节点颜色配置 */
export const NODE_COLORS: Record<string, { border: string; bg: string; text: string }> = {
  start: { border: "border-l-blue-500", bg: "bg-blue-50", text: "text-blue-700" },
  approver: { border: "border-l-orange-500", bg: "bg-orange-50", text: "text-orange-700" },
  cc: { border: "border-l-green-500", bg: "bg-green-50", text: "text-green-700" },
  condition: { border: "border-l-purple-500", bg: "bg-purple-50", text: "text-purple-700" },
  end: { border: "border-l-gray-400", bg: "bg-gray-50", text: "text-gray-600" }
}

/** 节点图标 */
export const NODE_ICONS: Record<string, string> = {
  start: "📝",
  approver: "👤",
  cc: "📋",
  condition: "🔀",
  end: "⏹"
}

interface DesignerNodeProps {
  node: ApprovalFlowNode
  onSelect: (node: ApprovalFlowNode) => void
  onDelete: (nodeId: string) => void
}

/** 单个节点卡片 */
export function DesignerNode({ node, onSelect, onDelete }: DesignerNodeProps) {
  const colors = NODE_COLORS[node.type] ?? NODE_COLORS.end
  const icon = NODE_ICONS[node.type] ?? "⏹"
  const canDelete = node.type !== "start" && node.type !== "end"

  function getDescription(): string {
    if (node.type === "approver") return (node.config.assignee as string) || "未配置"
    if (node.type === "cc") return (node.config.users as string) || "未配置"
    return ""
  }

  return (
    <div className="flex justify-center">
      {/* biome-ignore lint/a11y/useSemanticElements: 需要 div 布局样式 */}
      <div
        className={cn(
          "relative w-64 cursor-pointer rounded-lg border border-l-4 p-3 shadow-sm transition-shadow hover:shadow-md",
          colors.border,
          colors.bg
        )}
        onClick={() => onSelect(node)}
        onKeyDown={(e) => {
          if (e.key === "Enter") onSelect(node)
        }}
        role="button"
        tabIndex={0}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span>{icon}</span>
            <span className={cn("font-medium text-sm", colors.text)}>{node.name}</span>
          </div>
          {canDelete && (
            <Button
              variant="ghost"
              size="sm"
              className="h-5 w-5 p-0 text-muted-foreground hover:text-destructive"
              onClick={(e) => {
                e.stopPropagation()
                onDelete(node.id)
              }}
            >
              ×
            </Button>
          )}
        </div>
        {getDescription() && (
          <p className="mt-1 truncate text-muted-foreground text-xs">{getDescription()}</p>
        )}
      </div>
    </div>
  )
}
