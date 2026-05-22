"use client"

/**
 * ASR 双向流式语音识别示例页——通过 WebSocket 实时发送音频并接收识别结果
 * 路由：/dev/examples/asr
 * @author AaronZZH & Kiro
 */

import { useCallback, useRef, useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { TypographyH1, TypographyMuted } from "@/components/ui/typography"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { ScrollArea } from "@/components/ui/scroll-area"

type WsStatus = "connecting" | "connected" | "disconnected"

interface AsrMessage {
  text: string
  final: boolean
}

const WS_BASE = process.env.NEXT_PUBLIC_WS_URL ?? `ws://${typeof window !== "undefined" ? window.location.host : "localhost:8080"}`

export default function AsrExamplePage() {
  const [lang, setLang] = useState("zh-CN")
  const [status, setStatus] = useState<WsStatus>("disconnected")
  const [lines, setLines] = useState<string[]>([])
  const [recording, setRecording] = useState(false)

  const wsRef = useRef<WebSocket | null>(null)
  const recorderRef = useRef<MediaRecorder | null>(null)
  const streamRef = useRef<MediaStream | null>(null)

  const handleStart = useCallback(async () => {
    setLines([])

    // 建立 WebSocket 连接
    const ws = new WebSocket(`${WS_BASE}/ws/asr?lang=${lang}`)
    ws.binaryType = "arraybuffer"
    wsRef.current = ws
    setStatus("connecting")

    ws.onopen = async () => {
      setStatus("connected")

      // 获取麦克风权限并开始录音
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
        streamRef.current = stream

        const recorder = new MediaRecorder(stream)
        recorderRef.current = recorder

        recorder.ondataavailable = async (e: BlobEvent) => {
          if (e.data.size > 0 && ws.readyState === WebSocket.OPEN) {
            const buffer = await e.data.arrayBuffer()
            ws.send(buffer)
          }
        }

        recorder.start(250) // 每 250ms 产生一个 chunk
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
          if (next.length > 0 && !prev[prev.length - 1].endsWith("。") && !prev[prev.length - 1].endsWith(".")) {
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
  }, [lang])

  const handleStop = useCallback(() => {
    // 停止录音
    if (recorderRef.current && recorderRef.current.state !== "inactive") {
      recorderRef.current.stop()
    }
    recorderRef.current = null

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

  const statusVariant = status === "connected" ? "default" : status === "connecting" ? "secondary" : "outline"

  return (
    <PageContainer maxWidth="md">
      <div className="mb-6 space-y-2">
        <TypographyH1>ASR 流式语音识别</TypographyH1>
        <TypographyMuted>
          通过 WebSocket 双向流式传输音频，实时获取识别结果
        </TypographyMuted>
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
              <Button variant="destructive" onClick={handleStop}>停止</Button>
            )}
          </div>

          <ScrollArea className="h-64 rounded-md border p-4">
            {lines.length === 0 ? (
              <p className="text-muted-foreground text-sm">等待识别结果...</p>
            ) : (
              <div className="space-y-1">
                {lines.map((line, i) => (
                  <p key={`${i}-${line.slice(0, 8)}`} className="text-sm">{line}</p>
                ))}
              </div>
            )}
          </ScrollArea>
        </CardContent>
      </Card>
    </PageContainer>
  )
}
