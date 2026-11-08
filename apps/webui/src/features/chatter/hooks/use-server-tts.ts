/**
 * 服务端流式 TTS 播放（AAF-123 #12302）
 *
 * 接收 `POST /api/voice/tts/stream` 返回的 PCM 分片（16000Hz、单声道、16bit 小端），
 * 转 Float32 后按 nextStartTime 顺序排队播放，避免分片叠音。
 * 全局同一时间只播一条：开始新播放时停止上一条（含其他消息的实例）。
 *
 * @example
 * const tts = useServerTts()
 * tts.state === "idle" ? tts.play(text, voice).catch(() => toast.error("播放失败")) : tts.stop()
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { serverTtsStream } from "@/lib/store/voice-config"
import { createPcm16StreamDecoder } from "@/lib/utils/audio"

/** 后端 TTS 输出采样率 */
const TTS_SAMPLE_RATE = 16000

export type ServerTtsState = "idle" | "loading" | "playing"

interface PlaybackSession {
  controller: AbortController
  ctx: AudioContext
  sources: Set<AudioBufferSourceNode>
  disposed: boolean
  /** 结束本会话并让所属 hook 实例回到 idle */
  end: () => void
}

/** 当前正在播放的会话（模块级单例，保证同一时间只播一条；不含服务端数据） */
let activeSession: PlaybackSession | null = null

/** 中止请求、停掉已排队的 source 并关闭 AudioContext；重复调用无副作用 */
function disposeSession(session: PlaybackSession) {
  if (session.disposed) return
  session.disposed = true
  session.controller.abort()
  for (const source of session.sources) {
    source.onended = null
    source.stop()
    source.disconnect()
  }
  session.sources.clear()
  void session.ctx.close()
  if (activeSession === session) activeSession = null
}

export function useServerTts() {
  const [state, setState] = useState<ServerTtsState>("idle")
  const sessionRef = useRef<PlaybackSession | null>(null)

  const stop = useCallback(() => {
    sessionRef.current?.end()
  }, [])

  const play = useCallback(async (text: string, voice: string): Promise<void> => {
    if (!text) return
    activeSession?.end()

    // AudioContext 在点击回调内同步创建，避免被自动播放策略挂起
    const session: PlaybackSession = {
      controller: new AbortController(),
      ctx: new AudioContext(),
      sources: new Set(),
      disposed: false,
      end: () => {
        disposeSession(session)
        if (sessionRef.current !== session) return
        sessionRef.current = null
        setState("idle")
      }
    }
    sessionRef.current = session
    activeSession = session
    setState("loading")

    const decoder = createPcm16StreamDecoder()
    let nextStartTime = 0
    let streamDone = false

    // 流已结束且排队的 source 全部播完 → 自动回到 idle
    const endIfDrained = () => {
      if (streamDone && session.sources.size === 0) session.end()
    }

    try {
      await serverTtsStream(
        text,
        voice,
        (chunk) => {
          if (session.disposed) return
          const samples = decoder.push(new Uint8Array(chunk))
          if (samples.length === 0) return
          const { ctx } = session
          // 按 16000Hz 建 buffer，AudioContext 实际采样率不同时由浏览器重采样
          const buffer = ctx.createBuffer(1, samples.length, TTS_SAMPLE_RATE)
          buffer.copyToChannel(samples, 0)
          const source = ctx.createBufferSource()
          source.buffer = buffer
          source.connect(ctx.destination)
          source.onended = () => {
            session.sources.delete(source)
            source.disconnect()
            endIfDrained()
          }
          const startAt = Math.max(ctx.currentTime, nextStartTime)
          source.start(startAt)
          nextStartTime = startAt + buffer.duration
          session.sources.add(source)
          if (sessionRef.current === session) setState("playing")
        },
        session.controller.signal
      )
      streamDone = true
      endIfDrained()
    } catch (error) {
      // 主动停止（含切换到其他消息、卸载）导致的中止不算失败
      if (session.disposed) return
      session.end()
      throw error
    }
  }, [])

  // 卸载时停止播放并释放资源
  useEffect(() => stop, [stop])

  return { state, play, stop }
}
