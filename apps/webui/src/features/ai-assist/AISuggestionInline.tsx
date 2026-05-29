/**
 * AI 字段内联建议组件
 * 在输入框下方显示灰色建议文本，用户按 Tab 接受
 * @author AaronZZH & Kiro
 */
"use client"

import { useCallback, useEffect, useRef } from "react"
import { useAISettingsStore } from "@/lib/store/ai-settings-store"
import { cn } from "@/lib/utils"

export interface AISuggestionInlineProps {
  /** 建议文本 */
  suggestion: string | null
  /** 接受建议回调 */
  onAccept: (value: string) => void
  /** 忽略建议回调 */
  onDismiss?: () => void
  /** 附加样式 */
  className?: string
}

/**
 * 字段内联建议：输入框下方灰色建议文本，Tab 接受
 *
 * @example
 * <AISuggestionInline
 *   suggestion="张三"
 *   onAccept={(v) => form.setValue('name', v)}
 * />
 */
export function AISuggestionInline({
  suggestion,
  onAccept,
  onDismiss,
  className
}: AISuggestionInlineProps) {
  const enabled = useAISettingsStore((s) => s.enabled)
  const containerRef = useRef<HTMLDivElement>(null)

  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (!suggestion) return
      if (e.key === "Tab") {
        e.preventDefault()
        onAccept(suggestion)
      } else if (e.key === "Escape") {
        onDismiss?.()
      }
    },
    [suggestion, onAccept, onDismiss]
  )

  useEffect(() => {
    if (!suggestion || !enabled) return
    document.addEventListener("keydown", handleKeyDown)
    return () => document.removeEventListener("keydown", handleKeyDown)
  }, [suggestion, enabled, handleKeyDown])

  if (!enabled || !suggestion) return null

  return (
    <div
      ref={containerRef}
      className={cn(
        "flex items-center gap-1.5 px-1 py-0.5 text-muted-foreground text-xs",
        className
      )}
    >
      <span className="truncate opacity-60">{suggestion}</span>
      <kbd className="shrink-0 rounded border border-border bg-muted px-1 font-mono text-[10px]">
        Tab
      </kbd>
    </div>
  )
}
