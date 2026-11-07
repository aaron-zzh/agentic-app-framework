/**
 * OmniRealtimePanel——实时语音对话内嵌面板
 * 替换 ChatterThread 展示，点击工具栏电话按钮后切换显示
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { buildWsUrl } from "@/lib/api/config"
import { useAuthStore } from "@/lib/store/auth-store"
import { float32ToPcm16 } from "@/lib/utils/audio"
import { parseOmniRealtimeReadyEvent } from "@/lib/voice/omni-realtime-protocol"

// 本地播放结束后继续静音的余量（秒），避免扬声器尾音被麦克风录入
const PLAYBACK_TAIL_SECONDS = 0.3

type WsStatus = "connecting" | "ready" | "closing" | "disconnected"

interface OmniMessage {
  role: "user" | "assistant" | "system"
  text: string
}

interface RealtimeEvent {
  type: string
  text?: string
  code?: string
  message?: string
  fatal?: boolean
}

interface OmniRealtimePanelProps {
  onClose: () => void
  /** 显式指定角色 roleKey（如 system.role.elder-companion）；为空时后端回退到助理默认角色 */
  roleKey?: string
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value)
}

function parseRealtimeEvent(data: unknown): RealtimeEvent | null {
  if (typeof data !== "string") return null

  try {
    const parsed: unknown = JSON.parse(data)
    if (!isRecord(parsed) || typeof parsed.type !== "string") return null

    const { type, text, code, message, fatal } = parsed
    if (typeof type !== "string") return null

    return {
      type,
      ...(typeof text === "string" ? { text } : {}),
      ...(typeof code === "string" ? { code } : {}),
      ...(typeof message === "string" ? { message } : {}),
      ...(typeof fatal === "boolean" ? { fatal } : {})
    }
  } catch {
    return null
  }
}

