/**
 * 媒体生成 Composer 的共享输入壳。
 *
 * 只负责提示词、附件区、工具栏和提交按钮布局，不读取路由、store 或服务端数据。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ArrowUp } from "lucide-react"
import type { ReactNode } from "react"
import { AnimateBorder } from "@/components/animate/animate-border"
import { GlassCard } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { WsAsrButton } from "@/features/livechat/voice/WsAsrButton"
import { MediaPromptInput } from "@/features/studio/media-generation/MediaPromptInput"
import { cn } from "@/lib/utils"

interface MediaComposerShellProps {
  prompt: string
  onPromptChange: (value: string) => void
  onSubmit: () => void
  placeholder: string
  canSubmit: boolean
  isSubmitting: boolean
  maxLength?: number
  leadingTools?: ReactNode
  tools?: ReactNode
  attachments?: ReactNode
  extraInput?: ReactNode
  appearance?: "workspace" | "embedded"
  className?: string
}

/** 渲染媒体生成的通用输入结构。 */
export function MediaComposerShell({
  prompt,
  onPromptChange,
  onSubmit,
  placeholder,
  canSubmit,
  isSubmitting,
  maxLength = 3000,
  leadingTools,
  tools,
  attachments,
  extraInput,
  appearance = "workspace",
  className
}: MediaComposerShellProps) {
  const content = (
    <div className="flex flex-col">
      <div className="flex max-h-[min(55vh,22rem)] flex-col gap-2 overflow-y-auto px-4 pt-4 pb-1">
        {attachments ? <div className="flex flex-wrap gap-2">{attachments}</div> : null}
        <MediaPromptInput
          value={prompt}
          onChange={onPromptChange}
          onSubmit={onSubmit}
          placeholder={placeholder}
          maxLength={maxLength}
          disabled={isSubmitting}
        />
        {extraInput}
      </div>

      <div className="flex items-center gap-2 px-4 pb-2.5">
        <div className="flex flex-1 items-center gap-1.5 overflow-x-auto">
          {leadingTools}
          {tools}
        </div>
        <div className="flex shrink-0 items-center gap-1.5">
          <WsAsrButton onResult={onPromptChange} onInterim={onPromptChange} />
          <Button
            type="button"
            size="sm"
            onClick={onSubmit}
            disabled={!canSubmit}
            className="size-8 rounded-full p-0"
          >
            <ArrowUp className="size-4" />
          </Button>
        </div>
      </div>
    </div>
  )

  if (appearance === "embedded") {
    return (
      <div className={cn("w-full rounded-xl border border-foreground/8 bg-background", className)}>
        {content}
      </div>
    )
  }

  return (
    <AnimateBorder rounded="xl" borderWidth={1} duration={10} className={cn("flex w-full", className)}>
      <GlassCard glow="violet" className="w-full">
        {content}
      </GlassCard>
    </AnimateBorder>
  )
}
