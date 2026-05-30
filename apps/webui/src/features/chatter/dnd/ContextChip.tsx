/**
 * ContextChip——对话输入框中的上下文标签
 * 展示拖入的文档/图片/视频/字段等内容的摘要
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { X } from "lucide-react"

import type { ChatterDropItem } from "../types"

const ICONS: Record<string, string> = {
  doc: "📄",
  image: "🖼️",
  video: "🎬",
  file: "📎",
  text: "📝",
  "view-context": "📋",
  field: "🏷️",
  record: "📌"
}

interface ContextChipProps {
  item: ChatterDropItem
  onRemove: () => void
}

export function ContextChip({ item, onRemove }: ContextChipProps) {
  const icon = ICONS[item.type] ?? "📎"
  const label = item.summary ?? item.title ?? item.type

  if ((item.type === "image" || item.type === "video") && item.thumbnailUrl) {
    return (
      <span className="inline-flex items-center gap-1 rounded-md bg-muted px-2 py-1 text-xs">
        <img src={item.thumbnailUrl} alt={label} className="size-6 rounded object-cover" />
        <span className="max-w-[150px] truncate">{label}</span>
        <button
          type="button"
          onClick={onRemove}
          className="ml-0.5 text-muted-foreground hover:text-foreground"
          aria-label="移除"
        >
          <X className="size-3" />
        </button>
      </span>
    )
  }

  return (
    <span className="inline-flex max-w-[200px] items-center gap-1 rounded-md bg-muted px-2 py-1 text-xs">
      <span>{icon}</span>
      <span className="truncate">{label}</span>
      <button
        type="button"
        onClick={onRemove}
        className="ml-0.5 text-muted-foreground hover:text-foreground"
        aria-label="移除"
      >
        <X className="size-3" />
      </button>
    </span>
  )
}
