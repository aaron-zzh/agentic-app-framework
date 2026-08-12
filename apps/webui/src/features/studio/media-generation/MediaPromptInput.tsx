/**
 * 媒体生成工作台原生提示词输入。
 *
 * 使用受控 textarea 承载纯文本；附件、模型、技能和参数由外层独立组件组装。
 * 中文输入法组合期间不会响应 Enter 提交。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useRef } from "react"
import { Textarea } from "@/components/ui/textarea"
import { cn } from "@/lib/utils"

interface MediaPromptInputProps {
  value: string
  onChange: (value: string) => void
  onSubmit: () => void
  placeholder: string
  maxLength?: number
  disabled?: boolean
  className?: string
}

export function MediaPromptInput({
  value,
  onChange,
  onSubmit,
  placeholder,
  maxLength = 3000,
  disabled = false,
  className
}: MediaPromptInputProps) {
  const composingRef = useRef(false)

  return (
    <div className={cn("relative w-full", className)}>
      <Textarea
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onCompositionStart={() => {
          composingRef.current = true
        }}
        onCompositionEnd={() => {
          composingRef.current = false
        }}
        onKeyDown={(event) => {
          if (event.key !== "Enter" || event.shiftKey) return
          if (composingRef.current || event.nativeEvent.isComposing) return
          event.preventDefault()
          onSubmit()
        }}
        aria-label="创作提示词"
        placeholder={placeholder}
        maxLength={maxLength}
        disabled={disabled}
        className="max-h-[min(45vh,15rem)] min-h-16 resize-none overflow-y-auto border-0 bg-transparent px-1 py-1 text-sm leading-6 shadow-none focus-visible:border-transparent focus-visible:ring-0 dark:bg-transparent"
      />
      <span className="pointer-events-none absolute right-1 bottom-0 text-[10px] text-muted-foreground/70">
        {value.length}/{maxLength}
      </span>
    </div>
  )
}
