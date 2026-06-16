/**
 * OmniVoiceAdapter——基于后端 /ws/omni-realtime 的实时语音适配器
 * 实现 assistant-ui RealtimeVoiceAdapter 接口，接入 useVoiceState/useVoiceControls
 *
 * @author AaronZZH & Kiro
 */

import { createVoiceSession, type RealtimeVoiceAdapter } from "@assistant-ui/react"
import { buildWsUrl } from "@/lib/api/config"
import { float32ToPcm16 } from "@/lib/utils/audio"

export class OmniVoiceAdapter implements RealtimeVoiceAdapter {
  private _getToken: () => string | null | undefined

  constructor(options: { getToken: () => string | null | undefined }) {
    this._getToken = options.getToken
  }

  connect(options: { abortSignal?: AbortSignal }): RealtimeVoiceAdapter.Session {
    const getToken = this._getToken

    return createVoiceSession(options, async (helpers) => {
      const params = new URLSearchParams({ vad: "true", token: getToken() ?? "" })
      const ws = new WebSocket(buildWsUrl(`/ws/omni-realtime?${params.toString()}`))

      // 播放侧：24kHz AudioContext，无缝排队播放
      let playbackCtx: AudioContext | null = null
      let playhead = 0

      const playChunk = (base64Data: string) => {
        try {
          if (!playbackCtx || playbackCtx.state === "closed") {
            playbackCtx = new AudioContext({ sampleRate: 24000 })
            playhead = playbackCtx.currentTime
          }
          const binary = atob(base64Data)
          const bytes = new Uint8Array(binary.length)
          for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
          const int16 = new Int16Array(bytes.buffer)
          const float32 = new Float32Array(int16.length)
          for (let i = 0; i < int16.length; i++) float32[i] = int16[i] / 0x8000
          const buf = playbackCtx.createBuffer(1, float32.length, 24000)
          buf.getChannelData(0).set(float32)
          const src = playbackCtx.createBufferSource()
          src.buffer = buf
          src.connect(playbackCtx.destination)
          const startAt = Math.max(playbackCtx.currentTime, playhead)
          src.start(startAt)
          playhead = startAt + buf.duration
        } catch {
          // 静默忽略
        }
      }

      // 采集侧
      let audioCtx: AudioContext | null = null
      let processor: ScriptProcessorNode | null = null
      let stream: MediaStream | null = null

      ws.onopen = async () => {
        helpers.setStatus({ type: "running" })
        helpers.emitMode("listening")

        try {
          stream = await navigator.mediaDevices.getUserMedia({
            audio: { sampleRate: 16000, channelCount: 1 }
          })
          audioCtx = new AudioContext({ sampleRate: 16000 })
          const src = audioCtx.createMediaStreamSource(stream)
          processor = audioCtx.createScriptProcessor(4096, 1, 1)
          processor.onaudioprocess = (e: AudioProcessingEvent) => {
            if (helpers.isDisposed() || ws.readyState !== WebSocket.OPEN) return
            const pcm16 = float32ToPcm16(e.inputBuffer.getChannelData(0))

            // 实时音量上报（低频段均值）
            const data = e.inputBuffer.getChannelData(0)
            const rms = Math.sqrt(data.reduce((s, v) => s + v * v, 0) / data.length)
            helpers.emitVolume(Math.min(1, rms * 4))

            const bytes = new Uint8Array(pcm16.buffer as ArrayBuffer)
            let str = ""
            for (let i = 0; i < bytes.byteLength; i++) str += String.fromCharCode(bytes[i])
            ws.send(JSON.stringify({ type: "audio", data: btoa(str) }))
          }
          src.connect(processor)
          processor.connect(audioCtx.destination)
        } catch (err) {
          helpers.end("error", err instanceof Error ? err : new Error(String(err)))
        }
      }

      ws.onmessage = (event: MessageEvent) => {
        if (helpers.isDisposed()) return
        const msg = JSON.parse(event.data as string) as {
          type: string
          text?: string
          audioData?: string
        }
        switch (msg.type) {
          case "audio_transcript_delta":
            // 流式 assistant 转录（isFinal=false）
            helpers.emitTranscript({ role: "assistant", text: msg.text ?? "", isFinal: false })
            break
          case "transcript_done":
            helpers.emitTranscript({ role: "assistant", text: msg.text ?? "", isFinal: true })
            helpers.emitMode("listening")
            break
          case "audio_delta":
            helpers.emitMode("speaking")
            if (msg.audioData) playChunk(msg.audioData)
            break
          case "input_transcript":
            helpers.emitTranscript({ role: "user", text: msg.text ?? "", isFinal: true })
            break
          case "speech_started":
            helpers.emitMode("listening")
            break
          case "error":
            helpers.end("error", new Error(msg.text ?? "未知错误"))
            break
        }
      }

      ws.onclose = () => {
        if (!helpers.isDisposed()) helpers.end("finished")
      }
      ws.onerror = () => ws.close()

      const cleanup = () => {
        processor?.disconnect()
        processor = null
        audioCtx?.close()
        audioCtx = null
        playbackCtx?.close()
        playbackCtx = null
        if (stream) {
          for (const t of stream.getTracks()) t.stop()
          stream = null
        }
      }

      return {
        disconnect: () => {
          cleanup()
          ws.close()
        },
        mute: () => {
          if (stream) for (const t of stream.getAudioTracks()) t.enabled = false
        },
        unmute: () => {
          if (stream) for (const t of stream.getAudioTracks()) t.enabled = true
        }
      }
    })
  }
}