export function OmniRealtimePanel({ onClose, roleKey }: OmniRealtimePanelProps) {
  const accessToken = useAuthStore((s) => s.accessToken)
  const [status, setStatus] = useState<WsStatus>("disconnected")
  const [messages, setMessages] = useState<OmniMessage[]>([])
  const [responding, setResponding] = useState(false)
  const [currentInputTranscript, setCurrentInputTranscript] = useState("")
  const [currentResponseText, setCurrentResponseText] = useState("")

  const wsRef = useRef<WebSocket | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const audioContextRef = useRef<AudioContext | null>(null)
  const processorRef = useRef<ScriptProcessorNode | null>(null)
  const playbackCtxRef = useRef<AudioContext | null>(null)
  const playheadRef = useRef(0)
  const audioOutputActiveRef = useRef(false)
  const responseActiveRef = useRef(false)
  const responseTextRef = useRef("")
  const sessionClosingRef = useRef(false)

  const closePlayback = useCallback(() => {
    audioOutputActiveRef.current = false
    void playbackCtxRef.current?.close()
    playbackCtxRef.current = null
    playheadRef.current = 0
  }, [])

  // 模型语音仍在本地播放（含尾音余量）时返回 true
  const isPlaybackActive = useCallback(() => {
    const ctx = playbackCtxRef.current
    if (!ctx || ctx.state === "closed") return false
    return ctx.currentTime < playheadRef.current + PLAYBACK_TAIL_SECONDS
  }, [])

  const stopCapture = useCallback(() => {
    processorRef.current?.disconnect()
    processorRef.current = null
    void audioContextRef.current?.close()
    audioContextRef.current = null
    if (streamRef.current) {
      for (const track of streamRef.current.getTracks()) track.stop()
      streamRef.current = null
    }
  }, [])

  const playAudioChunk = useCallback(
    (pcm: ArrayBuffer) => {
      if (!audioOutputActiveRef.current || pcm.byteLength === 0 || pcm.byteLength % 2 !== 0) return

      try {
        let ctx = playbackCtxRef.current
        if (!ctx || ctx.state === "closed") {
          ctx = new AudioContext({ sampleRate: 24000 })
          playbackCtxRef.current = ctx
          playheadRef.current = ctx.currentTime
        }

        const int16 = new Int16Array(pcm)
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
        closePlayback()
      }
    },
    [closePlayback]
  )

  const startCapture = useCallback(
    async (ws: WebSocket) => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: {
            sampleRate: 16000,
            channelCount: 1,
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl: true
          }
        })
        if (wsRef.current !== ws || ws.readyState !== WebSocket.OPEN || sessionClosingRef.current) {
          for (const track of stream.getTracks()) track.stop()
          return
        }

        streamRef.current = stream
        const audioCtx = new AudioContext({ sampleRate: 16000 })
        audioContextRef.current = audioCtx
        const source = audioCtx.createMediaStreamSource(stream)
        const processor = audioCtx.createScriptProcessor(4096, 1, 1)
        processorRef.current = processor
        processor.onaudioprocess = (event: AudioProcessingEvent) => {
          if (ws.readyState !== WebSocket.OPEN || sessionClosingRef.current) return
          const pcm16 = float32ToPcm16(event.inputBuffer.getChannelData(0))
          // 响应进行中或本地仍在播放时发送等长静音：避免扬声器回声被识别为用户输入，同时让服务端持续收到输入不判定空闲
          const muted = responseActiveRef.current || isPlaybackActive()
          const frame = muted ? new Int16Array(pcm16.length) : pcm16
          ws.send(frame.buffer)
        }
        source.connect(processor)
        processor.connect(audioCtx.destination)
      } catch {
        stopCapture()
        ws.close()
      }
    },
    [isPlaybackActive, stopCapture]
  )

  const handleStart = useCallback(() => {
    if (!accessToken) {
      setMessages([{ role: "system", text: "请先登录后使用实时语音" }])
      return
    }
    setMessages([])
    setCurrentInputTranscript("")
    setCurrentResponseText("")
    responseTextRef.current = ""
    responseActiveRef.current = false
    sessionClosingRef.current = false
    const params = new URLSearchParams({ token: accessToken })
    const ws = new WebSocket(buildWsUrl(`/ws/realtime-conversation?${params.toString()}`))
    ws.binaryType = "arraybuffer"
    wsRef.current = ws
    setStatus("connecting")

    ws.onopen = () => {
      const hello: Record<string, unknown> = {
        type: "hello",
        protocol: "omni-realtime.v1",
        endpointing: "server"
      }
      if (roleKey) hello.roleKey = roleKey
      ws.send(JSON.stringify(hello))
    }

    ws.onmessage = (event: MessageEvent<unknown>) => {
      if (event.data instanceof ArrayBuffer) {
        playAudioChunk(event.data)
        return
      }

      const message = parseRealtimeEvent(event.data)
      if (!message) return

      switch (message.type) {
        case "ready":
          if (parseOmniRealtimeReadyEvent(event.data) === null) {
            stopCapture()
            closePlayback()
            ws.close()
            break
          }
          setStatus("ready")
          void startCapture(ws)
          break
        case "input_audio.speech_started":
          setCurrentInputTranscript("")
          break
        case "input_audio.transcript.delta":
          setCurrentInputTranscript((previous) => previous + (message.text ?? ""))
          break
        case "input_audio.transcript.completed":
          if (message.text) {
            setMessages((previous) => [...previous, { role: "user", text: message.text ?? "" }])
          }
          setCurrentInputTranscript("")
          break
        case "response.created":
          responseActiveRef.current = true
          responseTextRef.current = ""
          setCurrentResponseText("")
          setResponding(true)
          break
        case "response.text.delta":
          responseTextRef.current += message.text ?? ""
          setCurrentResponseText(responseTextRef.current)
          setResponding(true)
          break
        case "response.audio.started":
          audioOutputActiveRef.current = true
          setResponding(true)
          break
        case "response.audio.done":
          audioOutputActiveRef.current = false
          break
        case "response.completed":
          responseActiveRef.current = false
          if (responseTextRef.current) {
            setMessages((previous) => [
              ...previous,
              { role: "assistant", text: responseTextRef.current }
            ])
          }
          responseTextRef.current = ""
          setCurrentResponseText("")
          setResponding(false)
          break
        case "response.cancelled":
          responseActiveRef.current = false
          responseTextRef.current = ""
          setCurrentResponseText("")
          setResponding(false)
          closePlayback()
          break
        case "error":
          setMessages((previous) => [
            ...previous,
            {
              role: "system",
              text: `错误${message.code ? ` (${message.code})` : ""}: ${message.message ?? "未知错误"}`
            }
          ])
          if (message.fatal) ws.close()
          break
        case "closed":
          ws.close()
          break
      }
    }

    ws.onclose = () => {
      stopCapture()
      closePlayback()
      wsRef.current = null
      sessionClosingRef.current = false
      responseActiveRef.current = false
      setResponding(false)
      setStatus("disconnected")
    }
    ws.onerror = () => ws.close()
  }, [accessToken, closePlayback, playAudioChunk, roleKey, startCapture, stopCapture])

  const handleCancelResponse = useCallback(() => {
    const ws = wsRef.current
    if (!ws || ws.readyState !== WebSocket.OPEN || !responding) return

    closePlayback()
    ws.send(JSON.stringify({ type: "response.cancel" }))
  }, [closePlayback, responding])

  const handleStop = useCallback(() => {
    stopCapture()
    closePlayback()
    const ws = wsRef.current
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      setStatus("disconnected")
      return
    }

    sessionClosingRef.current = true
    setStatus("closing")
    ws.send(JSON.stringify({ type: "session.close" }))
  }, [closePlayback, stopCapture])

  useEffect(() => {
    return () => {
      stopCapture()
      closePlayback()
      const ws = wsRef.current
      if (ws?.readyState === WebSocket.OPEN && !sessionClosingRef.current) {
        ws.send(JSON.stringify({ type: "session.close" }))
      }
      ws?.close()
    }
  }, [closePlayback, stopCapture])

  const handleClose = useCallback(() => {
    handleStop()
    onClose()
  }, [handleStop, onClose])

  const statusVariant =
    status === "ready"
      ? "default"
      : status === "connecting" || status === "closing"
        ? "secondary"
        : "outline"

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
          {responding ? (
            <Button size="sm" variant="outline" onClick={handleCancelResponse}>
              取消回复
            </Button>
          ) : null}
          {status === "disconnected" ? (
            <Button size="sm" onClick={handleStart} disabled={!accessToken}>
              开始
            </Button>
          ) : (
            <Button
              size="sm"
              variant="destructive"
              onClick={handleStop}
              disabled={status === "closing"}
            >
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
        {messages.length === 0 && !currentInputTranscript && !currentResponseText ? (
          <p className="pt-8 text-center text-muted-foreground text-sm">
            点击「开始」进入实时对话...
          </p>
        ) : (
          <div className="flex flex-col gap-3">
            {messages.map((message) => (
              <div
                key={`${message.role}-${message.text}`}
                className={`flex ${message.role === "user" ? "justify-end" : "justify-start"}`}
              >
                <div
                  className={`max-w-[80%] rounded-lg px-3 py-2 text-sm ${
                    message.role === "user"
                      ? "bg-primary text-primary-foreground"
                      : message.role === "system"
                        ? "bg-destructive/10 text-destructive"
                        : "bg-muted"
                  }`}
                >
                  {message.text}
                </div>
              </div>
            ))}
            {currentInputTranscript ? (
              <div className="flex justify-end">
                <div className="max-w-[80%] rounded-lg bg-primary/70 px-3 py-2 text-primary-foreground text-sm italic">
                  {currentInputTranscript}
                </div>
              </div>
            ) : null}
            {currentResponseText ? (
              <div className="flex justify-start">
                <div className="max-w-[80%] rounded-lg bg-muted px-3 py-2 text-sm italic opacity-70">
                  {currentResponseText}
                </div>
              </div>
            ) : null}
          </div>
        )}
      </ScrollArea>
    </div>
  )
}
