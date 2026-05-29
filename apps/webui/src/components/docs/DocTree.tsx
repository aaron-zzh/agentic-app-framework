/**
 * 共享文档树组件
 * 业务文档页面和开发文档页面共用
 * @author AaronZZH & Kiro
 */
"use client"

import { FileText, Folder, FolderOpen } from "lucide-react"
import { useState } from "react"
import { useSemanticDraggable } from "@/features/chatter/dnd/useSemanticDraggable"
import type { DocTreeNode } from "@/lib/types/document"

interface DocTreeProps {
  nodes: DocTreeNode[]
  selectedId: number | null
  onSelect: (id: number) => void
  depth?: number
  /** 启用拖拽（文件节点可拖入 Chatter） */
  draggable?: boolean
}

export function DocTree({ nodes, selectedId, onSelect, depth = 0, draggable }: DocTreeProps) {
  const [expanded, setExpanded] = useState<Set<string>>(new Set())

  function toggle(path: string) {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(path)) next.delete(path)
      else next.add(path)
      return next
    })
  }

  return (
    <ul className="space-y-0.5">
      {nodes.map((node) => (
        <li key={node.path}>
          {node.isDir ? (
            <>
              <button
                type="button"
                className="flex w-full items-center gap-1.5 rounded px-2 py-1 text-left text-sm hover:bg-accent"
                style={{ paddingLeft: `${depth * 12 + 8}px` }}
                onClick={() => toggle(node.path)}
              >
                {expanded.has(node.path) ? (
                  <FolderOpen className="size-4 shrink-0 text-yellow-500" />
                ) : (
                  <Folder className="size-4 shrink-0 text-yellow-500" />
                )}
                <span className="truncate">{node.name}</span>
              </button>
              {expanded.has(node.path) && (
                <DocTree
                  nodes={node.children}
                  selectedId={selectedId}
                  onSelect={onSelect}
                  depth={depth + 1}
                  draggable={draggable}
                />
              )}
            </>
          ) : (
            <FileNode
              node={node}
              depth={depth}
              selected={selectedId === node.id}
              onSelect={onSelect}
              draggable={draggable}
            />
          )}
        </li>
      ))}
    </ul>
  )
}

/** 文件节点，可选拖拽包装 */
function FileNode({
  node,
  depth,
  selected,
  onSelect,
  draggable
}: {
  node: DocTreeNode
  depth: number
  selected: boolean
  onSelect: (id: number) => void
  draggable?: boolean
}) {
  const { ref, listeners, attributes, isDragging } = useSemanticDraggable({
    id: `doc-tree-${node.id ?? node.path}`,
    item: { type: "doc", id: node.id ?? undefined, title: node.name },
    disabled: !draggable || node.id == null
  })

  return (
    <button
      ref={ref}
      type="button"
      className={`flex w-full items-center gap-1.5 rounded px-2 py-1 text-left text-sm hover:bg-accent ${
        selected ? "bg-accent font-medium" : ""
      }`}
      style={{ paddingLeft: `${depth * 12 + 8}px`, opacity: isDragging ? 0.5 : 1 }}
      onClick={() => node.id != null && onSelect(node.id)}
      {...listeners}
      {...attributes}
    >
      <FileText className="size-4 shrink-0 text-blue-500" />
      <span className="truncate">{node.name}</span>
    </button>
  )
}
