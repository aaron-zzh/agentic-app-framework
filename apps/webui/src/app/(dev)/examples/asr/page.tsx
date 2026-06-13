"use client"

/**
 * ASR 双向流式语音识别示例页——通过 WebSocket 实时发送音频并接收识别结果
 * 路由：/dev/examples/asr
 * @author AaronZZH & Kiro
 */

import { useCallback, useRef, useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { TypographyH1, TypographyMuted } from "@/components/ui/typography"
import { buildWsUrl } from "@/lib/api/config"
import { useAuthStore } from "@/lib/store/auth-store"
import { float32ToPcm16 } from "@/lib/utils/audio"

type WsStatus = "connecting" | "connected" | "disconnected"

interface AsrMessage {
  text: string
  final: boolean
}

export default function AsrExamplePage() {
  const accessToken = useAuthStore((s) => s.accessToken)
  const [lang, setLang] = useState("zh-CN")
  const [status, setStatus] = useState<WsStatus>("disconnected")
  const [lines, setLines] = useState<string[]>([])
  const [recording, setRecording] = useState(false)

  const wsRef = useRef<WebSocket | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const audioContextRef = useRef<AudioContext | null>(null)
  const processorRef = useRef<ScriptProcessorNode | null>(null)

  const handleStart = useCallback(async () => {
    setLines([])

    // 建立 WebSocket 连接
    const params = new URLSearchParams({ lang, token: accessToken ?? "" })
    const ws = new WebSocket(buildWsUrl(`/ws/asr?${params.toString()}`))
    ws.binaryType = "arraybuffer"
    wsRef.current = ws
    setStatus("connecting")

    ws.onopen = async () => {
      setStatus("connected")

      // 获取麦克风权限并开始录音
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: { sampleRate: 16000, channelCount: 1 }
        })
        streamRef.current = stream

        // 采集原始 PCM 16kHz 单声道 —— DashScope 流式 ASR（fun-asr-realtime）要求的格式。
        // MediaRecorder 默认产出 WebM/Opus 容器分片，无法被 DashScope 当作 PCM 解码。
        const audioCtx = new AudioContext({ sampleRate: 16000 })
        audioContextRef.current = audioCtx
        const source = audioCtx.createMediaStreamSource(stream)
        const processor = audioCtx.createScriptProcessor(4096, 1, 1)
        processorRef.current = processor

        processor.onaudioprocess = (e: AudioProcessingEvent) => {
          if (ws.readyState !== WebSocket.OPEN) return
          const float32 = e.inputBuffer.getChannelData(0)
          const pcm16 = float32ToPcm16(float32)
          ws.send(pcm16.buffer as ArrayBuffer)
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
      const msg = JSON.parse(event.data as string) as AsrMessage
      if (msg.final) {
        setLines((prev) => [...prev, msg.text])
      } else {
        // 临时结果：更新最后一行（或追加新行）
        setLines((prev) => {
          const next = [...prev]
          if (
            next.length > 0 &&
            !prev[prev.length - 1].endsWith("。") &&
            !prev[prev.length - 1].endsWith(".")
          ) {
            next[next.length - 1] = msg.text
          } else {
            next.push(msg.text)
          }
          return next
        })
      }
    }

    ws.onclose = () => {
      setStatus("disconnected")
      setRecording(false)
    }

    ws.onerror = () => {
      ws.close()
    }
  }, [lang, accessToken])

  const handleStop = useCallback(() => {
    // 停止 PCM 采集
    if (processorRef.current) {
      processorRef.current.disconnect()
      processorRef.current.onaudioprocess = null
      processorRef.current = null
    }
    if (audioContextRef.current) {
      audioContextRef.current.close()
      audioContextRef.current = null
    }

    // 释放麦克风
    if (streamRef.current) {
      for (const track of streamRef.current.getTracks()) {
        track.stop()
      }
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
        <TypographyH1>ASR 流式语音识别</TypographyH1>
        <TypographyMuted>通过 WebSocket 双向流式传输音频，实时获取识别结果</TypographyMuted>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <span>语音识别</span>
            <Badge variant={statusVariant}>{status}</Badge>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center gap-3">
            <Select value={lang} onValueChange={(v) => setLang(v ?? "zh-CN")} disabled={recording}>
              <SelectTrigger className="w-36">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="zh-CN">中文</SelectItem>
                <SelectItem value="en-US">English</SelectItem>
              </SelectContent>
            </Select>

            {!recording ? (
              <Button onClick={handleStart}>开始录音</Button>
            ) : (
              <Button variant="destructive" onClick={handleStop}>
                停止
              </Button>
            )}
          </div>

          <ScrollArea className="h-64 rounded-md border p-4">
            {lines.length === 0 ? (
              <p className="text-muted-foreground text-sm">等待识别结果...</p>
            ) : (
              <div className="space-y-1">
                {lines.map((line, i) => (
                  <p key={`${i}-${line.slice(0, 8)}`} className="text-sm">
                    {line}
                  </p>
                ))}
              </div>
            )}
          </ScrollArea>
        </CardContent>
      </Card>
    </PageContainer>
  )
}
