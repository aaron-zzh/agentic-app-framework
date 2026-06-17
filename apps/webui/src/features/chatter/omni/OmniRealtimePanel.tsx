/**
 * OmniRealtimePanel——实时语音对话内嵌面板
 * 替换 ChatterThread 展示，点击工具栏电话按钮后切换显示
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useRef, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { buildWsUrl } from "@/lib/api/config"
import { useAuthStore } from "@/lib/store/auth-store"
import { float32ToPcm16 } from "@/lib/utils/audio"

type WsStatus = "connecting" | "connected" | "disconnected"

interface OmniMessage {
  role: "user" | "assistant" | "system"
  text: string
}

interface OmniRealtimePanelProps {
  onClose: () => void
}

export function OmniRealtimePanel({ onClose }: OmniRealtimePanelProps) {
  const accessToken = useAuthStore((s) => s.accessToken)
  const [status, setStatus] = useState<WsStatus>("disconnected")
  const [messages, setMessages] = useState<OmniMessage[]>([])
  const [recording, setRecording] = useState(false)
  const [currentTranscript, setCurrentTranscript] = useState("")

  const wsRef = useRef<WebSocket | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const audioContextRef = useRef<AudioContext | null>(null)
  const processorRef = useRef<ScriptProcessorNode | null>(null)
  const playbackCtxRef = useRef<AudioContext | null>(null)
  const playheadRef = useRef(0)

  const playAudioChunk = useCallback((base64Data: string | undefined) => {
    if (!base64Data) return
    try {
      let ctx = playbackCtxRef.current
      if (!ctx || ctx.state === "closed") {
        ctx = new AudioContext({ sampleRate: 24000 })
        playbackCtxRef.current = ctx
        playheadRef.current = ctx.currentTime
      }
      const binary = atob(base64Data)
      const bytes = new Uint8Array(binary.length)
      for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
      const int16 = new Int16Array(bytes.buffer)
      const float32 = new Float32Array(int16.length)
      for (let i = 0; i < int16.length; i++) float32[i] = int16[i] / 0x8000
      const audioBuffer = ctx.createBuffer(1, float32.length, 24000)
      audioBuffer.getChannelData(0).set(float32)
      const source = ctx.createBufferSource()
      source.buffer = audioBuffer
      source.connect(ctx.destination)
      const startAt = Math.max(ctx.currentTime, playheadRef.current)
      source.start(startAt)
      playheadRef.current = startAt + audioBuffer.duration
    } catch {
      // 静默忽略
    }
  }, [])

  const handleStart = useCallback(async () => {
    setMessages([])
    setCurrentTranscript("")
    const params = new URLSearchParams({ vad: "true", token: accessToken ?? "" })
    const ws = new WebSocket(buildWsUrl(`/ws/omni-realtime?${params.toString()}`))
    wsRef.current = ws
    setStatus("connecting")

    ws.onopen = async () => {
      setStatus("connected")
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: { sampleRate: 16000, channelCount: 1 }
        })
        streamRef.current = stream
        const audioCtx = new AudioContext({ sampleRate: 16000 })
        audioContextRef.current = audioCtx
        const source = audioCtx.createMediaStreamSource(stream)
        const processor = audioCtx.createScriptProcessor(4096, 1, 1)
        processorRef.current = processor
        processor.onaudioprocess = (e: AudioProcessingEvent) => {
          if (ws.readyState !== WebSocket.OPEN) return
          const pcm16 = float32ToPcm16(e.inputBuffer.getChannelData(0))
          const binary = new Uint8Array(pcm16.buffer as ArrayBuffer)
          let str = ""
          for (let i = 0; i < binary.byteLength; i++) str += String.fromCharCode(binary[i])
          ws.send(JSON.stringify({ type: "audio", data: btoa(str) }))
        }
        source.connect(processor)
        processor.connect(audioCtx.destination)
        setRecording(true)
      } catch {
        ws.close()
        setStatus("disconnected")
      }
    }

    ws.onmessage = (event: MessageEvent) => {
      const msg = JSON.parse(event.data as string) as {
        type: string
        text?: string
        audioData?: string
      }
      switch (msg.type) {
        case "audio_transcript_delta":
          setCurrentTranscript((prev) => prev + (msg.text ?? ""))
          break
        case "transcript_done":
          setMessages((prev) => [...prev, { role: "assistant", text: msg.text ?? "" }])
          setCurrentTranscript("")
          break
        case "audio_delta":
          playAudioChunk(msg.audioData)
          break
        case "input_transcript":
          setMessages((prev) => [...prev, { role: "user", text: msg.text ?? "" }])
          break
        case "speech_started":
          setCurrentTranscript("")
          break
        case "error":
          setMessages((prev) => [...prev, { role: "system", text: `错误: ${msg.text}` }])
          break
      }
    }

    ws.onclose = () => {
      setStatus("disconnected")
      setRecording(false)
    }
    ws.onerror = () => ws.close()
  }, [accessToken, playAudioChunk])

  const handleStop = useCallback(() => {
    processorRef.current?.disconnect()
    processorRef.current = null
    audioContextRef.current?.close()
    audioContextRef.current = null
    playbackCtxRef.current?.close()
    playbackCtxRef.current = null
    playheadRef.current = 0
    if (streamRef.current) {
      for (const t of streamRef.current.getTracks()) t.stop()
      streamRef.current = null
    }
    wsRef.current?.close()
    wsRef.current = null
    setRecording(false)
  }, [])

  const handleClose = useCallback(() => {
    handleStop()
    onClose()
  }, [handleStop, onClose])

  const statusVariant =
    status === "connected" ? "default" : status === "connecting" ? "secondary" : "outline"

  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      {/* 顶部状态栏 */}
      <div className="flex items-center justify-between border-b px-4 py-2">
        <div className="flex items-center gap-2">
          <span className="font-medium text-sm">实时语音对话</span>
          <Badge variant={statusVariant} className="text-xs">
            {status}
          </Badge>
        </div>
        <div className="flex items-center gap-2">
          {!recording ? (
            <Button size="sm" onClick={handleStart}>
              开始
            </Button>
          ) : (
            <Button size="sm" variant="destructive" onClick={handleStop}>
              结束
            </Button>
          )}
          <Button size="sm" variant="ghost" onClick={handleClose}>
            关闭
          </Button>
        </div>
      </div>

      {/* 对话内容 */}
      <ScrollArea className="flex-1 p-4">
        {messages.length === 0 && !currentTranscript ? (
          <p className="pt-8 text-center text-muted-foreground text-sm">
            点击「开始」进入实时对话...
          </p>
        ) : (
          <div className="space-y-3">
            {messages.map((msg, i) => (
              <div
                key={`${i}-${msg.text.slice(0, 8)}`}
                className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
              >
                <div
                  className={`max-w-[80%] rounded-lg px-3 py-2 text-sm ${
                    msg.role === "user"
                      ? "bg-primary text-primary-foreground"
                      : msg.role === "system"
                        ? "bg-destructive/10 text-destructive"
                        : "bg-muted"
                  }`}
                >
                  {msg.text}
                </div>
              </div>
            ))}
            {currentTranscript && (
              <div className="flex justify-start">
                <div className="max-w-[80%] rounded-lg bg-muted px-3 py-2 text-sm italic opacity-70">
                  {currentTranscript}
                </div>
              </div>
            )}
          </div>
        )}
      </ScrollArea>
    </div>
  )
}
