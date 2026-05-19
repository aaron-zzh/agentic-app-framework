/**
 * 实时语音对话组件
 *
 * 功能：
 * - WebSocket 双向音频流骨架
 * - MediaRecorder 录音 + 发送音频块
 * - VAD：AnalyserNode 音量检测，静音 1.5s 自动断句
 * - 打断机制：用户说话时停止 AI 播放
 * - 状态机：idle → listening → processing → speaking → idle
 *
 * @author AaronZZH & Kiro
 */
"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { Mic, Loader2, Volume2, Circle } from "lucide-react"
import { Button } from "@/components/ui/button"

type VoiceState = "idle" | "listening" | "processing" | "speaking"

interface RealtimeVoiceProps {
  /** WebSocket 地址 */
  wsUrl?: string
  /** 静音阈值（0~255），低于此值视为静音 */
  silenceThreshold?: number
  /** 静音超时（ms），超过后自动断句 */
  silenceTimeout?: number
  /** AI 开始说话回调 */
  onAISpeaking?: (audioBlob: Blob) => void
  /** 转写结果回调 */
  onTranscript?: (text: string) => void
  className?: string
}

export function RealtimeVoice({
  wsUrl = "ws://localhost:8080/ws/voice",
  silenceThreshold = 30,
  silenceTimeout = 1500,
  onAISpeaking,
  onTranscript,
  className,
}: RealtimeVoiceProps) {
  const [state, setState] = useState<VoiceState>("idle")
  const wsRef = useRef<WebSocket | null>(null)
  const recorderRef = useRef<MediaRecorder | null>(null)
  const analyserRef = useRef<AnalyserNode | null>(null)
  const silenceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const animFrameRef = useRef<number>(0)
  const streamRef = useRef<MediaStream | null>(null)

  /** 检测是否静音 */
  const checkSilence = useCallback(() => {
    const analyser = analyserRef.current
    if (!analyser) return

    const dataArray = new Uint8Array(analyser.frequencyBinCount)
    analyser.getByteFrequencyData(dataArray)

    const average = dataArray.reduce((sum, v) => sum + v, 0) / dataArray.length
    const isSilent = average < silenceThreshold

    if (isSilent) {
      if (!silenceTimerRef.current) {
        silenceTimerRef.current = setTimeout(() => {
          // 静音超时，自动断句
          stopRecording()
          setState("processing")
        }, silenceTimeout)
      }
    } else {
      // 有声音，重置静音计时
      if (silenceTimerRef.current) {
        clearTimeout(silenceTimerRef.current)
        silenceTimerRef.current = null
      }
    }

    if (state === "listening") {
      animFrameRef.current = requestAnimationFrame(checkSilence)
    }
  }, [silenceThreshold, silenceTimeout, state])

  /** 连接 WebSocket */
  const connectWs = useCallback(() => {
    const ws = new WebSocket(wsUrl)
    ws.binaryType = "arraybuffer"

    ws.onmessage = (event) => {
      if (typeof event.data === "string") {
        // 文本消息（转写结果）
        const msg = JSON.parse(event.data) as { type: string; text?: string }
        if (msg.type === "transcript" && msg.text) {
          onTranscript?.(msg.text)
          setState("idle")
        } else if (msg.type === "speaking") {
          setState("speaking")
        }
      } else {
        // 二进制音频数据（AI 语音回复）
        const blob = new Blob([event.data], { type: "audio/webm" })
        onAISpeaking?.(blob)
        setState("speaking")
      }
    }

    ws.onclose = () => {
      wsRef.current = null
      setState("idle")
    }

    wsRef.current = ws
  }, [wsUrl, onAISpeaking, onTranscript])

  /** 开始录音 */
  const startRecording = useCallback(async () => {
    // 打断机制：用户开始说话时停止 AI 播放
    if (state === "speaking") {
      window.speechSynthesis.cancel()
    }

    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
      connectWs()
    }

    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    streamRef.current = stream

    // 设置 VAD 分析器
    const audioCtx = new AudioContext()
    const source = audioCtx.createMediaStreamSource(stream)
    const analyser = audioCtx.createAnalyser()
    analyser.fftSize = 256
    source.connect(analyser)
    analyserRef.current = analyser

    // 开始录音
    const recorder = new MediaRecorder(stream, { mimeType: "audio/webm" })
    recorder.ondataavailable = (e) => {
      if (e.data.size > 0 && wsRef.current?.readyState === WebSocket.OPEN) {
        wsRef.current.send(e.data)
      }
    }
    recorder.start(250) // 每 250ms 发送一个音频块
    recorderRef.current = recorder

    setState("listening")
    animFrameRef.current = requestAnimationFrame(checkSilence)
  }, [state, connectWs, checkSilence])

  /** 停止录音 */
  const stopRecording = useCallback(() => {
    cancelAnimationFrame(animFrameRef.current)
    if (silenceTimerRef.current) {
      clearTimeout(silenceTimerRef.current)
      silenceTimerRef.current = null
    }
    recorderRef.current?.stop()
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
    analyserRef.current = null
  }, [])

  /** 切换录音状态 */
  const toggle = useCallback(() => {
    if (state === "idle" || state === "speaking") {
      startRecording()
    } else if (state === "listening") {
      stopRecording()
      setState("processing")
    }
  }, [state, startRecording, stopRecording])

  /** 组件卸载清理 */
  useEffect(() => {
    return () => {
      stopRecording()
      wsRef.current?.close()
    }
  }, [stopRecording])

  /** 状态图标 */
  const stateIcon = {
    idle: <Mic className="size-5" />,
    listening: <Mic className="size-5 text-red-500" />,
    processing: <Loader2 className="size-5 animate-spin" />,
    speaking: <Volume2 className="size-5 text-green-500" />,
  }

  /** 状态文案 */
  const stateLabel = {
    idle: "点击开始对话",
    listening: "正在聆听...",
    processing: "处理中...",
    speaking: "AI 正在回复...",
  }

  return (
    <div className={`flex flex-col items-center gap-3 ${className ?? ""}`}>
      {/* 状态动画指示器 */}
      <div className="relative flex items-center justify-center size-20">
        {/* 外圈脉冲动画 */}
        {state === "listening" && (
          <span className="absolute inset-0 rounded-full bg-red-500/20 animate-ping" />
        )}
        {state === "speaking" && (
          <span className="absolute inset-0 rounded-full bg-green-500/20 animate-pulse" />
        )}

        <Button
          type="button"
          variant="outline"
          size="icon"
          className="size-16 rounded-full"
          onClick={toggle}
          disabled={state === "processing"}
          aria-label={stateLabel[state]}
        >
          {stateIcon[state]}
        </Button>
      </div>

      {/* 状态文案 */}
      <span className="text-sm text-muted-foreground">{stateLabel[state]}</span>

      {/* 录音指示点 */}
      {state === "listening" && (
        <Circle className="size-3 fill-red-500 text-red-500 animate-pulse" />
      )}
    </div>
  )
}
