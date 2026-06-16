/**
 * ChatterComposer——输入区，支持拖放附件 + 语音输入（WebSocket 流式 ASR）
 * 基于 assistant-ui ComposerPrimitive + 附件列表 + WsAsrButton
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ComposerPrimitive, useAui } from "@assistant-ui/react"
import { useCallback, useState } from "react"
import { Button } from "@/components/ui/button"
import { VoiceWaveform3D } from "@/features/livechat/voice/VoiceWaveform3D"
import { WsAsrButton } from "@/features/livechat/voice/WsAsrButton"
import { ContextChip } from "./dnd/ContextChip"
import type { ChatterDropItem } from "./types"

interface ChatterComposerProps {
  attachments: ChatterDropItem[]
  onAttachmentRemove: (index: number) => void
}

export function ChatterComposer({ attachments, onAttachmentRemove }: ChatterComposerProps) {
  const api = useAui()
  const [waveformCtx, setWaveformCtx] = useState<MediaStream | null>(null)

  const handleVoiceResult = useCallback(
    (text: string) => {
      api.composer().setText(text)
    },
    [api]
  )

  return (
    <ComposerPrimitive.Root className="border-t">
      {/* 录音波形——显示在输入框上方 */}
      {waveformCtx && (
        <div className="h-[40px] w-full">
          <VoiceWaveform3D stream={waveformCtx} />
        </div>
      )}

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

      {/* 输入区 */}
      <div className={`flex items-end gap-2 px-3 pb-3 ${waveformCtx ? "pt-0" : "pt-3"}`}>
        <WsAsrButton
          onResult={handleVoiceResult}
          onRecordingChange={setWaveformCtx}
          className="shrink-0"
        />

        <ComposerPrimitive.Input
          className="max-h-36 flex-1 resize-none overflow-y-auto rounded-md border bg-background px-3 py-2 text-sm outline-none placeholder:text-muted-foreground focus:ring-1 focus:ring-ring"
          placeholder="输入消息，或点击麦克风语音输入..."
        />
        <ComposerPrimitive.Send asChild>
          <Button type="submit" size="sm" variant="ghost">
            发送
          </Button>
        </ComposerPrimitive.Send>
      </div>
    </ComposerPrimitive.Root>
  )
}
