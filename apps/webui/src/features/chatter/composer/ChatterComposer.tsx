/**
 * ChatterComposer——输入区
 * 按 assistant-ui 官方推荐结构：大圆角卡片，Input 在上，工具栏在下
 *
 * 底部工具栏：左 + 附件 | 模型选择 | 右 3D波形（语音时）+ 麦克风 + 发送/停止
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { AuiIf, ComposerPrimitive, useAui } from "@assistant-ui/react"
import { ArrowUpIcon, SquareIcon } from "lucide-react"
import { useCallback, useEffect, useRef, useState } from "react"
import { ModelSelector } from "@/components/common/ModelSelector"
import { Button } from "@/components/ui/button"
import { ContextChip } from "@/features/chatter/dnd/ContextChip"
import type { ChatterDropItem } from "@/features/chatter/types"
import { VoiceWaveform3D } from "@/features/livechat/voice/VoiceWaveform3D"
import { WsAsrButton } from "@/features/livechat/voice/WsAsrButton"
import type { AiModelVO } from "@/lib/api/rest/ai/ai-model"
import { useModelSelector } from "@/lib/hooks/use-model-selector"

interface ChatterComposerProps {
  attachments: ChatterDropItem[]
  onAttachmentRemove: (index: number) => void
  /** 粘贴长文本时回调，由上层决定是否转为 chip */
  onPasteText?: (item: ChatterDropItem) => void
  /** 发送后清空所有 attachments */
  onAfterSend?: () => void
  modelId?: string
  onModelChange?: (modelId: string, model: AiModelVO) => void
  /** 是否显示模型选择器，默认 true；未登录场景应传 false */
  showModelSelector?: boolean
}

/** 粘贴文本超过此长度时折叠为 chip */
const PASTE_CHIP_THRESHOLD = 200

