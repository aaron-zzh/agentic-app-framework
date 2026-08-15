/**
 * 媒体生成 Composer 的共享输入壳。
 *
 * 只负责提示词、附件区、工具栏和提交按钮布局，不读取路由、store 或服务端数据。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ArrowUp, Coins, Library, X } from "lucide-react"
import type { ReactNode } from "react"
import { AnimateBorder } from "@/components/animate/animate-border"
import { GlassCard } from "@/components/studio"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Popover,
  PopoverContent,
  PopoverDescription,
  PopoverHeader,
  PopoverTitle,
  PopoverTrigger
} from "@/components/ui/popover"
import { WsAsrButton } from "@/features/livechat/voice/WsAsrButton"
import { CoverThumbnail } from "@/features/aigc/generation/CoverThumbnail"
import { MediaPromptInput } from "@/features/studio/media-generation/MediaPromptInput"
import type { MediaCreditEstimate } from "@/features/studio/media-generation/types"
import type { AigcSnippet } from "@/lib/api/rest/ai/aigc"
import { cn } from "@/lib/utils"

interface MediaComposerShellProps {
  prompt: string
  onPromptChange: (value: string) => void
  onSubmit: () => void
  placeholder: string
  canSubmit: boolean
  isSubmitting: boolean
  creditEstimate: MediaCreditEstimate
  selectedSnippets?: AigcSnippet[]
  onRemoveSnippet?: (snippetId: number) => void
  maxLength?: number
  leadingTools?: ReactNode
  headerTools?: ReactNode
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
  creditEstimate,
  selectedSnippets = [],
  onRemoveSnippet,
  maxLength = 3000,
  leadingTools,
  headerTools,
  tools,
  attachments,
  extraInput,
  appearance = "workspace",
  className
}: MediaComposerShellProps) {
  const content = (
    <div className="flex flex-col">
      {leadingTools || headerTools ? (
        <div className="flex shrink-0 items-center justify-between gap-2 border-foreground/8 border-b px-4 py-2">
          <div className="min-w-0 overflow-x-auto">{leadingTools}</div>
          <div className="flex shrink-0 items-center">{headerTools}</div>
        </div>
      ) : null}

      <div className="flex max-h-[min(55vh,22rem)] flex-col gap-2 overflow-y-auto px-4 pt-3 pb-1">
        {attachments ? <div className="flex flex-wrap gap-2">{attachments}</div> : null}
        {selectedSnippets.length > 0 ? (
          <div className="flex flex-wrap gap-1.5" aria-label="已选片段">
            {selectedSnippets.map((snippet) => {
              const sourceLabel = snippet.builtin
                ? "内置"
                : snippet.ownedByCurrentUser
                  ? "我的"
                  : "公共"
              return (
                <Badge
                  key={snippet.id}
                  variant="secondary"
                  className="h-7 gap-0 overflow-visible p-0"
                >
                  <Popover>
                    <PopoverTrigger
                      render={
                        <button
                          type="button"
                          className="flex h-full min-w-0 items-center gap-1 rounded-l-full py-0 pr-1 pl-2 hover:bg-foreground/6"
                        />
                      }
                    >
                      <CoverThumbnail
                        src={snippet.coverUrl}
                        alt={`${snippet.name}封面`}
                        fallback={<Library className="size-3" aria-hidden />}
                        className="size-4 rounded-sm"
                      />
                      <span className="max-w-32 truncate">{snippet.name}</span>
                    </PopoverTrigger>
                    <PopoverContent side="top" align="start" className="w-80">
                      <PopoverHeader>
                        <PopoverTitle>{snippet.name}</PopoverTitle>
                        <PopoverDescription>
                          {[snippet.category, sourceLabel].filter(Boolean).join(" · ")}
                        </PopoverDescription>
                      </PopoverHeader>
                      <p className="max-h-48 overflow-y-auto whitespace-pre-wrap text-sm leading-6">
                        {snippet.content}
                      </p>
                    </PopoverContent>
                  </Popover>
                  <button
                    type="button"
                    onClick={() => onRemoveSnippet?.(snippet.id)}
                    className="flex h-full items-center rounded-r-full px-1.5 text-muted-foreground hover:bg-foreground/8 hover:text-foreground"
                    aria-label={`移除片段“${snippet.name}”`}
                  >
                    <X className="size-3" />
                  </button>
                </Badge>
              )
            })}
          </div>
        ) : null}
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
        <div className="flex flex-1 items-center gap-1.5 overflow-x-auto">{tools}</div>
        <div className="flex shrink-0 items-center gap-1.5">
          <output
            className={cn(
              "flex h-8 items-center gap-1 rounded-lg border border-foreground/8 px-2 text-muted-foreground text-xs tabular-nums",
              creditEstimate.credits !== null && !creditEstimate.sufficient && "text-amber-400"
            )}
            aria-label={`预计消耗 ${creditEstimate.credits ?? "未知"} 积分`}
            title="预计积分消耗"
          >
            <Coins className="size-3.5" />
            {creditEstimate.isLoading ? "…" : (creditEstimate.credits ?? "—")}
          </output>
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
    <AnimateBorder
      rounded="xl"
      borderWidth={1}
      duration={10}
      className={cn("flex w-full", className)}
    >
      <GlassCard glow="violet" className="w-full">
        {content}
      </GlassCard>
    </AnimateBorder>
  )
}
