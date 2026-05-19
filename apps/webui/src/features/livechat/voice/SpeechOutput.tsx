/**
 * 语音输出组件（TTS）——基于 SpeechSynthesis API
 *
 * 功能：
 * - 播放/暂停/停止/倍速控制
 * - 自动朗读模式开关
 * - 播放状态动画指示器
 *
 * @author AaronZZH & Kiro
 */
"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { Pause, Play, Square, Volume2, VolumeX } from "lucide-react"
import { Button } from "@/components/ui/button"

type PlayState = "idle" | "playing" | "paused"

interface SpeechOutputProps {
  /** 要朗读的文本 */
  text: string
  /** 是否自动朗读（AI 回复时自动播放） */
  autoPlay?: boolean
  /** 语音设置 */
  voice?: SpeechSynthesisVoice | null
  /** 语速 0.5~2.0 */
  rate?: number
  /** 音调 0.5~2.0 */
  pitch?: number
  /** 播放完成回调 */
  onEnd?: () => void
  className?: string
}

export function SpeechOutput({
  text,
  autoPlay = false,
  voice = null,
  rate = 1,
  pitch = 1,
  onEnd,
  className,
}: SpeechOutputProps) {
  const [state, setState] = useState<PlayState>("idle")
  const [autoMode, setAutoMode] = useState(autoPlay)
  const utteranceRef = useRef<SpeechSynthesisUtterance | null>(null)

  /** 停止当前播放 */
  const stop = useCallback(() => {
    window.speechSynthesis.cancel()
    setState("idle")
    utteranceRef.current = null
  }, [])

  /** 播放文本 */
  const play = useCallback(() => {
    if (!text) return
    stop()

    const utterance = new SpeechSynthesisUtterance(text)
    if (voice) utterance.voice = voice
    utterance.rate = rate
    utterance.pitch = pitch
    utterance.lang = voice?.lang ?? "zh-CN"

    utterance.onend = () => {
      setState("idle")
      onEnd?.()
    }
    utterance.onerror = () => setState("idle")

    utteranceRef.current = utterance
    window.speechSynthesis.speak(utterance)
    setState("playing")
  }, [text, voice, rate, pitch, stop, onEnd])

  /** 暂停 */
  const pause = useCallback(() => {
    window.speechSynthesis.pause()
    setState("paused")
  }, [])

  /** 恢复 */
  const resume = useCallback(() => {
    window.speechSynthesis.resume()
    setState("playing")
  }, [])

  /** 自动朗读 */
  useEffect(() => {
    if (autoMode && text) {
      play()
    }
  }, [autoMode, text, play])

  /** 组件卸载时停止 */
  useEffect(() => stop, [stop])

  return (
    <div className={`flex items-center gap-2 ${className ?? ""}`}>
      {/* 播放/暂停按钮 */}
      {state === "idle" && (
        <Button type="button" variant="ghost" size="icon" onClick={play} aria-label="播放">
          <Play className="size-4" />
        </Button>
      )}
      {state === "playing" && (
        <Button type="button" variant="ghost" size="icon" onClick={pause} aria-label="暂停">
          <Pause className="size-4" />
        </Button>
      )}
      {state === "paused" && (
        <Button type="button" variant="ghost" size="icon" onClick={resume} aria-label="继续">
          <Play className="size-4" />
        </Button>
      )}

      {/* 停止按钮 */}
      {state !== "idle" && (
        <Button type="button" variant="ghost" size="icon" onClick={stop} aria-label="停止">
          <Square className="size-3" />
        </Button>
      )}

      {/* 自动朗读开关 */}
      <Button
        type="button"
        variant="ghost"
        size="icon"
        onClick={() => setAutoMode((v) => !v)}
        aria-label={autoMode ? "关闭自动朗读" : "开启自动朗读"}
      >
        {autoMode ? <Volume2 className="size-4" /> : <VolumeX className="size-4" />}
      </Button>

      {/* 播放状态指示器 */}
      {state === "playing" && (
        <div className="flex items-end gap-0.5 h-4" aria-label="正在播放">
          <span className="w-0.5 bg-primary animate-bounce [animation-delay:0ms] h-2" />
          <span className="w-0.5 bg-primary animate-bounce [animation-delay:150ms] h-3" />
          <span className="w-0.5 bg-primary animate-bounce [animation-delay:300ms] h-4" />
          <span className="w-0.5 bg-primary animate-bounce [animation-delay:150ms] h-3" />
          <span className="w-0.5 bg-primary animate-bounce [animation-delay:0ms] h-2" />
        </div>
      )}
    </div>
  )
}
