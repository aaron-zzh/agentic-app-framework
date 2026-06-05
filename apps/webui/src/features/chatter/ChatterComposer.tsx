/**
 * ChatterComposer——输入区，支持拖放附件 + 语音输入
 * 基于 assistant-ui ComposerPrimitive + 附件列表 + SpeechInput
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ComposerPrimitive, useAui } from "@assistant-ui/react"
import { Mic } from "lucide-react"
import { useCallback, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { SpeechInput } from "@/features/livechat/voice/SpeechInput"
import { serverStt, useVoiceConfig } from "@/lib/store/voice-config"
import { ContextChip } from "./dnd/ContextChip"
import type { ChatterDropItem } from "./types"

interface ChatterComposerProps {
  attachments: ChatterDropItem[]
  onAttachmentRemove: (index: number) => void
}

export function ChatterComposer({ attachments, onAttachmentRemove }: ChatterComposerProps) {
  const [voiceOpen, setVoiceOpen] = useState(false)
  const api = useAui()
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const sttMode = useVoiceConfig((s) => s.sttMode)

  /** 语音识别完成：直接发送消息 */
  const handleVoiceResult = useCallback(
    (text: string) => {
      if (!text.trim()) return
      setVoiceOpen(false)
      api.thread().append({
        role: "user",
        content: [{ type: "text", text }]
      })
    },
    [api]
  )

  /** server 模式：录音完成后上传到后端 STT */
  const handleServerStt = useCallback(
    async (audioBlob: Blob) => {
      try {
        const text = await serverStt(audioBlob)
        handleVoiceResult(text)
      } catch (_e) {
        // console.error("[STT] 后端识别失败", _e)
        // TODO: toast 提示用户语音识别失败
      }
    },
    [handleVoiceResult]
  )

  return (
    <ComposerPrimitive.Root className="border-t">
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

      {/* 语音输入面板（展开时显示） */}
      {voiceOpen && (
        <div className="border-b px-3 py-2">
          {sttMode === "browser" ? (
            <SpeechInput onResult={handleVoiceResult} />
          ) : (
            <AudioRecorderPanel onRecorded={handleServerStt} />
          )}
        </div>
      )}

      {/* 输入区 */}
      <div className="flex items-end gap-2 p-3">
        {/* 语音输入切换按钮 */}
        <Button
          type="button"
          variant={voiceOpen ? "default" : "ghost"}
          size="icon"
          className="shrink-0"
          onClick={() => setVoiceOpen((v) => !v)}
          aria-label={voiceOpen ? "关闭语音输入" : "开启语音输入"}
        >
          <Mic className="size-4" />
        </Button>

        <ComposerPrimitive.Input
          ref={inputRef}
          className="flex-1 resize-none rounded-md border bg-background px-3 py-2 text-sm outline-none placeholder:text-muted-foreground focus:ring-1 focus:ring-ring"
          placeholder="输入消息，或点击麦克风语音输入..."
        />
        <ComposerPrimitive.Send />
      </div>
    </ComposerPrimitive.Root>
  )
}

/** server 模式录音面板：录音完成后回调 Blob */
function AudioRecorderPanel({ onRecorded }: { onRecorded: (blob: Blob) => void }) {
  const [recording, setRecording] = useState(false)
  const mediaRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])

  const start = useCallback(async () => {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    const recorder = new MediaRecorder(stream)
    chunksRef.current = []
    recorder.ondataavailable = (e) => chunksRef.current.push(e.data)
    recorder.onstop = () => {
      const blob = new Blob(chunksRef.current, { type: "audio/wav" })
      stream.getTracks().forEach((t) => {
        t.stop()
      })
      onRecorded(blob)
    }
    recorder.start()
    mediaRef.current = recorder
    setRecording(true)
  }, [onRecorded])

  const stop = useCallback(() => {
    mediaRef.current?.stop()
    setRecording(false)
  }, [])

  return (
    <div className="flex items-center gap-3">
      <Button
        type="button"
        variant={recording ? "destructive" : "default"}
        size="icon"
        onClick={recording ? stop : start}
        aria-label={recording ? "停止录音并识别" : "开始录音"}
      >
        <Mic className="size-4" />
      </Button>
      {recording && (
        <span className="animate-pulse text-muted-foreground text-sm">
          录音中，点击停止并识别...
        </span>
      )}
    </div>
  )
}
