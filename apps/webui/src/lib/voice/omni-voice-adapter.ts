/**
 * OmniVoiceAdapter——基于后端 /ws/realtime-conversation 的实时语音适配器
 * 实现 assistant-ui RealtimeVoiceAdapter 接口，接入 useVoiceState/useVoiceControls
 *
 * @author AaronZZH & Kiro
 */

import { createVoiceSession, type RealtimeVoiceAdapter } from "@assistant-ui/react"
import { buildWsUrl } from "@/lib/api/config"
import { float32ToPcm16 } from "@/lib/utils/audio"
import { parseOmniRealtimeReadyEvent } from "@/lib/voice/omni-realtime-protocol"

interface RealtimeEvent {
  type: string
  text?: string
  message?: string
  fatal?: boolean
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value)
}

function parseRealtimeEvent(data: unknown): RealtimeEvent | null {
  if (typeof data !== "string") return null

  try {
    const parsed: unknown = JSON.parse(data)
    if (!isRecord(parsed) || typeof parsed.type !== "string") return null

    const { type, text, message, fatal } = parsed
    if (typeof type !== "string") return null

    return {
      type,
      ...(typeof text === "string" ? { text } : {}),
      ...(typeof message === "string" ? { message } : {}),
      ...(typeof fatal === "boolean" ? { fatal } : {})
    }
  } catch {
    return null
  }
}

/** Omni Realtime v1 的 assistant-ui 实时语音适配器。 */
export class OmniVoiceAdapter implements RealtimeVoiceAdapter {
  private readonly getToken: () => string | null | undefined

  constructor(options: { getToken: () => string | null | undefined }) {
    this.getToken = options.getToken
  }

