/**
 * ChatterThread——消息列表区域
 * AI 消息：MarkdownMessage 富渲染 + 语音播放按钮
 * 用户消息：纯文本气泡
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { MessagePrimitive, ThreadPrimitive, useMessage } from "@assistant-ui/react"
import { Play } from "lucide-react"
import { useCallback } from "react"
import { Button } from "@/components/ui/button"
import { MarkdownMessage } from "@/features/livechat/components/MarkdownMessage"
import { SpeechOutput } from "@/features/livechat/voice/SpeechOutput"
import { serverTtsStream, useVoiceConfig } from "@/lib/store/voice-config"

/** AI 消息内容提取（用于 TTS） */
function useMessageText(): string {
  const message = useMessage()
  if (message.role !== "assistant") return ""
  return message.content
    .filter((p) => p.type === "text")
    .map((p) => ("text" in p ? p.text : ""))
    .join("")
}

/** AI 消息气泡：Markdown 富渲染 + 语音播放 */
function AssistantMessage() {
  const text = useMessageText()
  const ttsMode = useVoiceConfig((s) => s.ttsMode)
  const ttsVoice = useVoiceConfig((s) => s.ttsVoice)

  const handleServerPlay = useCallback(async () => {
    if (!text) return
    const audioCtx = new AudioContext()
    await serverTtsStream(text, ttsVoice, async (chunk) => {
      const buffer = await audioCtx.decodeAudioData(chunk)
      const source = audioCtx.createBufferSource()
      source.buffer = buffer
      source.connect(audioCtx.destination)
      source.start()
    })
  }, [text, ttsVoice])

  return (
    <div className="mb-3 flex justify-start">
      <div className="max-w-[80%] rounded-lg bg-muted px-3 py-2 text-sm">
        {/* MarkdownMessage 作为 Text 组件注入，支持代码高亮、表格等富渲染 */}
        <MessagePrimitive.Content components={{ Text: MarkdownMessage }} />
        {text && (
          <div className="mt-1 border-t pt-1">
            {ttsMode === "browser" ? (
              <SpeechOutput text={text} />
            ) : (
              <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={handleServerPlay}
                aria-label="播放语音（后端 TTS）"
              >
                <Play className="size-4" />
              </Button>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

/** 用户消息气泡 */
function UserMessage() {
  return (
    <div className="mb-3 flex justify-end">
      <div className="max-w-[80%] rounded-lg bg-primary px-3 py-2 text-primary-foreground text-sm">
        <MessagePrimitive.Content />
      </div>
    </div>
  )
}

export function ChatterThread() {
  return (
    <ThreadPrimitive.Root className="flex min-h-0 flex-1 flex-col">
      <ThreadPrimitive.Viewport className="min-h-0 flex-1 overflow-y-auto p-4">
        <ThreadPrimitive.Messages>
          {({ message }) => (message.role === "assistant" ? <AssistantMessage /> : <UserMessage />)}
        </ThreadPrimitive.Messages>
      </ThreadPrimitive.Viewport>
    </ThreadPrimitive.Root>
  )
}
