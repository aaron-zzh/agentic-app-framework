"use client"

/**
 * Qwen-Omni 实时多模态对话示例——通过 WebSocket 实时发送音频/视频并接收模型回复
 * 路由：/dev/examples/omni-realtime
 * @author AaronZZH & Kiro
 */

import { useCallback, useRef, useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Label } from "@/components/ui/label"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Switch } from "@/components/ui/switch"
import { TypographyH1, TypographyMuted } from "@/components/ui/typography"

type WsStatus = "connecting" | "connected" | "disconnected"

interface OmniMessage {
  role: "user" | "assistant" | "system"
  text: string
}

const WS_BASE =
  process.env.NEXT_PUBLIC_WS_URL ??
  `ws://${typeof window !== "undefined" ? window.location.host : "localhost:8080"}`

export default function OmniRealtimePage() {
  const [status, setStatus] = useState<WsStatus>("disconnected")
  const [messages, setMessages] = useState<OmniMessage[]>([])
  const [recording, setRecording] = useState(false)
  const [enableVideo, setEnableVideo] = useState(false)
  const [currentTranscript, setCurrentTranscript] = useState("")

  const wsRef = useRef<WebSocket | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const audioContextRef = useRef<AudioContext | null>(null)
  const processorRef = useRef<ScriptProcessorNode | null>(null)
  const videoIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const canvasRef = useRef<HTMLCanvasElement | null>(null)

  const captureAndSendFrame = useCallback((ws: WebSocket) => {
    const video = videoRef.current
    const canvas = canvasRef.current
    if (!video || !canvas || ws.readyState !== WebSocket.OPEN) return

    canvas.width = video.videoWidth || 640
    canvas.height = video.videoHeight || 480
    const ctx = canvas.getContext("2d")
    if (!ctx) return

    ctx.drawImage(video, 0, 0)
    const dataUrl = canvas.toDataURL("image/jpeg", 0.6)
    const base64 = dataUrl.split(",")[1]
    if (base64) {
      ws.send(JSON.stringify({ type: "video", data: base64 }))
    }
  }, [])

  const handleStart = useCallback(async () => {
    setMessages([])
    setCurrentTranscript("")

    const params = new URLSearchParams({ vad: "true" })
    const ws = new WebSocket(`${WS_BASE}/ws/omni-realtime?${params.toString()}`)
    wsRef.current = ws
    setStatus("connecting")

    ws.onopen = async () => {
      setStatus("connected")

      try {
        const constraints: MediaStreamConstraints = {
          audio: { sampleRate: 16000, channelCount: 1 }
        }
        if (enableVideo) {
          constraints.video = { width: 640, height: 480, frameRate: 2 }
        }

        const stream = await navigator.mediaDevices.getUserMedia(constraints)
        streamRef.current = stream

        // 音频处理：PCM 16kHz mono → base64
        const audioCtx = new AudioContext({ sampleRate: 16000 })
        audioContextRef.current = audioCtx
        const source = audioCtx.createMediaStreamSource(stream)
        const processor = audioCtx.createScriptProcessor(4096, 1, 1)
        processorRef.current = processor

        processor.onaudioprocess = (e: AudioProcessingEvent) => {
          if (ws.readyState !== WebSocket.OPEN) return
          const float32 = e.inputBuffer.getChannelData(0)
          const pcm16 = float32ToPcm16(float32)
          const base64 = arrayBufferToBase64(pcm16.buffer as ArrayBuffer)
          ws.send(JSON.stringify({ type: "audio", data: base64 }))
        }

        source.connect(processor)
        processor.connect(audioCtx.destination)

        // 视频处理：每秒 1 帧 JPEG → base64
        if (enableVideo && stream.getVideoTracks().length > 0) {
          const video = videoRef.current
          if (video) {
            video.srcObject = stream
            video.play()
          }
          videoIntervalRef.current = setInterval(() => {
            captureAndSendFrame(ws)
          }, 1000)
        }

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
  }, [enableVideo, captureAndSendFrame])

  const handleStop = useCallback(() => {
    // 停止视频采集
    if (videoIntervalRef.current) {
      clearInterval(videoIntervalRef.current)
      videoIntervalRef.current = null
    }

    // 停止音频处理
    if (processorRef.current) {
      processorRef.current.disconnect()
      processorRef.current = null
    }
    if (audioContextRef.current) {
      audioContextRef.current.close()
      audioContextRef.current = null
    }

    // 释放媒体流
    if (streamRef.current) {
      for (const track of streamRef.current.getTracks()) track.stop()
      streamRef.current = null
    }

    // 关闭 WebSocket
    if (wsRef.current) {
      wsRef.current.close()
      wsRef.current = null
    }

    setRecording(false)
  }, [])

  const statusVariant =
    status === "connected" ? "default" : status === "connecting" ? "secondary" : "outline"

  return (
    <PageContainer maxWidth="md">
      <div className="mb-6 space-y-2">
        <TypographyH1>Omni 实时多模态对话</TypographyH1>
        <TypographyMuted>
          基于 Qwen-Omni 模型，支持音频+视频实时双向交互，VAD 自动检测语音
        </TypographyMuted>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <span>实时对话</span>
            <Badge variant={statusVariant}>{status}</Badge>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2">
              <Switch
                id="enable-video"
                checked={enableVideo}
                onCheckedChange={setEnableVideo}
                disabled={recording}
              />
              <Label htmlFor="enable-video">启用摄像头</Label>
            </div>

            {!recording ? (
              <Button onClick={handleStart}>开始对话</Button>
            ) : (
              <Button variant="destructive" onClick={handleStop}>
                结束
              </Button>
            )}
          </div>

          {/* 视频预览 */}
          {enableVideo && (
            <div className="relative">
              <video
                ref={videoRef}
                className="h-48 w-full rounded-md bg-black object-cover"
                muted
                playsInline
              />
              <canvas ref={canvasRef} className="hidden" />
            </div>
          )}

          {/* 对话记录 */}
          <ScrollArea className="h-72 rounded-md border p-4">
            {messages.length === 0 && !currentTranscript ? (
              <p className="text-muted-foreground text-sm">等待对话开始...</p>
            ) : (
              <div className="space-y-2">
                {messages.map((msg, i) => (
                  <div
                    key={`${i}-${msg.text.slice(0, 8)}`}
                    className={`text-sm ${
                      msg.role === "user"
                        ? "text-blue-600"
                        : msg.role === "system"
                          ? "text-red-500"
                          : "text-foreground"
                    }`}
                  >
                    <span className="font-medium">
                      {msg.role === "user" ? "你: " : msg.role === "assistant" ? "AI: " : ""}
                    </span>
                    {msg.text}
                  </div>
                ))}
                {currentTranscript && (
                  <div className="text-muted-foreground text-sm italic">
                    AI: {currentTranscript}
                  </div>
                )}
              </div>
            )}
          </ScrollArea>
        </CardContent>
      </Card>
    </PageContainer>
  )
}

/** Float32 PCM → Int16 PCM */
function float32ToPcm16(float32: Float32Array): Int16Array {
  const int16 = new Int16Array(float32.length)
  for (let i = 0; i < float32.length; i++) {
    const s = Math.max(-1, Math.min(1, float32[i]))
    int16[i] = s < 0 ? s * 0x8000 : s * 0x7fff
  }
  return int16
}

/** ArrayBuffer → Base64 */
function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  let binary = ""
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  return btoa(binary)
}

/** 播放 Base64 PCM 24kHz 音频片段 */
function playAudioChunk(base64Data: string | undefined): void {
  if (!base64Data) return
  try {
    const binary = atob(base64Data)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i)
    }

    // PCM 24kHz 16bit mono → AudioBuffer
    const audioCtx = new AudioContext({ sampleRate: 24000 })
    const int16 = new Int16Array(bytes.buffer)
    const float32 = new Float32Array(int16.length)
    for (let i = 0; i < int16.length; i++) {
      float32[i] = int16[i] / 0x8000
    }

    const audioBuffer = audioCtx.createBuffer(1, float32.length, 24000)
    audioBuffer.getChannelData(0).set(float32)

    const source = audioCtx.createBufferSource()
    source.buffer = audioBuffer
    source.connect(audioCtx.destination)
    source.start()
  } catch {
    // 静默忽略播放错误
  }
}
