"use client"

/**
 * Omni Realtime v1 多模态实时对话示例，通过 WebSocket 发送 PCM 音频与 JPEG 图像。
 * 路由：/examples/realtime-conversation
 * @author AaronZZH & Kiro
 */

import { useBoolean } from "@aaf/hooks"
import { useCallback, useEffect, useRef, useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { TypographyH1 } from "@/components/ui/typography"
import { buildWsUrl } from "@/lib/api/config"
import { useAiRoleLibrary } from "@/lib/api/rest/ai/role"
import { useAuthStore } from "@/lib/store/auth-store"
import { float32ToPcm16 } from "@/lib/utils/audio"
import { parseOmniRealtimeReadyEvent } from "@/lib/voice/omni-realtime-protocol"

type WsStatus = "connecting" | "ready" | "closing" | "disconnected"
type KnowledgeSearchPhase = "idle" | "searching" | "completed"

interface RealtimeConversationMessage {
  id: number
  role: "user" | "assistant" | "system"
  text: string
}

interface RealtimeEvent {
  type: string
  text?: string
  code?: string
  message?: string
  fatal?: boolean
  contextResolveMs?: number
  knowledgeEnabled?: boolean
  status?: string
  durationMs?: number
  resultCount?: number
}

interface KnowledgeSearchState {
  phase: KnowledgeSearchPhase
  status: string | null
  durationMs: number | null
  resultCount: number | null
}

const DEFAULT_ROLE_KEY = "__default__"
const DEFAULT_VOICE = "Tina"
const REALTIME_VOICES = [
  { value: "Tina", label: "女声 · Tina" },
  { value: "Serena", label: "女声 · Serena" },
  { value: "Ethan", label: "男声 · Ethan" },
  { value: "Harvey", label: "男声 · Harvey" },
  { value: "Momo", label: "活泼 · Momo" }
] as const
const WS_STATUS_LABELS: Record<WsStatus, string> = {
  connecting: "连接中",
  ready: "已连接",
  closing: "正在断开",
  disconnected: "未连接"
}
const INITIAL_KNOWLEDGE_SEARCH_STATE: KnowledgeSearchState = {
  phase: "idle",
  status: null,
  durationMs: null,
  resultCount: null
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value)
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value)
}

function parseRealtimeEvent(data: unknown): RealtimeEvent | null {
  if (typeof data !== "string") return null

  try {
    const parsed: unknown = JSON.parse(data)
    if (!isRecord(parsed) || typeof parsed.type !== "string") return null

    return {
      type: parsed.type,
      ...(typeof parsed.text === "string" ? { text: parsed.text } : {}),
      ...(typeof parsed.code === "string" ? { code: parsed.code } : {}),
      ...(typeof parsed.message === "string" ? { message: parsed.message } : {}),
      ...(typeof parsed.fatal === "boolean" ? { fatal: parsed.fatal } : {}),
      ...(isFiniteNumber(parsed.contextResolveMs)
        ? { contextResolveMs: parsed.contextResolveMs }
        : {}),
      ...(typeof parsed.knowledgeEnabled === "boolean"
        ? { knowledgeEnabled: parsed.knowledgeEnabled }
        : {}),
      ...(typeof parsed.status === "string" ? { status: parsed.status } : {}),
      ...(isFiniteNumber(parsed.durationMs) ? { durationMs: parsed.durationMs } : {}),
      ...(isFiniteNumber(parsed.resultCount) ? { resultCount: parsed.resultCount } : {})
    }
  } catch {
    return null
  }
}