  connect(options: { abortSignal?: AbortSignal }): RealtimeVoiceAdapter.Session {
    const getToken = this.getToken

    return createVoiceSession(options, async (helpers) => {
      const token = getToken()
      if (!token) throw new Error("请先登录后使用实时语音")
      const params = new URLSearchParams({ token })
      const ws = new WebSocket(buildWsUrl(`/ws/realtime-conversation?${params.toString()}`))
      ws.binaryType = "arraybuffer"

      let playbackCtx: AudioContext | null = null
      let playhead = 0
      let audioOutputActive = false
      let audioCtx: AudioContext | null = null
      let processor: ScriptProcessorNode | null = null
      let stream: MediaStream | null = null
      let stopped = false
      let cleanedUp = false
      let ended = false
      let responding = false
      let responseText = ""
      let sessionClosing = false

      const closePlayback = () => {
        audioOutputActive = false
        void playbackCtx?.close()
        playbackCtx = null
        playhead = 0
      }

      const playChunk = (pcm: ArrayBuffer) => {
        if (!audioOutputActive || pcm.byteLength === 0 || pcm.byteLength % 2 !== 0) return

        try {
          if (!playbackCtx || playbackCtx.state === "closed") {
            playbackCtx = new AudioContext({ sampleRate: 24000 })
            playhead = playbackCtx.currentTime
          }

          const int16 = new Int16Array(pcm)
          const float32 = new Float32Array(int16.length)
          for (let i = 0; i < int16.length; i++) float32[i] = int16[i] / 0x8000

          const buffer = playbackCtx.createBuffer(1, float32.length, 24000)
          buffer.getChannelData(0).set(float32)
          const source = playbackCtx.createBufferSource()
          source.buffer = buffer
          source.connect(playbackCtx.destination)
          const startAt = Math.max(playbackCtx.currentTime, playhead)
          source.start(startAt)
          playhead = startAt + buffer.duration
        } catch {
          closePlayback()
        }
      }

      const end = (reason: "error" | "finished", error?: Error) => {
        if (ended || helpers.isDisposed()) return
        ended = true
        if (reason === "error") {
          helpers.end("error", error ?? new Error("语音会话异常结束"))
          return
        }
        helpers.end("finished")
      }

      const cleanup = () => {
        if (cleanedUp) return
        cleanedUp = true
        stopped = true
        processor?.disconnect()
        processor = null
        void audioCtx?.close()
        audioCtx = null
        closePlayback()
        if (stream) {
          for (const track of stream.getTracks()) track.stop()
          stream = null
        }
      }

      const closeSession = () => {
        cleanup()
        if (sessionClosing || ws.readyState !== WebSocket.OPEN) {
          ws.close()
          return
        }

        sessionClosing = true
        ws.send(JSON.stringify({ type: "session.close" }))
      }

      const cancelResponse = () => {
        if (!responding || ws.readyState !== WebSocket.OPEN) return
        closePlayback()
        ws.send(JSON.stringify({ type: "response.cancel" }))
      }

      const startCapture = async () => {
        try {
          const captureStream = await navigator.mediaDevices.getUserMedia({
            audio: { sampleRate: 16000, channelCount: 1 }
          })
          if (stopped || helpers.isDisposed() || ws.readyState !== WebSocket.OPEN) {
            for (const track of captureStream.getTracks()) track.stop()
            return
          }

          stream = captureStream
          audioCtx = new AudioContext({ sampleRate: 16000 })
          const source = audioCtx.createMediaStreamSource(stream)
          processor = audioCtx.createScriptProcessor(4096, 1, 1)
          processor.onaudioprocess = (event: AudioProcessingEvent) => {
            if (stopped || helpers.isDisposed() || ws.readyState !== WebSocket.OPEN) return
            const samples = event.inputBuffer.getChannelData(0)
            const pcm16 = float32ToPcm16(samples)
            const rms = Math.sqrt(
              samples.reduce((sum, sample) => sum + sample * sample, 0) / samples.length
            )
            helpers.emitVolume(Math.min(1, rms * 4))
            ws.send(pcm16.buffer)
          }
          source.connect(processor)
          processor.connect(audioCtx.destination)
          helpers.setStatus({ type: "running" })
          helpers.emitMode("listening")
        } catch (error) {
          cleanup()
          end("error", error instanceof Error ? error : new Error(String(error)))
          ws.close()
        }
      }

      ws.onopen = () => {
        if (stopped || ws.readyState !== WebSocket.OPEN) return
        ws.send(
          JSON.stringify({
            type: "hello",
            protocol: "omni-realtime.v1",
            endpointing: "server"
          })
        )
      }

      ws.onmessage = (event: MessageEvent<unknown>) => {
        if (helpers.isDisposed()) return
        if (event.data instanceof ArrayBuffer) {
          playChunk(event.data)
          return
        }

        const message = parseRealtimeEvent(event.data)
        if (!message) return

        switch (message.type) {
          case "ready":
            if (parseOmniRealtimeReadyEvent(event.data) === null) {
              cleanup()
              end("error", new Error("Omni Realtime ready 合同无效"))
              ws.close()
              break
            }
            void startCapture()
            break
          case "input_audio.speech_started":
            cancelResponse()
            helpers.emitMode("listening")
            break
          case "input_audio.transcript.delta":
            helpers.emitTranscript({ role: "user", text: message.text ?? "", isFinal: false })
            break
          case "input_audio.transcript.completed":
            helpers.emitTranscript({ role: "user", text: message.text ?? "", isFinal: true })
            break
          case "response.created":
            responseText = ""
            responding = true
            break
          case "response.text.delta": {
            const text = message.text ?? ""
            responseText += text
            responding = true
            helpers.emitTranscript({ role: "assistant", text, isFinal: false })
            break
          }
          case "response.audio.started":
            responding = true
            audioOutputActive = true
            helpers.emitMode("speaking")
            break
          case "response.audio.done":
            audioOutputActive = false
            break
          case "response.completed":
            responding = false
            if (responseText) {
              helpers.emitTranscript({ role: "assistant", text: responseText, isFinal: true })
            }
            responseText = ""
            helpers.emitMode("listening")
            break
          case "response.cancelled":
            responding = false
            responseText = ""
            closePlayback()
            helpers.emitMode("listening")
            break
          case "error":
            if (message.fatal) {
              cleanup()
              end("error", new Error(message.message ?? "未知错误"))
              ws.close()
            }
            break
          case "closed":
            ws.close()
            break
        }
      }

      ws.onclose = () => {
        cleanup()
        end("finished")
      }
      ws.onerror = () => ws.close()
      if (options.abortSignal?.aborted) {
        closeSession()
      } else {
        options.abortSignal?.addEventListener("abort", closeSession, { once: true })
      }

      return {
        disconnect: closeSession,
        mute: () => {
          if (stream) for (const track of stream.getAudioTracks()) track.enabled = false
        },
        unmute: () => {
          if (stream) for (const track of stream.getAudioTracks()) track.enabled = true
        }
      }
    })
  }
}
