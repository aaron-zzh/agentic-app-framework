/**
 * 语音配置——STT/TTS 模式选择（浏览器原生 or 后端接口）
 * 持久化到 localStorage
 * @author AaronZZH & Kiro
 */

import { create } from "zustand"
import { persist } from "zustand/middleware"
import { buildApiUrl } from "@/lib/api/config"
import { useAuthStore } from "@/lib/store/auth-store"

export type VoiceMode = "browser" | "server"

interface VoiceConfigStore {
  sttMode: VoiceMode
  ttsMode: VoiceMode
  ttsVoice: string
  setSttMode: (mode: VoiceMode) => void
  setTtsMode: (mode: VoiceMode) => void
  setTtsVoice: (voice: string) => void
}

/** 持久化版本：v1 起 ttsMode 默认改为 server（AAF-123 #12302） */
const VOICE_CONFIG_VERSION = 1

export const useVoiceConfig = create<VoiceConfigStore>()(
  persist(
    (set) => ({
      sttMode: "browser",
      ttsMode: "server",
      // 空串表示使用后端默认音色（CosyVoice，见 aaf.speech.tts.voice）
      ttsVoice: "",
      setSttMode: (sttMode) => set({ sttMode }),
      setTtsMode: (ttsMode) => set({ ttsMode }),
      setTtsVoice: (ttsVoice) => set({ ttsVoice })
    }),
    {
      name: "aaf-voice-config",
      version: VOICE_CONFIG_VERSION,
      // v0 没有任何界面能修改 ttsMode / ttsVoice，已存值只可能是旧默认值：
      // ttsMode 一次性改为 server；ttsVoice 旧默认 Cherry 是 Qwen-TTS 音色，CosyVoice 不识别，清空走后端默认。
      // 返回值只含持久化字段，由 persist 与初始状态合并补齐 action
      migrate: (persisted, version) => {
        const state = persisted as VoiceConfigStore
        return version < 1 ? { ...state, ttsMode: "server", ttsVoice: "" } : state
      }
    }
  )
)

/** 获取认证头 */
function getAuthHeaders(): Record<string, string> {
  const token = useAuthStore.getState().accessToken
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/** 调用后端 STT 接口 */
export async function serverStt(audioBlob: Blob, lang = "zh-CN"): Promise<string> {
  const form = new FormData()
  form.append("audio", audioBlob, "audio.wav")
  form.append("lang", lang)
  const res = await fetch(buildApiUrl("/voice/stt"), {
    method: "POST",
    headers: getAuthHeaders(),
    body: form
  })
  if (!res.ok) throw new Error(`STT 失败: ${res.status}`)
  const data = (await res.json()) as { data: string }
  return data.data
}

/**
 * 调用后端 TTS 流式接口，逐分片回调原始 PCM（16000Hz、单声道、16bit 小端）。
 *
 * 分片不保证按采样对齐，调用方需自行拼接奇数字节（见 createPcm16StreamDecoder）。
 * signal 中止时请求与读取一并终止，Promise 以 AbortError 拒绝。
 */
export async function serverTtsStream(
  text: string,
  voice: string,
  onChunk: (chunk: ArrayBuffer) => void,
  signal?: AbortSignal
): Promise<void> {
  const res = await fetch(buildApiUrl("/voice/tts/stream"), {
    method: "POST",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify({ text, voice }),
    signal
  })
  if (!res.ok || !res.body) throw new Error(`TTS 失败: ${res.status}`)
  const reader = res.body.getReader()
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    // value 可能是更大 buffer 上的视图，按 byteOffset/byteLength 切出本分片
    onChunk(value.buffer.slice(value.byteOffset, value.byteOffset + value.byteLength))
  }
}
