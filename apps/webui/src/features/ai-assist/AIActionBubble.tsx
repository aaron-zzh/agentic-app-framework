/**
 * AI 操作建议浮动气泡
 * 右下角浮动显示 AI 推荐的下一步操作，3 秒后自动消失
 * @author AaronZZH & Kiro
 */
"use client"

import { Lightbulb, X } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { useAISettingsStore } from "@/lib/store/ai-settings-store"
import { cn } from "@/lib/utils"

export interface AIActionSuggestion {
  /** 建议唯一标识 */
  id: string
  /** 建议描述 */
  description: string
  /** 置信度 0-1 */
  confidence: number
  /** 执行回调 */
  onApply: () => void
}

export interface AIActionBubbleProps {
  /** 当前建议 */
  suggestion: AIActionSuggestion | null
  /** 忽略回调 */
  onDismiss?: (id: string) => void
  /** 附加样式 */
  className?: string
}

/**
 * 右下角浮动操作建议气泡
 *
 * @example
 * <AIActionBubble
 *   suggestion={{ id: '1', description: '下一步：审批此文档', confidence: 0.9, onApply: () => {} }}
 *   onDismiss={(id) => dismiss(id)}
 * />
 */
export function AIActionBubble({ suggestion, onDismiss, className }: AIActionBubbleProps) {
  const enabled = useAISettingsStore((s) => s.enabled)
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    if (!suggestion || !enabled) {
      setVisible(false)
      return
    }
    setVisible(true)
    const timer = setTimeout(() => {
      setVisible(false)
      onDismiss?.(suggestion.id)
    }, 5000)
    return () => clearTimeout(timer)
  }, [suggestion, enabled, onDismiss])

  const handleApply = useCallback(() => {
    suggestion?.onApply()
    setVisible(false)
  }, [suggestion])

  const handleDismiss = useCallback(() => {
    setVisible(false)
    if (suggestion) onDismiss?.(suggestion.id)
  }, [suggestion, onDismiss])

  if (!visible || !suggestion) return null

  return (
    <div
      className={cn(
        "fade-in slide-in-from-bottom-2 fixed right-4 bottom-4 z-50 flex max-w-xs animate-in items-start gap-2 rounded-lg border border-border bg-popover p-3 shadow-lg",
        className
      )}
    >
      <Lightbulb className="mt-0.5 size-4 shrink-0 text-amber-500" />
      <div className="flex-1 space-y-1.5">
        <p className="text-sm leading-snug">{suggestion.description}</p>
        <Button size="xs" onClick={handleApply}>
          执行
        </Button>
      </div>
      <button
        type="button"
        onClick={handleDismiss}
        className="shrink-0 rounded p-0.5 text-muted-foreground hover:text-foreground"
      >
        <X className="size-3.5" />
      </button>
    </div>
  )
}
