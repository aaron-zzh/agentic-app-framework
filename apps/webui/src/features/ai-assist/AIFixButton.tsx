/**
 * AI 错误修复按钮
 * 在表单校验错误信息旁显示 [AI 修复] 按钮，点击后自动修复字段值
 * @author AaronZZH & Kiro
 */
"use client"

import { useCallback, useState } from "react"
import { Sparkles, Loader2 } from "lucide-react"

import { Button } from "@/components/ui/button"
import { useAISettingsStore } from "@/lib/store/ai-settings-store"

export interface AIFixButtonProps {
  /** 修复回调，返回修复后的值 */
  onFix: () => Promise<void> | void
  /** 附加样式 */
  className?: string
}

/**
 * 错误修复按钮：错误信息旁 [AI 修复]
 *
 * @example
 * <AIFixButton onFix={async () => { form.setValue('email', await aiSuggestFix('email')) }} />
 */
export function AIFixButton({ onFix, className }: AIFixButtonProps) {
  const enabled = useAISettingsStore((s) => s.enabled)
  const [loading, setLoading] = useState(false)

  const handleClick = useCallback(async () => {
    setLoading(true)
    try {
      await onFix()
    } finally {
      setLoading(false)
    }
  }, [onFix])

  if (!enabled) return null

  return (
    <Button
      type="button"
      variant="ghost"
      size="xs"
      onClick={handleClick}
      disabled={loading}
      className={className}
    >
      {loading ? (
        <Loader2 className="size-3 animate-spin" data-icon="inline-start" />
      ) : (
        <Sparkles className="size-3" data-icon="inline-start" />
      )}
      AI 修复
    </Button>
  )
}
