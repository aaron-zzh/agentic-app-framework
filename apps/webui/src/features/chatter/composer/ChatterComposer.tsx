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
import { ArrowUpIcon, PlusIcon, SquareIcon } from "lucide-react"
import { useCallback, useRef, useState } from "react"
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
  modelId?: string
  onModelChange?: (modelId: string, model: AiModelVO) => void
}

export function ChatterComposer({
  attachments,
  onAttachmentRemove,
  modelId,
  onModelChange
}: ChatterComposerProps) {
  const api = useAui()
  const [waveformCtx, setWaveformCtx] = useState<MediaStream | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const {
    options,
    modelId: selectedModelId,
    setModelId
  } = useModelSelector("CHAT", {
    value: modelId,
    onChange: onModelChange
  })

  const handleVoiceResult = useCallback(
    (text: string) => {
      api.composer().setText(text)
    },
    [api]
  )

  return (
    <ComposerPrimitive.Root className="px-3 pb-3">
      <div className="rounded-xl border border-border bg-background transition-colors focus-within:border-foreground/60">
        {/* 附件列表 */}
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
            <input ref={fileInputRef} type="file" multiple className="hidden" onChange={() => {}} />
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

            <ModelSelector options={options} value={selectedModelId} onChange={setModelId} />
          </div>

          {/* 右：3D波形（语音激活时）+ 麦克风 + 发送/停止 */}
          <div className="flex items-center gap-1">
            {waveformCtx && (
              <div className="pointer-events-none absolute inset-y-1.5 right-[100px] left-[100px] overflow-hidden rounded-lg">
                <VoiceWaveform3D stream={waveformCtx} />
              </div>
            )}

            <WsAsrButton onResult={handleVoiceResult} onRecordingChange={setWaveformCtx} />

            <AuiIf condition={(s) => !s.thread.isRunning}>
              <ComposerPrimitive.Send asChild>
                <Button size="icon" className="size-7 rounded-lg">
                  <ArrowUpIcon className="size-4" />
                </Button>
              </ComposerPrimitive.Send>
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
