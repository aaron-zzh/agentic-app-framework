/**
 * 音频消息组件——录制与播放
 *
 * 功能：
 * - 语音消息录制（MediaRecorder）
 * - 录制时长显示 + 波形动画
 * - 发送按钮（录制完成后回调 onSend(blob)）
 * - 播放组件：音频条 + 播放/暂停 + 时长
 * - 转文字占位展示
 *
 * @author AaronZZH & Kiro
 */
"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { Mic, Pause, Play, Send, Square, Trash2 } from "lucide-react"
import { Button } from "@/components/ui/button"

/** 格式化秒数为 mm:ss */
function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, "0")}`
}

// ─── 录制组件 ───────────────────────────────────────────────

interface AudioRecorderProps {
  /** 录制完成发送回调 */
  onSend: (blob: Blob) => void
  className?: string
}

export function AudioRecorder({ onSend, className }: AudioRecorderProps) {
  const [recording, setRecording] = useState(false)
  const [duration, setDuration] = useState(0)
  const [blob, setBlob] = useState<Blob | null>(null)

  const recorderRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const streamRef = useRef<MediaStream | null>(null)

  /** 开始录制 */
  const start = useCallback(async () => {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    streamRef.current = stream

    const recorder = new MediaRecorder(stream, { mimeType: "audio/webm" })
    chunksRef.current = []

    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunksRef.current.push(e.data)
    }

    recorder.onstop = () => {
      const audioBlob = new Blob(chunksRef.current, { type: "audio/webm" })
      setBlob(audioBlob)
    }

    recorder.start()
    recorderRef.current = recorder
    setRecording(true)
    setDuration(0)
    setBlob(null)

    timerRef.current = setInterval(() => {
      setDuration((d) => d + 1)
    }, 1000)
  }, [])

  /** 停止录制 */
  const stop = useCallback(() => {
    recorderRef.current?.stop()
    streamRef.current?.getTracks().forEach((t) => t.stop())
    if (timerRef.current) clearInterval(timerRef.current)
    setRecording(false)
  }, [])

  /** 丢弃录音 */
  const discard = useCallback(() => {
    setBlob(null)
    setDuration(0)
  }, [])

  /** 发送 */
  const send = useCallback(() => {
    if (blob) {
      onSend(blob)
      setBlob(null)
      setDuration(0)
    }
  }, [blob, onSend])

  return (
    <div className={`flex items-center gap-2 ${className ?? ""}`}>
      {!recording && !blob && (
        <Button type="button" variant="ghost" size="icon" onClick={start} aria-label="开始录音">
          <Mic className="size-4" />
        </Button>
      )}

      {recording && (
        <>
          {/* 波形动画 */}
          <div className="flex items-end gap-0.5 h-4">
            {[0, 100, 200, 300, 200].map((delay, i) => (
              <span
                key={`bar-${delay}-${i}`}
                className="w-0.5 bg-red-500 animate-bounce"
                style={{ animationDelay: `${delay}ms`, height: `${8 + (i % 3) * 4}px` }}
              />
            ))}
          </div>
          <span className="text-sm text-red-500 font-mono">{formatDuration(duration)}</span>
          <Button type="button" variant="ghost" size="icon" onClick={stop} aria-label="停止录音">
            <Square className="size-3 fill-red-500 text-red-500" />
          </Button>
        </>
      )}

      {blob && !recording && (
        <>
          <span className="text-sm text-muted-foreground font-mono">
            {formatDuration(duration)}
          </span>
          <Button type="button" variant="ghost" size="icon" onClick={discard} aria-label="丢弃">
            <Trash2 className="size-4" />
          </Button>
          <Button type="button" variant="default" size="icon" onClick={send} aria-label="发送语音">
            <Send className="size-4" />
          </Button>
        </>
      )}
    </div>
  )
}

// ─── 播放组件 ───────────────────────────────────────────────

interface AudioPlayerProps {
  /** 音频 URL 或 Blob URL */
  src: string
  /** 时长（秒） */
  duration?: number
  /** 是否显示转文字占位 */
  showTranscript?: boolean
  className?: string
}

export function AudioPlayer({ src, duration, showTranscript, className }: AudioPlayerProps) {
  const [playing, setPlaying] = useState(false)
  const [currentTime, setCurrentTime] = useState(0)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const animRef = useRef<number>(0)

  /** 播放/暂停 */
  const toggle = useCallback(() => {
    const audio = audioRef.current
    if (!audio) return
    if (playing) {
      audio.pause()
      setPlaying(false)
    } else {
      audio.play()
      setPlaying(true)
    }
  }, [playing])

  /** 更新进度 */
  useEffect(() => {
    const audio = audioRef.current
    if (!audio) return

    const update = () => {
      setCurrentTime(audio.currentTime)
      if (!audio.paused) animRef.current = requestAnimationFrame(update)
    }

    const onPlay = () => {
      animRef.current = requestAnimationFrame(update)
    }
    const onPause = () => {
      cancelAnimationFrame(animRef.current)
      setPlaying(false)
    }
    const onEnded = () => {
      setPlaying(false)
      setCurrentTime(0)
    }

    audio.addEventListener("play", onPlay)
    audio.addEventListener("pause", onPause)
    audio.addEventListener("ended", onEnded)

    return () => {
      audio.removeEventListener("play", onPlay)
      audio.removeEventListener("pause", onPause)
      audio.removeEventListener("ended", onEnded)
      cancelAnimationFrame(animRef.current)
    }
  }, [])

  const totalDuration = duration ?? audioRef.current?.duration ?? 0
  const progress = totalDuration > 0 ? (currentTime / totalDuration) * 100 : 0

  return (
    <div className={`flex flex-col gap-1 ${className ?? ""}`}>
      <div className="flex items-center gap-2">
        {/* biome-ignore lint/a11y/useMediaCaption: 语音消息无字幕 */}
        <audio ref={audioRef} src={src} preload="metadata" />

        <Button type="button" variant="ghost" size="icon" onClick={toggle} aria-label={playing ? "暂停" : "播放"}>
          {playing ? <Pause className="size-4" /> : <Play className="size-4" />}
        </Button>

        {/* 进度条 */}
        <div className="flex-1 h-1 bg-muted rounded-full overflow-hidden">
          <div
            className="h-full bg-primary transition-[width] duration-100"
            style={{ width: `${progress}%` }}
          />
        </div>

        {/* 时长 */}
        <span className="text-xs text-muted-foreground font-mono min-w-[3ch]">
          {formatDuration(playing ? currentTime : (totalDuration || 0))}
        </span>
      </div>

      {/* 转文字占位 */}
      {showTranscript && (
        <span className="text-xs text-muted-foreground italic pl-10">[语音转文字]</span>
      )}
    </div>
  )
}
