/**
 * 语音输入组件（STT）——基于 Web Speech API
 *
 * 功能：
 * - 录音按钮（点击开始/停止）
 * - 音频波形可视化（AudioContext + AnalyserNode + canvas）
 * - 转写结果实时显示
 * - 转写完成后回调 onResult(text)
 * - 浏览器不支持时显示提示
 *
 * @author AaronZZH & Kiro
 */
"use client"

import { AlertCircle, Mic, MicOff } from "lucide-react"
import { useCallback, useEffect, useRef, useState } from "react"
import { Button } from "@/components/ui/button"

/** Web Speech API 类型声明 */
interface SpeechRecognitionEvent {
  results: SpeechRecognitionResultList
  resultIndex: number
}

interface SpeechRecognitionErrorEvent {
  error: string
  message: string
}

interface SpeechRecognitionInstance extends EventTarget {
  continuous: boolean
  interimResults: boolean
  lang: string
  start(): void
  stop(): void
  abort(): void
  onresult: ((event: SpeechRecognitionEvent) => void) | null
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null
  onend: (() => void) | null
  onstart: (() => void) | null
}

interface SpeechInputProps {
  /** 转写完成回调 */
  onResult: (text: string) => void
  /** 语言，默认 zh-CN */
  lang?: string
  /** 自定义类名 */
  className?: string
}

/** 获取 SpeechRecognition 构造函数 */
function getSpeechRecognition(): (new () => SpeechRecognitionInstance) | null {
  if (typeof window === "undefined") return null
  const w = window as unknown as Record<string, unknown>
  return (w.SpeechRecognition ?? w.webkitSpeechRecognition) as
    | (new () => SpeechRecognitionInstance)
    | null
}

export function SpeechInput({ onResult, lang = "zh-CN", className }: SpeechInputProps) {
  const [supported, setSupported] = useState(true)
  const [listening, setListening] = useState(false)
  const [interim, setInterim] = useState("")
  const [final, setFinal] = useState("")

  const recognitionRef = useRef<SpeechRecognitionInstance | null>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const analyserRef = useRef<AnalyserNode | null>(null)
  const animFrameRef = useRef<number>(0)
  const streamRef = useRef<MediaStream | null>(null)

  useEffect(() => {
    if (!getSpeechRecognition()) setSupported(false)
  }, [])

  /** 绘制波形 */
  const drawWaveform = useCallback(() => {
    const analyser = analyserRef.current
    const canvas = canvasRef.current
    if (!analyser || !canvas) return

    const ctx = canvas.getContext("2d")
    if (!ctx) return

    const bufferLength = analyser.frequencyBinCount
    const dataArray = new Uint8Array(bufferLength)

    const draw = () => {
      animFrameRef.current = requestAnimationFrame(draw)
      analyser.getByteTimeDomainData(dataArray)

      ctx.fillStyle = "hsl(var(--muted))"
      ctx.fillRect(0, 0, canvas.width, canvas.height)

      ctx.lineWidth = 2
      ctx.strokeStyle = "hsl(var(--primary))"
      ctx.beginPath()

      const sliceWidth = canvas.width / bufferLength
      let x = 0
      for (let i = 0; i < bufferLength; i++) {
        const v = dataArray[i] / 128.0
        const y = (v * canvas.height) / 2
        if (i === 0) ctx.moveTo(x, y)
        else ctx.lineTo(x, y)
        x += sliceWidth
      }
      ctx.lineTo(canvas.width, canvas.height / 2)
      ctx.stroke()
    }
    draw()
  }, [])

  /** 启动音频可视化 */
  const startVisualizer = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      streamRef.current = stream
      const audioCtx = new AudioContext()
      const source = audioCtx.createMediaStreamSource(stream)
      const analyser = audioCtx.createAnalyser()
      analyser.fftSize = 256
      source.connect(analyser)
      analyserRef.current = analyser
      drawWaveform()
    } catch {
      // 麦克风权限被拒绝，静默处理
    }
  }, [drawWaveform])

  /** 停止音频可视化 */
  const stopVisualizer = useCallback(() => {
    cancelAnimationFrame(animFrameRef.current)
    streamRef.current?.getTracks().forEach((t) => {
      t.stop()
    })
    streamRef.current = null
    analyserRef.current = null
  }, [])

  /** 开始录音 */
  const start = useCallback(() => {
    const Ctor = getSpeechRecognition()
    if (!Ctor) return

    const recognition = new Ctor()
    recognition.continuous = true
    recognition.interimResults = true
    recognition.lang = lang

    recognition.onstart = () => setListening(true)

    recognition.onresult = (event: SpeechRecognitionEvent) => {
      let interimText = ""
      let finalText = ""
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const transcript = event.results[i][0].transcript
        if (event.results[i].isFinal) {
          finalText += transcript
        } else {
          interimText += transcript
        }
      }
      if (finalText) {
        setFinal((prev) => prev + finalText)
        onResult(finalText)
      }
      setInterim(interimText)
    }

    recognition.onerror = () => {
      setListening(false)
      stopVisualizer()
    }

    recognition.onend = () => {
      setListening(false)
      stopVisualizer()
    }

    recognitionRef.current = recognition
    recognition.start()
    startVisualizer()
  }, [lang, onResult, startVisualizer, stopVisualizer])

  /** 停止录音 */
  const stop = useCallback(() => {
    recognitionRef.current?.stop()
    stopVisualizer()
  }, [stopVisualizer])

  if (!supported) {
    return (
      <div className={`flex items-center gap-2 text-destructive text-sm ${className ?? ""}`}>
        <AlertCircle className="size-4" />
        <span>当前浏览器不支持语音识别，请使用 Chrome 或 Edge</span>
      </div>
    )
  }

  return (
    <div className={`flex flex-col gap-2 ${className ?? ""}`}>
      {/* 控制按钮 */}
      <div className="flex items-center gap-3">
        <Button
          type="button"
          variant={listening ? "destructive" : "default"}
          size="icon"
          onClick={listening ? stop : start}
          aria-label={listening ? "停止录音" : "开始录音"}
        >
          {listening ? <MicOff className="size-4" /> : <Mic className="size-4" />}
        </Button>
        {listening && (
          <span className="animate-pulse text-muted-foreground text-sm">正在聆听...</span>
        )}
      </div>

      {/* 波形可视化 */}
      {listening && (
        <canvas ref={canvasRef} width={300} height={40} className="h-10 w-full rounded border" />
      )}

      {/* 转写结果 */}
      {(final || interim) && (
        <div className="rounded bg-muted p-2 text-sm">
          <span>{final}</span>
          {interim && <span className="text-muted-foreground">{interim}</span>}
        </div>
      )}
    </div>
  )
}
