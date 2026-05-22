/**
 * ChatterComposer——输入区，支持拖放附件
 * 基于 assistant-ui ComposerPrimitive + 附件列表
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ComposerPrimitive } from "@assistant-ui/react"
import { X } from "lucide-react"
import { Button } from "@/components/ui/button"
import type { ChatterDropItem } from "./types"

interface ChatterComposerProps {
  attachments: ChatterDropItem[]
  onAttachmentRemove: (index: number) => void
}

/**
 * 对话输入区
 * 显示已拖入的附件列表 + ComposerPrimitive 输入框
 */
export function ChatterComposer({ attachments, onAttachmentRemove }: ChatterComposerProps) {
  return (
    <ComposerPrimitive.Root className="border-t">
      {attachments.length > 0 && (
        <div className="flex flex-wrap gap-1 px-3 pt-2">
          {attachments.map((item, i) => (
            <span
              key={`${item.type}-${item.id ?? i}`}
              className="inline-flex items-center gap-1 rounded bg-muted px-2 py-0.5 text-xs"
            >
              {item.title ?? item.type}
              <Button
                variant="ghost"
                size="icon-sm"
                className="size-4"
                onClick={() => onAttachmentRemove(i)}
                aria-label="移除附件"
              >
                <X className="size-3" />
              </Button>
            </span>
          ))}
        </div>
      )}
      <div className="p-3">
        <ComposerPrimitive.Input
          className="w-full resize-none rounded-md border bg-background px-3 py-2 text-sm outline-none placeholder:text-muted-foreground focus:ring-1 focus:ring-ring"
          placeholder="输入消息..."
        />
        <ComposerPrimitive.Send />
      </div>
    </ComposerPrimitive.Root>
  )
}