export function ChatterComposer({
  attachments,
  onAttachmentRemove,
  onPasteText,
  onAfterSend,
  modelId,
  onModelChange,
  showModelSelector = true
}: ChatterComposerProps) {
  const api = useAui()
  const [waveformCtx, setWaveformCtx] = useState<MediaStream | null>(null)
  const composerBoxRef = useRef<HTMLDivElement>(null)

  const handleVoiceResult = useCallback(
    (text: string) => {
      api.composer().setText(text)
    },
    [api]
  )

  // 发送前把 text chip 内容 prepend 到输入框，再调 send
  const handleSend = useCallback(() => {
    const textChips = attachments.filter((a) => a.type === "text" && a.content)
    if (textChips.length > 0) {
      const current = api.composer().getState().text ?? ""
      const prefix = textChips.map((a) => a.content).join("\n\n")
      api.composer().setText(prefix + (current ? `\n\n${current}` : ""))
    }
    api.composer().send()
    onAfterSend?.()
  }, [api, attachments, onAfterSend])

  // 在捕获阶段拦截 paste，优先于 assistant-ui 内部处理
  useEffect(() => {
    const box = composerBoxRef.current
    if (!box || !onPasteText) return
    const handler = (e: ClipboardEvent) => {
      const text = e.clipboardData?.getData("text/plain") ?? ""
      if (text.length < PASTE_CHIP_THRESHOLD) return
      const target = e.target as HTMLTextAreaElement | null
      const selStart = target?.selectionStart ?? 0
      e.preventDefault()
      e.stopPropagation()
      onPasteText({
        type: "text",
        title: "粘贴文本",
        summary: text.slice(0, 60) + (text.length > 60 ? "…" : ""),
        content: text
      })
      // 恢复焦点并保持光标原位置
      // 用 execCommand 插入空字符串使浏览器 undo 栈记录此操作，支持 Ctrl+Z
      requestAnimationFrame(() => {
        const textarea = box.querySelector("textarea")
        if (textarea) {
          textarea.focus()
          textarea.setSelectionRange(selStart, selStart)
          // biome-ignore lint/suspicious/noExplicitAny: execCommand deprecated but still works for undo support
          ;(document as any).execCommand("insertText", false, "")
        }
      })
    }
    box.addEventListener("paste", handler, true) // capture=true
    return () => box.removeEventListener("paste", handler, true)
  }, [onPasteText])

  return (
    <ComposerPrimitive.Root className="px-3 pb-3">
      <div
        ref={composerBoxRef}
        className="rounded-xl border border-border bg-background transition-colors focus-within:border-foreground/60"
      >
        {/* 附件列表：原生 assistant-ui attachments + 自定义 text chip */}
        <ComposerPrimitive.Attachments>
          {({ attachment }) => (
            <div className="relative m-1 inline-flex size-16 shrink-0 overflow-hidden rounded-lg border border-border">
              {/* biome-ignore lint/performance/noImgElement: 附件预览 */}
              <img
                src={
                  (attachment as { content?: Array<{ type: string; image?: string }> })
                    ?.content?.[0]?.image ?? undefined
                }
                alt={attachment.name}
                className="size-full object-cover"
              />
              <button
                type="button"
                onClick={() => api.composer().attachment({ id: attachment.id }).remove()}
                className="absolute top-0.5 right-0.5 flex size-4 items-center justify-center rounded-full bg-black/60 text-white hover:bg-black/80"
              >
                <span className="text-[10px] leading-none">×</span>
              </button>
            </div>
          )}
        </ComposerPrimitive.Attachments>
        {/* 自定义 text/doc chip */}
        {attachments.length > 0 && (
          <div className="flex flex-wrap gap-1 px-3 pt-2">
            {attachments.map((item, i) => (
              <ContextChip
                key={`${item.type}-${item.id ?? i}`}
                item={item}
                onRemove={() => onAttachmentRemove(i)}
              />
            ))}
          </div>
        )}

        {/* 输入框 */}
        <ComposerPrimitive.Input
          placeholder="输入消息..."
          className="field-sizing-content max-h-36 w-full resize-none bg-transparent px-3 pt-2.5 pb-2 text-sm leading-5 placeholder:text-muted-foreground focus:outline-none"
          rows={1}
        />

        {/* 底部工具栏 */}
        <div className="relative flex items-center justify-between px-1.5 pb-1.5">
          {/* 左：附件 + 模型选择 */}
          <div className="flex items-center gap-1">
            {/* 文件上传（暂时隐藏，待后端支持附件后开放）
            <input
              ref={fileInputRef}
              type="file"
              multiple
              className="hidden"
              onChange={handleFileChange}
            />
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="size-7 rounded-lg"
              aria-label="上传附件"
              onClick={() => fileInputRef.current?.click()}
            >
              <PlusIcon className="size-4" />
            </Button>
            */}

            {showModelSelector && (
              <ModelSelectorSlot modelId={modelId} onModelChange={onModelChange} />
            )}
          </div>

          {/* 右：3D波形（语音激活时）+ 麦克风 + 发送/停止 */}
          <div className="flex items-center gap-1">
            {waveformCtx && (
              <div className="pointer-events-none absolute inset-y-1.5 right-[100px] left-[100px] overflow-hidden rounded-lg">
                <VoiceWaveform3D stream={waveformCtx} />
              </div>
            )}

            <WsAsrButton
              onResult={handleVoiceResult}
              onInterim={handleVoiceResult}
              onRecordingChange={setWaveformCtx}
            />

            <AuiIf condition={(s) => !s.thread.isRunning}>
              <Button size="icon" className="size-7 rounded-lg" onClick={handleSend}>
                <ArrowUpIcon className="size-4" />
              </Button>
            </AuiIf>
            <AuiIf condition={(s) => s.thread.isRunning}>
              <ComposerPrimitive.Cancel asChild>
                <Button type="button" variant="secondary" size="icon" className="size-7 rounded-lg">
                  <SquareIcon className="size-3 fill-current" />
                </Button>
              </ComposerPrimitive.Cancel>
            </AuiIf>
          </div>
        </div>
      </div>
    </ComposerPrimitive.Root>
  )
}

/**
 * 模型选择槽——仅在需要展示时渲染，避免未登录场景调用 /ai/models 触发 401
 */
function ModelSelectorSlot({
  modelId,
  onModelChange
}: {
  modelId?: string
  onModelChange?: (modelId: string, model: AiModelVO) => void
}) {
  const {
    options,
    modelId: selectedModelId,
    setModelId
  } = useModelSelector("CHAT", {
    value: modelId,
    onChange: onModelChange
  })
  return <ModelSelector options={options} value={selectedModelId} onChange={setModelId} />
}