export default function RealtimeConversation() {
  const accessToken = useAuthStore((s) => s.accessToken)
  const { data: roleLibrary } = useAiRoleLibrary()
  const [status, setStatus] = useState<WsStatus>("disconnected")
  const [messages, setMessages] = useState<RealtimeConversationMessage[]>([])
  const [responding, setResponding] = useState(false)
  const [selectedRoleKey, setSelectedRoleKey] = useState(DEFAULT_ROLE_KEY)
  const [selectedVoice, setSelectedVoice] = useState(DEFAULT_VOICE)
  const [temperatureInput, setTemperatureInput] = useState("1.0")
  const [currentInputTranscript, setCurrentInputTranscript] = useState("")
  const [currentResponseText, setCurrentResponseText] = useState("")
  const [contextResolveMs, setContextResolveMs] = useState<number | null>(null)
  const [readyKnowledgeEnabled, setReadyKnowledgeEnabled] = useState<boolean | null>(null)
  const [knowledgeSearch, setKnowledgeSearch] = useState<KnowledgeSearchState>(
    INITIAL_KNOWLEDGE_SEARCH_STATE
  )
  const [responseLatencyMs, setResponseLatencyMs] = useState<number | null>(null)
  const knowledge = useBoolean(true)
  const video = useBoolean(false)

  const availableRoles =
    roleLibrary?.items.filter((item) => item.bound && item.enabled && !item.defaultRole) ?? []
  const defaultRoleName = roleLibrary?.items.find((item) => item.defaultRole)?.role.name
  const selectedRoleLabel =
    selectedRoleKey === DEFAULT_ROLE_KEY
      ? `默认角色${defaultRoleName ? `（${defaultRoleName}）` : ""}`
      : (roleLibrary?.items.find((item) => item.role.code === selectedRoleKey)?.role.name ??
        "选择角色")
  const selectedVoiceLabel =
    REALTIME_VOICES.find((voice) => voice.value === selectedVoice)?.label ?? "选择音色"

  const wsRef = useRef<WebSocket | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const audioContextRef = useRef<AudioContext | null>(null)
  const sourceRef = useRef<MediaStreamAudioSourceNode | null>(null)
  const processorRef = useRef<ScriptProcessorNode | null>(null)
  const videoIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const playbackCtxRef = useRef<AudioContext | null>(null)
  const playheadRef = useRef(0)
  const audioOutputActiveRef = useRef(false)
  const responseActiveRef = useRef(false)
  const responseTextRef = useRef("")
  const sessionClosingRef = useRef(false)
  const messageIdRef = useRef(0)

  const appendMessage = useCallback((role: RealtimeConversationMessage["role"], text: string) => {
    messageIdRef.current += 1
    setMessages((previous) => [...previous, { id: messageIdRef.current, role, text }])
  }, [])

  const closePlayback = useCallback(() => {
    audioOutputActiveRef.current = false
    void playbackCtxRef.current?.close()
    playbackCtxRef.current = null
    playheadRef.current = 0
  }, [])

  const stopCapture = useCallback(() => {
    if (videoIntervalRef.current) {
      clearInterval(videoIntervalRef.current)
      videoIntervalRef.current = null
    }
    processorRef.current?.disconnect()
    processorRef.current = null
    sourceRef.current?.disconnect()
    sourceRef.current = null
    void audioContextRef.current?.close()
    audioContextRef.current = null
    if (streamRef.current) {
      for (const track of streamRef.current.getTracks()) track.stop()
      streamRef.current = null
    }
    if (videoRef.current) videoRef.current.srcObject = null
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

  const captureAndSendFrame = useCallback((ws: WebSocket) => {
    const videoElement = videoRef.current
    const canvas = canvasRef.current
    if (
      !videoElement ||
      !canvas ||
      videoElement.readyState < HTMLMediaElement.HAVE_CURRENT_DATA ||
      ws.readyState !== WebSocket.OPEN ||
      sessionClosingRef.current ||
      responseActiveRef.current
    ) {
      return
    }

    canvas.width = videoElement.videoWidth || 640
    canvas.height = videoElement.videoHeight || 480
    const context = canvas.getContext("2d")
    if (!context) return

    context.drawImage(videoElement, 0, 0)
    const dataUrl = canvas.toDataURL("image/jpeg", 0.6)
    const data = dataUrl.slice(dataUrl.indexOf(",") + 1)
    if (data) ws.send(JSON.stringify({ type: "input.image", mimeType: "image/jpeg", data }))
  }, [])

  const startCapture = useCallback(
    async (ws: WebSocket, enableVideo: boolean) => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: { sampleRate: 16000, channelCount: 1 },
          ...(enableVideo ? { video: { width: 640, height: 480, frameRate: 2 } } : {})
        })
        if (wsRef.current !== ws || ws.readyState !== WebSocket.OPEN || sessionClosingRef.current) {
          for (const track of stream.getTracks()) track.stop()
          return
        }

        streamRef.current = stream
        const audioCtx = new AudioContext({ sampleRate: 16000 })
        audioContextRef.current = audioCtx
        const source = audioCtx.createMediaStreamSource(stream)
        sourceRef.current = source
        const processor = audioCtx.createScriptProcessor(4096, 1, 1)
        processorRef.current = processor
        processor.onaudioprocess = (event: AudioProcessingEvent) => {
          if (
            ws.readyState !== WebSocket.OPEN ||
            sessionClosingRef.current ||
            responseActiveRef.current
          ) {
            return
          }
          const pcm16 = float32ToPcm16(event.inputBuffer.getChannelData(0))
          ws.send(pcm16.buffer as ArrayBuffer)
        }
        source.connect(processor)
        processor.connect(audioCtx.destination)

        if (enableVideo && stream.getVideoTracks().length > 0) {
          const videoElement = videoRef.current
          if (videoElement) {
            videoElement.srcObject = stream
            void videoElement.play().catch(() => undefined)
          }
          videoIntervalRef.current = setInterval(() => captureAndSendFrame(ws), 1000)
        }
      } catch {
        stopCapture()
        ws.close()
      }
    },
    [captureAndSendFrame, stopCapture]
  )

  const handleStart = useCallback(() => {
    if (!accessToken) {
      appendMessage("system", "请先登录后使用实时语音")
      return
    }
    const temperature = temperatureInput.trim() === "" ? undefined : Number(temperatureInput)
    if (
      temperature !== undefined &&
      (!Number.isFinite(temperature) || temperature < 0 || temperature >= 2)
    ) {
      appendMessage("system", "temperature 必须大于等于 0 且小于 2")
      return
    }
    setMessages([])
    messageIdRef.current = 0
    setCurrentInputTranscript("")
    setCurrentResponseText("")
    setContextResolveMs(null)
    setReadyKnowledgeEnabled(null)
    setKnowledgeSearch(INITIAL_KNOWLEDGE_SEARCH_STATE)
    setResponseLatencyMs(null)
    responseTextRef.current = ""
    responseActiveRef.current = false
    sessionClosingRef.current = false
    const params = new URLSearchParams({ token: accessToken })
    const ws = new WebSocket(buildWsUrl(`/ws/realtime-conversation?${params.toString()}`))
    ws.binaryType = "arraybuffer"
    wsRef.current = ws
    setStatus("connecting")

    ws.onopen = () => {
      const hello: {
        type: "hello"
        protocol: "omni-realtime.v1"
        endpointing: "server"
        knowledge: boolean
        roleKey?: string
        voice?: string
        temperature?: number
      } = {
        type: "hello",
        protocol: "omni-realtime.v1",
        endpointing: "server",
        knowledge: knowledge.value
      }
      if (selectedRoleKey !== DEFAULT_ROLE_KEY) hello.roleKey = selectedRoleKey
      if (selectedVoice !== DEFAULT_VOICE) hello.voice = selectedVoice
      if (temperature !== undefined) hello.temperature = temperature
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
        case "ready": {
          if (parseOmniRealtimeReadyEvent(event.data) === null) {
            stopCapture()
            closePlayback()
            ws.close()
            break
          }
          setContextResolveMs(message.contextResolveMs ?? null)
          setReadyKnowledgeEnabled(message.knowledgeEnabled ?? null)
          setStatus("ready")
          void startCapture(ws, video.value)
          break
        }
        case "input_audio.speech_started":
          setCurrentInputTranscript("")
          break
        case "input_audio.transcript.delta":
          setCurrentInputTranscript((previous) => previous + (message.text ?? ""))
          break
        case "input_audio.transcript.completed":
          if (message.text) appendMessage("user", message.text)
          setCurrentInputTranscript("")
          break
        case "response.created":
          responseActiveRef.current = true
          responseTextRef.current = ""
          setCurrentResponseText("")
          setResponseLatencyMs(null)
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
        case "response.latency":
          setResponseLatencyMs(message.durationMs ?? null)
          break
        case "response.completed":
          responseActiveRef.current = false
          if (responseTextRef.current) appendMessage("assistant", responseTextRef.current)
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
        case "knowledge_search.started":
          setKnowledgeSearch({
            phase: "searching",
            status: null,
            durationMs: null,
            resultCount: null
          })
          break
        case "knowledge_search.completed":
          setKnowledgeSearch({
            phase: "completed",
            status: message.status ?? "unknown",
            durationMs: message.durationMs ?? null,
            resultCount: message.resultCount ?? null
          })
          break
        case "error":
          appendMessage(
            "system",
            `错误${message.code ? ` (${message.code})` : ""}: ${message.message ?? "未知错误"}`
          )
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
  }, [
    accessToken,
    appendMessage,
    closePlayback,
    knowledge.value,
    playAudioChunk,
    selectedRoleKey,
    selectedVoice,
    startCapture,
    stopCapture,
    temperatureInput,
    video.value
  ])

  const handleCancelResponse = useCallback(() => {
    const ws = wsRef.current
    if (!ws || ws.readyState !== WebSocket.OPEN || !responding) return

    closePlayback()
    ws.send(JSON.stringify({ type: "response.cancel" }))
  }, [closePlayback, responding])

  const handleStop = useCallback(() => {
    const ws = wsRef.current
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      stopCapture()
      closePlayback()
      setStatus("disconnected")
      return
    }

    sessionClosingRef.current = true
    stopCapture()
    closePlayback()
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

  const statusVariant =
    status === "ready"
      ? "default"
      : status === "connecting" || status === "closing"
        ? "secondary"
        : "outline"

  return (
    <PageContainer maxWidth="md">
      <div className="mb-6 flex flex-col gap-2">
        <TypographyH1>Realtime Conversation 实时对话</TypographyH1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <span>实时对话</span>
            <Badge variant={statusVariant}>{WS_STATUS_LABELS[status]}</Badge>
          </CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-wrap items-center gap-4">
            <div className="flex items-center gap-2">
              <Label htmlFor="realtime-role">角色</Label>
              <Select
                value={selectedRoleKey}
                onValueChange={(value) => setSelectedRoleKey(value ?? DEFAULT_ROLE_KEY)}
                disabled={status !== "disconnected"}
              >
                <SelectTrigger id="realtime-role" className="w-56">
                  <SelectValue>{selectedRoleLabel}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value={DEFAULT_ROLE_KEY}>
                      默认角色{defaultRoleName ? `（${defaultRoleName}）` : ""}
                    </SelectItem>
                    {availableRoles.map((item) => (
                      <SelectItem key={item.role.id} value={item.role.code}>
                        {item.role.name}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </div>

            <div className="flex items-center gap-2">
              <Label htmlFor="realtime-voice">音色</Label>
              <Select
                value={selectedVoice}
                onValueChange={(value) => setSelectedVoice(value ?? DEFAULT_VOICE)}
                disabled={status !== "disconnected"}
              >
                <SelectTrigger id="realtime-voice" className="w-40">
                  <SelectValue>{selectedVoiceLabel}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {REALTIME_VOICES.map((voice) => (
                      <SelectItem key={voice.value} value={voice.value}>
                        {voice.label}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </div>

            <div className="flex items-center gap-2">
              <Label htmlFor="realtime-temperature">Temperature</Label>
              <Input
                id="realtime-temperature"
                type="number"
                min="0"
                max="1.9"
                step="0.1"
                placeholder="供应商默认"
                value={temperatureInput}
                onChange={(event) => setTemperatureInput(event.target.value)}
                disabled={status !== "disconnected"}
                className="w-32"
              />
            </div>

            <div className="flex items-center gap-2">
              <Switch
                id="realtime-knowledge"
                checked={knowledge.value}
                onCheckedChange={(checked) => knowledge.setValue(checked)}
                disabled={status !== "disconnected"}
              />
              <Label htmlFor="realtime-knowledge">启用知识库</Label>
            </div>

            <div className="flex items-center gap-2">
              <Switch
                id="realtime-video"
                checked={video.value}
                onCheckedChange={(checked) => video.setValue(checked)}
                disabled={status !== "disconnected"}
              />
              <Label htmlFor="realtime-video">启用摄像头</Label>
            </div>

            {status === "ready" ? (
              <Button type="button" variant={responding ? "destructive" : "default"} disabled>
                {responding ? "模型回复中，暂停收音" : "正在收音"}
              </Button>
            ) : null}
            {responding ? (
              <Button variant="outline" onClick={handleCancelResponse}>
                取消回复
              </Button>
            ) : null}
            {status === "disconnected" ? (
              <Button onClick={handleStart} disabled={!accessToken}>
                开始对话
              </Button>
            ) : (
              <Button variant="destructive" onClick={handleStop} disabled={status === "closing"}>
                结束
              </Button>
            )}
          </div>

          {video.value ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="flex flex-col gap-2">
                <span className="font-medium text-sm">摄像头预览</span>
                <video
                  ref={videoRef}
                  className="h-48 w-full rounded-md bg-black object-cover"
                  muted
                  playsInline
                />
              </div>
              <div className="flex flex-col gap-2">
                <span className="font-medium text-sm">JPEG 采样预览</span>
                <canvas ref={canvasRef} className="h-48 w-full rounded-md bg-muted object-cover" />
              </div>
            </div>
          ) : null}

          <div className="flex flex-wrap gap-x-4 gap-y-1 text-muted-foreground text-sm">
            <span>
              上下文解析：{contextResolveMs === null ? "等待 ready" : `${contextResolveMs} ms`}
            </span>
            <span>
              知识库：
              {readyKnowledgeEnabled === null
                ? "等待 ready"
                : readyKnowledgeEnabled
                  ? "已启用"
                  : "未启用"}
            </span>
            <span>
              知识检索：
              {knowledgeSearch.phase === "idle"
                ? "未开始"
                : knowledgeSearch.phase === "searching"
                  ? "检索中"
                  : `${knowledgeSearch.status ?? "unknown"}${
                      knowledgeSearch.durationMs === null
                        ? ""
                        : `，${knowledgeSearch.durationMs} ms`
                    }${knowledgeSearch.resultCount === null ? "" : `，${knowledgeSearch.resultCount} 条`}`}
            </span>
            <span>
              响应延迟：{responseLatencyMs === null ? "等待响应" : `${responseLatencyMs} ms`}
            </span>
          </div>

          <ScrollArea className="h-72 rounded-md border p-4">
            {messages.length === 0 && !currentInputTranscript && !currentResponseText ? (
              <p className="text-muted-foreground text-sm">等待实时会话就绪...</p>
            ) : (
              <div className="flex flex-col gap-2">
                {messages.map((message) => (
                  <div
                    key={message.id}
                    className={`text-sm ${
                      message.role === "user"
                        ? "text-primary"
                        : message.role === "system"
                          ? "text-destructive"
                          : "text-foreground"
                    }`}
                  >
                    <span className="font-medium">
                      {message.role === "user"
                        ? "你: "
                        : message.role === "assistant"
                          ? "AI: "
                          : ""}
                    </span>
                    {message.text}
                  </div>
                ))}
                {currentInputTranscript ? (
                  <div className="text-primary text-sm italic">你: {currentInputTranscript}</div>
                ) : null}
                {currentResponseText ? (
                  <div className="text-muted-foreground text-sm italic">
                    AI: {currentResponseText}
                  </div>
                ) : null}
              </div>
            )}
          </ScrollArea>
        </CardContent>
      </Card>
    </PageContainer>
  )
}
