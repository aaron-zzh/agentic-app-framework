/**
 * WsAsrButton——基于百炼 WebSocket 流式 ASR 的语音输入按钮。
 *
 * 交互方式：
 * - 点击麦克风按钮：开始/停止录音
 * - 长按右 Ctrl：按住录音，松开停止并回调文本
 *
 * @author AaronZZH & Kiro
 */
"use client"

import { m } from "framer-motion"
import { Mic, MicOff } from "lucide-react"
import { useCallback, useEffect, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { buildWsUrl } from "@/lib/api/config"
import { useAuthStore } from "@/lib/store/auth-store"
import { float32ToPcm16 } from "@/lib/utils/audio"

interface WsAsrButtonProps {
  /** 识别完成回调，返回累积的完整文本 */
  onResult: (text: string) => void
  /** 临时识别结果回调（实时预览，非最终） */
  onInterim?: (text: string) => void
  /** 录音状态变化回调，录音中传入 stream，停止时传 null */
  onRecordingChange?: (stream: MediaStream | null) => void
  /** 识别语言，默认 zh-CN */
  lang?: string
  className?: string
}

/** 录音状态动画：5 个黑白圆点随整体音量同步跳动，供外部复用 */
export function AudioWaveform({ stream }: { stream: MediaStream }) {
  const [height, setHeight] = useState(2)
  const rafRef = useRef<number>(0)

  useEffect(() => {
    const audioCtx = new AudioContext()
    const analyser = audioCtx.createAnalyser()
    analyser.fftSize = 256
    const src = audioCtx.createMediaStreamSource(stream)
    src.connect(analyser)
    const data = new Uint8Array(analyser.frequencyBinCount)

    const tick = () => {
      if (audioCtx.state === "closed") return
      analyser.getByteFrequencyData(data)
      // 只取低频段（前1/4），语音能量主要在这里
      const lowFreq = data.slice(0, data.length / 4)
      const avg = lowFreq.reduce((s, v) => s + v, 0) / lowFreq.length
      setHeight(Math.max(2, Math.round((avg / 255) * 20)))
      rafRef.current = requestAnimationFrame(tick)
    }
    rafRef.current = requestAnimationFrame(tick)
    return () => {
      cancelAnimationFrame(rafRef.current)
      src.disconnect()
      audioCtx.close()
    }
  }, [stream])

  return (
    <div className="flex h-5 items-center gap-1" aria-hidden>
      {[0, 1, 2, 3, 4].map((i) => (
        <m.div
          key={i}
          className="w-1.5 rounded-full bg-foreground"
          animate={{ height: `${height}px` }}
          transition={{ duration: 0.08, ease: "easeOut" }}
        />
      ))}
    </div>
  )
}

export function WsAsrButton({
  onResult,
  onInterim,
  onRecordingChange,
  lang = "zh-CN",
  className
}: WsAsrButtonProps) {
  const [recording, setRecording] = useState(false)
  const [_waveformCtx, setWaveformCtx] = useState<MediaStream | null>(null)
  const accessToken = useAuthStore((s) => s.accessToken)

  const wsRef = useRef<WebSocket | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const audioCtxRef = useRef<AudioContext | null>(null)
  const processorRef = useRef<ScriptProcessorNode | null>(null)
  const finalTextRef = useRef("")
  const interimTextRef = useRef("")
  const recordingRef = useRef(false)
  const stoppingRef = useRef(false) // 标记主动停止，区分异常断开

  const cleanup = useCallback(() => {
    processorRef.current?.disconnect()
    processorRef.current = null
    audioCtxRef.current?.close()
    audioCtxRef.current = null
    streamRef.current?.getTracks().forEach((t) => {
      t.stop()
    })
    streamRef.current = null
    if (wsRef.current) {
      wsRef.current.onclose = null
      wsRef.current.onmessage = null
      wsRef.current.close()
      wsRef.current = null
    }
    stoppingRef.current = false
    setRecording(false)
    setWaveformCtx(null)
    onRecordingChange?.(null)
  }, [onRecordingChange])

  const stop = useCallback(() => {
    if (stoppingRef.current) return
    stoppingRef.current = true

    // 停止麦克风和音频采集，但保持 WebSocket 连接等待最后一帧结果
    processorRef.current?.disconnect()
    processorRef.current = null
    audioCtxRef.current?.close()
    audioCtxRef.current = null
    for (const t of streamRef.current?.getTracks() ?? []) t.stop()
    streamRef.current = null
    setRecording(false)
    setWaveformCtx(null)
    onRecordingChange?.(null)

    const ws = wsRef.current
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      // 没有连接，直接用当前已有文本回调
      const text = (finalTextRef.current + interimTextRef.current).trim()
      finalTextRef.current = ""
      interimTextRef.current = ""
      stoppingRef.current = false
      if (text) onResult(text)
      return
    }

    // 发送静音帧（全0 PCM），触发服务端 VAD sentence end，等待最后一条 final 结果
    // fun-asr-realtime 检测到静音后会推 isSentenceEnd，流自然结束
    const silenceFrames = 3
    const silenceChunk = new ArrayBuffer(4096 * 2) // 4096 samples * 2 bytes (int16)
    for (let i = 0; i < silenceFrames; i++) {
      if (ws.readyState === WebSocket.OPEN) ws.send(silenceChunk)
    }

    // 超时兜底：1s 内没收到 final 就强制关闭
    const timeout = setTimeout(() => {
      const text = (finalTextRef.current + interimTextRef.current).trim()
      ws.onclose = null
      ws.onmessage = null
      ws.close()
      wsRef.current = null
      finalTextRef.current = ""
      interimTextRef.current = ""
      stoppingRef.current = false
      if (text) onResult(text)
    }, 5000)

    // 若 500ms 内收到 final 消息则提前结束
    ws.onmessage = (event: MessageEvent) => {
      const msg = JSON.parse(event.data as string) as { text: string; final: boolean }
      if (msg.final) {
        finalTextRef.current += msg.text
        interimTextRef.current = ""
      } else {
        interimTextRef.current = msg.text
      }
      if (msg.final) {
        clearTimeout(timeout)
        const text = (finalTextRef.current + interimTextRef.current).trim()
        ws.onclose = null
        ws.onmessage = null
        ws.close()
        wsRef.current = null
        finalTextRef.current = ""
        interimTextRef.current = ""
        stoppingRef.current = false
        if (text) onResult(text)
      }
    }
  }, [onRecordingChange, onResult])

  const start = useCallback(async () => {
    finalTextRef.current = ""
    interimTextRef.current = ""

    const params = new URLSearchParams({ lang, token: accessToken ?? "" })
    const ws = new WebSocket(buildWsUrl(`/ws/asr?${params.toString()}`))
    ws.binaryType = "arraybuffer"
    wsRef.current = ws

    ws.onopen = async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: { sampleRate: 16000, channelCount: 1 }
        })
        streamRef.current = stream
        const audioCtx = new AudioContext({ sampleRate: 16000 })
        audioCtxRef.current = audioCtx
        const source = audioCtx.createMediaStreamSource(stream)
        const processor = audioCtx.createScriptProcessor(4096, 1, 1)
        processorRef.current = processor
        processor.onaudioprocess = (e: AudioProcessingEvent) => {
          if (ws.readyState !== WebSocket.OPEN) return
          ws.send(float32ToPcm16(e.inputBuffer.getChannelData(0)).buffer as ArrayBuffer)
        }
        source.connect(processor)
        processor.connect(audioCtx.destination)
        setRecording(true)
        setWaveformCtx(stream)
        onRecordingChange?.(stream)
      } catch {
        ws.close()
      }
    }

    ws.onmessage = (event: MessageEvent) => {
      const msg = JSON.parse(event.data as string) as { text: string; final: boolean }
      if (msg.final) {
        finalTextRef.current += msg.text
        interimTextRef.current = ""
        onInterim?.(finalTextRef.current)
      } else {
        interimTextRef.current = msg.text
        onInterim?.(finalTextRef.current + msg.text)
      }
    }

    ws.onclose = () => {
      // 异常断开（非主动 stop）时清理资源
      if (!stoppingRef.current) cleanup()
    }
  }, [lang, accessToken, onInterim, onRecordingChange, cleanup])

  // 长按右 Ctrl：按下开始，松开停止
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.code === "ControlRight" && !e.repeat && !recordingRef.current) {
        e.preventDefault()
        recordingRef.current = true
        start()
      }
    }
    const onKeyUp = (e: KeyboardEvent) => {
      if (e.code === "ControlRight" && recordingRef.current) {
        e.preventDefault()
        recordingRef.current = false
        stop()
      }
    }
    // 页面失焦时自动停止，防止 keyup 丢失导致麦克风一直开着
    const onBlur = () => {
      if (recordingRef.current) {
        recordingRef.current = false
        stop()
      }
    }
    window.addEventListener("keydown", onKeyDown)
    window.addEventListener("keyup", onKeyUp)
    window.addEventListener("blur", onBlur)
    return () => {
      window.removeEventListener("keydown", onKeyDown)
      window.removeEventListener("keyup", onKeyUp)
      window.removeEventListener("blur", onBlur)
    }
  }, [start, stop])

  return (
    <div className={className}>
      <Button
        type="button"
        variant={recording ? "destructive" : "ghost"}
        size="icon"
        onClick={recording ? stop : start}
        aria-label={recording ? "停止录音（或松开左 Alt）" : "语音输入（或长按左 Alt）"}
        title={recording ? "停止录音" : "语音输入（长按左 Alt）"}
      >
        {recording ? <MicOff className="size-4" /> : <Mic className="size-4" />}
      </Button>
    </div>
  )
}
